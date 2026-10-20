package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.math.MathContent;

import java.util.Arrays;

final class DocumentComponentsPage {
    private DocumentComponentsPage() {}

    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, Runnable back) {
        ComponentExamples e =
                new ComponentExamples(p, theme, "文字与文档", "标签、富文本、块文档与数学共同消费正式排版和内容声明。", back);
        Signal<String> feedback = p.domain().signal("尚未激活链接");
        TextContent rich =
                TextContent.richTags(
                        "普通文字与 <b>粗体</b>、<i>斜体</i>，以及 <a href=\"example:details\">可操作链接</a>。");
        DocumentContent document =
                new DocumentContent(
                        Arrays.asList(
                                DocumentContent.Block.heading(TextContent.plain("同一份文档"), 2, 0, 0),
                                DocumentContent.Block.paragraph(
                                        TextContent.plain("段落随可用宽度换行，字体和主题变化沿同一排版来源更新。")),
                                DocumentContent.Block.code(
                                        TextContent.plain(
                                                "SceneDocument.create(page, content, theme,"
                                                        + " activate);"),
                                        "java",
                                        0,
                                        0)));
        e.section(
                "文本显示",
                "文本控件支持自然排版、换行与单行省略。",
                e.style.label(() -> "这是一段随窗口宽度变化的普通文字。", 1, false),
                e.style.ellipsis(() -> "单行过长内容使用正式省略策略，完整文本仍由语义和提示读取。", 1, true));
        e.section(
                "富文本与链接",
                "链接激活只发布目标，不在组件内打开外部应用。",
                SceneRichText.create(p, () -> rich, theme, feedback::set),
                e.style.label(feedback, .9f, true));
        e.section(
                "块文档",
                "标题、段落和代码通过共同文档结构表达。",
                SceneDocument.create(p, () -> document, theme, null, feedback::set));
        MathContent formula = MathContent.parse("x^2 + y^2 = r^2");
        e.section(
                "数学",
                "公式通过正式数学布局与绘制入口呈现。",
                SceneMath.create(
                        p,
                        () -> formula,
                        () ->
                                theme.get()
                                        .withTextStyle(
                                                new club.heiqi.uilib.ui.scene.text.TextStyle(
                                                        "default",
                                                        Math.round(theme.get().textStyle.logicalEm),
                                                        theme.get().textStyle.color)),
                        club.heiqi.uilib.ui.scene.text.math.MathStyle.DISPLAY,
                        java.util.Collections.emptyMap()));
        return e.root;
    }
}
