package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

final class NoticeComponentsPage {
    private NoticeComponentsPage() {}

    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, Runnable back) {
        ComponentExamples e =
                new ComponentExamples(
                        p, theme, "状态反馈", "说明、成功、警告、错误与空态使用同一内容组合；业务显式决定持续时间和动作。", back);
        for (SceneNotice.Kind kind : SceneNotice.Kind.values()) {
            String title =
                    kind == SceneNotice.Kind.INFO
                            ? "说明"
                            : kind == SceneNotice.Kind.SUCCESS
                                    ? "成功"
                                    : kind == SceneNotice.Kind.WARNING
                                            ? "警告"
                                            : kind == SceneNotice.Kind.ERROR ? "错误" : "暂无内容";
            e.section(
                    title,
                    "可组合文字和业务动作。",
                    SceneNotice.create(
                            p,
                            theme,
                            () ->
                                    new SceneNotice.Message(
                                            kind,
                                            title,
                                            kind == SceneNotice.Kind.EMPTY
                                                    ? "这里还没有记录。"
                                                    : "这是一条" + title + "消息。")));
        }
        Signal<String> feedback = p.domain().signal(""), displayed = p.domain().signal("");
        Signal<Integer> serial = p.domain().signal(0);
        Signal<Boolean> failed = p.domain().signal(false);
        SceneNode transientNotice =
                SceneNotice.create(
                        p,
                        theme,
                        () ->
                                new SceneNotice.Message(
                                        SceneNotice.Kind.SUCCESS, "", displayed.get()));
        transientNotice
                .bind(
                        NodeBinding.transientValue(
                                feedback,
                                2_000_000_000L,
                                (node, value) -> displayed.set(value == null ? "" : value)))
                .bind(NodeBinding.value(() -> !displayed.get().isEmpty(), SceneNode::visible));
        // 短暂消息的显示来源是同一 transientValue 投影，不写业务状态或使用独立计时器。
        SceneNode live =
                SceneNotice.create(
                        p,
                        theme,
                        () ->
                                failed.get()
                                        ? new SceneNotice.Message(
                                                SceneNotice.Kind.ERROR, "操作失败", "错误持续显示，直到显式清除。")
                                        : null,
                        e.style.action(
                                "清除错误",
                                null,
                                () -> failed.set(false),
                                SceneAppearance.Role.OUTLINE));
        e.section(
                "显式反馈生命周期",
                "短暂成功与持续错误采用不同声明。",
                SceneLayouts.wrap(8)
                        .add(
                                e.style.action(
                                        "显示成功",
                                        null,
                                        () -> {
                                            serial.set(serial.get() + 1);
                                            feedback.set("已保存示例 " + serial.get());
                                        },
                                        SceneAppearance.Role.PRIMARY))
                        .add(
                                e.style.action(
                                        "显示错误",
                                        null,
                                        () -> failed.set(true),
                                        SceneAppearance.Role.OUTLINE)),
                transientNotice,
                live);
        return e.root;
    }
}
