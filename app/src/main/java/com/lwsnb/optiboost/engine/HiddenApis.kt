package com.lwsnb.optiboost.engine

/**
 * 隐藏 API 只读封装：getRunningAppProcesses 为 @hide 方法，反射调用。
 * 强停等写操作统一走 ShellEngine 的 `am force-stop`（跨版本稳定），不在此处反射。
 */
object HiddenApis {

    fun runningAppProcesses(): List<String> = try {
        val amClass = Class.forName("android.app.ActivityManager")
        val method = amClass.getDeclaredMethod("getRunningAppProcesses")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = method.invoke(null) as? List<Any> ?: return emptyList()
        list.mapNotNull { p ->
            try {
                val f = p.javaClass.getField("processName")
                f.get(p) as? String
            } catch (t: Throwable) { null }
        }
    } catch (t: Throwable) {
        emptyList()
    }
}
