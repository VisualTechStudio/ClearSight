package com.vtstudio.clearsight

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ClearSightScreen()
        }
    }
}

@Composable
fun ClearSightScreen() {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val categories = remember(refreshTrigger) {
        val loaded = loadAllCategories(context)
        val defaultOrder = listOf("Bootloader/TEE/Key", "System properties", "Apps", "Files")
        
        loaded.sortedWith(
            compareByDescending<CheckCategory> { cat ->
                cat.subItems.any { it.isCritical && it.isFound }
            }.thenByDescending { cat ->
                cat.subItems.any { !it.isCritical && it.isFound }
            }.thenBy { cat ->
                defaultOrder.indexOf(cat.name).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
        )
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { fetchRevocationList(context) }
        refreshTrigger++
    }

    val startTime = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) }
    val version = remember { getAppVersion(context) }
    val buildInfo = remember { getBuildInfo(context) }.split("-").firstOrNull() ?: "release"
    val deviceInfo = remember(refreshTrigger) { getDeviceInfoSummary() }
    val watermarkText = "ClearSight(明澈之眼) | $startTime | V$version - $buildInfo"

    val backgroundColor = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val titleColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    val subTitleColor = Color(0xFF8E8E93)

    val watermarkPaint = remember(isDark) {
        android.graphics.Paint().apply {
            color = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            alpha = (255 * 0.05f).toInt()
            textSize = 30f
            isAntiAlias = true
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(backgroundColor)
        .drawWithContent {
            drawContent()
            val rotation = -30f
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                for (x in (-200..(size.width.toInt() + 200)) step 500) {
                    for (y in (0..(size.height.toInt() + 500)) step 400) {
                        nativeCanvas.save()
                        nativeCanvas.rotate(rotation, x.toFloat(), y.toFloat())
                        nativeCanvas.drawText(watermarkText, x.toFloat(), y.toFloat(), watermarkPaint)
                        nativeCanvas.restore()
                    }
                }
            }
        }
    ) {
        MainContent(
            isDark = isDark,
            categories = categories,
            onRefresh = { refreshTrigger++ },
            onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
            version = version,
            buildInfo = buildInfo,
            deviceInfo = deviceInfo,
            titleColor = titleColor,
            subTitleColor = subTitleColor
        )
    }
}

@Composable
fun DeviceInfoSection(info: DeviceInfoSummary, isDark: Boolean) {
    val subTextColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
    val labelColor = if (isDark) Color(0xFF888888) else Color(0xFF999999)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoRow("Device", info.device, labelColor, subTextColor)
            InfoRow("Hardware", info.hardware, labelColor, subTextColor)
            InfoRow("Kernel", info.kernel, labelColor, subTextColor)
            InfoRow("OS", info.os, labelColor, subTextColor)
            InfoRow("Fingerprint", info.fingerprint, labelColor, subTextColor)
            InfoRow("Security Patch", info.security, labelColor, subTextColor)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, labelColor: Color, valueColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(text = value, fontSize = 11.sp, color = valueColor, fontWeight = FontWeight.Normal, lineHeight = 14.sp)
    }
}


@Composable
fun MainContent(
    isDark: Boolean,
    categories: List<CheckCategory>,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    version: String,
    buildInfo: String,
    deviceInfo: DeviceInfoSummary,
    titleColor: Color,
    subTitleColor: Color
) {
    val hasCriticalIssue = remember(categories) { categories.any { cat -> cat.subItems.any { it.isCritical && it.isFound } } }
    val hasSuspiciousIssue = remember(categories) { categories.any { cat -> cat.subItems.any { !it.isCritical && it.isFound } } }
    val isHmaSuspicion = remember(categories) { categories.find { it.name == "Apps" }?.subItems?.any { it.isFound && it.checkMethod.startsWith("File Trace:") } == true }

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
    val expandedStates = remember(categories.size) { mutableStateListOf<Boolean>().apply { repeat(categories.size) { add(true) } } }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarPadding + 16.dp,
                bottom = navBarPadding + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = "ClearSight", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = titleColor)
                        Text(text = "明澈之眼", fontSize = 14.sp, color = subTitleColor, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), shape = RoundedCornerShape(20.dp)).clickable { onRefresh() },
                            contentAlignment = Alignment.Center
                        ) { Text("↻", fontSize = 24.sp, color = titleColor, modifier = Modifier.offset(y = (-2).dp)) }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Box(
                            modifier = Modifier.size(40.dp).background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), shape = RoundedCornerShape(20.dp)).clickable { onOpenSettings() },
                            contentAlignment = Alignment.Center
                        ) { Text("⚙", fontSize = 20.sp, color = titleColor) }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = statusText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (hasCriticalIssue) Color(0xFFEF4444) else textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "V $version", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.5f))
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            val buildTypeStr = if (buildInfo.startsWith("debug")) "DEBUG" else "RELEASE"
                            Text(text = "BUILD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.4f))
                            Text(text = buildTypeStr, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            if (isHmaSuspicion) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF332500) else Color(0xFFFFF9E6))) {
                        Text(text = "您似乎正在使用HMA(OSS)进行应用列表隐藏，但泄露了一些痕迹", modifier = Modifier.padding(16.dp), color = if (isDark) Color(0xFFFFD666) else Color(0xFFB78103), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            item {
                DeviceInfoSection(deviceInfo, isDark)
            }

            itemsIndexed(categories) { index, category ->
                val isExpanded = expandedStates.getOrNull(index) ?: true
                val categoryBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                val catCritical = category.subItems.any { it.isCritical && it.isFound }
                val catSuspicious = category.subItems.any { !it.isCritical && it.isFound }
                val categoryIconColor = if (catCritical) Color(0xFFEF4444) else if (catSuspicious) Color(0xFFFF9500) else Color(0xFF34C759)
                val categoryStatusText = if (catCritical) "异常" else if (catSuspicious) "可疑" else "正常"
                val visibleItems = remember(category.subItems) { 
                    category.subItems.asSequence()
                        .filter { it.isFound }
                        .sortedByDescending { it.isCritical }
                        .toList()
                }

                Card(
                    modifier = Modifier.fillMaxWidth().clickable { if (index < expandedStates.size) expandedStates[index] = !isExpanded },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = categoryBg)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).background(categoryIconColor, shape = RoundedCornerShape(5.dp)))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = category.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = titleColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(20.dp).clickable { showInfoDialogForCategory = category }, contentAlignment = Alignment.Center) {
                                    Text(text = "ⓘ", color = subTitleColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = categoryStatusText, fontSize = 13.sp, color = categoryIconColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                                if (visibleItems.isNotEmpty()) {
                                    Text(text = if (isExpanded) "▲" else "▼", fontSize = 11.sp, color = subTitleColor)
                                }
                            }
                        }

                        AnimatedVisibility(visible = isExpanded && visibleItems.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth().background(if (isDark) Color(0xFF161616) else Color(0xFFF1F2F3)).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                visibleItems.forEach { subItem ->
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                            if (category.name == "Apps" && subItem.appIcon != null) {
                                                Image(bitmap = subItem.appIcon.toBitmap(width = 48, height = 48).asImageBitmap(), contentDescription = null, modifier = Modifier.padding(end = 10.dp).size(24.dp))
                                            }
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = if (subItem.isCritical) "[危险] " else "[可疑] ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (subItem.isCritical) Color(0xFFEF4444) else Color(0xFFFF9500))
                                                    Text(text = subItem.appName ?: subItem.cleanPath, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                                                }
                                                if ((category.name == "Bootloader/TEE/Key" || category.name == "System properties") && subItem.result != null) {
                                                    Text(text = "返回值: ${subItem.result}", fontSize = 10.sp, color = subTitleColor.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp))
                                                }
                                                Text(text = "方法: ${subItem.checkMethod}", fontSize = 10.sp, color = subTitleColor.copy(alpha = 0.7f), modifier = Modifier.padding(top = 1.dp))
                                            }
                                        }
                                        Text(text = "✗", color = if (subItem.isCritical) Color(0xFFEF4444) else Color(0xFFFF9500), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp))
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
        val dialogCleanItems = remember(category.subItems) { category.subItems.filter { !it.isFound }.sortedByDescending { it.isCritical } }
        val dialogBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
        AlertDialog(
            onDismissRequest = { showInfoDialogForCategory = null },
            confirmButton = { TextButton(onClick = { showInfoDialogForCategory = null }) { Text("确定", color = Color(0xFF34C759), fontWeight = FontWeight.Bold) } },
            title = { Text(text = when(category.name) { "Files" -> "未检测到的风险文件"; "Apps" -> "未检测到的风险App"; "Bootloader/TEE/Key" -> "硬件与密钥安全详情"; "System properties" -> "系统属性详情"; else -> category.name }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = titleColor) },
            text = {
                if (dialogCleanItems.isEmpty()) { Text("所有配置的风险拦截项均已触发异常。", color = subTitleColor, fontSize = 13.sp) }
                else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(dialogCleanItems) { item ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    if (category.name == "Apps" && item.appIcon != null) { Image(bitmap = item.appIcon.toBitmap(width = 40, height = 40).asImageBitmap(), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(20.dp)) }
                                    Column {
                                        Text(text = item.appName ?: item.cleanPath, fontSize = 12.sp, color = titleColor, fontWeight = FontWeight.Medium)
                                        if ((category.name == "Bootloader/TEE/Key" || category.name == "System properties") && item.result != null) { Text(text = "返回值: ${item.result}", fontSize = 9.sp, color = Color(0xFF34C759).copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp)) }
                                        Text(text = "方法: ${item.checkMethod}", fontSize = 9.sp, color = Color(0xFF34C759).copy(alpha = 0.7f), modifier = Modifier.padding(top = 1.dp))
                                    }
                                }
                                Text(text = "✓", color = Color(0xFF34C759), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
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
