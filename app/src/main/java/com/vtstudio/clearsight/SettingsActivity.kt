package com.vtstudio.clearsight

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

            OS3BackgroundContainer(isDark = isDark) {
                SettingsScreen(
                    onBack = { finish() },
                    context = this,
                    isDark = isDark,
                    titleColor = titleColor,
                    subTitleColor = subTitleColor,
                    backgroundColor = backgroundColor
                )
            }
        }
    }
}

private const val OS3_NOISE_SHADER = """
    uniform shader composable;
    uniform float iTime;

    float rand(float2 co) {
        return fract(sin(dot(co.xy, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        half4 color = composable.eval(fragCoord);
        if (color.a == 0.0) {
            return color;
        }
        float noise = rand(fragCoord + float2(iTime * 0.05, iTime * 0.02));
        color.rgb = color.rgb * (1.0 + (noise - 0.5) * 0.035); 
        return color;
    }
"""

@Composable
fun OS3BackgroundContainer(isDark: Boolean, content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "os3_bg_pro")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "time"
    )

    val xOffset1 by infiniteTransition.animateFloat(
        initialValue = -0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "x1"
    )
    val yOffset1 by infiniteTransition.animateFloat(
        initialValue = -0.1f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "y1"
    )
    val xOffset2 by infiniteTransition.animateFloat(
        initialValue = 1.2f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(16000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "x2"
    )
    val yOffset2 by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "y2"
    )

    val color1 = if (isDark) Color(0xFF4A148C).copy(alpha = 0.5f) else Color(0xFFF3E5F5)
    val color2 = if (isDark) Color(0xFF0D47A1).copy(alpha = 0.45f) else Color(0xFFE3F2FD)
    val baseBg = if (isDark) Color(0xFF0C0D14) else Color(0xFFF5F7FA)

    Box(modifier = Modifier.fillMaxSize().background(baseBg)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(100.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color1, color1.copy(alpha = 0.4f), Color.Transparent),
                    radius = size.width * 1.4f
                ),
                center = Offset(size.width * xOffset1, size.height * yOffset1)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color2, color2.copy(alpha = 0.3f), Color.Transparent),
                    radius = size.width * 1.5f
                ),
                center = Offset(size.width * xOffset2, size.height * yOffset2)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val runtimeShader = RuntimeShader(OS3_NOISE_SHADER)
                        runtimeShader.setFloatUniform("iTime", time)
                        renderEffect = android.graphics.RenderEffect
                            .createShaderEffect(runtimeShader)
                            .asComposeRenderEffect()
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f))
                        } else {
                            listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                        }
                    )
                )
        )

        content()
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, context: Context, isDark: Boolean, titleColor: Color, subTitleColor: Color, backgroundColor: Color) {
    val version = getAppVersion(context)
    val versionCode = getAppVersionCode(context)
    val buildInfo = getBuildInfo(context).split("-").firstOrNull() ?: "release"
    val cardBg = if (isDark) Color(0x991E1E1E) else Color(0x99FFFFFF)
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
            .verticalScroll(scrollState)
            .padding(
                top = statusBarPadding + 16.dp,
                bottom = navBarPadding + 16.dp,
                start = 16.dp,
                end = 16.dp
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", fontSize = 24.sp, color = titleColor)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "设置", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = titleColor)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val appIcon = remember {
                    try {
                        context.packageManager.getApplicationIcon(context.packageName)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (appIcon != null) {
                    val density = LocalDensity.current
                    val pixelSize = with(density) { 120.dp.roundToPx() }
                    Image(
                        bitmap = appIcon.toBitmap(width = pixelSize, height = pixelSize).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(100.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                val logoBrush = if (isDark) {
                    Brush.linearGradient(listOf(Color(0xFFE3E3E3), Color(0xFF9E9E9E)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF1F1F1F), Color(0xFF757575)))
                }

                Text(
                    text = "ClearSight",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = LocalTextStyle.current.copy(brush = logoBrush),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "V $version.$versionCode | $buildInfo",
                    fontSize = 14.sp,
                    color = subTitleColor,
                    modifier = Modifier.padding(top = 4.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "检测设置", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = subTitleColor, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
        Text(text = "证书吊销数据", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = subTitleColor, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                    subLabel = "无法连接Google官方服务器时使用",
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
                        Text(if (isTestingLatency) "检测中..." else "刷新", fontSize = 12.sp)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = subTitleColor.copy(alpha = 0.2f))
                SettingsRow(label = "数据日期", value = revocationFetchDate, titleColor = titleColor, subTitleColor = subTitleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsRow(label = "已吊销的证书总数", value = "$revocationEntryCount 条", titleColor = titleColor, subTitleColor = subTitleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsRow(label = "上次更新结果", value = revocationUpdateResult, titleColor = titleColor, subTitleColor = when(revocationUpdateResult) { "成功", "更新成功" -> Color(0xFF34C759); "失败" -> Color(0xFFEF4444); "超时" -> Color(0xFFFF9500); else -> subTitleColor })
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { isUpdating = true; scope.launch(Dispatchers.IO) { fetchRevocationList(context); isUpdating = false } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF34C759) else Color(0xFF28CD41)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isUpdating) "更新中..." else "更新数据", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SettingsSwitch(label: String, subLabel: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit, titleColor: Color, subTitleColor: Color, isDark: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 14.sp, color = titleColor, lineHeight = 18.sp)
            if (subLabel != null) {
                Text(text = subLabel, fontSize = 11.sp, color = subTitleColor, lineHeight = 13.sp, modifier = Modifier.padding(top = 1.dp))
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

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreenPreviewContent(isDark = false)
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenDarkPreview() {
    SettingsScreenPreviewContent(isDark = true)
}

@Composable
fun SettingsScreenPreviewContent(isDark: Boolean) {
    val backgroundColor = if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA)
    val titleColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    val subTitleColor = Color(0xFF8E8E93)

    OS3BackgroundContainer(isDark = isDark) {
        SettingsScreen(
            onBack = {},
            context = LocalContext.current,
            isDark = isDark,
            titleColor = titleColor,
            subTitleColor = subTitleColor,
            backgroundColor = backgroundColor
        )
    }
}