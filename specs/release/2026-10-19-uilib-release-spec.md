# UILib 独立仓发布规格

状态：5.0.0-beta3 发布准备；自有代码采用 MIT，随附第三方适用各自许可。

## 仓库与产物

Core、Platform 与 Minecraft 保持独立 Git、构建与发布生命周期。工作站只负责编排，不是公开构建的前提。旧公开仓 QuanhuZeYu/Qz-UILib 继续维护 Minecraft Mod 与旧 Release；Core 与 Platform 分别使用 QuanhuZeYu/Qz-UILib-Core、QuanhuZeYu/Qz-UILib-Platform。仓库创建、公开与默认分支切换由用户在发布准备就绪后决定。

Core 交付 Java21、Java8 正式 Jar、开发与源码产物；gallery 和 core-verification 是明确独立的测试消费者。Platform 交付现有双 Java 目标适配、desktop 发行与独立验证消费者。Minecraft 覆盖现有四个宿主，正式 Mod 不携带 gallery/manual 测试代码，媒体可选包和人工测试附加包保持独立。正式 Jar 的许可证归属和版本元数据必须与发布版本一致。

## 版本与发布操作

Core、Platform 与 Minecraft 本次统一为 5.0.0-beta3；独立 Forge 1.7.10 兼容 Mod Qz-UILib-Compat 首版为 1.0.0-beta1，明确消费同一 5.0.0-beta3 的 UILib 组合。用户可验证域名为 quanhuzeyu.asia，Central 命名空间为 asia.quanhuzeyu，5.0 的公开 Maven group 为 asia.quanhuzeyu.uilib。默认 group 按有效版本选择：4.x 保留 club.heiqi.uilib，5.x 起使用新 group；显式 -PuilibGroup 优先。原有 -PuilibVersion 与 -PuilibRepository 调用无需为旧版本补充 group 参数；Java 包名及 4.x 历史不修改。Maven 版本、Mod 元数据、产物与 tag 使用同一版本；允许现有稳定 x.y.z、alpha/beta/rc 的连续编号写法（beta1、beta.1、beta-1），不把 tag 前缀引入 Maven 版本。所有预发布标记 GitHub prerelease 且不设为 latest；稳定版才可设为 latest。

普通分支与 PR 工作流只构建和上传 Actions artifact。实际发布使用 workflow_dispatch，接收既存 tag 与确认短语 PUBLISH <tag>，从该 tag 检出并完整构建，再创建或更新同一 Release。tag 不创建、不移动、不重写；发布并发按 tag 串行。PR 不取得发布权限或跨仓私有凭据。

## 分发与依赖

GitHub Release assets 提供下载及离线产物；用户已授权接通 Maven Central，由工作站 agent 管理命名空间、构建配置与发布状态。Central 的账号、域名验证及签名准备就绪后作为公开 Maven 渠道，尚未实际发布的坐标不得宣称可解析。Core 与 Platform 使用已有 publishWorkspace 生成标准 Maven 布局的版本 ZIP，固定资产名分别为 qz-uilib-core-<version>-maven.zip、qz-uilib-platform-<version>-maven.zip；ZIP 根目录直接是 asia/quanhuzeyu/uilib/...，不额外嵌套仓名。Core 普通 CI 另提供 core-jars artifact，包含 qz-uilib-core-<version>.jar（Java21）与 qz-uilib-core-<version>-java8.jar；Release 也将这两个正式 Jar 作为独立附件提供，原 core-maven-zip artifact 与 Maven ZIP 保留。保留现有 -PuilibVersion 和 -PuilibRepository 用法、含义及消费既有 Maven URL 的能力；publishWorkspace 仍只写本地目录。

Platform 的独立 CI 下载同版本 Core 的 Maven ZIP，Minecraft 下载同版本 Core 与 Platform ZIP，解到同一本地 Maven 目录后按原接口构建。下载使用明确仓名、tag 和唯一资产名；上游缺失即报告所需发布顺序，不读取工作站、邻仓源码/classes/build 或私有 submodule。发布顺序为 Core → Platform → Minecraft。构建产物选取来自明确任务输出，不从可能含旧版本的 build/libs 无差别发布。

Central 凭据与签名密钥通过受支持的安全存储和发布 job 使用，不进入公开仓或日志。独立测试消费者可继续由 Maven ZIP 供 CI 使用；Central 中的生产 publication 须具备真实 sources/javadoc、完整 POM、签名及有效依赖。发布为不可变版本，先验证部署再由明确发行操作公开。

## Release 正文与重复运行

静态正文沿用各仓 .changelogs/<tag>.md，说明交付变化、升级动作及影响使用的验证范围。Gemini4 可以作为正文作者；流水线消费已审查的文本，不依赖 LLM 密钥或运行时生成文案。正文保持简洁，不能加入内部阶段、机器路径、协作过程或无依据的兼容承诺。

工作流生成独立动态证据区：准确 tag/commit、Actions run 链接、构建与资产范围。重跑时更新同一 Release、替换同名本次资产并保留其他资产，整体正文按静态说明与单份动态证据重建，不累加重复段落。不能先宣布成功再上传资产；最终成功状态只能在构建、上传和 Release 属性更新完成后产生。权限默认 contents:read，仅发布 job 使用 contents:write；不向日志输出凭据。

## 验收

沿用测试规格：默认 check/build 运行无窗口闭环，窗口、GPU、OS 与游戏保持显式入口，不通过 -x 或静默跳过真实检查使发布变绿。Core 双 Java 目标、Platform 真实字体/成品隔离、Minecraft 正式包与人工包隔离均实际执行。设备或游戏证据按真实执行范围说明，不将无窗口通过外推。

本地验证包括完整受影响 build、标准 Maven ZIP 经独立消费解析、四个 Mod 版本元数据与许可、工作流与脚本语法、发布脚本对 tag/版本/prerelease/重跑路径的必要检查。GitHub 托管 runner 与远端 Release 的实际执行依赖仓库及 tag 就绪，应在首次远端运行验收，不伪报已执行。
