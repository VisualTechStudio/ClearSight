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
var skipInsignificantChecks by mutableStateOf(false)
var checkRevocationOnStartup by mutableStateOf(true)
var useMirrorServer by mutableStateOf(true)

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
    val prefs = context.getSharedPreferences("clearsight_prefs", Context.MODE_PRIVATE)
    skipInsignificantChecks = prefs.getBoolean("skip_insignificant", false)
    checkRevocationOnStartup = prefs.getBoolean("check_revocation_on_startup", true)
    useMirrorServer = prefs.getBoolean("use_mirror_server", true)

    initRevocationList(context)
    val fileLines = readConfFile(context, "check.conf")
    val appLines = readConfFile(context, "appcheck.conf")
    
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
        
        items.add(
            CheckSubItem(
                rawPath = "Attestation Security Level",
                cleanPath = "硬件密钥库等级",
                isFound = attestation.securityLevel == "Software",
                isCritical = attestation.securityLevel == "Software",
                checkMethod = "Android Key Attestation API",
                result = "Level: ${attestation.securityLevel}",
            ),
        )

        val teeImpl = when {
            File("/dev/trusty-ipc-dev0").exists() -> "Trusty (Google)"
            File("/dev/qseecom").exists() -> "QTEE (Qualcomm)"
            File("/dev/teetz").exists() -> "TEE (MediaTek)"
            else -> "Generic TEE"
        }
        val isTeeHealthy = (attestation.securityLevel != "Software") && 
                          (attestation.verifiedBootState == "Verified") && 
                          (attestation.isLocked)

        items.add(
            CheckSubItem(
                rawPath = "TEE OS",
                cleanPath = "TEE 可信执行环境",
                isFound = !isTeeHealthy,
                isCritical = true,
                checkMethod = "TrustZone / Secure World",
                result = if (isTeeHealthy) "正常 ($teeImpl)" 
                         else "损坏(${attestation.verifiedBootState} / ${attestation.securityLevel})",
            ),
        )

        items.add(
            CheckSubItem(
                rawPath = "Device Locked",
                cleanPath = "设备引导加载程序(Bootloader)已解锁",
                isFound = !attestation.isLocked,
                isCritical = !attestation.isLocked,
                checkMethod = "Hardware Attestation (ASN.1)",
                result = if (attestation.isLocked) "Locked (Secure)" else "Unlocked",
            ),
        )

        val revocationStatus = checkRevocation(attestation.serials)
        items.add(
            CheckSubItem(
                rawPath = "Key Revocation",
                cleanPath = "密钥状态",
                isFound = revocationStatus != "VALID",
                isCritical = revocationStatus == "REVOKED",
                checkMethod = "Google CRL Status List",
                result = "Status: $revocationStatus",
            ),
        )

        items.add(
            CheckSubItem(
                rawPath = "Key Authenticity",
                cleanPath = "密钥类型",
                isFound = !attestation.isGoogleRoot,
                isCritical = !attestation.isGoogleRoot,
                checkMethod = "Root CA Verification",
                result = if (attestation.isGoogleRoot) "Official: ${attestation.rootSubject}" else "AOSP/Test: ${attestation.rootSubject}",
            ),
        )

        if (!skipInsignificantChecks) {
            val patchCheck = checkSecurityPatch()
            items.add(
                CheckSubItem(
                    rawPath = "Security Patch Level",
                    cleanPath = "Android安全补丁",
                    isFound = patchCheck.isOutdated,
                    isCritical = false,
                    checkMethod = "System Build API",
                    result = "Patch: ${patchCheck.patchDate}",
                ),
            )
        }

        items
    }

    val systemSubItems = mutableListOf<CheckSubItem>()
    val selinuxStatus = try {
        val process = Runtime.getRuntime().exec("getenforce")
        process.inputStream.bufferedReader().use { it.readLine()?.trim() ?: "Enforcing" }
    } catch (_: Exception) { "Enforcing" }

    systemSubItems.add(
        CheckSubItem(
            rawPath = "SELinux Status",
            cleanPath = "SELinux",
            isFound = selinuxStatus != "Enforcing",
            isCritical = selinuxStatus != "Enforcing",
            checkMethod = "Shell getenforce",
            result = selinuxStatus,
        ),
    )

    if (!skipInsignificantChecks) {
        val adbEnabled = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) != 0
        systemSubItems.add(
            CheckSubItem(
                rawPath = "USB Debugging",
                cleanPath = "USB 调试",
                isFound = adbEnabled,
                isCritical = false,
                checkMethod = "Settings.Global",
                result = if (adbEnabled) "Enabled" else "Disabled",
            ),
        )

        val devMode = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        systemSubItems.add(
            CheckSubItem(
                rawPath = "Developer Options",
                cleanPath = "开发者选项",
                isFound = devMode,
                isCritical = false,
                checkMethod = "Settings.Global",
                result = if (devMode) "Enabled" else "Disabled",
            ),
        )
    }

    for ((pkg, name) in appFallbackNames) {
        val isPandora = pkg.contains("pandora", ignoreCase = true) || name.contains("Pandora", ignoreCase = true)
        if (pkg.startsWith("system.") && !isPandora) {
            systemSubItems.add(
                CheckSubItem(
                    rawPath = pkg,
                    cleanPath = name,
                    isFound = false,
                    isCritical = false,
                    checkMethod = "System Integrity Check",
                    result = "Normal",
                )
            )
        }
    }

    val kernelLines = readConfFile(context, "kernelcheck.conf")
    val fullKernelVersion = try { File("/proc/version").readText().trim() } catch (_: Exception) { System.getProperty("os.version") ?: "Unknown" }
    
    val kernelVersion = fullKernelVersion
        .removePrefix("Linux version ")
        .split(" ", "(")
        .firstOrNull() ?: fullKernelVersion
    
    var kernelIssueFound = false
    val matchedRules = mutableListOf<String>()

    if (File("/dev/pandora").exists()) {
        kernelIssueFound = true
        matchedRules.add("found /dev/pandora")
    }

    for (line in kernelLines) {
        if (line.startsWith("#")) continue
        val parts = line.split(":", limit = 2)
        if (parts.isNotEmpty()) {
            val type = parts[0].trim()
            val pattern = if (parts.size == 2) parts[1].trim() else ""
            
            var match = false
            when (type) {
                "NON_ASCII" -> if (kernelVersion.any { it.code > 127 }) match = true
                "REGEX" -> if (pattern.isNotEmpty() && Regex(pattern).containsMatchIn(kernelVersion)) match = true
                "LITERAL" -> if (pattern.isNotEmpty() && kernelVersion.contains(pattern, ignoreCase = true)) match = true
            }
            if (match) {
                kernelIssueFound = true
                matchedRules.add(if (type == "NON_ASCII") "Non-ASCII" else pattern)
                break
            }
        }
    }

    systemSubItems.add(
        CheckSubItem(
            rawPath = "Custom Kernel",
            cleanPath = "Custom Kernel",
            isFound = kernelIssueFound, 
            isCritical = true,
            checkMethod = if (kernelIssueFound) matchedRules.joinToString(" + ") else "Kernel Name Analysis",
            result = if (kernelIssueFound) kernelVersion else "Official / Clean",
        )
    )

    val fileResults = fileSubItems.associateBy { it.cleanPath }

    for ((id, paths) in crossCheckMap) {
        val triggeringPaths = paths.filter { fileResults[it]?.isFound == true }
        if (triggeringPaths.isNotEmpty()) {
            val triggeringPath = triggeringPaths.first()
            
            if (id.startsWith("system.")) {
                val displayName = appFallbackNames[id] ?: id.removePrefix("system.")
                val isPandora = id.contains("pandora", ignoreCase = true) || displayName.contains("Pandora", ignoreCase = true)
                if (isPandora) continue
                
                val index = systemSubItems.indexOfFirst { it.rawPath == id }
                val newItem = CheckSubItem(
                    rawPath = id,
                    cleanPath = displayName,
                    isFound = true,
                    isCritical = true,
                    checkMethod = "File Trace: $triggeringPath",
                    result = displayName,
                )
                if (index != -1) {
                    systemSubItems[index] = newItem
                } else {
                    systemSubItems.add(newItem)
                }
            } else {
                val identity = resolveAppIdentity(context, id, hasRootPermission)
                val displayName = identity.first ?: appFallbackNames[id] ?: id
                
                val existingIndex = appSubItems.indexOfFirst { it.cleanPath == id || it.appName == displayName }

                if (existingIndex != -1) {
                    val originalItem = appSubItems[existingIndex]
                    if (!originalItem.isFound || !originalItem.checkMethod.startsWith("File Trace:")) {
                        appSubItems[existingIndex] = originalItem.copy(
                            isFound = true,
                            isCritical = true,
                            checkMethod = "File Trace: $triggeringPath",
                            result = "Matched Trace: $triggeringPath",
                            appName = displayName,
                            appIcon = identity.second ?: originalItem.appIcon,
                        )
                    }
                } else {
                    appSubItems.add(
                        CheckSubItem(
                            rawPath = id,
                            cleanPath = id,
                            isFound = true,
                            isCritical = true,
                            checkMethod = "File Trace: $triggeringPath",
                            result = "Matched Trace: $triggeringPath",
                            appName = displayName,
                            appIcon = identity.second,
                        )
                    )
                }
            }
        }
    }

    val memorySubItems = checkMemoryIntegrity()

    return listOf(
        CheckCategory(name = "Files", subItems = fileSubItems, hasIssue = fileSubItems.any { it.isFound }),
        CheckCategory(name = "Apps", subItems = appSubItems, hasIssue = appSubItems.any { it.isFound }),
        CheckCategory(name = "Bootloader/TEE/Key", subItems = securitySubItems, hasIssue = securitySubItems.any { it.isFound }),
        CheckCategory(name = "System properties", subItems = systemSubItems, hasIssue = systemSubItems.any { it.isFound }),
        CheckCategory(name = "Memory Integrity", subItems = memorySubItems, hasIssue = memorySubItems.any { it.isFound })
    )
}

fun checkMemoryIntegrity(): List<CheckSubItem> {
    val items = mutableListOf<CheckSubItem>()
    val mapsFile = File("/proc/self/maps")
    
    var anonymousExecutableFound = false
    var anonCount = 0
    try {
        mapsFile.forEachLine { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 4) {
                val perms = parts[1]
                val pathname = if (parts.size > 5) parts.last() else ""
                
                if (perms.contains("x") && pathname.isEmpty()) {
                    anonymousExecutableFound = true
                    anonCount++
                }
            }
        }
    } catch (_: Exception) {}

    items.add(
        CheckSubItem(
            rawPath = "Anon Exec",
            cleanPath = "检测到Hook(内存异常)",
            isFound = anonymousExecutableFound,
            isCritical = true,
            checkMethod = "Memory maps(/proc/self/maps)",
            result = if (anonymousExecutableFound) "发现 $anonCount 处匿名 r-xp 注入" else "未发现",
        )
    )

    return items
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
            appIcon = fetchedIcon,
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
                if ((icon != null) && (icon != defaultAppIcon)) return Pair(label, icon)
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

fun getAppVersionCode(context: Context): Long {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    } catch (_: Exception) {
        0L
    }
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
    val verifiedBootState: String,
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
        if (chain == null || chain.isEmpty()) return AttestationResult("Unknown", isLocked = false, verifiedBootState = "Unknown", serials = emptyList(), isGoogleRoot = false, rootSubject = "No Chain")

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
        val extensionData = leafCert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17") ?: return AttestationResult("Software", isLocked = false, verifiedBootState = "Unknown", serials = serials, isGoogleRoot = isGoogleRoot, rootSubject = rootSubject)

        val derStr = extensionData.joinToString("") { "%02x".format(it) }
        
        var securityLevel = "Software"
        var deviceLocked = false
        var verifiedBootState = "Unknown"

        if (derStr.contains("0a0101")) securityLevel = "TEE"
        if (derStr.contains("0a0102")) securityLevel = "StrongBox"
        if (derStr.contains("0101ff")) deviceLocked = true
        
        when {
            derStr.contains("0a0100") -> verifiedBootState = "Verified"
            derStr.contains("0a0101") && derStr.indexOf("0a0101") != derStr.lastIndexOf("0a0101") -> verifiedBootState = "Self-signed"
            derStr.contains("0a0102") && derStr.indexOf("0a0102") != derStr.lastIndexOf("0a0102") -> verifiedBootState = "Unverified"
            derStr.contains("0a0103") -> verifiedBootState = "Failed"
        }

        return AttestationResult(securityLevel, deviceLocked, verifiedBootState, serials, isGoogleRoot, rootSubject)
    } catch (e: Exception) {
        return AttestationResult("Error", isLocked = false, verifiedBootState = "Error", serials = emptyList(), isGoogleRoot = false, rootSubject = e.message ?: "Unknown Error")
    }
}

@Serializable
data class RevocationList(val entries: Map<String, RevocationEntry>)

@Serializable
data class RevocationEntry(val status: String, val reason: String? = null)

private var cachedRevocationList: RevocationList? = null
var googleApiLatency by mutableStateOf("未检测")
var mirrorServerLatency by mutableStateOf("未检测")

fun measureLatencies() {
    val urls = listOf(
        "Google API" to "https://android.googleapis.com/attestation/status",
        "镜像服务器" to "https://cdn.jsdelivr.net/gh/VisualTechStudio/ClearSight@main/CRL.json"
    )

    urls.forEach { (name, urlStr) ->
        val start = System.currentTimeMillis()
        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=0-0")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val end = System.currentTimeMillis()
                val latency = "${end - start}ms"
                if (name == "Google API") googleApiLatency = latency else mirrorServerLatency = latency
            } else {
                val errorMsg = "错误 $responseCode"
                if (name == "Google API") googleApiLatency = errorMsg else mirrorServerLatency = errorMsg
            }
            connection.disconnect()
        } catch (_: Exception) {
            if (name == "Google API") googleApiLatency = "超时/失败" else mirrorServerLatency = "超时/失败"
        }
    }
}

private val jsonParser = Json { ignoreUnknownKeys = true }

fun fetchRevocationList(context: Context) {
    initRevocationList(context)

    try {
        val urlString = if (useMirrorServer) "https://cdn.jsdelivr.net/gh/VisualTechStudio/ClearSight@main/CRL.json" else "https://android.googleapis.com/attestation/status"
        val url = URL(urlString)
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
            revocationUpdateResult = "使用缓存"
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

    val isOnePlus = brand.equals("OnePlus", ignoreCase = true)
    val marketName = if (isOnePlus) {
        val oplusName = getSystemProperty("ro.vendor.oplus.market.name")
        if (oplusName.isNotEmpty()) oplusName else getSystemProperty("ro.product.marketname")
    } else {
        getSystemProperty("ro.product.marketname")
    }

    val deviceStr = if (isOnePlus) {
        if (marketName.isNotEmpty()) {
            "$marketName ($product $deviceName)"
        } else {
            "$model ($product $deviceName)"
        }
    } else {
        if (marketName.isNotEmpty()) {
            "$marketName ($product $deviceName)"
        } else {
            "$brand $model ($product $deviceName)"
        }
    }

    val hardware = Build.HARDWARE
    val board = Build.BOARD
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"

    val hardwareStr = "$hardware $board ($abi)"

    val kernelStr = try {
        File("/proc/version").readText().trim().let {
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
        security = securityStr,
    )
}
