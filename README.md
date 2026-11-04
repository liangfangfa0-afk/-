# 晕动舒缓 MotionGuard

一款 Android 晕动舒缓辅助应用，通过运动传感器与悬浮视觉提示，尝试帮助用户在乘车等场景中减轻视觉与运动感受之间的不适。

- 当前版本：**1.71**
- 应用包名：`com.ljj.yundongshuhuan`
- 最低 Android 版本：Android 8.0（API 26）
- 主要能力：运动传感器读取、动态粒子气泡悬浮层、灵敏度与颜色设置
- 界面使用 Jetpack Compose。全局页面表面、卡片、按钮、滑块和选项采用 [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL)（MIT）实现液态玻璃；底部导航栏保留 [LiquidGlassBar](https://github.com/wzhdgithub/LiquidGlassBar)（GPL-3.0-or-later）。

## 下载

正式 APK 请前往 [GitHub Releases](https://github.com/liangfangfa0-afk/MotionGuard/releases/tag/v1.71) 下载 `MotionGuard-v1.71.apk`。

SHA-256：`e0cec14c70c0bde53ebf5846cb4f84c4675cea5be4ed0a1472a5d53b2524046d`

## 构建环境

- JDK 17
- Gradle 8.13
- Android Gradle Plugin 8.13.0
- compileSdk 37

本仓库通过 Git submodule 引入 LiquidGlassBar 与 PrismalAGSL，克隆后请使用：

```bash
git clone --recurse-submodules https://github.com/liangfangfa0-afk/MotionGuard.git
```

## 许可

本项目按 **GNU GPL v3.0 或更高版本**发布，详见根目录 [LICENSE](LICENSE)。全场景液态玻璃采用 [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL)，许可证为 MIT；底部导航栏继续采用 [LiquidGlassBar](https://github.com/wzhdgithub/LiquidGlassBar)，许可证为 GPL-3.0-or-later，其 NOTICE 列明实际采用的 KernelSU（GPL-3.0-or-later）、AndroidLiquidGlass（Apache-2.0）及 Miuix（Apache-2.0）代码/组件。AndroidX 与 Jetpack Compose 依赖采用 Apache-2.0。

## 重要说明

本应用是实验性辅助工具，不是医疗设备，也不保证能够预防或治疗晕动症。悬浮窗显示受 Android 系统、安全界面及厂商后台管理策略限制；用户需要自行授予悬浮窗权限。使用效果因设备与场景而异。
