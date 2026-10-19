# Qz-UILib-Core

构建无 Minecraft、Forge 或 LWJGL 前提的 UILib 核心：

```text
./gradlew build
```

[规格目录](<specs/README.md>)维护共同合同，实际接口与共享广场从源码和可运行场景进入。[构建入口](<build.gradle>)分别生成同一源码的 Java21 与 Java8 产物，并运行无平台及成品隔离场景。

5.0 起公开 Maven 坐标为 `asia.quanhuzeyu.uilib`，ZIP 根目录为 `asia/quanhuzeyu/uilib/`；4.x 历史 tag 与既有消费仓继续使用 `club.heiqi.uilib`。Java package 与私有重定位前缀仍是 `club.heiqi.uilib`，不随 Maven 坐标迁移。`-PuilibGroup` 可显式选择本次构建的项目坐标。

`publishWorkspace` 将指定版本写入本地 Maven 目录，供平台和 Minecraft 仓独立消费：

```text
./gradlew publishWorkspace -PuilibRepository=/path/to/local-maven -PuilibVersion=5.0.0-beta1
```

`releaseAssets` 只把本次版本发布到独立阶段目录并写成固定名 ZIP，根目录直接是 `asia/quanhuzeyu/uilib/`：

```text
./gradlew releaseAssets
# build/release/qz-uilib-core-<version>-maven.zip
```

当前公开分发入口是 GitHub Release 中的该 Maven ZIP。Maven Central 命名空间 `asia.quanhuzeyu` 已 Verified，可运行 Central workflow，仍使用同一坐标、签名、sources 与 javadoc，不改变 ZIP 与 `-P` 用法。发布 workflow 只接受已存在 tag 与 `PUBLISH <tag>`，从该 tag 完整构建后创建或更新同一 Release，静态说明来自 `.changelogs/<tag>.md`。

Central 发布使用 `publishToMavenCentral`：该任务上传 USER_MANAGED 部署并取得 deployment ID；在 Central Portal 核对状态为 `VALIDATED` 后，再按授权执行公开。同一 Central workflow 可填写 `deploymentId`，配合原 tag 与 `PUBLISH <tag>` 只公开该部署：它会核对本仓本版本的精确 Maven 坐标，不重新构建或上传；留空仍采用原上传流程。公开后以实际 Central 坐标可下载为准。workflow 只允许手动 tag + `PUBLISH <tag>`，构建 job 无发布凭据，发布 job 仅注入 `MAVEN_CENTRAL_USERNAME`、`MAVEN_CENTRAL_PASSWORD`、`SIGNING_IN_MEMORY_KEY`、`SIGNING_IN_MEMORY_KEY_ID`、`SIGNING_IN_MEMORY_KEY_PASSWORD`。没有签名或 Central 凭据时，本地 `build`、`releaseAssets` 与 `publishWorkspace` 仍可用。

自有代码采用 [MIT](<LICENSE>)；[第三方材料](<THIRD-PARTY-NOTICES.md>)保留各自许可。

Core 正式产物只持有中性能力。gallery 与 core-verification 是独立测试消费者，不进入正式发行。字体、GL、媒体设备和桌面实现在 [Platform](<../Qz-UILib-Platform/>)，游戏接入在 [Minecraft](<../Qz-UILib-Minecraft/>)。