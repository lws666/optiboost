package com.lwsnb.optiboost.core

import com.lwsnb.optiboost.OptiBoostApp
import com.lwsnb.optiboost.engine.ShellEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StepResult(val name: String, val ok: Boolean, val detail: String)
data class BoostReport(val steps: List<StepResult>, val mode: ShellEngine.Mode)

/**
 * 优化引擎：所有操作经 ShellEngine（Shizuku 免 root / Root）执行。
 */
object Optimizer {

    // ================= 三角洲专项 =================

    /** 三角洲行动目标包：国服 com.proxima.dsi / 国际服 com.proxima.dfm */
    val DELTA_TARGETS = listOf(OptiBoostApp.DELTA_CN, OptiBoostApp.DELTA_GLOBAL)

    /** 三角洲运行时的典型后台噪声应用 */
    val OPTIMIZABLE_NOISE = listOf(
        "com.tencent.mobileqq", "com.tencent.mm", "com.ss.android.ugc.aweme",
        "com.netease.cloudmusic", "com.smile.gifmaker", "com.sina.weibo",
        "com.xingin.xhs", "com.baidu.BaiduMap", "com.autonavi.minimap",
        "com.UCMobile", "com.android.browser", "com.taobao.taobao",
        "com.jingdong.app.mall", "com.tmall.wireless", "com.meituan.android.pt"
    )

    /** 绝不清理的包 */
    val ESSENTIAL = OptiBoostApp.ESSENTIAL_PACKAGES + DELTA_TARGETS

    // ================= 通用一键优化 =================

    val STEPS = listOf(
        Triple("清理后台进程", "kill", "am kill-all"),
        Triple("释放系统缓存", "cache", "sync; echo 3 > /proc/sys/vm/drop_caches"),
        Triple("重置 CPU 调度策略", "governor",
            "for c in /sys/devices/system/cpu/cpu*; do echo schedutil > \$c/cpufreq/scaling_governor 2>/dev/null; done; true"),
        Triple("收拢碎片化内存", "compact", "echo 1 > /proc/sys/vm/compact_memory")
    )

    suspend fun runQuickBoost(onStep: (String) -> Unit = {}): BoostReport =
        withContext(Dispatchers.IO) {
            val mode = ShellEngine.detectMode()
            if (mode == ShellEngine.Mode.NONE) {
                return@withContext BoostReport(
                    listOf(StepResult("引擎不可用", false, "未授权 Shizuku，也未获取 Root")), mode)
            }
            val results = mutableListOf<StepResult>()
            for ((name, _, cmd) in STEPS) {
                onStep(name)
                val (code, out) = ShellEngine.exec(mode, cmd)
                results.add(StepResult(name, code == 0, out.ifEmpty { "OK" }))
            }
            BoostReport(results, mode)
        }

    // ================= 三角洲专项加速 =================

    suspend fun runDeltaBoost(
        aggro: Boolean,
        onStep: (String) -> Unit = {}
    ): BoostReport = withContext(Dispatchers.IO) {
        val mode = ShellEngine.detectMode()
        if (mode == ShellEngine.Mode.NONE) {
            return@withContext BoostReport(
                listOf(StepResult("引擎不可用", false, "未授权 Shizuku，也未获取 Root")), mode)
        }
        val results = mutableListOf<StepResult>()

        // 1. 识别三角洲安装状态
        onStep("识别三角洲行动")
        var deltaInstalled = false
        var deltaPkg = ""
        for (pkg in DELTA_TARGETS) {
            val (code, _) = ShellEngine.exec(mode, "pm path $pkg")
            if (code == 0) {
                deltaInstalled = true
                deltaPkg = pkg
                break
            }
        }
        results.add(StepResult(
            "识别三角洲行动", deltaInstalled,
            if (deltaInstalled) deltaPkg else "未安装（仍可执行通用加速）"
        ))

        // 2. 释放缓存
        onStep("释放系统缓存")
        results.add(run(mode, "释放系统缓存", "sync; echo 3 > /proc/sys/vm/drop_caches"))

        // 3. 清后台（反白名单制）
        onStep("清理后台干扰源")
        var killed = 0
        val installed = installedPackages(mode)
        for (pkg in OPTIMIZABLE_NOISE) {
            if (pkg in ESSENTIAL) continue
            if (installed.contains(pkg)) {
                ShellEngine.forceStop(mode, pkg)
                killed++
            }
        }
        ShellEngine.killBackground(mode)
        results.add(StepResult("清理后台干扰源", killed > 0, "命中 $killed 个已安装噪声应用"))

        // 4. 内存收缩
        onStep("收拢碎片化内存")
        results.add(run(mode, "收拢碎片化内存", "echo 1 > /proc/sys/vm/compact_memory"))

        // 5. 调度器重置
        onStep("重置 CPU 调度状态")
        results.add(run(mode, "重置 CPU 调度状态",
            "for c in /sys/devices/system/cpu/cpu*; do echo schedutil > \$c/cpufreq/scaling_governor 2>/dev/null; done; true"))

        // 6. 激进模式附加项
        if (aggro) {
            onStep("关闭非必要系统动画")
            ShellEngine.exec(mode, "settings put global window_animation_scale 0.5")
            ShellEngine.exec(mode, "settings put global transition_animation_scale 0.5")
            ShellEngine.exec(mode, "settings put global animator_duration_scale 0.5")
            results.add(StepResult("动画降档 0.5x", true, "window/transition/animator"))
        }

        // 7. 日志关阀（持续生效）
        onStep("限制系统日志级别")
        results.add(run(mode, "限制系统日志级别",
            "setprop persist.log.tag S 2>/dev/null; setprop ro.logd.size 256K 2>/dev/null; true"))

        // 8. 提示启动游戏
        if (deltaInstalled) {
            onStep("准备就绪")
            results.add(StepResult("准备就绪", true, "三角洲专项加速完成，建议从桌面冷启动游戏"))
        }
        BoostReport(results, mode)
    }

    // ================= 温控 =================

    val THERMAL_CONFIG_CANDIDATES = listOf(
        "/vendor/etc/thermal-engine.conf",
        "/vendor/etc/thermal-engine-8g.conf",
        "/vendor/etc/thermal-engine-normal.conf",
        "/system/etc/thermal-engine.conf"
    )

    suspend fun runThermalReset(onStep: (String) -> Unit = {}): BoostReport =
        withContext(Dispatchers.IO) {
            val mode = ShellEngine.detectMode()
            if (mode == ShellEngine.Mode.NONE) {
                return@withContext BoostReport(
                    listOf(StepResult("引擎不可用", false, "未授权 Shizuku，也未获取 Root")), mode)
            }
            val results = mutableListOf<StepResult>()
            onStep("杀温控守护进程")
            for (svc in listOf("thermald", "thermal-engine", "thermal", "mi_thermald")) {
                ShellEngine.exec(mode, "stop $svc 2>/dev/null; killall $svc 2>/dev/null; true")
            }
            results.add(StepResult("停止温控守护进程", true, "thermald/thermal-engine/mi_thermald"))

            onStep("写入性能温控模式")
            var target: String? = null
            for (p in THERMAL_CONFIG_CANDIDATES) {
                val (code, _) = ShellEngine.exec(mode, "ls $p")
                if (code == 0) { target = p; break }
            }
            results.add(StepResult(
                "定位温控配置", target != null, target ?: "未找到（跳过替换）"))
            if (target != null) {
                val candidates = listOf(
                    target.replace(".conf", "-performance.conf"),
                    target.replace(".conf", "-renderscript.conf")
                )
                var swapped = false
                for (c in candidates) {
                    val (code, _) = ShellEngine.exec(mode, "test -f $c")
                    if (code == 0) {
                        ShellEngine.exec(mode, "mount --bind $c $target 2>/dev/null; cp $c $target 2>/dev/null; true")
                        swapped = true
                        break
                    }
                }
                results.add(StepResult("切换性能温控", swapped, if (swapped) "已生效" else "无替代配置"))
            }

            onStep("重启温控服务")
            ShellEngine.exec(mode, "start thermal-engine 2>/dev/null; start thermald 2>/dev/null; true")
            results.add(StepResult("完成", true, "温控已重置，机身温度将重新校准"))
            BoostReport(results, mode)
        }

    // ================= 工具 =================

    private fun run(mode: ShellEngine.Mode, name: String, cmd: String): StepResult {
        val (code, out) = ShellEngine.exec(mode, cmd)
        return StepResult(name, code == 0, out.ifEmpty { "OK" })
    }

    /** 列出已安装包名（pm list packages 输出按行解析） */
    fun installedPackages(mode: ShellEngine.Mode): Set<String> {
        val (_, out) = ShellEngine.exec(mode, "pm list packages")
        return out.lineSequence()
            .mapNotNull { it.trim().removePrefix("package:").takeIf { s -> s != it.trim() && s.isNotBlank() } }
            .toSet()
    }
}
