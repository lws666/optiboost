package com.lwsnb.optiboost.engine

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * 命令执行引擎：Shizuku（免 root，shell 身份）优先，不可用时回退到 Root（su）。
 * 所有特权操作（force-stop / 清后台 / 冰冻 / 温控写入）都经由这里。
 */
object ShellEngine {

    private const val TAG = "ShellEngine"

    enum class Mode { NONE, SHIZUKU, ROOT }

    @Volatile var lastMode: Mode = Mode.NONE
        private set

    // ---------- 能力检测 ----------

    fun shizukuAvailable(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun shizukuInstalled(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun rootAvailable(): Boolean = try {
        val p = Runtime.getRuntime().exec("su -c id")
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("uid=0")
    } catch (t: Throwable) {
        false
    }

    fun detectMode(): Mode = when {
        shizukuAvailable() -> Mode.SHIZUKU
        rootAvailable() -> Mode.ROOT
        else -> Mode.NONE
    }

    // ---------- 执行 ----------

    /** 执行单条命令，返回 Pair(退出码, 合并输出) */
    fun exec(mode: Mode, cmd: String): Pair<Int, String> = try {
        val p = when (mode) {
            Mode.SHIZUKU -> Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            Mode.ROOT -> Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            Mode.NONE -> return Pair(-1, "no engine")
        }
        val out = p.inputStream.bufferedReader().use { it.readText() }
        val err = p.errorStream.bufferedReader().use { it.readText() }
        p.waitFor()
        Pair(p.exitValue(), (out + "\n" + err).trim())
    } catch (t: Throwable) {
        Log.w(TAG, "exec[$mode] $cmd -> ${t.message}")
        Pair(-1, t.message ?: "error")
    }

    fun exec(cmd: String): Pair<Int, String> {
        val m = detectMode()
        lastMode = m
        return exec(m, cmd)
    }

    fun requestShizukuPermission(code: Int) {
        try {
            if (Shizuku.isPreV11()) return
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            Shizuku.requestPermission(code)
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission: ${t.message}")
        }
    }

    // ---------- 高层语义操作 ----------

    fun forceStop(mode: Mode, pkg: String) =
        exec(mode, "am force-stop $pkg")

    fun freeze(mode: Mode, pkg: String) =
        exec(mode, "pm disable-user --user 0 $pkg")

    fun unfreeze(mode: Mode, pkg: String) =
        exec(mode, "pm enable $pkg")

    fun isFrozen(mode: Mode, pkg: String): Boolean {
        val (_, out) = exec(mode, "pm list packages -d $pkg")
        return out.contains("package:$pkg")
    }

    fun trimCaches(mode: Mode): Pair<Int, String> =
        exec(mode, "sync; echo 3 > /proc/sys/vm/drop_caches")

    fun killBackground(mode: Mode): Pair<Int, String> =
        exec(mode, "am kill-all")

    /** 读取温控文件路径存在性，返回 (路径, 是否存在) */
    fun findThermalConfig(mode: Mode, paths: List<String>): String? =
        paths.firstOrNull { path ->
            val (code, _) = exec(mode, "ls $path")
            code == 0
        }
}
