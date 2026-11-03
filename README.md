# 晕动舒缓 MotionGuard

一款 Android 晕动舒缓辅助应用，通过运动传感器与悬浮视觉提示，尝试帮助用户在乘车等场景中减轻视觉与运动感受之间的不适。

- 当前版本：**1.0.35**
- 应用包名：`com.ljj.yundongshuhuan`
- 最低 Android 版本：Android 8.0（API 26）
- 主要能力：运动传感器读取、动态粒子气泡悬浮层、灵敏度与颜色设置
- 界面使用 Jetpack Compose；全局液态玻璃材质采用 [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL)，包含背景采样、AGSL 折射、模糊、高光，以及液态玻璃按钮、滑块、开关和底部导航。PrismalAGSL 使用 MIT License。

## 下载

正式 APK 请前往 [GitHub Releases](https://github.com/liangfangfa0-afk/MotionGuard/releases) 下载。Actions 中的构建产物仅用于 CI 测试，不作为长期发布地址。

## 构建环境

- JDK 24
- Gradle 8.14.4
- Android Gradle Plugin 8.13.0
- compileSdk 37

本仓库将 PrismalAGSL v1.0.4 固定为 Git submodule，并由本仓库的 `:prismal` Android Library module 直接编译其 Compose 源码，避免上游 JitPack 坐标与旧版 Prismal 仓库产物冲突。上游源码保留在 `prismal-upstream`，MIT 许可文件随 submodule 一并提供。克隆后请使用：

```bash
git clone --recurse-submodules https://github.com/liangfangfa0-afk/MotionGuard.git
```

## 发布方式

每次发布时，将 `app/build.gradle.kts` 中的 `versionName` 与 `versionCode` 更新，然后创建匹配的版本标签（例如 `v1.0.35`）并推送。GitHub Actions 会在该次运行中**只构建一次 APK**，再将同一个 APK 和 SHA-256 校验文件上传到对应 Release。

## 许可

本项目按 **GNU GPL v3.0 或更高版本**发布，详见根目录 [LICENSE](LICENSE)。全局液态玻璃引擎采用 [PrismalAGSL](https://github.com/styropyr0/PrismalAGSL)，许可证为 MIT。AndroidX 与 Jetpack Compose 依赖采用 Apache-2.0。应用内“设置 → 开源许可与第三方声明”提供来源入口。

## 重要说明

本应用是实验性辅助工具，不是医疗设备，也不保证能够预防或治疗晕动症。悬浮窗显示受 Android 系统、安全界面及厂商后台管理策略限制；用户需要自行授予悬浮窗权限。使用效果因设备与场景而异。
