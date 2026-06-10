package com.vtstudio.clearsight

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = isSystemInDarkTheme()
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            val backgroundColor = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
            val titleColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
            val subTitleColor = Color(0xFF8E8E93)
            Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {
                SettingsScreen(onBack = { finish() }, context = this, isDark = isDark, titleColor = titleColor, subTitleColor = subTitleColor, backgroundColor = backgroundColor)
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, context: Context, isDark: Boolean, titleColor: Color, subTitleColor: Color, backgroundColor: Color) {
    val version = getAppVersion(context)
    val buildInfo = getBuildInfo(context).split("-").firstOrNull() ?: "release"
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    var isUpdating by remember { mutableStateOf(value = false) }
    var isTestingLatency by remember { mutableStateOf(value = false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        isTestingLatency = true
        scope.launch(Dispatchers.IO) {
            measureLatencies()
            isTestingLatency = false
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(scrollState)
            .padding(
                top = statusBarPadding + 16.dp,
                bottom = navBarPadding + 16.dp,
                start = 16.dp,
                end = 16.dp
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { Text("←", fontSize = 24.sp, color = titleColor) }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "设置", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = titleColor)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val appIcon = context.packageManager.getApplicationIcon(context.packageName)
                Image(bitmap = appIcon.toBitmap(width = 120, height = 120).asImageBitmap(), contentDescription = null, modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "ClearSight", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = titleColor)
                Text(text = "V $version ($buildInfo)", fontSize = 12.sp, color = subTitleColor, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "检测设置", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = subTitleColor, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsSwitch(
                    label = "省去无关紧要的检查",
                    subLabel = "跳过安全补丁、USB调试及开发者选项检测",
                    checked = skipInsignificantChecks,
                    onCheckedChange = { checked ->
                        skipInsignificantChecks = checked
                        context.getSharedPreferences("clearsight_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("skip_insignificant", checked).apply()
                    },
                    titleColor = titleColor,
                    subTitleColor = subTitleColor,
                    isDark = isDark
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "连接状态", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = subTitleColor, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Google API: ", fontSize = 12.sp, color = titleColor)
                            Text(text = googleApiLatency, fontSize = 12.sp, color = if (googleApiLatency.contains("ms")) Color(0xFF34C759) else Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "镜像服务器: ", fontSize = 12.sp, color = titleColor)
                            Text(text = mirrorServerLatency, fontSize = 12.sp, color = if (mirrorServerLatency.contains("ms")) Color(0xFF34C759) else Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { isTestingLatency = true; scope.launch(Dispatchers.IO) { measureLatencies(); isTestingLatency = false } },
                        enabled = !isTestingLatency,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), contentColor = titleColor)
                    ) {
                        Text(if (isTestingLatency) "检测中..." else "刷新延迟", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "证书吊销数据", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = subTitleColor, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsSwitch(
                    label = "每次启动检测吊销列表",
                    checked = checkRevocationOnStartup,
                    onCheckedChange = { checked ->
                        checkRevocationOnStartup = checked
                        context.getSharedPreferences("clearsight_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("check_revocation_on_startup", checked).apply()
                    },
                    titleColor = titleColor,
                    subTitleColor = subTitleColor,
                    isDark = isDark
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), thickness = 0.5.dp, color = subTitleColor.copy(alpha = 0.2f))
                SettingsSwitch(
                    label = "使用镜像",
                    subLabel = "若无法连接Google可开启",
                    checked = useMirrorServer,
                    onCheckedChange = { checked ->
                        useMirrorServer = checked
                        context.getSharedPreferences("clearsight_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("use_mirror_server", checked).apply()
                    },
                    titleColor = titleColor,
                    subTitleColor = subTitleColor,
                    isDark = isDark
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = subTitleColor.copy(alpha = 0.2f))
                SettingsRow(label = "证书吊销数据", value = revocationFetchDate, titleColor = titleColor, subTitleColor = subTitleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsRow(label = "已吊销的证书总数", value = "$revocationEntryCount 条", titleColor = titleColor, subTitleColor = subTitleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsRow(label = "上次更新结果", value = revocationUpdateResult, titleColor = titleColor, subTitleColor = when(revocationUpdateResult) { "成功", "更新成功" -> Color(0xFF34C759); "失败" -> Color(0xFFEF4444); "超时" -> Color(0xFFFF9500); else -> subTitleColor })
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { isUpdating = true; scope.launch(Dispatchers.IO) { fetchRevocationList(context); isUpdating = false } }, modifier = Modifier.fillMaxWidth(), enabled = !isUpdating, colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF34C759) else Color(0xFF28CD41)), shape = RoundedCornerShape(12.dp)) {
                    Text(if (isUpdating) "更新中..." else "更新数据", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SettingsSwitch(label: String, subLabel: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit, titleColor: Color, subTitleColor: Color, isDark: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(text = label, fontSize = 14.sp, color = titleColor, lineHeight = 18.sp)
            if (subLabel != null) {
                Text(text = subLabel, fontSize = 11.sp, color = subTitleColor, lineHeight = 14.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.85f),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = if (isDark) Color(0xFF34C759) else Color(0xFF28CD41))
        )
    }
}

@Composable
fun SettingsRow(label: String, value: String, titleColor: Color, subTitleColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 14.sp, color = titleColor)
        Text(text = value, fontSize = 14.sp, color = subTitleColor, fontWeight = FontWeight.Medium)
    }
}
