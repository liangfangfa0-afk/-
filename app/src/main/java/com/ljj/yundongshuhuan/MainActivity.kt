package com.ljj.yundongshuhuan

import android.Manifest
import androidx.activity.ComponentActivity
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Environment
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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

private val Palette = listOf(Color(0xFFB8B8B8), Color(0xFF82B7A0), Color(0xFFAA9AC8), Color(0xFFD0A36D))
private val PaletteNames = listOf("雾灰", "鼠尾草", "灰紫", "砂金")

class MainActivity : ComponentActivity() {
    var status by mutableStateOf("准备就绪"); private set
    var running by mutableStateOf(false); private set
    var sensitivity by mutableFloatStateOf(1.8f); private set
    var paletteIndex by mutableIntStateOf(0); private set
    var themeMode by mutableIntStateOf(0); private set
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
        themeMode = prefs.getInt("theme_mode", 0).coerceIn(0, 2)
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
    fun setThemeMode(value: Int) {
        themeMode = value.coerceIn(0, 2)
        getSharedPreferences("yundongshuhuan", MODE_PRIVATE).edit().putInt("theme_mode", themeMode).apply()
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
        Toast.makeText(this, "正在检查 GitHub 更新…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val connection = (URL("https://api.github.com/repos/liangfangfa0-afk/-/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "MotionGuard-Android")
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()
                val tag = json.optString("tag_name").removePrefix("v")
                val remoteCode = tag.substringAfterLast(".").toIntOrNull() ?: 0
                val currentCode = packageManager.getPackageInfo(packageName, 0).versionName
                    ?.substringAfterLast(".")?.toIntOrNull() ?: 0
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                val notes = json.optString("body").ifBlank { "此版本没有提供更新说明。" }
                Handler(Looper.getMainLooper()).post {
                    if (remoteCode <= currentCode) {
                        Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_LONG).show()
                    } else if (apkUrl.isNullOrBlank()) {
                        Toast.makeText(this, "发现 $tag，但 Release 中没有 APK", Toast.LENGTH_LONG).show()
                    } else {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("发现新版本 $tag")
                            .setMessage(notes.take(1200))
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("下载并安装") { _, _ -> downloadAndInstall(apkUrl!!) }
                            .show()
                    }
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "检查更新失败：网络不可用或暂未发布 Release", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun downloadAndInstall(url: String) {
        Thread {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MotionGuard-Android")
                }
                val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val apk = File(directory, "motionguard-update.apk")
                connection.inputStream.use { input ->
                    FileOutputStream(apk).use { output -> input.copyTo(output) }
                }
                connection.disconnect()
                Handler(Looper.getMainLooper()).post {
                    if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                        Toast.makeText(this, "请允许安装未知来源应用，然后重新检查更新", Toast.LENGTH_LONG).show()
                        return@post
                    }
                    try {
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
                        val install = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(install)
                    } catch (_: Exception) {
                        Toast.makeText(this, "无法启动系统安装器", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "APK 下载失败，请检查网络", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

@Composable
private fun MotionGuardApp(activity: MainActivity) {
    var tab by remember { mutableIntStateOf(0) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (activity.themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    val glass = remember { runCatching { isGlassBlurSupported() }.getOrDefault(false) }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFD0D0D0), secondary = Color(0xFF9EB8AA), background = Color(0xFF151515),
        surface = Color(0xFF222222), surfaceVariant = Color(0xFF303030),
        onBackground = Color(0xFFF0F0F0), onSurface = Color(0xFFF0F0F0), onSurfaceVariant = Color(0xFFAAAAAA)
    ) else lightColorScheme(
        primary = Color(0xFF454545), secondary = Color(0xFF648675), background = Color(0xFFF3F2EF),
        surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE8E7E3),
        onBackground = Color(0xFF242424), onSurface = Color(0xFF242424), onSurfaceVariant = Color(0xFF777670)
    )
    MaterialTheme(colorScheme = scheme) {
        GlassShell(glass = glass, darkTheme = dark,
            items = listOf(GlassBarItem(Icons.Rounded.Home, "首页"), GlassBarItem(Icons.Rounded.Settings, "设置")),
            selectedIndex = tab, onSelect = { tab = it }, snackbarHost = {},
            fallbackBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("首页") })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("设置") })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                Text("MOTION  /  GUARD", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Text("晕动舒缓", color = MaterialTheme.colorScheme.onSurface, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("让视觉拥有稳定的运动参照", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = .14f)).border(1.dp, accent.copy(alpha = .4f), CircleShape), Alignment.Center) {
                Icon(Icons.Rounded.Shield, null, tint = accent, modifier = Modifier.size(25.dp))
            }
        }
        Card(shape = RoundedCornerShape(27.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxWidth().height(210.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)))) {
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
                    Text("MOTION CUES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Text("LIVE PREVIEW", Modifier.align(Alignment.BottomStart).padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .95f)), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (a.running) Color(0xFF6FA88B) else MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(Modifier.width(9.dp))
                    Text(if (a.running) "运行中" else "尚未启动", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(a.status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Button(
                    onClick = a::toggleMonitor, modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (a.running) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary, contentColor = if (a.running) Color.White else MaterialTheme.colorScheme.background)
                ) {
                    Icon(if (a.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (a.running) "停止屏幕气泡" else "开启屏幕气泡", fontWeight = FontWeight.Bold)
                }
                Text("气泡位于屏幕边缘，不拦截触摸操作。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
    Box(Modifier.size((size * 0.72f).dp).background(color.copy(alpha = 0.72f), CircleShape))
}

@Composable
private fun SummaryTile(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(19.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
        Text("调整气泡反馈与应用行为", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(23.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("外观模式", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                        FilterChip(selected = a.themeMode == index, onClick = { a.setThemeMode(index) }, label = { Text(label) })
                    }
                }
                Text("不使用系统动态取色，保持中性灰阶外观。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(23.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("运动反馈", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("灵敏度", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.1f×", a.sensitivity), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(value = a.sensitivity, onValueChange = { a.updateConfig(sensitivityValue = it) }, valueRange = .6f..3.5f)
                Text("数值越高，气泡对设备运动的响应越明显。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(23.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("气泡颜色", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.forEachIndexed { index, color ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                                .background(if (a.paletteIndex == index) color.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (a.paletteIndex == index) color.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(13.dp))
                                .clickable { a.updateConfig(colorValue = index) }.padding(vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.size(18.dp).background(color, CircleShape))
                            Spacer(Modifier.height(6.dp))
                            Text(PaletteNames[index], color = if (a.paletteIndex == index) color else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            if (a.paletteIndex == index) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(23.dp)) {
            Column {
                SettingRow(Icons.Rounded.SystemUpdate, "检查更新", "查询 GitHub 版本与更新包", a::checkUpdates)
                HorizontalDivider(color = Color.White.copy(alpha = .06f))
                SettingRow(Icons.Rounded.Shield, "悬浮窗权限", "管理屏幕边缘气泡显示权限") {
                    a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .72f)), shape = RoundedCornerShape(19.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("外观能力", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(if (glass) "Liquid Glass：AGSL 折射可用" else "Liquid Glass：当前设备将使用兼容底栏", color = if (glass) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("版本 1.0.26", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
