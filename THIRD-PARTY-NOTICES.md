# 第三方材料

Qz-UILib Core 自有代码采用 MIT，见 [LICENSE](<LICENSE>)。以下第三方材料保留各自许可，完整文本随产物位于 `assets/qz_uilib/licenses/`（仓库内为 `src/core/resources/assets/qz_uilib/licenses/`）。

| 组件 | 版本 | 许可 |
| --- | --- | --- |
| ICU4J | 76.1 | Unicode License V3 |
| SnakeYAML | 2.4 | Apache License 2.0 |
| CommonMark 与 GFM 扩展 | 0.21.0 | BSD 2-Clause |
| jsoup | 1.19.1 | MIT |
| Autolink | 0.10.0 | MIT |

正式 Core 的 Shadow 产物由 QuanhuZeYu 将 SnakeYAML 2.4 的 `org.yaml.snakeyaml` 包名及其字节码引用重定位为 `club.heiqi.uilib.shadow.org.yaml.snakeyaml`。修改文件为正式 Jar 中 `club/heiqi/uilib/shadow/org/yaml/snakeyaml/**/*.class`；未修改 YAML 算法或上游源文件。此修改通知见 `assets/qz_uilib/licenses/SnakeYAML-2.4-MODIFICATIONS.txt`，原版权与 Apache 2.0 全文保留在相邻 `SnakeYAML-2.4-LICENSE.txt`。`development` 与源码产物使用原始依赖，不执行重定位。

随核心资源分发的字体、Unicode 数据与数学字体事实保留各自许可，文本位于对应资源目录：

- Noto / Noto Emoji 字体：`fonts/noto/LICENSE-Noto*.txt`（SIL OFL 1.1）
- GNU Unifont：`fonts/unifont/LICENSE.txt`（GPL-2.0+ 字体例外与 SIL OFL 1.1）
- 默认 UI 字体：`fonts/ui/OFL.txt`（SIL OFL 1.1）
- STIX Two Math：`fonts/math/OFL.txt`（SIL OFL 1.1）
- 数学文字 Noto Serif 1.03 / Noto Sans 1.04：`fonts/math-text/{roman,sans}/LICENSE-Apache-2.0.txt`（按字体内嵌 Apache 2.0 授权分发）；上游随附 OFL 与来源信息保留，当前许可选择见 `fonts/math-text/SOURCE.md`
- IBM Plex Mono：`fonts/math-text/mono/OFL.txt`（SIL OFL 1.1）
- 广场 Google Sans：`assets/qz_uilib/gallery/fonts/google-sans/OFL.txt`（SIL OFL 1.1，随 gallery 产物）
- Unicode Emoji 数据：`unicode/emoji17/LICENSE.txt`（Unicode Terms of Use）
- KaTeX 数学事实：`math/catalog/LICENSE-KaTeX.txt`（MIT）
