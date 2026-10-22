# Qz-UILib Core

独立于 Minecraft 和图形平台的 UI 核心，提供场景、布局、控件、响应式状态与绘制命令。

游戏用户请使用 [Minecraft 发行包](https://github.com/QuanhuZeYu/Qz-UILib/releases/tag/5.0.0-beta3)。

## 开发接入

```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("asia.quanhuzeyu.uilib:core:5.0.0-beta3")
}
```

默认产物需要 Java 21；Java 8 项目改用 `asia.quanhuzeyu.uilib:core:5.0.0-beta3:java8`。字体、GL、媒体与桌面实现见 [Platform](https://github.com/QuanhuZeYu/Qz-UILib-Platform)。

[接口约定](specs/2026-10-07-uilib-core-contract-design.md) · [下载与更新说明](https://github.com/QuanhuZeYu/Qz-UILib-Core/releases/tag/5.0.0-beta3)

[MIT](LICENSE) · [第三方许可](THIRD-PARTY-NOTICES.md)
