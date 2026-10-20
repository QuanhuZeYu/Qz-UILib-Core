package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.Arrays;

final class DialogComponentsPage {
    private DialogComponentsPage() {}

    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, Runnable back, GalleryCompositeLabels labels) {
        ComponentExamples e =
                new ComponentExamples(p, theme, "弹窗与选择", "确认、输入与内容选择共用同一模态外框，ESC 逐层返回。", back);
        Signal<Boolean> confirm = p.domain().signal(false),
                prompt = p.domain().signal(false),
                choice = p.domain().signal(false);
        Signal<String> result = p.domain().signal("等待操作"), selected = p.domain().signal("alpha");
        SceneEditState name = new SceneEditState(p.domain(), "");
        e.section(
                "确认与输入",
                "关闭后焦点返回原触发按钮；打开输入框后可再进入选择面板。",
                SceneLayouts.wrap(8)
                        .add(
                                e.style.action(
                                        "打开确认框",
                                        null,
                                        () -> confirm.set(true),
                                        SceneAppearance.Role.PRIMARY))
                        .add(
                                e.style.action(
                                        "打开输入框",
                                        null,
                                        () -> prompt.set(true),
                                        SceneAppearance.Role.OUTLINE))
                        .add(
                                e.style.action(
                                        "打开内容选择",
                                        null,
                                        () -> choice.set(true),
                                        SceneAppearance.Role.OUTLINE)),
                e.style.label(result, 1, false));
        SceneNode cancel =
                e.style.action(
                        "取消操作", null, () -> confirm.set(false), SceneAppearance.Role.OUTLINE);
        SceneNode apply =
                e.style.action(
                        "确认操作",
                        null,
                        () -> {
                            result.set("已确认操作");
                            confirm.set(false);
                        },
                        SceneAppearance.Role.PRIMARY);
        SceneNode confirmation =
                SceneDialog.create(
                        p,
                        theme,
                        confirm,
                        () -> new SceneDialog.Options("确认操作", 520, 280),
                        new SceneDialog.Content(
                                e.style.label(() -> "确认操作", 1.3f, false).padding(16),
                                e.style.label(() -> "确认后只更新示例结果。", 1, true).padding(16),
                                SceneLayouts.wrap(8).padding(16).add(cancel).add(apply),
                                cancel,
                                () -> confirm.set(false)));
        SceneNode input =
                SceneTextField.create(
                        p,
                        name,
                        theme,
                        () -> false,
                        s -> {
                            if (s.text.trim().isEmpty()) name.reject(s, "名称不能为空。");
                            else if (name.accept(s, s.text.trim())) {
                                result.set("输入结果：" + s.text.trim());
                                prompt.set(false);
                            }
                        },
                        SceneTextField.SubmitPolicy.CONFIRM,
                        () -> "输入示例名称",
                        false);
        SceneNode close =
                e.style.action("取消输入", null, () -> prompt.set(false), SceneAppearance.Role.OUTLINE);
        SceneNode choose =
                e.style.action(
                        "选择预设名称", null, () -> choice.set(true), SceneAppearance.Role.OUTLINE);
        SceneNode promptBody =
                SceneAppearance.column(12)
                        .padding(16)
                        .add(
                                SceneField.create(
                                        p,
                                        theme,
                                        () ->
                                                new SceneField.Labels(
                                                        "名称",
                                                        "按 Enter 确认。",
                                                        name.snapshot().get().error == null
                                                                ? ""
                                                                : name.snapshot().get().error),
                                        new SceneField.Content(e.style.field(input), input, null)));
        SceneNode naming =
                SceneDialog.create(
                        p,
                        theme,
                        prompt,
                        () -> new SceneDialog.Options("输入名称", 560, 340),
                        new SceneDialog.Content(
                                e.style.label(() -> "输入名称", 1.3f, false).padding(16),
                                promptBody,
                                SceneLayouts.wrap(8).padding(16).add(close).add(choose),
                                input,
                                () -> prompt.set(false)));
        SceneNode picking =
                SceneChoiceDialog.create(
                        p,
                        theme,
                        choice,
                        () -> new SceneDialog.Options("选择示例内容", 560, 380),
                        () ->
                                Arrays.asList(
                                        new SceneSelect.Option<>("alpha", () -> "Alpha 项目"),
                                        new SceneSelect.Option<>("beta", () -> "Beta 项目"),
                                        new SceneSelect.Option<>(
                                                "disabled", () -> "暂不可用的项目", () -> false)),
                        selected,
                        key -> {
                            selected.set(key);
                            name.reset(key.equals("alpha") ? "Alpha 项目" : "Beta 项目");
                            result.set("已选择：" + name.text().get());
                            choice.set(false);
                        },
                        labels.cancelChoice,
                        () -> choice.set(false));
        return SceneLayouts.overlay().add(e.root).add(confirmation).add(naming).add(picking);
    }
}
