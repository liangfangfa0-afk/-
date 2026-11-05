package com.ljj.yundongshuhuan

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wzhdgithub.liquidglass.*
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.PrismalGlass
import com.styropyr0.prismal.PrismalGlassSurface
import com.styropyr0.prismal.components.PrismalGlassButton
import com.styropyr0.prismal.components.PrismalGlassSlider
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import java.util.Locale

private val Palette = listOf(Color(0xFF00D9FF), Color(0xFFFF6D00), Color(0xFFB6FF00), Color(0xFFB000FF))
private val PaletteNames = listOf("亮青", "烈橙", "荧光绿", "电紫")

class MainActivity : ComponentActivity() {
    var status by mutableStateOf("准备就绪"); private set
    var running by mutableStateOf(false); private set
    var sensitivity by mutableFloatStateOf(4.0f); private set
    var paletteIndex by mutableIntStateOf(0); private set
    var themeMode by mutableIntStateOf(0); private set
    var roll by mutableFloatStateOf(0f); private set
    var pitch by mutableFloatStateOf(0f); private set
    var azimuth by mutableFloatStateOf(0f); private set
    var yawRate by mutableFloatStateOf(0f); private set
    var gpsProvider by mutableStateOf("未定位"); private set
    var gpsLat by mutableStateOf("--"); private set
    var gpsLng by mutableStateOf("--"); private set
    var gpsSpeed by mutableStateOf("--"); private set
    private var receiverRegistered = false
    private var waitingPermission = false
    private var locationManager: LocationManager? = null
    private var locationListening = false
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) startLocationUpdates() else gpsProvider = "未授权定位"
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            gpsProvider = if (location.provider == LocationManager.GPS_PROVIDER) "GPS 定位" else "网络定位"
            gpsLat = String.format(Locale.US, "%.5f", location.latitude)
            gpsLng = String.format(Locale.US, "%.5f", location.longitude)
            gpsSpeed = if (location.hasSpeed())
                String.format(Locale.US, "%.1f km/h", location.speed * 3.6f)
            else "--"
        }
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) gpsProvider = "GPS 已关闭"
        }
        override fun onProviderEnabled(provider: String) {
            gpsProvider = "定位中…"
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MotionMonitorService.ACTION_UPDATE) return
            status = intent.getStringExtra(MotionMonitorService.EXTRA_STATUS) ?: "正在读取传感器"
            running = status != "监测已停止" && status != "传感器启动失败" && status != "设备没有可用的加速度传感器"
            roll = intent.getFloatExtra(MotionMonitorService.EXTRA_ROLL, 0f)
            pitch = intent.getFloatExtra(MotionMonitorService.EXTRA_PITCH, 0f)
            azimuth = intent.getFloatExtra(MotionMonitorService.EXTRA_AZIMUTH, 0f)
            yawRate = intent.getFloatExtra(MotionMonitorService.EXTRA_YAW_RATE, 0f)
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
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (hasLocationPermission()) startLocationUpdates() else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocationUpdates() {
        val lm = locationManager ?: return
        if (locationListening) return
        if (!hasLocationPermission()) return
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, locationListener)
                gpsProvider = "GPS 定位中…"
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, locationListener)
                if (gpsProvider == "GPS 定位中…") gpsProvider = "GPS/网络定位中…"
            }
            locationListening = true
        } catch (_: SecurityException) {
            gpsProvider = "未授权定位"
        } catch (_: IllegalArgumentException) {
            gpsProvider = "定位不可用"
        }
    }

    override fun onDestroy() {
        try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) { }
        super.onDestroy()
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
            val backdrop = rememberPrismalGlassLayer()
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.linearGradient(
                            if (dark) listOf(Color(0xFF10151B), Color(0xFF20252C), Color(0xFF15191F))
                            else listOf(Color(0xFFE8EDF0), Color(0xFFF7F5F0), Color(0xFFE9E9E5))
                        ))
                        .prismalGlassLayer(backdrop)
                ) {
                    Box(Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 90.dp).size(190.dp).clip(CircleShape).background(Color(0xFF8EAFA7).copy(alpha = .18f)))
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 90.dp).size(230.dp).clip(CircleShape).background(Color(0xFF9A9FC0).copy(alpha = .16f)))
                }
                AnimatedContent(tab, label = "main-page") { page ->
                    if (page == 0) HomePage(activity, backdrop, Modifier.padding(padding))
                    else SettingsPage(activity, backdrop, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(
    backdrop: PrismalBackdrop,
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    PrismalGlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = { PrismalRoundedRectangle(radius) },
        tint = MaterialTheme.colorScheme.surface,
        tintAlpha = if (MaterialTheme.colorScheme.background == Color(0xFF151515)) .18f else .20f,
        blurRadius = 13.dp,
        saturation = 1.7f,
        refractionHeightPx = 18f,
        refractionAmountPx = 28f,
        chromaticAberration = .10f,
        content = content
    )
}

@Composable
private fun HomePage(a: MainActivity, backdrop: PrismalBackdrop, modifier: Modifier = Modifier) {
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
            PrismalGlassSurface(
                backdrop = backdrop, modifier = Modifier.size(52.dp),
                shape = { PrismalRoundedRectangle(18.dp) }, tint = accent, tintAlpha = .22f
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Shield, null, tint = accent, modifier = Modifier.size(25.dp))
                }
            }
        }
        GlassPanel(backdrop, modifier = Modifier.fillMaxWidth(), radius = 27.dp) {
            Box(Modifier.fillMaxWidth().height(210.dp)) {
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
                            .background(accent, CircleShape)
                    )
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("动态粒子场", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("MOTION CUES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Text("LIVE PREVIEW", Modifier.align(Alignment.BottomStart).padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
        }
        GlassPanel(backdrop, modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (a.running) Color(0xFF6FA88B) else MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(Modifier.width(9.dp))
                    Text(if (a.running) "运行中" else "尚未启动", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(a.status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                PrismalGlassButton(
                    onClick = a::toggleMonitor,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    height = 54.dp,
                    shape = { PrismalRoundedRectangle(18.dp) },
                    tint = if (a.running) MaterialTheme.colorScheme.surfaceVariant else Palette[a.paletteIndex],
                    tintAlpha = .28f,
                    contentPadding = PaddingValues(horizontal = 18.dp)
                ) {
                    Icon(if (a.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurface)
                    Text(if (a.running) "停止屏幕气泡" else "开启屏幕气泡", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Text("动态气泡随设备运动流动，不拦截触摸操作。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        GlassPanel(backdrop, modifier = Modifier.fillMaxWidth(), radius = 24.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("实时运动信息", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(a.status, color = if (a.status == "静止") MaterialTheme.colorScheme.onSurfaceVariant else Palette[a.paletteIndex], fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCell("横滚", "${a.roll.toInt()}°", backdrop, Modifier.weight(1f))
                    InfoCell("俯仰", "${a.pitch.toInt()}°", backdrop, Modifier.weight(1f))
                    InfoCell("方位", "${a.azimuth.toInt()}°", backdrop, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (a.yawRate > 0.1f) Icons.Rounded.TurnLeft else if (a.yawRate < -0.1f) Icons.Rounded.TurnRight else Icons.Rounded.Explore, null, tint = Palette[a.paletteIndex], modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("转向角速度", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.2f rad/s", a.yawRate), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GpsFixed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("定位方式", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(a.gpsProvider, color = if (a.gpsProvider == "GPS 定位") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCell("纬度", a.gpsLat, backdrop, Modifier.weight(1f))
                    InfoCell("经度", a.gpsLng, backdrop, Modifier.weight(1f))
                    InfoCell("速度", a.gpsSpeed, backdrop, Modifier.weight(1f))
                }
                Text("定位由 GPS 卫星或网络提供，仅用于显示，不会上传。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("灵敏度", String.format(Locale.US, "%.1f×", a.sensitivity), Icons.Rounded.Tune, backdrop, Modifier.weight(1f))
            SummaryTile("气泡主题", PaletteNames[a.paletteIndex], Icons.Rounded.Palette, backdrop, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoCell(title: String, value: String, backdrop: PrismalBackdrop, modifier: Modifier = Modifier) {
    PrismalGlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = { PrismalRoundedRectangle(14.dp) },
        tint = MaterialTheme.colorScheme.surfaceVariant,
        tintAlpha = .14f,
        blurRadius = 7.dp
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BubbleDot(color: Color, size: Int) {
    Box(Modifier.size((size * 0.72f).dp).background(color, CircleShape))
}

@Composable
private fun SummaryTile(title: String, value: String, icon: ImageVector, backdrop: PrismalBackdrop, modifier: Modifier = Modifier) {
    GlassPanel(backdrop, modifier = modifier, radius = 19.dp) {
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
private fun SettingsPage(a: MainActivity, backdrop: PrismalBackdrop, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp).padding(top = 27.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Text("设置", color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("调整气泡反馈与应用行为", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        GlassPanel(backdrop, modifier = Modifier.fillMaxWidth(), radius = 23.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("外观模式", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                        PrismalGlassSurface(
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = { PrismalRoundedRectangle(14.dp) },
                            onClick = { a.applyThemeMode(index) },
                            tint = if (a.themeMode == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            tintAlpha = if (a.themeMode == index) .30f else .12f
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Text("不使用系统动态取色，保持中性灰阶外观。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        GlassPanel(backdrop, modifier = Modifier.fillMaxWidth(), radius = 23.dp) {
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
                PrismalGlassSlider(
                    value = { a.sensitivity },
                    onValueChange = { a.updateConfig(sensitivityValue = it) },
                    valueRange = .6f..4.0f,
                    visibilityThreshold = .01f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
                Text("数值越高，气泡对设备运动的响应越明显。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        GlassPanel(backdrop, modifier = Modifier.fillMaxWidth(), radius = 23.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("气泡颜色", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.forEachIndexed { index, color ->
                        PrismalGlassSurface(
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(78.dp),
                            shape = { PrismalRoundedRectangle(14.dp) },
                            onClick = { a.updateConfig(colorValue = index) },
                            tint = color,
                            tintAlpha = if (a.paletteIndex == index) .38f else .15f,
                            blurRadius = 8.dp
                        ) {
                            Column(Modifier.fillMaxSize().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Box(Modifier.size(18.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = .65f), CircleShape))
                                Spacer(Modifier.height(5.dp))
                                Text(PaletteNames[index], color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
                                if (a.paletteIndex == index) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
        }

        GlassPanel(backdrop, modifier = Modifier.fillMaxWidth(), radius = 23.dp) {
            Column {
                SettingRow(Icons.Rounded.Shield, "悬浮窗权限", "管理屏幕边缘气泡显示权限") {
                    a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
                }
            }
        }
        GlassPanel(backdrop, modifier = Modifier.fillMaxWidth(), radius = 19.dp) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("外观能力", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    if (PrismalGlass.supportsRefraction) "PrismalAGSL：完整折射与色散可用"
                    else if (PrismalGlass.supportsBackdropBlur) "Prismal：背景模糊可用，折射效果按系统能力降级"
                    else "Prismal：使用兼容磨砂玻璃效果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
                Text("版本 ${a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "未知"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                Text("开源许可与第三方声明", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("本应用按 GNU GPL-3.0-or-later 发布。全场景液态玻璃采用 PrismalAGSL（MIT）；底部导航继续采用 LiquidGlassBar（GPL-3.0-or-later），其 NOTICE 列明 KernelSU、AndroidLiquidGlass 与 Miuix 的第三方组件许可。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("PrismalAGSL 项目与 MIT 许可", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                    modifier = Modifier.clickable { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/styropyr0/PrismalAGSL"))) })
                Text("LiquidGlassBar 项目与完整第三方版权、许可清单", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                    modifier = Modifier.clickable { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wzhdgithub/LiquidGlassBar"))) })
                Text("本应用源码及 GPL-3.0 许可", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                    modifier = Modifier.clickable { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/liangfangfa0-afk/MotionGuard"))) })
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
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
