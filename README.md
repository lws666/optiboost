# 极启优化 OptiBoost

免 Root 极致优化 Android 应用。**Kotlin + Jetpack Compose** 原生构建，特权操作走
[Shizuku](https://shizuku.rikka.app/)（shell 身份，免 Root）自动回退 Root（su）双引擎。

## 功能

- **仪表盘**：内存 / CPU 频率与占用 / 电池温度 / 机身 thermal 温度实时监控
- **一键优化**：清后台（am kill-all）→ 释放页缓存（drop_caches）→ CPU 调度策略重置
  （schedutil）→ 内存碎片收拢（compact_memory）
- **三角洲行动专项**：自动识别国服 `com.proxima.dsi` / 国际服 `com.proxima.dfm`，
  反白名单强停后台噪声应用（IM / 短视频 / 地图 / 购物等 15 类）、释放缓存、收拢内存、
  日志限流；激进模式附加动画降档 0.5x
- **温控管理**：读取电池/机身实时温度，停止温控守护进程，探测并切换性能温控配置
- **应用管理**：搜索用户应用，一键冻结 / 解冻（`pm disable-user`，可逆）

## 目录

```
app/src/main/java/com/lwsnb/optiboost/
├── OptiBoostApp.kt        # Application：Sui 初始化、包缓存、全局协程域
├── MainActivity.kt        # Compose UI（首页/三角洲/温控/应用/设置）
├── engine/
│   ├── ShellEngine.kt     # Shizuku/Root 双引擎执行器
│   ├── SysReader.kt       # /proc /sys 只读监控
│   └── HiddenApis.kt      # 隐藏 API 只读反射
├── core/
│   └── Optimizer.kt       # 优化编排（一键/三角洲/温控）
└── ui/
    └── UiState.kt         # 全局 UI 状态
```

## 免 Root 原理

设备安装 [Shizuku](https://shizuku.rikka.app/download/) 并激活（Android 11+ 支持本机
无线调试自启，无需电脑）后，本应用通过 `dev.rikka.shizuku:api` 以 **shell (adb) 身份**
执行 `am` / `pm` / `settings` / `setprop` 等特权命令，效果等同 adb 优化，全程无需 Root。
未装 Shizuku 的设备自动回退 `su`（Root），两者都不可用时仅展示监控数据。

## 构建

不在本地构建（磁盘受限），推送 GitHub Actions 自动出包：

- 推送 `main` 分支 → 构建并上传 APK Artifacts
- 打 `v*` tag → 自动发布 GitHub Releases

```bash
git push origin main          # CI 构建产物在 Actions Artifacts
git tag v1.0.0 && git push origin v1.0.0   # 触发 Release
```

本地如需验证仅跑静态检查（不打包）：

```bash
./gradlew :app:lint
```
