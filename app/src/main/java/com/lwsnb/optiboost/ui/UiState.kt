package com.lwsnb.optiboost.ui

import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lwsnb.optiboost.OptiBoostApp
import com.lwsnb.optiboost.core.Optimizer
import com.lwsnb.optiboost.engine.ShellEngine
import com.lwsnb.optiboost.engine.SysReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 全局 UI 状态（单 Activity 应用，直接用单例状态容器） */
object UiState {
    var engineMode by mutableStateOf(ShellEngine.Mode.NONE)
    var busy by mutableStateOf(false)
    var currentStep by mutableStateOf("")
    val logs = mutableStateListOf<String>()

    // 仪表盘
    var memTotalKb by mutableStateOf(-1L)
    var memAvailKb by mutableStateOf(-1L)
    var cpuUsage by mutableStateOf(-1)
    var batteryTemp by mutableStateOf(-1)
    var batteryLevel by mutableStateOf(-1)
    var zoneTemp by mutableStateOf(-1)

    // 三角洲
    var deltaVariant by mutableStateOf("")   // "国服" / "国际服" / ""
    var deltaPkg by mutableStateOf("")
    var aggroMode by mutableStateOf(true)

    // 应用管理
    var appSearch by mutableStateOf("")
    var frozenSet by mutableStateOf(setOf<String>())

    fun log(s: String) {
        logs.add(0, "[${SysReader.currentTime()}] $s")
        if (logs.size > 200) logs.removeAt(logs.lastIndex)
    }

    fun refreshEngine() {
        engineMode = ShellEngine.detectMode()
    }

    fun refreshDashboard() {
        memTotalKb = SysReader.memTotalKb()
        memAvailKb = SysReader.memAvailableKb()
        batteryLevel = SysReader.batteryLevel()
        batteryTemp = SysReader.batteryTempTenthC()
        zoneTemp = SysReader.thermalZoneTempC()
    }

    fun refreshCpu() {
        cpuUsage = SysReader.cpuUsage(250)
    }

    /** 检测三角洲安装情况（走 pm path，免引擎时降级为 PackageManager） */
    fun refreshDelta(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val pm = OptiBoostApp.instance.packageManager
            var variant = ""
            var pkg = ""
            for ((p, v) in listOf(OptiBoostApp.DELTA_CN to "国服", OptiBoostApp.DELTA_GLOBAL to "国际服")) {
                val found = try {
                    pm.getPackageInfo(p, 0); true
                } catch (t: Throwable) { false }
                if (found) { variant = v; pkg = p; break }
            }
            deltaVariant = variant
            deltaPkg = pkg
        }
    }

    fun refreshFrozen(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val mode = ShellEngine.detectMode()
            if (mode == ShellEngine.Mode.NONE) return@launch
            val (_, out) = ShellEngine.exec(mode, "pm list packages -d")
            frozenSet = out.lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }

    fun runBoost(kind: String, scope: CoroutineScope) {
        if (busy) return
        busy = true
        currentStep = ""
        scope.launch(Dispatchers.IO) {
            try {
                val report = when (kind) {
                    "quick" -> Optimizer.runQuickBoost { currentStep = it }
                    "delta" -> Optimizer.runDeltaBoost(aggroMode) { currentStep = it }
                    "thermal" -> Optimizer.runThermalReset { currentStep = it }
                    else -> null
                }
                report?.let {
                    it.steps.forEach { s -> log("${s.name} — ${if (s.ok) "✓" else "✗"} ${s.detail}") }
                    log("引擎: ${it.mode} · 完成 ${it.steps.count { s -> s.ok }}/${it.steps.size}")
                }
            } catch (t: Throwable) {
                log("异常: ${t.message}")
            } finally {
                busy = false
                currentStep = ""
                refreshEngine()
                refreshDashboard()
            }
        }
    }

    fun toggleFreeze(pkg: String, scope: CoroutineScope) {
        val mode = engineMode
        if (mode == ShellEngine.Mode.NONE || busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val frozen = pkg in frozenSet
                val (code, out) =
                    if (frozen) ShellEngine.unfreeze(mode, pkg)
                    else ShellEngine.freeze(mode, pkg)
                log("${if (frozen) "解冻" else "冻结"} $pkg → $code $out")
                if (code == 0) {
                    frozenSet =
                        if (frozen) frozenSet - pkg else frozenSet + pkg
                }
            } finally {
                busy = false
            }
        }
    }
}
