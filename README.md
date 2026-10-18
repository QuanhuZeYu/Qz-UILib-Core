# Qz-UILib-Core

构建无 Minecraft、Forge 或 LWJGL 前提的 UILib 核心：

```text
./gradlew build
```

[规格目录](<specs/README.md>)维护共同合同，实际接口与共享广场从源码和可运行场景进入。[构建入口](<build.gradle>)分别生成同一源码的 Java21 与 Java8 产物，并运行无平台及成品隔离场景。

`publishWorkspace` 将指定版本写入本地 Maven 目录，供平台和 Minecraft 仓独立消费：

```text
./gradlew publishWorkspace -PuilibRepository=/path/to/local-maven -PuilibVersion=4.11.1
```

Core 正式产物只持有中性能力。gallery 与 core-verification 是独立测试消费者，不进入正式发行。字体、GL、媒体设备和桌面实现在 [Platform](<../Qz-UILib-Platform/>)，游戏接入在 [Minecraft](<../Qz-UILib-Minecraft/>)。
