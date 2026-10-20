package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.Arrays;

final class InputComponentsPage {
    private InputComponentsPage() {}

    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, Runnable back) {
        ComponentExamples e =
                new ComponentExamples(p, theme, "基础输入", "按钮、编辑、范围、选择、菜单与标签全部使用正式公共控件。", back);
        Signal<Integer> clicks = p.domain().signal(0);
        Signal<Boolean> enabled = p.domain().signal(true), checked = p.domain().signal(false);
        SceneNode buttons = SceneLayouts.wrap(8);
        for (SceneAppearance.Role role :
                new SceneAppearance.Role[] {
                    SceneAppearance.Role.PRIMARY,
                    SceneAppearance.Role.OUTLINE,
                    SceneAppearance.Role.GHOST
                })
            buttons.add(
                    e.style.action(
                            role == SceneAppearance.Role.PRIMARY
                                    ? "主要按钮"
                                    : role == SceneAppearance.Role.OUTLINE ? "描边按钮" : "轻量按钮",
                            null,
                            () -> clicks.set(clicks.get() + 1),
                            role));
        buttons.add(
                SceneIconButton.create(
                        p,
                        SceneIconButton.Symbol.STAR,
                        () -> "收藏示例",
                        theme,
                        () -> clicks.set(clicks.get() + 1)));
        buttons.add(
                e.style.action(
                        () -> "禁用按钮",
                        null,
                        SceneCommand.of(() -> false, () -> clicks.set(-1)),
                        SceneAppearance.Role.OUTLINE));
        e.section(
                "按钮与图标",
                "鼠标、Tab、Enter 与 Space 共用正式激活规则。",
                buttons,
                e.style.label(() -> "按钮触发：" + clicks.get(), 1, true));
        SceneEditState text = new SceneEditState(p.domain(), "保留这份输入");
        SceneEditState notes =
                new SceneEditState(p.domain(), "第一行\n第二行", SceneEditState.Mode.MULTI_LINE);
        e.section(
                "单行与多行编辑",
                "编辑状态独立于页面挂载，返回广场再进入仍保留输入。",
                SceneField.create(
                        p,
                        theme,
                        () -> new SceneField.Labels("示例名称", "按 Enter 接受这份输入。", ""),
                        e.style.field(
                                SceneTextField.create(
                                        p, text, theme, () -> false, s -> text.accept(s, s.text)))),
                SceneTextArea.create(p, notes, theme, () -> false, s -> notes.accept(s, s.text))
                        .size(Size.fill(1), Size.fixed(100)));
        Signal<String> choice = p.domain().signal("daily");
        Signal<Double> volume = p.domain().signal(40d);
        e.section(
                "状态与选择",
                "切换开关、勾选和单选，调整滑杆。",
                SceneSwitch.create(p, enabled, () -> "允许修改", theme, enabled::set),
                SceneCheckBox.create(
                        p,
                        () ->
                                checked.get()
                                        ? SceneCheckBox.State.CHECKED
                                        : SceneCheckBox.State.UNCHECKED,
                        () -> "已阅读说明",
                        theme,
                        checked::set),
                SceneRadioGroup.create(
                                p,
                                () ->
                                        Arrays.asList(
                                                new SceneRadioGroup.Option<>("daily", () -> "每天"),
                                                new SceneRadioGroup.Option<>("weekly", () -> "每周")),
                                choice,
                                choice::set,
                                theme)
                        .node(),
                SceneSlider.create(
                        p,
                        () -> new SceneSlider.Model(0, 100, 1, volume.get(), enabled.get()),
                        volume::set,
                        volume::set,
                        theme),
                e.style.label(() -> "当前数值：" + Math.round(volume.get()), 1, true));
        e.section(
                "下拉选择与菜单",
                "选项可用状态和命令沿同一正式导航。",
                SceneSelect.create(
                        p,
                        () ->
                                Arrays.asList(
                                        new SceneSelect.Option<>("daily", () -> "每天"),
                                        new SceneSelect.Option<>("weekly", () -> "每周")),
                        choice,
                        choice::set,
                        () -> "选择频率",
                        theme),
                SceneMenu.create(
                        p,
                        () -> "打开操作菜单",
                        () ->
                                Arrays.asList(
                                        new SceneMenu.Item<>(
                                                "reset",
                                                () -> "重置数值",
                                                SceneCommand.of(() -> true, () -> volume.set(40d))),
                                        new SceneMenu.Item<>(
                                                "disabled",
                                                () -> "不可用操作",
                                                SceneCommand.of(() -> false, () -> {}))),
                        theme));
        Signal<String> tab = p.domain().signal("overview");
        e.section(
                "标签导航",
                "方向键切换标签，面板按当前选择挂载。",
                SceneTabs.create(
                                p,
                                () ->
                                        Arrays.asList(
                                                new SceneTabs.Tab<>(
                                                        "overview",
                                                        () -> "概览",
                                                        () ->
                                                                e.style.label(
                                                                        () -> "概览内容", 1, false)),
                                                new SceneTabs.Tab<>(
                                                        "details",
                                                        () -> "详情",
                                                        () ->
                                                                e.style.label(
                                                                        () -> "详情内容", 1, false))),
                                tab,
                                tab::set,
                                theme)
                        .node());
        return e.root;
    }
}
