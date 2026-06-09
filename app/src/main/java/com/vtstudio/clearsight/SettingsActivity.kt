package com.vtstudio.clearsight

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(backgroundColor).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
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

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "证书吊销数据", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = subTitleColor, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsRow(label = "密钥吊销数据", value = revocationFetchDate, titleColor = titleColor, subTitleColor = subTitleColor)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRow(label = "已吊销的证书总数", value = "$revocationEntryCount 条", titleColor = titleColor, subTitleColor = subTitleColor)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRow(label = "上次更新结果", value = revocationUpdateResult, titleColor = titleColor, subTitleColor = when(revocationUpdateResult) { "成功" -> Color(0xFF34C759); "失败" -> Color(0xFFEF4444); "超时" -> Color(0xFFFF9500); else -> subTitleColor })
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { isUpdating = true; scope.launch(Dispatchers.IO) { fetchRevocationList(context); isUpdating = false } }, modifier = Modifier.fillMaxWidth(), enabled = !isUpdating, colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF34C759) else Color(0xFF28CD41)), shape = RoundedCornerShape(12.dp)) {
                    Text(if (isUpdating) "更新中..." else "更新数据", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SettingsRow(label: String, value: String, titleColor: Color, subTitleColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 14.sp, color = titleColor)
        Text(text = value, fontSize = 14.sp, color = subTitleColor, fontWeight = FontWeight.Medium)
    }
}
