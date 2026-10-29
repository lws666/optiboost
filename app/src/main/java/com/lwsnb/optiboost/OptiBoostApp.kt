package com.lwsnb.optiboost

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import rikka.sui.Sui

class OptiBoostApp : Application() {

    companion object {
        private const val TAG = "OptiBoostApp"
        lateinit var instance: OptiBoostApp
            private set

        // ---------- 目标游戏 ----------
        const val DELTA_CN = "com.proxima.dsi"
        const val DELTA_GLOBAL = "com.proxima.dfm"
        /** 无畏契约：源能行动（国服） */
        const val VALORANT_CN = "com.tencent.tmgp.codev"

        /** 游戏包名 -> 显示名 */
        val GAME_TARGETS = linkedMapOf(
            DELTA_CN to "三角洲行动·国服",
            DELTA_GLOBAL to "三角洲行动·国际服",
            VALORANT_CN to "无畏契约·源能行动"
        )

        /** 绝不禁用/清理的包（系统命脉 + 游戏本体） */
        val ESSENTIAL_PACKAGES = listOf(
            "android", "com.android.systemui", "com.android.settings",
            "com.android.launcher", "com.android.launcher3",
            "com.android.phone", "com.android.mms", "com.android.dialer",
            "com.android.incallui", "com.android.server.telecom",
            "com.oplus.safecenter", "com.oplus.securitykeyboard",
            "com.coloros.securityguard", "com.oplus.battery",
            "com.oplus.athena", "com.lwsnb.optiboost"
        ) + GAME_TARGETS.keys

        // ---------- ColorOS 16 专属 ----------

        /** C16 关键性能组件：确保放行而非禁用 */
        val OPLUS_PERF_PACKAGES = listOf(
            "com.oplus.athena",        // 一键清理/内存管理
            "com.oplus.battery",       // 电池策略
            "com.oplus.powermonitor",
            "com.oplus.games",         // 游戏空间（性能调度通道）
            "com.coloros.gamesettings",
            "com.oplus.aiunit"
        )

        /**
         * C16 受系统保护、`pm disable-user` 会抛 SecurityException 的包，
         * 必须降级用 `pm suspend`（可逆，不触发安全检查）。
         */
        val C16_SUSPEND_ONLY = setOf(
            "com.heytap.quicksearchbox",
            "com.coloros.assistantscreen",
            "com.heytap.browser",
            "com.heytap.pictorial",
            "com.heytap.market",
            "com.heytap.cloud",
            "com.oplus.pay",
            "com.nearme.instant.platform"
        )

        /** C16 可安全挂起的后台耗电与广告组件 */
        val C16_NOISE = linkedMapOf(
            "com.heytap.mcs" to "系统消息推送",
            "com.opos.ads" to "OPPO 广告服务",
            "com.coloros.operationManual" to "操作手册",
            "com.coloros.video" to "视频应用",
            "com.oplus.acc.gac" to "智能云加速(付费)",
            "com.oplus.beaconlink" to "蓝牙对讲",
            "com.microsoft.appmanager" to "连接至 Windows",
            "com.coloros.scenemod" to "简易模式组件",
            "com.oplus.omoji" to "Omoji 虚拟形象",
            "com.coloros.codebook" to "密码本",
            "com.oplus.romupdate" to "系统更新",
            "com.oplus.ota" to "OTA 升级",
            "com.oplus.sau" to "系统升级助手"
        )

        /** 三角洲/无畏契约运行时的第三方干扰源 */
        val GAME_NOISE_PACKAGES = listOf(
            "com.tencent.mobileqq", "com.tencent.mm", "com.ss.android.ugc.aweme",
            "com.netease.cloudmusic", "com.smile.gifmaker", "com.sina.weibo",
            "com.xingin.xhs", "com.baidu.BaiduMap", "com.autonavi.minimap",
            "com.UCMobile", "com.android.browser", "com.taobao.taobao",
            "com.jingdong.app.mall", "com.tmall.wireless", "com.meituan.android.pt",
            "com.tencent.qqlive", "com.qiyi.video", "com.youku.phone",
            "tv.danmaku.bili", "com.kuaishou.nebula", "com.zhihu.android"
        )

        /** 是否 OPlus 系（ColorOS/realme UI 共同根特征） */
        fun isColorOS(): Boolean = try {
            instance.packageManager.getPackageInfo("com.oplus.athena", 0)
            true
        } catch (t: Throwable) {
            false
        }

        fun colorOsVersion(): String = try {
            val display = Build.DISPLAY ?: ""
            val m = Regex("ColorOS[\\s_.-]*([0-9]{2})", RegexOption.IGNORE_CASE).find(display)
            m?.groupValues?.getOrNull(1)?.let { "ColorOS $it" }
                ?: if (isColorOS()) "ColorOS" else "非 ColorOS"
        } catch (t: Throwable) {
            if (isColorOS()) "ColorOS" else "非 ColorOS"
        }
    }

    var packageCache: List<ApplicationInfo> = emptyList()
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Sui.init(packageName)
        loadPackages()
    }

    private fun loadPackages() {
        try {
            packageCache = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            Log.i(TAG, "loaded ${packageCache.size} packages")
        } catch (t: Throwable) {
            Log.w(TAG, "loadPackages failed: ${t.message}")
        }
    }

    fun refreshPackages() = loadPackages()

    fun appName(info: ApplicationInfo): String = try {
        packageManager.getApplicationLabel(info).toString()
    } catch (t: Throwable) {
        info.packageName
    }

    fun isUserApp(info: ApplicationInfo): Boolean =
        (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
            (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (t: Throwable) {
        false
    }
}
