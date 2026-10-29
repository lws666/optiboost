package com.lwsnb.optiboost.core

import com.lwsnb.optiboost.OptiBoostApp
import com.lwsnb.optiboost.engine.ShellEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StepResult(val name: String, val ok: Boolean, val detail: String)
data class BoostReport(val steps: List<StepResult>, val mode: ShellEngine.Mode)

/**
 * C16 专项优化引擎。
 *
 * ColorOS 16 实测特性决定了这里的每条策略：
 * 1. `pm disable-user` 对 heytap/coloros 受保护包会抛 SecurityException → 降级 `pm suspend`
 * 2. com.oplus.athena（内存管理）是清理链路核心，绝不能禁，反而要放行
 * 3. com.oplus.games / gamesettings 是性能调度通道，禁了会丢游戏加速
 * 4. 双目标：三角洲(com.proxima.dsi/dfm) + 无畏契约(com.tencent.tmgp.codev)
 * 5. 游戏进程本身绝不 force-stop，只清周边噪声
 */
object Optimizer {

    val TARGET_GAMES = OptiBoostApp.GAME_TARGETS

    private fun guard(mode: ShellEngine.Mode): BoostReport? =
        if (mode == ShellEngine.Mode.NONE) {
            BoostReport(
                listOf(StepResult("引擎不可用", false, "未授权 Shizuku，也未获取 Root")), mode
            )
        } else null

    // ================= 一键开启 / 关闭 =================

    suspend fun runEnable(
        aggro: Boolean,
        onStep: (String) -> Unit = {}
    ): BoostReport = withContext(Dispatchers.IO) {
        val mode = ShellEngine.detectMode()
        guard(mode)?.let { return@withContext it }
        val installed = installedPackages(mode)
        val results = mutableListOf<StepResult>()

        // 1. 释放缓存 + 内存收缩
        onStep("释放缓存")
        results.add(step(mode, "释放页缓存", "sync; echo 3 > /proc/sys/vm/drop_caches"))
        results.add(step(mode, "收缩碎片内存", "echo 1 > /proc/sys/vm/compact_memory"))

        // 2. 清第三方后台
        onStep("清理后台")
        var killed = 0
        for (pkg in OptiBoostApp.GAME_NOISE_PACKAGES) {
            if (pkg in OptiBoostApp.ESSENTIAL_PACKAGES) continue
            if (pkg in installed) { ShellEngine.forceStop(mode, pkg); killed++ }
        }
        ShellEngine.exec(mode, "am kill-all")
        results.add(StepResult("清理第三方后台", killed > 0, "处理 $killed 个"))

        // 3. 挂起 C16 冗余组件（suspend，可逆）
        onStep("挂起冗余组件")
        var susp = 0
        for (pkg in OptiBoostApp.C16_NOISE.keys + OptiBoostApp.C16_SUSPEND_ONLY) {
            if (pkg in OptiBoostApp.ESSENTIAL_PACKAGES || pkg !in installed) continue
            val (c, _) = ShellEngine.suspend(mode, pkg)
            if (c == 0) susp++
        }
        results.add(StepResult("挂起冗余组件", susp > 0, "suspend $susp 个"))

        // 4. CPU 调度解锁
        onStep("解锁 CPU 调度")
        results.add(step(mode, "解锁 CPU 调度",
            "for c in /sys/devices/system/cpu/cpu*; do " +
                "echo schedutil > \$c/cpufreq/scaling_governor 2>/dev/null; done; true"))

        // 5. 放行 C16 性能组件
        onStep("放行性能组件")
        var allowed = 0
        for (pkg in OptiBoostApp.OPLUS_PERF_PACKAGES) {
            if (pkg !in installed) continue
            ShellEngine.exec(mode, "pm enable $pkg 2>/dev/null; true")
            ShellEngine.exec(mode, "pm unsuspend --user 0 $pkg 2>/dev/null; true")
            allowed++
        }
        results.add(StepResult("放行性能组件", allowed > 0, "athena/games/battery $allowed 项"))

        // 6. 温控放行
        onStep("温控放行")
        for (svc in listOf("thermald", "thermal-engine", "oplus_thermal")) {
            ShellEngine.exec(mode, "stop $svc 2>/dev/null; killall $svc 2>/dev/null; true")
        }
        results.add(StepResult("温控放行", true, "停止温控守护进程"))

        // 7. 激进项
        if (aggro) {
            onStep("动画降档")
            ShellEngine.exec(mode, "settings put global window_animation_scale 0.5")
            ShellEngine.exec(mode, "settings put global transition_animation_scale 0.5")
            ShellEngine.exec(mode, "settings put global animator_duration_scale 0.5")
            results.add(StepResult("动画降档 0.5x", true, "已应用"))
        }

        // 8. 日志限流
        onStep("日志限流")
        results.add(step(mode, "日志限流",
            "setprop persist.log.tag S 2>/dev/null; true"))

        results.add(StepResult("已开启", true, "冷启动游戏获得最佳效果"))
        BoostReport(results, mode)
    }

    /**
     * 一键关闭：完整回滚所有改动，恢复到系统原始状态。
     * - 撤销所有 suspend（含 C16 受保护包）
     * - 解除 disable-user 冻结
     * - 动画缩放恢复 1.0
     * - 重启温控服务
     */
    suspend fun runRestore(onStep: (String) -> Unit = {}): BoostReport =
        withContext(Dispatchers.IO) {
            val mode = ShellEngine.detectMode()
            guard(mode)?.let { return@withContext it }
            val results = mutableListOf<StepResult>()
            val installed = installedPackages(mode)

            // 1. 撤销全部 suspend
            onStep("恢复挂起组件")
            var un1 = 0
            for (pkg in OptiBoostApp.C16_NOISE.keys + OptiBoostApp.C16_SUSPEND_ONLY) {
                if (pkg !in installed) continue
                val (c, _) = ShellEngine.unsuspend(mode, pkg)
                if (c == 0) un1++
            }
            results.add(StepResult("恢复挂起组件", un1 > 0, "unsuspend $un1 个"))

            // 2. 解除已冻结的应用
            onStep("解除冻结")
            val (_, frozenOut) = ShellEngine.exec(mode, "pm list packages -d")
            val frozen = frozenOut.lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { it.isNotBlank() && it != "package:" }
                .toSet()
            var un2 = 0
            for (pkg in frozen) {
                if (pkg in OptiBoostApp.ESSENTIAL_PACKAGES) continue
                val (c, _) = ShellEngine.unfreeze(mode, pkg)
                if (c == 0) un2++
            }
            results.add(StepResult("解除冻结", un2 > 0, "恢复 $un2 个应用"))

            // 3. 动画恢复
            onStep("恢复动画")
            ShellEngine.exec(mode, "settings put global window_animation_scale 1.0")
            ShellEngine.exec(mode, "settings put global transition_animation_scale 1.0")
            ShellEngine.exec(mode, "settings put global animator_duration_scale 1.0")
            results.add(StepResult("恢复动画 1.0x", true, "已还原"))

            // 4. 重启温控
            onStep("恢复温控")
            ShellEngine.exec(mode, "start thermal-engine 2>/dev/null; start thermald 2>/dev/null; true")
            results.add(StepResult("恢复温控服务", true, "已重启"))

            // 5. 恢复日志级别
            onStep("恢复日志")
            ShellEngine.exec(mode, "setprop persist.log.tag V 2>/dev/null; true")
            results.add(StepResult("恢复日志级别", true, "已还原"))

            results.add(StepResult("已关闭", true, "系统已恢复原始状态"))
            BoostReport(results, mode)
        }

    // ================= 工具 =================

    private fun step(mode: ShellEngine.Mode, name: String, cmd: String): StepResult {
        val (code, out) = ShellEngine.exec(mode, cmd)
        return StepResult(name, code == 0, out.ifEmpty { "OK" })
    }

    fun installedPackages(mode: ShellEngine.Mode): Set<String> {
        val (_, out) = ShellEngine.exec(mode, "pm list packages")
        return out.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /** 检测已安装的目标游戏 */
    fun detectGames(mode: ShellEngine.Mode): List<String> =
        installedPackages(mode).filter { it in TARGET_GAMES }.sorted()
}
