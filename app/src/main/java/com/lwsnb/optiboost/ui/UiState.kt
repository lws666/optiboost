package com.lwsnb.optiboost.ui

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

/** 全局 UI 状态：只有「开 / 关」两种态 */
object UiState {
    var engineMode by mutableStateOf(ShellEngine.Mode.NONE)
    var busy by mutableStateOf(false)
    var currentStep by mutableStateOf("")
    val logs = mutableStateListOf<String>()

    /** 优化总开关状态 */
    var enabled by mutableStateOf(false)

    // 仪表盘
    var memTotalKb by mutableStateOf(-1L)
    var memAvailKb by mutableStateOf(-1L)
    var cpuUsage by mutableStateOf(-1)
    var batteryTemp by mutableStateOf(-1)
    var batteryLevel by mutableStateOf(-1)
    var zoneTemp by mutableStateOf(-1)

    var isColorOS by mutableStateOf(false)
    var osLabel by mutableStateOf("")
    var aggroMode by mutableStateOf(true)

    fun log(s: String) {
        logs.add(0, "[${SysReader.currentTime()}] $s")
        if (logs.size > 200) logs.removeAt(logs.lastIndex)
    }

    fun refreshEngine() { engineMode = ShellEngine.detectMode() }

    fun refreshDashboard() {
        memTotalKb = SysReader.memTotalKb()
        memAvailKb = SysReader.memAvailableKb()
        batteryLevel = SysReader.batteryLevel()
        batteryTemp = SysReader.batteryTempTenthC()
        zoneTemp = SysReader.thermalZoneTempC()
        isColorOS = OptiBoostApp.isColorOS()
        osLabel = OptiBoostApp.colorOsVersion()
    }

    fun refreshCpu() { cpuUsage = SysReader.cpuUsage(250) }

    /** 开关：true = 一键开启，false = 一键关闭（完整回滚） */
    fun toggle(scope: CoroutineScope) {
        if (busy) return
        val target = !enabled
        busy = true
        currentStep = ""
        scope.launch(Dispatchers.IO) {
            try {
                val report = if (target) {
                    Optimizer.runEnable(aggroMode) { currentStep = it }
                } else {
                    Optimizer.runRestore { currentStep = it }
                }
                report.steps.forEach { s ->
                    log("${s.name} — ${if (s.ok) "✓" else "✗"} ${s.detail}")
                }
                log("引擎 ${report.mode} · ${report.steps.count { it.ok }}/${report.steps.size}")
                enabled = target
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
}
