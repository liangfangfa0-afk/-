# 晕动舒缓 MotionGuard

- 版本：1.0.24
- 包名：com.ljj.yundongshuhuan
- 首页 / 设置双页面
- Liquid Glass 底部导航：背景模糊、AGSL 边缘折射、色散、按压放大镜与拖动切换
- 不支持 AGSL 的设备会自动回退到兼容底栏
- 保留运动传感器、悬浮气泡、灵敏度和颜色设置

## 构建

JDK 24、Gradle 8.14.4、AGP 8.13.0、compileSdk 37。

Liquid Glass 模块通过 Git submodule 引入：[wzhdgithub/LiquidGlassBar](https://github.com/wzhdgithub/LiquidGlassBar)。
该模块采用 GPL-3.0-or-later，第三方来源及版权声明见子模块中的 NOTICE.md 和 LICENSE。

注意：“检查更新”目前为界面入口占位，GitHub OTA 查询、APK 校验和及安装流程尚未接入。Android 普通应用安装 APK 仍需用户在系统安装器中确认。
