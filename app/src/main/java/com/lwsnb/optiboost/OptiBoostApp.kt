package com.lwsnb.optiboost

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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

        const val DELTA_CN = "com.proxima.dsi"
        const val DELTA_GLOBAL = "com.proxima.dfm"
        const val GAME_PKGS = "com.miHoYo.Yuanshen,com.netease.dwrg,com.tencent.tmgp.pubgmhd,com.tencent.tmgp.sgame"

        /** 所有需要被识别/清理的包（含三角洲专项），供查询与反白名单比对 */
        fun deltaTarget(context: Context, pkg: String): Boolean = when (pkg) {
            DELTA_CN, DELTA_GLOBAL -> true
            else -> false
        }

        /** 静默后台包名黑名单：三角洲运行时的典型干扰源（后台常驻、频繁唤醒） */
        val DELTA_NOISE_PACKAGES = listOf(
            "com.tencent.mobileqq", "com.tencent.mm", "com.ss.android.ugc.aweme",
            "com.netease.cloudmusic", "com.smile.gifmaker", "com.sina.weibo",
            "com.xingin.xhs", "com.baidu.BaiduMap", "com.autonavi.minimap",
            "com.UCMobile", "com.android.browser", "com.taobao.taobao",
            "com.jingdong.app.mall", "com.tmall.wireless", "com.meituan.android.pt"
        )

        val ESSENTIAL_PACKAGES = listOf(
            "com.android.systemui", "com.android.settings", "android",
            "com.android.incallui", "com.android.dialer", "com.android.server.telecom",
            "com.lwsnb.optiboost"
        )
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
}
