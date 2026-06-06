package com.vtstudio.clearsight

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ClearSightScreen()
        }
    }
}

data class CheckSubItem(
    val rawPath: String,
    val cleanPath: String,
    val isFound: Boolean,
    val isCritical: Boolean,
    val checkMethod: String,
    val appName: String? = null,
    val appIcon: Drawable? = null
)
data class CheckCategory(val name: String, val subItems: List<CheckSubItem>, val hasIssue: Boolean)

@Composable
fun ClearSightScreen() {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var refreshTrigger by remember { mutableIntStateOf(0) }
    
    val categories: List<CheckCategory> = remember(refreshTrigger) { loadAllCategories(context) }

    val hasRootPermission = remember { checkRootPermission() }

    var hasStoragePermission by remember {
        mutableStateOf(
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                    (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
        )
    }

    var isPollingActive by remember { mutableStateOf(false) }

    val startTime = remember { 
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) 
    }
    val version = remember { getAppVersion(context) }
    val buildInfo = remember { getBuildInfo(context) }.split("-").firstOrNull() ?: "release"
    val watermarkText = "ClearSight(明澈之眼) | $startTime | V$version - $buildInfo"

    LaunchedEffect(isPollingActive, hasStoragePermission) {
        if (isPollingActive && !hasStoragePermission) {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 5000) {
                delay(200)

                val currentPermission = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                        (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())

                if (currentPermission) {
                    hasStoragePermission = true
                    isPollingActive = false
                    refreshTrigger++
                    break
                }
            }
            if (!hasStoragePermission) {
                isPollingActive = false
            }
        }
    }

    val hasCriticalIssue = remember(categories) {
        categories.any { cat: CheckCategory -> cat.subItems.any { it.isCritical && it.isFound } }
    }
    val hasSuspiciousIssue = remember(categories) {
        categories.any { cat -> cat.subItems.any { !it.isCritical && it.isFound } }
    }

    val isHmaSuspicion = remember(categories) {
        categories.find { it.name == "Apps" }?.subItems?.any { it.isFound && it.checkMethod.startsWith("File Trace:") } == true
    }

    val backgroundColor = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val titleColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    val subTitleColor = Color(0xFF8E8E93)

    val statusText = when {
        hasCriticalIssue -> "危险"
        hasSuspiciousIssue -> "可疑"
        else -> "可信环境"
    }
    val baseColor = when {
        hasCriticalIssue -> Color(0xFFEF4444)
        hasSuspiciousIssue -> Color(0xFFFF9500)
        else -> Color(0xFF34C759)
    }

    val alpha = if (isDark) 0.12f else 0.08f
    val cardColor = baseColor.copy(alpha = alpha)
    val textColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF2C2C2E)

    var showInfoDialogForCategory by remember { mutableStateOf<CheckCategory?>(null) }

    val expandedStates = remember(categories.size) {
        mutableStateListOf<Boolean>().apply { repeat(categories.size) { add(true) } }
    }

    val watermarkPaint = remember(isDark) {
        android.graphics.Paint().apply {
            color = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            setAlpha((255 * 0.05f).toInt())
            textSize = 30f
            isAntiAlias = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
                .drawWithContent {
                    drawContent()
                    val rotation = -30f
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                                for (x in -200..size.width.toInt() step 500) {
                                    for (y in 0..size.height.toInt() step 400) {
                                nativeCanvas.save()
                                nativeCanvas.rotate(rotation, x.toFloat(), y.toFloat())
                                nativeCanvas.drawText(watermarkText, x.toFloat(), y.toFloat(), watermarkPaint)
                                nativeCanvas.restore()
                            }
                        }
                    }
                }
        ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "ClearSight",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        Text(
            text = "明澈之眼",
            fontSize = 14.sp,
            color = subTitleColor,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.TopStart)) {
                    Text(
                        text = statusText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasCriticalIssue) Color(0xFFEF4444) else textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "V $version",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = "BUILD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.4f)
                    )
                    Text(
                        text = buildInfo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (!hasRootPermission && !hasStoragePermission) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isPollingActive = true
                        try {
                            if (Build.VERSION.SDK_INT >= 30) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (anr: Exception) {
                                anr.printStackTrace()
                            }
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF332500) else Color(0xFFFFF9E6)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "未获取到\"所有文件访问权限\"，检测结果可能不可靠",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFFFFD666) else Color(0xFFB78103)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "点此前往权限设置",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isDark) Color(0xFFFFE599) else Color(0xFFD49B0F)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isHmaSuspicion) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF332500) else Color(0xFFFFF9E6)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = "您似乎正在使用HMA(OSS)进行应用列表隐藏，但泄露了一些痕迹",
                            modifier = Modifier.padding(16.dp),
                            color = if (isDark) Color(0xFFFFD666) else Color(0xFFB78103),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            itemsIndexed(categories) { index, category ->
                val isExpanded = expandedStates.getOrNull(index) ?: true
                val categoryBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)

                val catCritical = category.subItems.any { it.isCritical && it.isFound }
                val catSuspicious = category.subItems.any { !it.isCritical && it.isFound }

                val categoryIconColor = if (catCritical) Color(0xFFEF4444) else if (catSuspicious) Color(0xFFFF9500) else Color(0xFF34C759)
                val categoryStatusText = if (catCritical) "异常" else if (catSuspicious) "可疑" else "正常"

                val visibleItems = remember(category.subItems) {
                    category.subItems
                        .filter { it.isFound }
                        .sortedByDescending { it.isCritical }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (index < expandedStates.size) expandedStates[index] = !isExpanded },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = categoryBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(categoryIconColor, shape = RoundedCornerShape(5.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = category.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { showInfoDialogForCategory = category },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ⓘ",
                                        color = subTitleColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = categoryStatusText,
                                    fontSize = 13.sp,
                                    color = categoryIconColor,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = if (isExpanded) "▲" else "▼",
                                    fontSize = 11.sp,
                                    color = subTitleColor
                                )
                            }
                        }

                        AnimatedVisibility(visible = isExpanded && visibleItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDark) Color(0xFF161616) else Color(0xFFF1F2F3))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                visibleItems.forEach { subItem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (category.name == "Apps" && subItem.appIcon != null) {
                                                Image(
                                                    bitmap = subItem.appIcon.toBitmap(width = 48, height = 48).asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .padding(end = 10.dp)
                                                        .size(24.dp)
                                                )
                                            }

                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (subItem.isCritical) "[危险] " else "[可疑] ",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (subItem.isCritical) Color(0xFFEF4444) else Color(0xFFFF9500)
                                                    )
                                                    Text(
                                                        text = subItem.appName ?: subItem.cleanPath,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = titleColor
                                                    )
                                                }

                                                Text(
                                                    text = if (category.name == "Apps") subItem.cleanPath else "方法: ${subItem.checkMethod}",
                                                    fontSize = 11.sp,
                                                    color = subTitleColor,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )

                                                if (category.name == "Apps") {
                                                    Text(
                                                        text = "方法: ${subItem.checkMethod}",
                                                        fontSize = 10.sp,
                                                        color = subTitleColor.copy(alpha = 0.7f),
                                                        modifier = Modifier.padding(top = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "✗",
                                            color = if (subItem.isCritical) Color(0xFFEF4444) else Color(0xFFFF9500),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    showInfoDialogForCategory?.let { category ->
        val dialogCleanItems = remember(category.subItems) {
            category.subItems
                .filter { !it.isFound }
                .sortedByDescending { it.isCritical }
        }
        val dialogBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)

        AlertDialog(
            onDismissRequest = { showInfoDialogForCategory = null },
            confirmButton = {
                TextButton(onClick = { showInfoDialogForCategory = null }) {
                    Text("确定", color = Color(0xFF34C759), fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    text = if (category.name == "Files") "未检测到的风险文件" else "未检测到的风险App",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            },
            text = {
                if (dialogCleanItems.isEmpty()) {
                    Text("所有配置的风险拦截项均已触发异常。", color = subTitleColor, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(dialogCleanItems) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (category.name == "Apps" && item.appIcon != null) {
                                        Image(
                                            bitmap = item.appIcon.toBitmap(width = 40, height = 40).asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .size(20.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = item.appName ?: item.cleanPath,
                                            fontSize = 12.sp,
                                            color = titleColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (category.name == "Apps") item.cleanPath else "方法: ${item.checkMethod}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF34C759),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        if (category.name == "Apps") {
                                            Text(
                                                text = "方法: ${item.checkMethod}",
                                                fontSize = 9.sp,
                                                color = Color(0xFF34C759).copy(alpha = 0.7f),
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "✓",
                                    color = Color(0xFF34C759),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = dialogBg,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

fun loadAllCategories(context: Context): List<CheckCategory> {
    val fileLines = readConfFile(context, "check.conf")
    val appLines = readConfFile(context, "appcheck.conf")

    val hasRootPermission = checkRootPermission()

    val fileSubItems = processCategoryItems(context, fileLines, isAppCheck = false, hasRootPermission)
    val initialAppSubItems = processCategoryItems(context, appLines, isAppCheck = true, hasRootPermission)
    val appSubItems = initialAppSubItems.toMutableList()

    val fileResults = fileSubItems.associateBy { it.cleanPath }
    val crossCheckMap = mapOf(
        "bin.mt.plus" to listOf("/sdcard/MT2"),
        "com.omarea.vtools" to listOf("/dev/scene", "/dev/cpuset/scene-daemon")
    )

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
                        appName = identity.first,
                        appIcon = identity.second
                    )
                )
            }
        }
    }

    return listOf(
        CheckCategory(name = "Files", subItems = fileSubItems, hasIssue = fileSubItems.any { it.isFound }),
        CheckCategory(name = "Apps", subItems = appSubItems, hasIssue = appSubItems.any { it.isFound })
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
    } catch (_: Exception) {
    }
    return lines
}

fun processCategoryItems(context: Context, lines: List<String>, isAppCheck: Boolean, hasRootPermission: Boolean): List<CheckSubItem> {
    if (lines.isEmpty()) return emptyList()

    val subItems = mutableListOf<CheckSubItem>()
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
        if (hasRootPermission) {
            checkAppsWithRoot(targets)
        } else {
            checkAppsWithPm(context, targets)
        }
    } else {
        if (hasRootPermission) {
            checkPathsWithRoot(targets)
        } else {
            checkPathsWithNormalApi(context, targets)
        }
    }

    for (task in tasks) {
        val line = task.first
        val cleanTarget = task.second
        val isCritical = task.third
        val res = results[cleanTarget] ?: Pair(false, "Unknown Engine")

        var fetchedName: String? = null
        var fetchedIcon: Drawable? = null

        if (isAppCheck) {
            val identity = resolveAppIdentity(context, cleanTarget, hasRootPermission)
            fetchedName = identity.first
            fetchedIcon = identity.second
        }

        subItems.add(
            CheckSubItem(
                rawPath = line,
                cleanPath = cleanTarget,
                isFound = res.first,
                isCritical = isCritical,
                checkMethod = res.second,
                appName = fetchedName,
                appIcon = fetchedIcon
            )
        )
    }
    return subItems
}

fun resolveAppIdentity(context: Context, pkgName: String, hasRoot: Boolean): Pair<String?, Drawable?> {
    val pm = context.packageManager
    val defaultAppIcon = try {
        pm.defaultActivityIcon
    } catch (_: Exception) {
        null
    }

    try {
        val appInfo = pm.getApplicationInfo(pkgName, 0)
        val label = appInfo.loadLabel(pm).toString()
        val icon = appInfo.loadIcon(pm)
        if (label.isNotEmpty() && label != pkgName) {
            return Pair(label, icon)
        }
    } catch (_: Exception) {
    }

    if (hasRoot) {
        try {
            val pathProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm path $pkgName"))
            val reader = BufferedReader(InputStreamReader(pathProcess.inputStream))
            val line = reader.readLine()
            reader.close()
            pathProcess.destroy()

            if (!line.isNullOrEmpty() && line.startsWith("package:")) {
                val apkPath = line.substring(8).trim()
                
                val archiveInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA)
                val appInfo = archiveInfo?.applicationInfo
                if (appInfo != null) {
                    appInfo.sourceDir = apkPath
                    appInfo.publicSourceDir = apkPath
                    
                    val label = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm)
                    
                    if (icon != null && icon != defaultAppIcon) {
                        return Pair(label, icon)
                    }
                    
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
        } catch (_: Exception) {
        }
    }

    val fallbackName = when (pkgName) {
        "bin.mt.plus" -> "MT管理器"
        "com.omarea.vtools" -> "Scene"
        "com.topjohnwu.magisk" -> "Magisk"
        "io.github.truboxl.helis" -> "HMA"
        "com.catchingnow.icebox" -> "冰箱"
        "com.vmos.glow" -> "VMOS"
        else -> null
    }
    
    return Pair(fallbackName, defaultAppIcon)
}

fun checkRootPermission(): Boolean {
    var process: Process? = null
    var os: DataOutputStream? = null
    var isReader: BufferedReader? = null
    return try {
        process = Runtime.getRuntime().exec("su")
        os = DataOutputStream(process.outputStream)
        isReader = BufferedReader(InputStreamReader(process.inputStream))
        os.writeBytes("id\n")
        os.flush()
        os.writeBytes("exit\n")
        os.flush()
        val line = isReader.readLine()
        line != null && line.contains("uid=0")
    } catch (_: Exception) {
        false
    } finally {
        try {
            os?.close()
            isReader?.close()
            process?.destroy()
        } catch (_: Exception) {
        }
    }
}

fun checkPathsWithRoot(paths: List<String>): Map<String, Pair<Boolean, String>> {
    val resultMap = mutableMapOf<String, Pair<Boolean, String>>()
    var process: Process? = null
    var os: DataOutputStream? = null
    var isReader: BufferedReader? = null
    try {
        process = Runtime.getRuntime().exec("su")
        os = DataOutputStream(process.outputStream)
        isReader = BufferedReader(InputStreamReader(process.inputStream))
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
    } catch (_: Exception) {
    } finally {
        try {
            os?.writeBytes("exit\n")
            os?.flush()
            os?.close()
            isReader?.close()
            process?.destroy()
        } catch (_: Exception) {
        }
    }
    return resultMap
}

fun checkPathsWithNormalApi(context: Context, paths: List<String>): Map<String, Pair<Boolean, String>> {
    val resultMap = mutableMapOf<String, Pair<Boolean, String>>()
    val hasStoragePermission = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
    for (path in paths) {
        val file = File(path)
        val method = if (hasStoragePermission) "Android File Permissions" else "Android Sandbox / Scoped Storage"
        try {
            resultMap[path] = Pair(file.exists(), method)
        } catch (_: Exception) {
            resultMap[path] = Pair(false, method)
        }
    }
    return resultMap
}

fun checkAppsWithPm(context: Context, packageNames: List<String>): Map<String, Pair<Boolean, String>> {
    val resultMap = mutableMapOf<String, Pair<Boolean, String>>()
    val pm = context.packageManager
    for (pkg in packageNames) {
        try {
            pm.getPackageInfo(pkg, 0)
            resultMap[pkg] = Pair(true, "PackageManager API")
        } catch (_: PackageManager.NameNotFoundException) {
            resultMap[pkg] = Pair(false, "PackageManager API")
        } catch (_: Exception) {
            resultMap[pkg] = Pair(false, "PackageManager API Inquiry Error")
        }
    }
    return resultMap
}

fun checkAppsWithRoot(packageNames: List<String>): Map<String, Pair<Boolean, String>> {
    val resultMap = mutableMapOf<String, Pair<Boolean, String>>()
    var process: Process? = null
    var os: DataOutputStream? = null
    var isReader: BufferedReader? = null
    try {
        process = Runtime.getRuntime().exec("su")
        os = DataOutputStream(process.outputStream)
        isReader = BufferedReader(InputStreamReader(process.inputStream))
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
    } catch (_: Exception) {
    } finally {
        try {
            os?.writeBytes("exit\n")
            os?.flush()
            os?.close()
            isReader?.close()
            process?.destroy()
        } catch (_: Exception) {
        }
    }
    return resultMap
}

fun getAppVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) {
        "1.0.0"
    }
}

fun getBuildInfo(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val type = if (isDebug) "debug" else "release"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        "$type-${sdf.format(Date(packageInfo.lastUpdateTime))}"
    } catch (_: Exception) {
        "release-${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}"
    }
}