# 极启 OptiBoost · C16 专项

免 Root 极致优化 Android 应用。**开 / 关两个按钮**，一键切换。

针对 **ColorOS 16** 深度适配，主攻 **三角洲行动**（`com.proxima.dsi` / `com.proxima.dfm`）
与 **无畏契约：源能行动**（`com.tencent.tmgp.codev`）。

## 两个按钮

| 按钮 | 作用 |
|---|---|
| **开启** | 释放缓存 → 清第三方后台 → 挂起 C16 冗余组件 → 内存收缩 → CPU 全核解锁 → 放行 athena/games/battery 性能通道 → 温控放行 → 动画降档 → 日志限流 |
| **关闭** | 完整回滚：unsuspend 全部挂起项 → 解除所有冻结 → 动画恢复 1.0x → 重启温控 → 恢复日志级别 |

## C16 关键适配

ColorOS 16 与旧版最大差异在权限模型，这里逐条应对：

1. **受保护包必须用 suspend**
   `com.heytap.quicksearchbox` / `com.heytap.browser` / `com.coloros.assistantscreen` 等
   执行 `pm disable-user` 会抛 `SecurityException: Access denied`。
   本应用统一走 `pm suspend`，效果等同冻结且**完全可逆**。

2. **性能组件绝不冻结**
   `com.oplus.athena`（内存管理/一键清理）、`com.oplus.battery`、`com.oplus.games`
   （游戏空间调度通道）一旦被禁会直接丢性能。本应用不但不禁，还主动 `enable` + `unsuspend` 放行。

3. **温控路径按 C16 机型探测**
   覆盖 `/vendor/etc/thermal-engine*.conf` 多命名变体，停止 `oplus_thermal` 等守护进程。

## 免 Root 原理

设备装 [Shizuku](https://shizuku.rikka.app/download/) 并激活后（Android 11+ 可用无线调试
本机自启，无需电脑），以 **shell (adb) 身份**执行 `am` / `pm` / `settings` / `setprop`。
未装 Shizuku 时自动回退 `su`（Root）。

## 构建

不在本地构建，推送 GitHub Actions 自动出包：

```bash
git push origin main                        # Artifacts 取 APK
git tag v2.0.0 && git push origin v2.0.0    # 发布 Release
```

## 源码结构

```
app/src/main/java/com/lwsnb/optiboost/
├── OptiBoostApp.kt      # 游戏目标、C16 包清单、设备识别
├── MainActivity.kt      # 极简单页 UI（开/关两按钮）
├── engine/
│   ├── ShellEngine.kt   # Shizuku/Root 执行器 + suspend/freeze 语义
│   ├── SysReader.kt     # /proc /sys 监控
│   └── HiddenApis.kt    # 隐藏 API 只读反射
├── core/
│   └── Optimizer.kt     # runEnable / runRestore 双通道
└── ui/
    └── UiState.kt       # 开关状态机
```
