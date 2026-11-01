package com.ljj.yundongshuhuan

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wzhdgithub.liquidglass.*
import java.util.Locale

private val Bg = Color(0xFF07111F)
private val Panel = Color(0xFF112239)
private val Muted = Color(0xFF9BAEC5)
private val Accent = Color(0xFF72C8FF)
private val Palette = listOf(Color(0xFF55B8F4), Color(0xFF62D9B7), Color(0xFFB89AFF), Color(0xFFFFB969))
private val PaletteNames = listOf("冰蓝", "薄荷", "柔紫", "暖橙")

class MainActivity : Activity() {
    var status by mutableStateOf("准备就绪"); private set
    var running by mutableStateOf(false); private set
    var sensitivity by mutableFloatStateOf(1.8f); private set
    var paletteIndex by mutableIntStateOf(0); private set
    private var receiverRegistered = false
    private var waitingPermission = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MotionMonitorService.ACTION_UPDATE) return
            status = intent.getStringExtra(MotionMonitorService.EXTRA_STATUS) ?: "正在读取传感器"
            running = status != "监测已停止" && status != "传感器启动失败" && status != "设备没有可用的加速度传感器"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("yundongshuhuan", MODE_PRIVATE)
        sensitivity = prefs.getFloat("sensitivity", 1.8f).coerceIn(0.6f, 3.5f)
        paletteIndex = prefs.getInt("bubble_color", 0).coerceIn(0, 3)
        running = prefs.getBoolean("running", false)
        status = if (running) "屏幕气泡正在运行" else "准备就绪"
        setContent { MotionGuardApp(this) }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MotionMonitorService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        receiverRegistered = true
    }
    override fun onStop() {
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false }
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if (waitingPermission) {
            waitingPermission = false
            if (Settings.canDrawOverlays(this)) startMonitor()
        }
    }

    fun toggleMonitor() {
        if (running || getSharedPreferences("yundongshuhuan", MODE_PRIVATE).getBoolean("running", false)) {
            startService(Intent(this, MotionMonitorService::class.java).setAction(MotionMonitorService.ACTION_STOP))
            running = false; status = "已停止"
        } else if (!Settings.canDrawOverlays(this)) {
            waitingPermission = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else startMonitor()
    }
    private fun startMonitor() {
        val i = Intent(this, MotionMonitorService::class.java).setAction(MotionMonitorService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        running = true; status = "正在启动气泡提示"
    }
    fun updateConfig(sensitivityValue: Float = sensitivity, colorValue: Int = paletteIndex) {
        sensitivity = sensitivityValue.coerceIn(0.6f, 3.5f)
        paletteIndex = colorValue.coerceIn(0, 3)
        getSharedPreferences("yundongshuhuan", MODE_PRIVATE).edit()
            .putFloat("sensitivity", sensitivity).putInt("bubble_color", paletteIndex).apply()
        val i = Intent(this, MotionMonitorService::class.java).setAction(MotionMonitorService.ACTION_CONFIG)
            .putExtra(MotionMonitorService.EXTRA_SENSITIVITY, sensitivity)
            .putExtra(MotionMonitorService.EXTRA_COLOR, paletteIndex)
        try { startService(i) } catch (_: Exception) { }
    }
    fun checkUpdates() {
        Toast.makeText(this, "GitHub OTA 更新检查入口已预留，更新服务尚未接入。", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun MotionGuardApp(activity: MainActivity) {
    var tab by remember { mutableIntStateOf(0) }
    val glass = remember { runCatching { isGlassBlurSupported() }.getOrDefault(false) }
    val colors = darkColorScheme(
        primary = Accent, secondary = Color(0xFF83E0C2), background = Bg,
        surface = Panel, surfaceVariant = Color(0xFF1B3049),
        onBackground = Color(0xFFF4F8FC), onSurface = Color(0xFFF4F8FC), onSurfaceVariant = Muted
    )
    MaterialTheme(colorScheme = colors) {
        GlassShell(
            glass = glass, darkTheme = true,
            items = listOf(GlassBarItem(Icons.Rounded.Home, "首页"), GlassBarItem(Icons.Rounded.Settings, "设置")),
            selectedIndex = tab, onSelect = { tab = it }, snackbarHost = {},
            fallbackBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("首页") })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("设置") })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10243B), Bg)))) {
                AnimatedContent(tab, label = "main-page") { page ->
                    if (page == 0) HomePage(activity, Modifier.padding(padding))
                    else SettingsPage(activity, glass, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun HomePage(a: MainActivity, modifier: Modifier = Modifier) {
    val accent = Palette[a.paletteIndex]
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp).padding(top = 25.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MOTION  /  GUARD", color = Accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Text("晕动舒缓", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("让视觉拥有稳定的运动参照", color = Muted, fontSize = 13.sp)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = .14f)).border(1.dp, accent.copy(alpha = .4f), CircleShape), Alignment.Center) {
                Icon(Icons.Rounded.Shield, null, tint = accent, modifier = Modifier.size(25.dp))
            }
        }
        Card(shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF142A43))) {
            Box(Modifier.fillMaxWidth().height(210.dp).background(Brush.linearGradient(listOf(Color(0xFF1A3857), Color(0xFF0A192B))))) {
                repeat(7) { i ->
                    val y = ((i + 1) / 8f * 190).dp
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = y), horizontalArrangement = Arrangement.SpaceBetween) {
                        BubbleDot(accent, 10 + i % 3 * 2)
                        BubbleDot(accent, 10 + i % 3 * 2)
                    }
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("●   ·   ●   ·   ●", color = accent.copy(alpha = .8f), fontSize = 12.sp)
                    Text("动态边缘气泡", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("MOTION CUES", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Text("LIVE PREVIEW", Modifier.align(Alignment.BottomStart).padding(16.dp), color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .95f)), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (a.running) Color(0xFF6EE7B7) else Muted))
                    Spacer(Modifier.width(9.dp))
                    Text(if (a.running) "运行中" else "尚未启动", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(a.status, color = Muted, fontSize = 11.sp)
                }
                Button(
                    onClick = a::toggleMonitor, modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (a.running) Color(0xFF263B54) else Accent, contentColor = if (a.running) Color.White else Bg)
                ) {
                    Icon(if (a.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (a.running) "停止屏幕气泡" else "开启屏幕气泡", fontWeight = FontWeight.Bold)
                }
                Text("气泡位于屏幕边缘，不拦截触摸操作。", color = Muted, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("灵敏度", String.format(Locale.US, "%.1f×", a.sensitivity), Icons.Rounded.Tune, Modifier.weight(1f))
            SummaryTile("气泡主题", PaletteNames[a.paletteIndex], Icons.Rounded.Palette, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BubbleDot(color: Color, size: Int) {
    Box(Modifier.size(size.dp).shadow(8.dp, CircleShape).background(Brush.radialGradient(listOf(Color.White, color, color.copy(alpha = .1f))), CircleShape))
}

@Composable
private fun SummaryTile(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(19.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(title, color = Muted, fontSize = 11.sp)
                Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsPage(a: MainActivity, glass: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp).padding(top = 27.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Text("设置", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("调整气泡反馈与应用行为", color = Muted, fontSize = 13.sp)

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(23.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("运动反馈", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("灵敏度", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.1f×", a.sensitivity), color = Accent, fontWeight = FontWeight.Bold)
                }
                Slider(value = a.sensitivity, onValueChange = { a.updateConfig(sensitivityValue = it) }, valueRange = .6f..3.5f)
                Text("数值越高，气泡对设备运动的响应越明显。", color = Muted, fontSize = 12.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(23.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("气泡颜色", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.forEachIndexed { index, color ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                                .background(if (a.paletteIndex == index) color.copy(alpha = .16f) else Color(0xFF1A2D43))
                                .border(1.dp, if (a.paletteIndex == index) color.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(13.dp))
                                .clickable { a.updateConfig(colorValue = index) }.padding(vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.size(18.dp).background(color, CircleShape))
                            Spacer(Modifier.height(6.dp))
                            Text(PaletteNames[index], color = if (a.paletteIndex == index) color else Muted, fontSize = 11.sp)
                            if (a.paletteIndex == index) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(23.dp)) {
            Column {
                SettingRow(Icons.Rounded.SystemUpdate, "检查更新", "查询 GitHub 版本与更新包", a::checkUpdates)
                HorizontalDivider(color = Color.White.copy(alpha = .06f))
                SettingRow(Icons.Rounded.Shield, "悬浮窗权限", "管理屏幕边缘气泡显示权限") {
                    a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .72f)), shape = RoundedCornerShape(19.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("外观能力", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(if (glass) "Liquid Glass：AGSL 折射可用" else "Liquid Glass：当前设备将使用兼容底栏", color = if (glass) Color(0xFF6EE7B7) else Muted, fontSize = 12.sp)
                Text("版本 1.0.24", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}
