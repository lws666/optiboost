package com.lwsnb.optiboost.engine

import android.content.pm.PackageManager
import android.util.Log
import dev.rikka.shizuku.IShizukuService
import rikka.sui.Sui
import java.io.InputStream
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

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

    // ---------- Shizuku 执行（IShizukuService AIDL + 反射读取隐藏类 Process） ----------

    private fun shizukuExec(cmd: String): Pair<Int, String> = try {
        val binder = Shizuku.getBinder()
            ?: return Pair(-1, "shizuku binder not ready")
        val service = IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
            ?: return Pair(-1, "shizuku service null")
        val p: Any = service.newProcess(arrayOf("sh", "-c", cmd), null, null)
            ?: return Pair(-1, "null process")
        val out = (p.javaClass.getMethod("getInputStream").invoke(p) as InputStream)
            .bufferedReader().use { it.readText() }
        val err = (p.javaClass.getMethod("getErrorStream").invoke(p) as InputStream)
            .bufferedReader().use { it.readText() }
        p.javaClass.getMethod("waitFor").invoke(p)
        val code = p.javaClass.getMethod("exitValue").invoke(p) as Int
        Pair(code, (out + "\n" + err).trim())
    } catch (t: Throwable) {
        Log.w(TAG, "shizukuExec: ${t.message}")
        Pair(-1, t.message ?: "shizuku error")
    }

    private fun rootExec(cmd: String): Pair<Int, String> = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().use { it.readText() }
        val err = p.errorStream.bufferedReader().use { it.readText() }
        p.waitFor()
        Pair(p.exitValue(), (out + "\n" + err).trim())
    } catch (t: Throwable) {
        Log.w(TAG, "rootExec: ${t.message}")
        Pair(-1, t.message ?: "root error")
    }

    /** 执行单条命令，返回 Pair(退出码, 合并输出) */
    fun exec(mode: Mode, cmd: String): Pair<Int, String> = when (mode) {
        Mode.SHIZUKU -> shizukuExec(cmd)
        Mode.ROOT -> rootExec(cmd)
        Mode.NONE -> Pair(-1, "no engine")
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
}
