package com.lwsnb.optiboost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lwsnb.optiboost.engine.ShellEngine
import com.lwsnb.optiboost.engine.SysReader
import com.lwsnb.optiboost.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiState.refreshEngine()
        UiState.refreshDashboard()
        UiState.refreshDelta(OptiBoostApp.instance.appScope)
        setContent { OptiBoostUI() }
    }
}

// ===================== 主题与配色 =====================

val BgDeep = Color(0xFF0A0E1F)
val CardDeep = Color(0xFF141A33)
val AccentBlue = Color(0xFF3D5AFE)
val AccentCyan = Color(0xFF00E5FF)
val AccentGreen = Color(0xFF00E676)
val AccentRed = Color(0xFFFF5252)
val AccentAmber = Color(0xFFFFC400)
val TextDim = Color(0xFF8A93B5)

@Composable
fun OptiBoostUI() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentBlue,
            secondary = AccentCyan,
            background = BgDeep,
            surface = CardDeep,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        var tab by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                withContext(Dispatchers.IO) {
                    UiState.refreshCpu()
                    UiState.refreshDashboard()
                }
                delay(3000)
            }
        }
        Scaffold(
            containerColor = BgDeep,
            bottomBar = {
                NavigationBar(containerColor = CardDeep) {
                    listOf(
                        Triple("首页", Icons.Filled.Speed, 0),
                        Triple("三角洲", Icons.Filled.RocketLaunch, 1),
                        Triple("温控", Icons.Filled.DeviceThermostat, 2),
                        Triple("应用", Icons.Filled.Apps, 3),
                        Triple("设置", Icons.Filled.Settings, 4)
                    ).forEach { (label, icon, idx) ->
                        NavigationBarItem(
                            selected = tab == idx,
                            onClick = { tab = idx },
                            icon = { Icon(icon, null) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentCyan,
                                selectedTextColor = AccentCyan,
                                indicatorColor = AccentBlue.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomePage()
                    1 -> DeltaPage()
                    2 -> ThermalPage()
                    3 -> AppsPage()
                    4 -> SettingsPage()
                }
            }
        }
    }
}

// ===================== 通用组件 =====================

@Composable
fun StatCard(scope: RowScope, title: String, value: String, sub: String, tint: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDeep),
        modifier = with(scope) { Modifier.weight(1f) }
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, color = TextDim)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint)
            Text(sub, fontSize = 11.sp, color = TextDim)
        }
    }
}

@Composable
fun EngineBadge() {
    val mode = UiState.engineMode
    val (label, color) = when (mode) {
        ShellEngine.Mode.SHIZUKU -> "Shizuku 免 Root 引擎" to AccentGreen
        ShellEngine.Mode.ROOT -> "Root 引擎" to AccentAmber
        ShellEngine.Mode.NONE -> "未接入特权引擎" to AccentRed
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, color = color)
        }
    }
}

@Composable
fun BigButton(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !UiState.busy,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = tint.copy(alpha = 0.18f), contentColor = tint
        ),
        modifier = Modifier.fillMaxWidth().height(58.dp)
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BusyBar(tint: Color) {
    if (UiState.busy) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            Modifier.fillMaxWidth(), color = tint, trackColor = CardDeep
        )
        Text(
            UiState.currentStep, fontSize = 12.sp, color = TextDim,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun LogCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDeep)
    ) {
        if (UiState.logs.isEmpty()) {
            Text(
                "暂无记录，点击上方按钮开始",
                fontSize = 12.sp, color = TextDim, modifier = Modifier.padding(14.dp)
            )
        } else {
            LazyColumn(Modifier.height(220.dp)) {
                items(UiState.logs) { line ->
                    Text(
                        line, fontSize = 11.sp, color = TextDim,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

// ===================== 首页 =====================

@Composable
fun HomePage() {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("极启优化", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.weight(1f))
            EngineBadge()
        }
        Text(
            "${SysReader.deviceModel()} · Android ${SysReader.androidVersion()}",
            fontSize = 12.sp, color = TextDim, modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(16.dp))

        val used = UiState.memTotalKb - UiState.memAvailKb
        val memPct = if (UiState.memTotalKb > 0) (used * 100 / UiState.memTotalKb).toInt() else -1
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(this@Row,
                "内存", if (memPct >= 0) "$memPct%" else "--",
                "${fmtKb(used)} / ${fmtKb(UiState.memTotalKb)}",
                if (memPct > 85) AccentRed else AccentCyan
            )
            StatCard(this@Row,
                "CPU", if (UiState.cpuUsage >= 0) "${UiState.cpuUsage}%" else "--",
                "${SysReader.cpuCores()} 核 ${freqText()}",
                if (UiState.cpuUsage > 85) AccentRed else AccentGreen
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(this@Row,
                "电池温度",
                if (UiState.batteryTemp > 0) "%.1f℃".format(UiState.batteryTemp / 10.0) else "--",
                "电量 ${if (UiState.batteryLevel >= 0) "${UiState.batteryLevel}%" else "--"}",
                if (UiState.batteryTemp > 400) AccentRed else AccentAmber
            )
            StatCard(this@Row,
                "机身温度",
                if (UiState.zoneTemp > 0) "${UiState.zoneTemp}℃" else "--",
                "thermal 最高读数",
                if (UiState.zoneTemp >= 45) AccentRed else AccentCyan
            )
        }

        Spacer(Modifier.height(20.dp))
        BigButton("一键优化", Icons.Filled.Bolt, AccentCyan) {
            UiState.runBoost("quick", scope)
        }
        BusyBar(AccentCyan)

        Spacer(Modifier.height(20.dp))
        Text("执行日志", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        LogCard()
    }
}

fun fmtKb(kb: Long): String =
    if (kb < 0) "--"
    else if (kb > 1024L * 1024L) "%.1fGB".format(kb / 1024.0 / 1024.0)
    else "${kb / 1024}MB"

fun freqText(): String {
    val f = SysReader.cpuFreqKhz()
    return if (f > 0) "%.1fGHz".format(f / 1_000_000.0) else ""
}

// ===================== 三角洲 =====================

@Composable
fun DeltaPage() {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(AccentBlue.copy(alpha = 0.35f), CardDeep)),
                    RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Text("三角洲行动专项", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "免 Root 极致加速：清后台 / 释放缓存 / 调度重置 / 日志限流",
                    fontSize = 12.sp, color = TextDim
                )
                Spacer(Modifier.height(12.dp))
                if (UiState.deltaVariant.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(50), color = AccentGreen.copy(alpha = 0.15f)) {
                        Text(
                            "已检测到：三角洲行动 ${UiState.deltaVariant}（${UiState.deltaPkg}）",
                            fontSize = 12.sp, color = AccentGreen,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Surface(shape = RoundedCornerShape(50), color = AccentAmber.copy(alpha = 0.15f)) {
                        Text(
                            "未检测到三角洲行动，通用加速仍可用",
                            fontSize = 12.sp, color = AccentAmber,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardDeep)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("激进模式", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = UiState.aggroMode,
                        onCheckedChange = { UiState.aggroMode = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                }
                Text("额外执行：系统动画降档 0.5x", fontSize = 11.sp, color = TextDim)
            }
        }

        Spacer(Modifier.height(16.dp))
        BigButton("启动专项加速", Icons.Filled.RocketLaunch, AccentBlue) {
            UiState.runBoost("delta", scope)
        }
        BusyBar(AccentBlue)

        Spacer(Modifier.height(20.dp))
        Text("执行明细", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        LogCard()
    }
}

// ===================== 温控 =====================

@Composable
fun ThermalPage() {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("温控管理", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text("读取实时温度，重置温控策略", fontSize = 12.sp, color = TextDim)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(this@Row,
                "电池",
                if (UiState.batteryTemp > 0) "%.1f℃".format(UiState.batteryTemp / 10.0) else "--",
                "电量 ${UiState.batteryLevel}%",
                if (UiState.batteryTemp > 400) AccentRed else AccentGreen
            )
            StatCard(this@Row,
                "机身",
                if (UiState.zoneTemp > 0) "${UiState.zoneTemp}℃" else "--",
                "thermal zone max",
                if (UiState.zoneTemp >= 45) AccentRed else AccentCyan
            )
        }

        Spacer(Modifier.height(20.dp))
        BigButton("温控重置（性能向）", Icons.Filled.DeviceThermostat, AccentAmber) {
            UiState.runBoost("thermal", scope)
        }
        BusyBar(AccentAmber)

        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardDeep)) {
            Column(Modifier.padding(14.dp)) {
                Text("说明", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "· 停止 thermald / thermal-engine / mi_thermald 守护进程\n" +
                        "· 探测 /vendor/etc/thermal-engine*.conf 并尝试切换性能档\n" +
                        "· 部分机型温控在内核层实现，shell 权限无法完全绕过\n" +
                        "· 重置后建议立即冷启动游戏",
                    fontSize = 11.sp, color = TextDim, lineHeight = 17.sp
                )
            }
        }
    }
}

// ===================== 应用管理 =====================

@Composable
fun AppsPage() {
    val app = OptiBoostApp.instance
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshTick) { UiState.refreshFrozen(app.appScope) }

    val query = UiState.appSearch
    val frozenSet = UiState.frozenSet
    val pkgCount = app.packageCache.size
    data class AppRow(val pkg: String, val name: String, val frozen: Boolean)
    val apps = remember(query, refreshTick, frozenSet.size, pkgCount) {
        val frozen = frozenSet
        app.packageCache
            .filter { app.isUserApp(it) }
            .mapNotNull { info ->
                val name = app.appName(info)
                if (query.isBlank() || name.contains(query, true) || info.packageName.contains(query, true))
                    AppRow(info.packageName, name, info.packageName in frozen)
                else null
            }
            .sortedWith(compareByDescending<AppRow> { it.frozen }.thenBy { it.name })
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("应用管理", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text("冻结 = pm disable-user（可逆），随时可解冻恢复", fontSize = 12.sp, color = TextDim)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = UiState.appSearch,
            onValueChange = { UiState.appSearch = it },
            placeholder = { Text("搜索应用", fontSize = 13.sp, color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = AccentBlue,
                unfocusedContainerColor = CardDeep,
                focusedContainerColor = CardDeep,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("用户应用 ${apps.size} 个", fontSize = 12.sp, color = TextDim)
            Spacer(Modifier.weight(1f))
            Text("冻结 ${frozenSet.size} 个", fontSize = 12.sp, color = AccentCyan)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                app.refreshPackages()
                refreshTick++
            }) { Text("刷新", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apps) { row ->
                val pkg = row.pkg
                val isFrozen = row.frozen
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDeep)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.name, fontSize = 14.sp, color = Color.White, maxLines = 1)
                            Text(pkg, fontSize = 10.sp, color = TextDim, maxLines = 1)
                        }
                        if (isFrozen) {
                            Icon(
                                Icons.Filled.AcUnit, null,
                                tint = AccentCyan, modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        OutlinedButton(
                            onClick = { UiState.toggleFreeze(pkg, scope) },
                            enabled = !UiState.busy && UiState.engineMode != ShellEngine.Mode.NONE,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isFrozen) AccentGreen else AccentRed
                            )
                        ) {
                            Text(if (isFrozen) "解冻" else "冻结", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ===================== 设置 =====================

@Composable
fun SettingsPage() {
    val app = OptiBoostApp.instance
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("设置", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardDeep)) {
            Column(Modifier.padding(16.dp)) {
                Text("特权引擎", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                EngineBadge()
                Spacer(Modifier.height(10.dp))
                Text(
                    when (UiState.engineMode) {
                        ShellEngine.Mode.SHIZUKU ->
                            "Shizuku 已连接，所有优化功能可用（免 Root）"
                        ShellEngine.Mode.ROOT ->
                            "已通过 Root 执行，功能全量可用"
                        ShellEngine.Mode.NONE ->
                            "尚未授权。安装 Shizuku 并在本机启动（Android 11+ 可用无线调试激活），然后点击下方申请权限"
                    },
                    fontSize = 12.sp, color = TextDim, lineHeight = 17.sp
                )
                if (UiState.engineMode == ShellEngine.Mode.NONE) {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { ShellEngine.requestShizukuPermission(1001) }) {
                        Text("申请 Shizuku 权限")
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    UiState.refreshEngine()
                    UiState.refreshDelta(app.appScope)
                    UiState.refreshFrozen(app.appScope)
                }) { Text("重新检测") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardDeep)) {
            Column(Modifier.padding(16.dp)) {
                Text("关于", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "极启优化 OptiBoost v1.0.0\n" +
                        "免 Root 极致优化 · 三角洲行动专项\n" +
                        "引擎：Shizuku（shell 身份）/ Root 双通道",
                    fontSize = 12.sp, color = TextDim, lineHeight = 18.sp
                )
            }
        }
    }
}
