package com.vtstudio.clearsight

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*

var revocationFetchDate by mutableStateOf("未获取")
var revocationEntryCount by mutableIntStateOf(0)
var revocationUpdateResult by mutableStateOf("未更新")

private val appFallbackNames = mutableMapOf<String, String>()

data class CheckSubItem(
    val rawPath: String,
    val cleanPath: String,
    val isFound: Boolean,
    val isCritical: Boolean,
    val checkMethod: String,
    val result: String? = null,
    val appName: String? = null,
    val appIcon: Drawable? = null,
)

data class CheckCategory(val name: String, val subItems: List<CheckSubItem>, val hasIssue: Boolean)

fun loadAllCategories(context: Context): List<CheckCategory> {
    initRevocationList(context)
    val fileLines = readConfFile(context, "check.conf")
    val appLines = readConfFile(context, "appcheck.conf")
    
    // Load cross-check and fallback names from crosscheck.conf
    appFallbackNames.clear()
    val crossCheckMap = mutableMapOf<String, MutableList<String>>()
    val crossCheckLines = readConfFile(context, "crosscheck.conf")
    for (line in crossCheckLines) {
        val parts = line.split(":")
        if (parts.size >= 2) {
            val pkg = parts[0].trim()
            val name = parts[1].trim()
            if (name.isNotEmpty()) {
                appFallbackNames[pkg] = name
            }
            if (parts.size >= 3) {
                val path = parts[2].trim()
                if (path.isNotEmpty()) {
                    crossCheckMap.getOrPut(pkg) { mutableListOf() }.add(path)
                }
            }
        }
    }

    val hasRootPermission = checkRootPermission()

    val fileSubItems = processCategoryItems(context, fileLines, isAppCheck = false, hasRootPermission)
    val initialAppSubItems = processCategoryItems(context, appLines, isAppCheck = true, hasRootPermission)
    val appSubItems = initialAppSubItems.toMutableList()

    val securitySubItems = run {
        val items = mutableListOf<CheckSubItem>()
        val attestation = checkKeyAttestation()
        
        items.add(CheckSubItem(
            rawPath = "Attestation Security Level",
            cleanPath = "硬件密钥库等级",
            isFound = attestation.securityLevel == "Software",
            isCritical = attestation.securityLevel == "Software",
            checkMethod = "Android Key Attestation API",
            result = "Level: ${attestation.securityLevel}"
        ))

        items.add(CheckSubItem(
            rawPath = "Device Locked",
            cleanPath = "设备引导加载程序(Bootloader)已解锁",
            isFound = !attestation.isLocked,
            isCritical = !attestation.isLocked,
            checkMethod = "Hardware Attestation (ASN.1)",
            result = if (attestation.isLocked) "Locked (Secure)" else "Unlocked / Unknown"
        ))

        val revocationStatus = checkRevocation(attestation.serials)
        items.add(CheckSubItem(
            rawPath = "Key Revocation",
            cleanPath = "密钥状态",
            isFound = revocationStatus != "VALID",
            isCritical = revocationStatus == "REVOKED",
            checkMethod = "Google CRL Status List",
            result = "Status: $revocationStatus"
        ))

        items.add(CheckSubItem(
            rawPath = "Key Authenticity",
            cleanPath = "密钥类型",
            isFound = !attestation.isGoogleRoot,
            isCritical = !attestation.isGoogleRoot,
            checkMethod = "Root CA Verification",
            result = if (attestation.isGoogleRoot) "Official: ${attestation.rootSubject}" else "AOSP/Test: ${attestation.rootSubject}"
        ))

        val patchCheck = checkSecurityPatch()
        items.add(CheckSubItem(
            rawPath = "Security Patch Level",
            cleanPath = "Android安全补丁",
            isFound = patchCheck.isOutdated,
            isCritical = false,
            checkMethod = "System Build API",
            result = "Patch: ${patchCheck.patchDate}"
        ))

        items
    }

    val fileResults = fileSubItems.associateBy { it.cleanPath }

    for ((pkgName, paths) in crossCheckMap) {
        val triggeringPath = paths.find { fileResults[it]?.isFound == true }
        if (triggeringPath != null) {
            val existingIndex = appSubItems.indexOfFirst { it.cleanPath == pkgName }
            val identity = resolveAppIdentity(context, pkgName, hasRootPermission)

            if (existingIndex != -1) {
                val originalItem = appSubItems[existingIndex]
                appSubItems[existingIndex] = originalItem.copy(
                    isFound = true,
                    checkMethod = "File Trace: $triggeringPath",
                    result = "Matched Trace: $triggeringPath",
                    appName = identity.first ?: originalItem.appName,
                    appIcon = identity.second ?: originalItem.appIcon
                )
            } else {
                appSubItems.add(
                    CheckSubItem(
                        rawPath = pkgName,
                        cleanPath = pkgName,
                        isFound = true,
                        isCritical = true,
                        checkMethod = "File Trace: $triggeringPath",
                        result = "Matched Trace: $triggeringPath",
                        appName = identity.first,
                        appIcon = identity.second
                    )
                )
            }
        }
    }

    return listOf(
        CheckCategory(name = "Files", subItems = fileSubItems, hasIssue = fileSubItems.any { it.isFound }),
        CheckCategory(name = "Apps", subItems = appSubItems, hasIssue = appSubItems.any { it.isFound }),
        CheckCategory(name = "Security", subItems = securitySubItems, hasIssue = securitySubItems.any { it.isFound })
    )
}

fun readConfFile(context: Context, fileName: String): List<String> {
    val lines = mutableListOf<String>()
    try {
        context.assets.open(fileName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isNotEmpty()) {
                        lines.add(trimmed)
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return lines
}

fun processCategoryItems(context: Context, lines: List<String>, isAppCheck: Boolean, hasRootPermission: Boolean): List<CheckSubItem> {
    if (lines.isEmpty()) return emptyList()

    val tasks = lines.map { line ->
        val isCritical = line.startsWith("!")
        val cleanTarget = when {
            isCritical -> line.substring(1).trim()
            line.startsWith("?") -> line.substring(1).trim()
            else -> line
        }
        Triple(line, cleanTarget, isCritical)
    }

    val targets = tasks.map { it.second }
    val results: Map<String, Pair<Boolean, String>> = if (isAppCheck) {
        if (hasRootPermission) checkAppsWithRoot(targets) else checkAppsWithPm(context, targets)
    } else {
        if (hasRootPermission) checkPathsWithRoot(targets) else checkPathsWithNormalApi(targets)
    }

    return tasks.map { (line, cleanTarget, isCritical) ->
        val res = results[cleanTarget] ?: Pair(false, "Unknown Engine")
        val (fetchedName, fetchedIcon) = if (isAppCheck) resolveAppIdentity(context, cleanTarget, hasRootPermission) else Pair(null, null)

        CheckSubItem(
            rawPath = line,
            cleanPath = cleanTarget,
            isFound = res.first,
            isCritical = isCritical,
            checkMethod = res.second,
            appName = fetchedName,
            appIcon = fetchedIcon
        )
    }
}

fun resolveAppIdentity(context: Context, pkgName: String, hasRoot: Boolean): Pair<String?, Drawable?> {
    val pm = context.packageManager
    val defaultAppIcon = try { pm.defaultActivityIcon } catch (_: Exception) { null }

    try {
        val appInfo = pm.getApplicationInfo(pkgName, 0)
        val label = appInfo.loadLabel(pm).toString()
        val icon = appInfo.loadIcon(pm)
        if (label.isNotEmpty() && (label != pkgName)) return Pair(label, icon)
    } catch (_: Exception) {}

    if (hasRoot) {
        val line = ShellExecutor.runCommand("pm path $pkgName")
        if (!line.isNullOrEmpty() && line.startsWith("package:")) {
            val apkPath = line.substring(8).trim()
            val archiveInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA)
            val appInfo = archiveInfo?.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
                val label = appInfo.loadLabel(pm).toString()
                val icon = appInfo.loadIcon(pm)
                if (icon != null && icon != defaultAppIcon) return Pair(label, icon)
                try {
                    val res = pm.getResourcesForApplication(appInfo)
                    if (appInfo.icon != 0) {
                        val drawable = res.getDrawable(appInfo.icon, null)
                        if (drawable != null) return Pair(label, drawable)
                    }
                } catch (_: Exception) {}
                if (label.isNotEmpty()) return Pair(label, icon ?: defaultAppIcon)
            }
        }
    }

    val fallbackName = appFallbackNames[pkgName]
    return Pair(fallbackName, defaultAppIcon)
}

object ShellExecutor {
    fun runCommand(cmd: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val isReader = BufferedReader(InputStreamReader(process.inputStream))
            os.writeBytes("$cmd\n")
            os.writeBytes("exit\n")
            os.flush()
            val result = isReader.readLine()
            os.close()
            isReader.close()
            process.destroy()
            result
        } catch (_: Exception) { null }
    }

    fun checkPaths(paths: List<String>): Map<String, Pair<Boolean, String>> {
        val resultMap = mutableMapOf<String, Pair<Boolean, String>>()
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val isReader = BufferedReader(InputStreamReader(process.inputStream))
            for (path in paths) {
                os.writeBytes("if [ -e \"$path\" ]; then echo \"1\"; else echo \"0\"; fi\n")
            }
            os.writeBytes("echo \"[END]\"\n")
            os.flush()
            for (path in paths) {
                val line = isReader.readLine() ?: "0"
                if (line == "[END]") break
                resultMap[path] = Pair(line.trim() == "1", "SU / Root Engine")
            }
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            isReader.close()
            process.destroy()
        } catch (_: Exception) {}
        return resultMap
    }

    fun checkApps(packageNames: List<String>): Map<String, Pair<Boolean, String>> {
        val resultMap = mutableMapOf<String, Pair<Boolean, String>>()
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val isReader = BufferedReader(InputStreamReader(process.inputStream))
            for (pkg in packageNames) {
                os.writeBytes("pm list packages | grep -q \"package:$pkg\" && echo \"1\" || echo \"0\"\n")
            }
            os.writeBytes("echo \"[END]\"\n")
            os.flush()
            for (pkg in packageNames) {
                val line = isReader.readLine() ?: "0"
                if (line == "[END]") break
                resultMap[pkg] = Pair(line.trim() == "1", "SU / PM Shell")
            }
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            isReader.close()
            process.destroy()
        } catch (_: Exception) {}
        return resultMap
    }
}

fun checkRootPermission(): Boolean {
    val line = ShellExecutor.runCommand("id")
    return line != null && line.contains("uid=0")
}

fun checkPathsWithRoot(paths: List<String>): Map<String, Pair<Boolean, String>> = ShellExecutor.checkPaths(paths)

fun checkAppsWithRoot(packageNames: List<String>): Map<String, Pair<Boolean, String>> = ShellExecutor.checkApps(packageNames)

fun getAppVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }
}

fun getBuildInfo(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val type = if (isDebug) "debug" else "release"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        "$type-$versionCode"
    } catch (_: Exception) {
        "release-0"
    }
}

fun checkAppsWithPm(context: Context, packageNames: List<String>): Map<String, Pair<Boolean, String>> {
    val pm = context.packageManager
    return packageNames.associateWith { pkg ->
        try {
            pm.getPackageInfo(pkg, 0)
            Pair(true, "PackageManager API")
        } catch (_: Exception) {
            Pair(false, "PackageManager API")
        }
    }
}

fun checkPathsWithNormalApi(paths: List<String>): Map<String, Pair<Boolean, String>> {
    return paths.associateWith { path ->
        val file = File(path)
        Pair(file.exists(), "Standard File API")
    }
}

data class AttestationResult(
    val securityLevel: String,
    val isLocked: Boolean,
    val serials: List<String>,
    val isGoogleRoot: Boolean,
    val rootSubject: String
)

fun checkKeyAttestation(): AttestationResult {
    val alias = "ClearSightAttestationKey"
    try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }

        val kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
        kpg.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setAttestationChallenge("ClearSight".toByteArray())
                .build()
        )
        kpg.generateKeyPair()

        val chain = keyStore.getCertificateChain(alias)
        if (chain == null || chain.isEmpty()) return AttestationResult("Unknown", isLocked = false, serials = emptyList(), isGoogleRoot = false, rootSubject = "No Chain")

        val rootCert = chain.last() as X509Certificate
        val rootSubject = rootCert.subjectDN.name
        
        val hasGoogleKeywords = rootSubject.contains("Google", ignoreCase = true) || 
                               rootSubject.contains("Android Keystore Root", ignoreCase = true) ||
                               rootSubject.contains("Key Attestation CA", ignoreCase = true)
        
        val isGoogleHardwareSerial = rootSubject.contains("f92009e853b6b045", ignoreCase = true) ||
                                    rootSubject.contains("66393230303965383533623662303435", ignoreCase = true)
                               
        val isAospTest = rootSubject.contains("Software", ignoreCase = true) || 
                        rootSubject.contains("Emulator", ignoreCase = true) ||
                        rootSubject.contains("AOSP", ignoreCase = true)

        val isGoogleRoot = (hasGoogleKeywords || isGoogleHardwareSerial) && !isAospTest

        val serials = (0 until chain.size - 1).map { (chain[it] as X509Certificate).serialNumber.toString(16).lowercase() }

        val leafCert = chain[0] as X509Certificate
        val extensionData = leafCert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17") ?: return AttestationResult("Software", isLocked = false, serials = serials, isGoogleRoot = isGoogleRoot, rootSubject = rootSubject)

        val derStr = extensionData.joinToString("") { "%02x".format(it) }
        
        var securityLevel = "Software"
        var deviceLocked = false

        if (derStr.contains("0a0101")) securityLevel = "TEE"
        if (derStr.contains("0a0102")) securityLevel = "StrongBox"
        if (derStr.contains("0101ff")) deviceLocked = true

        return AttestationResult(securityLevel, deviceLocked, serials, isGoogleRoot, rootSubject)
    } catch (e: Exception) {
        return AttestationResult("Error", false, emptyList(), false, e.message ?: "Unknown Error")
    }
}

@Serializable
data class RevocationList(val entries: Map<String, RevocationEntry>)

@Serializable
data class RevocationEntry(val status: String, val reason: String? = null)

private var cachedRevocationList: RevocationList? = null
private val jsonParser = Json { ignoreUnknownKeys = true }

fun fetchRevocationList(context: Context) {
    initRevocationList(context)

    try {
        val url = URL("https://android.googleapis.com/attestation/status")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        if (connection.responseCode == 200) {
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val decoded = jsonParser.decodeFromString<RevocationList>(jsonString)
            cachedRevocationList = decoded
            revocationEntryCount = decoded.entries.size
            revocationFetchDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            revocationUpdateResult = "更新成功"
            
            // Save to cache
            try { 
                val cacheFile = File(context.cacheDir, "revocation_list.json")
                cacheFile.writeText(jsonString) 
            } catch (_: Exception) {}
        } else {
            revocationUpdateResult = if (cachedRevocationList != null) "更新失败,使用缓存"
            else "失败 (${connection.responseCode})"
        }
    } catch (_: java.net.SocketTimeoutException) {
        revocationUpdateResult = if (cachedRevocationList != null) "超时,使用缓存"
        else "超时"
    } catch (_: Exception) {
        revocationUpdateResult = if (cachedRevocationList != null) "更新失败,使用缓存"
        else "失败"
    }
}

fun initRevocationList(context: Context) {
    if (cachedRevocationList != null) return
    val cacheFile = File(context.cacheDir, "revocation_list.json")
    if (cacheFile.exists()) {
        try {
            val cachedJson = cacheFile.readText()
            val decoded = jsonParser.decodeFromString<RevocationList>(cachedJson)
            cachedRevocationList = decoded
            revocationEntryCount = decoded.entries.size
            revocationFetchDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(cacheFile.lastModified()))
            revocationUpdateResult = "已加载缓存"
        } catch (_: Exception) {}
    }
}

fun checkRevocation(serials: List<String>): String {
    if (serials.isEmpty()) return "UNKNOWN"
    val list = cachedRevocationList ?: return "NOT_FETCHED"
    
    var worstStatus = "VALID"
    for (serial in serials) {
        val status = list.entries[serial]?.status
        if (status == "REVOKED") return "REVOKED"
        if (status == "SUSPENDED") worstStatus = "SUSPENDED"
    }
    return worstStatus
}

data class PatchResult(val patchDate: String, val isOutdated: Boolean)

fun checkSecurityPatch(): PatchResult {
    val patch = Build.VERSION.SECURITY_PATCH
    if (patch.isNullOrEmpty()) return PatchResult("Unknown", isOutdated = true)
    
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val patchDate = sdf.parse(patch) ?: return PatchResult(patch, isOutdated = true)
        val sixMonthsAgo = Calendar.getInstance()
        sixMonthsAgo.add(Calendar.MONTH, -6)
        val isOutdated = patchDate.before(sixMonthsAgo.time)
        PatchResult(patch, isOutdated)
    } catch (_: Exception) {
        PatchResult(patch, isOutdated = true)
    }
}

private fun getSystemProperty(key: String): String {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
        process.inputStream.bufferedReader().use { it.readLine() ?: "" }
    } catch (_: Exception) { "" }
}

data class DeviceInfoSummary(
    val device: String,
    val hardware: String,
    val kernel: String,
    val android: String,
    val os: String,
    val fingerprint: String,
    val security: String
)

fun getDeviceInfoSummary(): DeviceInfoSummary {
    val brand = Build.BRAND
    val product = Build.PRODUCT
    val deviceName = Build.DEVICE
    val model = Build.MODEL

    val deviceStr = "$brand $model ($product $deviceName)"

    val hardware = Build.HARDWARE
    val board = Build.BOARD
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown" // Use primary ABI for cleaner look
    val hardwareStr = "$hardware $board ($abi)"

    // Get detailed kernel info from /proc/version
    val kernelStr = try {
        File("/proc/version").readText().trim().let {
            // Shorten kernel string if it's too long, focus on version and date
            if (it.length > 100) it.take(100) + "..." else it
        }
    } catch (_: Exception) {
        System.getProperty("os.version") ?: "Unknown"
    }

    val androidVer = Build.VERSION.RELEASE
    val apiLevel = Build.VERSION.SDK_INT
    val buildId = Build.ID
    val osStr = "Android $androidVer $buildId (API $apiLevel)"

    val fingerprint = Build.FINGERPRINT

    val osPatch = Build.VERSION.SECURITY_PATCH
    val vendorPatch = getSystemProperty("ro.vendor.build.security_patch")
    val securityStr = "OS: $osPatch | Vendor: $vendorPatch"

    return DeviceInfoSummary(
        device = deviceStr,
        hardware = hardwareStr,
        kernel = kernelStr,
        android = osStr,
        os = osStr,
        fingerprint = fingerprint,
        security = securityStr
    )
}
