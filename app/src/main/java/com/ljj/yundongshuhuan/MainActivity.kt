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
import java.security.MessageDigest
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
import com.styropyr0.prismal.PrismalGlassSurface
import com.styropyr0.prismal.sources.PrismalGlassLayer
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.components.PrismalGlassButton
import com.styropyr0.prismal.components.PrismalGlassSlider
import com.styropyr0.prismal.components.PrismalGlassToggle
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import io.github.wzhdgithub.liquidglass.*
import java.util.Locale

private val Palette = listOf(Color(0xFFB8B8B8), Color(0xFF82B7A0), Color(0xFFAA9AC8), Color(0xFFD0A36D))
private val PaletteNames = listOf("雾灰", "鼠尾草", "灰紫", "砂金")

class MainActivity : ComponentActivity() {
    var status by mutableStateOf("准备就绪"); private set
    var running by mutableStateOf(false); private set
    var sensitivity by mutableFloatStateOf(4.0f); private set
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
        sensitivity = prefs.getFloat("sensitivity", 4.0f).coerceIn(0.6f, 4.0f)
        paletteIndex = prefs.getInt("bubble_color", 0).coerceIn(0, 3)
        themeMode = prefs.getInt("theme_mode", 0).coerceIn(0, 2)
        running = prefs.getBoolean("running", false)
        status = if (running) "屏幕气泡正在运行" else "准备就绪"
        setContent { MotionGuardApp(this) }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        // 启动后延迟检查一次更新，避免阻塞首屏加载。
        Handler(Looper.getMainLooper()).postDelayed({ checkUpdates(silent = true) }, 1500)
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
    fun applyThemeMode(value: Int) {
        themeMode = value.coerceIn(0, 2)
        getSharedPreferences("yundongshuhuan", MODE_PRIVATE).edit().putInt("theme_mode", themeMode).apply()
    }
    fun updateConfig(sensitivityValue: Float = sensitivity, colorValue: Int = paletteIndex) {
        sensitivity = sensitivityValue.coerceIn(0.6f, 4.0f)
        paletteIndex = colorValue.coerceIn(0, 3)
        getSharedPreferences("yundongshuhuan", MODE_PRIVATE).edit()
            .putFloat("sensitivity", sensitivity).putInt("bubble_color", paletteIndex).apply()
        val i = Intent(this, MotionMonitorService::class.java).setAction(MotionMonitorService.ACTION_CONFIG)
            .putExtra(MotionMonitorService.EXTRA_SENSITIVITY, sensitivity)
            .putExtra(MotionMonitorService.EXTRA_COLOR, paletteIndex)
        try { startService(i) } catch (_: Exception) { }
    }
    fun checkUpdates(silent: Boolean = false) {
        if (!silent) Toast.makeText(this, "正在检查 OTA 更新…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                // GitHub 原始地址优先；公共 CDN 作为无账号备用分发源。
                // CDN 可能有缓存延迟，因此每次检查均按顺序尝试，且不改变清单校验规则。
                val manifestSources = listOf(
                    "https://cdn.jsdelivr.net/gh/liangfangfa0-afk/MotionGuard@main/ota/stable.json",
                    "https://githubraw.com/liangfangfa0-afk/MotionGuard/main/ota/stable.json",
                    "https://raw.githubusercontent.com/liangfangfa0-afk/MotionGuard/main/ota/stable.json"
                )
                var manifestText: String? = null
                var lastSourceError = "没有可用的 OTA 清单源"
                for (source in manifestSources) {
                    var sourceConnection: HttpURLConnection? = null
                    try {
                        sourceConnection = (URL(source).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8000
                            readTimeout = 12000
                            requestMethod = "GET"
                            instanceFollowRedirects = true
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("User-Agent", "MotionGuard-Android")
                            setRequestProperty("Cache-Control", "no-cache")
                        }
                        val response = sourceConnection.responseCode
                        if (response != HttpURLConnection.HTTP_OK) {
                            lastSourceError = "清单源 HTTP $response"
                            continue
                        }
                        manifestText = sourceConnection.inputStream.bufferedReader().use { it.readText() }
                        if (manifestText.isNullOrBlank()) {
                            lastSourceError = "清单源返回空内容"
                            manifestText = null
                            continue
                        }
                        break
                    } catch (sourceError: Exception) {
                        lastSourceError = sourceError.message ?: "清单源连接失败"
                    } finally {
                        sourceConnection?.disconnect()
                    }
                }
                val manifest = JSONObject(manifestText ?: throw IllegalStateException(lastSourceError))
                require(manifest.optString("appId") == packageName) { "OTA 清单应用标识不匹配" }
                val remoteCode = manifest.optLong("versionCode", -1L)
                require(remoteCode > 0L) { "OTA 清单版本号无效" }
                val currentInfo = packageManager.getPackageInfo(packageName, 0)
                val currentCode = if (Build.VERSION.SDK_INT >= 28) currentInfo.longVersionCode else currentInfo.versionCode.toLong()
                val currentVersion = currentInfo.versionName ?: "未知"
                val tag = manifest.optString("versionName", "未知")
                val apk = manifest.optJSONObject("apk") ?: throw IllegalStateException("OTA 清单缺少 APK 信息")
                val downloadUrl = apk.optString("url")
                val sha256 = apk.optString("sha256").lowercase(Locale.ROOT)
                require(downloadUrl.startsWith("https://")) { "APK 地址必须使用 HTTPS" }
                require(sha256.matches(Regex("[a-f0-9]{64}"))) { "APK SHA-256 格式无效" }
                val notes = manifest.optString("releaseNotes").trim().ifBlank { "此版本暂无更新日志。" }
                val mandatory = manifest.optBoolean("mandatory", false)
                val minimumCode = manifest.optLong("minimumSupportedVersionCode", 1L)
                val hasUpdate = remoteCode > currentCode
                Handler(Looper.getMainLooper()).post {
                    if (hasUpdate) {
                        val builder = android.app.AlertDialog.Builder(this)
                            .setTitle("发现新版本  v$tag")
                            .setMessage("当前版本：v$currentVersion（$currentCode）\n最新版本：v$tag（$remoteCode）\n\n更新日志\n$notes".take(6000))
                            .setPositiveButton("下载并安装") { _, _ -> downloadAndInstall(downloadUrl, sha256) }
                            .setCancelable(!mandatory)
                        if (!mandatory) builder.setNegativeButton("稍后更新", null)
                        builder.show()
                    } else if (!silent) {
                        val message = if (currentCode < minimumCode) "当前版本已低于最低支持版本，请联系项目维护者" else "当前已是最新版本（v$currentVersion）"
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    if (!silent) Toast.makeText(this, "检查 OTA 更新失败：${e.message ?: "网络不可用"}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun compareVersions(remote: String, current: String): Int {
        fun parts(value: String) = value.trim().removePrefix("v")
            .substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun downloadAndInstall(url: String, expectedSha256: String) {
        Thread {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MotionGuard-Android")
                }
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    throw IllegalStateException("APK 下载 HTTP $responseCode")
                }
                val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val apk = File(directory, "motionguard-update.apk")
                connection.inputStream.use { input ->
                    FileOutputStream(apk).use { output -> input.copyTo(output) }
                }
                connection.disconnect()
                val digest = MessageDigest.getInstance("SHA-256")
                val actualSha256 = apk.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    apk.delete()
                    throw SecurityException("APK SHA-256 校验失败，已取消安装")
                }
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
                    Toast.makeText(this, "APK 下载或完整性校验失败，已取消安装", Toast.LENGTH_LONG).show()
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
        primary = Color(0xFFD8D8D8), secondary = Color(0xFF9EB8AA), background = Color(0xFF111318),
        surface = Color(0xFF20242B), surfaceVariant = Color(0xFF303640),
        onBackground = Color(0xFFF2F3F5), onSurface = Color(0xFFF2F3F5), onSurfaceVariant = Color(0xFFB6BBC4)
    ) else lightColorScheme(
        primary = Color(0xFF424B56), secondary = Color(0xFF648675), background = Color(0xFFE8EDF2),
        surface = Color(0xFFF8FAFC), surfaceVariant = Color(0xFFDDE4EB),
        onBackground = Color(0xFF202832), onSurface = Color(0xFF202832), onSurfaceVariant = Color(0xFF66717D)
    )
    val backdrop = rememberPrismalGlassLayer()
    MaterialTheme(colorScheme = scheme) {
        GlassShell(
            glass = glass,
            darkTheme = dark,
            items = listOf(
                GlassBarItem(Icons.Rounded.Home, "首页"),
                GlassBarItem(Icons.Rounded.Settings, "设置")
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
            snackbarHost = {},
            fallbackBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Rounded.Home, null) },
                        label = { Text("首页") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Rounded.Settings, null) },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .prismalGlassLayer(backdrop)
                )
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize(),
                    label = "main-page"
                ) { page ->
                    if (page == 0) HomePage(activity, backdrop, Modifier.padding(padding), onGoSettings = { tab = 1 })
                    else SettingsPage(activity, backdrop, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(
    backdrop: PrismalGlassLayer,
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val darkSurface = MaterialTheme.colorScheme.background == Color(0xFF111318)
    PrismalGlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = { PrismalRoundedRectangle(radius) },
        onClick = onClick,
        blurRadius = 12.dp,
        saturation = 1.18f,
        refractionHeightPx = 8f,
        refractionAmountPx = 10f,
        depthEffect = true,
        tint = MaterialTheme.colorScheme.surface,
        tintAlpha = if (darkSurface) 0.42f else 0.56f,
        content = content
    )
}

@Composable
private fun GlassActionButton(
    backdrop: PrismalGlassLayer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    PrismalGlassButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        height = height,
        shape = { PrismalRoundedRectangle(16.dp) },
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        tintAlpha = if (selected) 0.22f else if (MaterialTheme.colorScheme.background == Color(0xFF111318)) 0.38f else 0.52f,
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
private fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: PrismalGlassLayer,
    modifier: Modifier = Modifier
) {
    PrismalGlassSlider(
        value = { value },
        onValueChange = onValueChange,
        valueRange = valueRange,
        visibilityThreshold = 0.01f,
        backdrop = backdrop,
        modifier = modifier
    )
}

@Composable
private fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: PrismalGlassLayer,
    modifier: Modifier = Modifier
) {
    PrismalGlassToggle(
        selected = { checked },
        onSelect = onCheckedChange,
        backdrop = backdrop,
        modifier = modifier
    )
}

@Composable
private fun HomePage(a: MainActivity, backdrop: PrismalGlassLayer, modifier: Modifier = Modifier, onGoSettings: () -> Unit = {}) {
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
        GlassPanel(backdrop, Modifier.fillMaxWidth().height(210.dp), 27.dp) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
                Color(0xFF9EB6C6).copy(alpha = .10f),
                Color(0xFFB8A9C9).copy(alpha = .08f),
                Color.Transparent
            )))) {
                val previewDots = listOf(
                    0.10f to 0.18f, 0.22f to 0.32f, 0.38f to 0.13f, 0.66f to 0.22f, 0.88f to 0.16f,
                    0.14f to 0.55f, 0.31f to 0.72f, 0.73f to 0.61f, 0.91f to 0.48f,
                    0.08f to 0.84f, 0.25f to 0.91f, 0.48f to 0.83f, 0.69f to 0.91f, 0.86f to 0.78f,
                    0.53f to 0.37f, 0.40f to 0.57f, 0.62f to 0.49f
                )
                previewDots.forEachIndexed { i, point ->
                    Box(
                        Modifier.offset(x = (point.first * 320f).dp, y = (point.second * 190f).dp)
                            .size((5 + i % 3 * 2).dp)
                            .background(accent.copy(alpha = .38f + (i % 3) * .12f), CircleShape)
                    )
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("动态粒子场", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("MOTION CUES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Text("LIVE PREVIEW", Modifier.align(Alignment.BottomStart).padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
        }
        GlassPanel(backdrop, Modifier.fillMaxWidth(), 24.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (a.running) Color(0xFF6FA88B) else MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(Modifier.width(9.dp))
                    Text(if (a.running) "运行中" else "尚未启动", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(a.status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                GlassActionButton(
                    backdrop = backdrop,
                    onClick = a::toggleMonitor,
                    modifier = Modifier.fillMaxWidth(),
                    height = 54.dp,
                    selected = !a.running
                ) {
                    Icon(if (a.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                    Text(if (a.running) "停止屏幕气泡" else "开启屏幕气泡", fontWeight = FontWeight.Bold)
                }
                Text("动态气泡随设备运动流动，不拦截触摸操作。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(backdrop, "灵敏度", String.format(Locale.US, "%.1f×", a.sensitivity), Icons.Rounded.Tune, Modifier.weight(1f), onClick = onGoSettings)
            SummaryTile(backdrop, "气泡主题", PaletteNames[a.paletteIndex], Icons.Rounded.Palette, Modifier.weight(1f), onClick = onGoSettings)
        }
    }
}

@Composable
private fun SummaryTile(backdrop: PrismalGlassLayer, title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    GlassPanel(backdrop, modifier, 19.dp, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsPage(a: MainActivity, backdrop: PrismalGlassLayer, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp).padding(top = 27.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Text("设置", color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("调整气泡反馈与应用行为", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        GlassPanel(backdrop, Modifier.fillMaxWidth(), 23.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("外观模式", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                        GlassActionButton(
                            backdrop = backdrop,
                            onClick = { a.applyThemeMode(index) },
                            modifier = Modifier.weight(1f),
                            selected = a.themeMode == index,
                            height = 42.dp
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
                Text("不使用系统动态取色，保持中性灰阶外观。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        GlassPanel(backdrop, Modifier.fillMaxWidth(), 23.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("运动反馈", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("灵敏度", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.1f×", a.sensitivity), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                GlassSlider(
                    value = a.sensitivity,
                    onValueChange = { a.updateConfig(sensitivityValue = it) },
                    valueRange = .6f..4.0f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
                Text("数值越高，气泡对设备运动的响应越明显。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        GlassPanel(backdrop, Modifier.fillMaxWidth(), 23.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("气泡颜色", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.forEachIndexed { index, color ->
                        GlassActionButton(
                            backdrop = backdrop,
                            onClick = { a.updateConfig(colorValue = index) },
                            modifier = Modifier.weight(1f),
                            selected = a.paletteIndex == index,
                            height = 58.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(16.dp).background(color, CircleShape))
                                Text(PaletteNames[index], fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        GlassPanel(backdrop, Modifier.fillMaxWidth(), 21.dp) {
            Column {
                GlassSettingRow(backdrop, Icons.Rounded.SystemUpdate, "检查更新", "查询 GitHub 版本与更新包", a::checkUpdates)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                GlassSettingRow(backdrop, Icons.Rounded.Shield, "悬浮窗权限", "管理屏幕边缘气泡显示权限") {
                    a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
                }
            }
        }
        GlassPanel(backdrop, Modifier.fillMaxWidth(), 19.dp) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("全局液态玻璃", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("PrismalAGSL · AGSL 折射 / 背景采样 / 动态模糊 / 边缘高光", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("版本 ${a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "未知"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                Text("开源许可与第三方声明", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("MotionGuard 按 GNU GPL-3.0-or-later 发布。全局液态玻璃效果使用 PrismalAGSL（MIT License）。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("PrismalAGSL 开源项目", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                    modifier = Modifier.clickable { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/styropyr0/PrismalAGSL"))) })
                Text("本应用源码及 GPL-3.0 许可", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                    modifier = Modifier.clickable { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/liangfangfa0-afk/MotionGuard"))) })
            }
        }
    }
}

@Composable
private fun GlassSettingRow(backdrop: PrismalGlassLayer, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassActionButton(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        height = 68.dp
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
