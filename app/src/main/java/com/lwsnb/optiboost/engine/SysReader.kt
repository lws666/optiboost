package com.lwsnb.optiboost.engine

import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 读取 /proc、/sys 节点的轻量工具（app 自身可读的部分），失败一律返回 null/-1 */
object SysReader {

    fun readTrimmed(path: String): String? = try {
        File(path).bufferedReader().use { it.readText().trim() }
    } catch (t: Throwable) {
        null
    }

    fun readInt(path: String): Int = readTrimmed(path)?.toIntOrNull() ?: -1

    fun readLong(path: String): Long = readTrimmed(path)?.toLongOrNull() ?: -1L

    fun exists(path: String): Boolean = try { File(path).exists() } catch (t: Throwable) { false }

    fun firstExisting(paths: List<String>): String? = paths.firstOrNull { exists(it) }

    // ---------- 内存 ----------

    fun memTotalKb(): Long {
        val line = readTrimmed("/proc/meminfo")?.lineSequence()?.firstOrNull { it.startsWith("MemTotal") }
        return line?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: -1L
    }

    fun memAvailableKb(): Long {
        val text = readTrimmed("/proc/meminfo") ?: return -1L
        for (line in text.lineSequence()) {
            if (line.startsWith("MemAvailable")) {
                return line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: -1L
            }
        }
        return -1L
    }

    // ---------- CPU ----------

    fun cpuCores(): Int = Runtime.getRuntime().availableProcessors()

    private fun totalJiffies(): LongArray? = try {
        RandomAccessFile("/proc/stat", "r").use { raf ->
            val parts = raf.readLine().split(Regex("\\s+"))
            var idle = 0L
            var total = 0L
            for (i in 1 until parts.size) {
                val v = parts[i].toLongOrNull() ?: 0L
                total += v
                if (i == 4 || i == 5) idle += v
            }
            longArrayOf(idle, total)
        }
    } catch (t: Throwable) {
        null
    }

    /** 采样间隔 ms，返回 0..100 整体 CPU 占用率 */
    fun cpuUsage(sampleMs: Long = 300): Int {
        val a = totalJiffies() ?: return -1
        try { Thread.sleep(sampleMs) } catch (t: Throwable) { return -1 }
        val b = totalJiffies() ?: return -1
        val dt = b[1] - a[1]
        return if (dt <= 0) -1 else (((dt - (b[0] - a[0])) * 100) / dt).toInt().coerceIn(0, 100)
    }

    fun cpuFreqKhz(): Int = readInt("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")

    fun cpuMaxFreqKhz(): Int {
        var max = 0
        for (i in 0 until cpuCores()) {
            val f = readInt("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (f > max) max = f
        }
        return max
    }

    // ---------- 电池 / 温度 ----------

    fun batteryTempTenthC(): Int {
        for (p in listOf(
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/bms/temp"
        )) {
            val v = readInt(p)
            if (v > 0) return v
        }
        return -1
    }

    fun batteryLevel(): Int {
        for (p in listOf(
            "/sys/class/power_supply/battery/capacity",
            "/sys/class/power_supply/bms/capacity"
        )) {
            val v = readInt(p)
            if (v in 0..100) return v
        }
        return -1
    }

    fun thermalZoneTempC(): Int {
        var max = -1
        for (i in 0..30) {
            val t = readInt("/sys/class/thermal/thermal_zone$i/temp")
            if (t > max) max = t
        }
        return if (max > 1000) max / 1000 else max
    }

    // ---------- 系统 ----------

    fun uptimeSeconds(): Long = readLong("/proc/uptime")?.let { it.toLong() } ?: -1L

    fun bootCompleted(): Boolean = uptimeSeconds() > 60

    fun currentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    fun deviceModel(): String = android.os.Build.MODEL ?: "unknown"

    fun androidVersion(): String = android.os.Build.VERSION.RELEASE ?: "unknown"
}
