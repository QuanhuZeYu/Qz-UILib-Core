package club.heiqi.uilib.ui.settings;

import java.util.Collections;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 外观只呈现字段；候选编辑由字体编辑器承担，样本永远读取真实生效字体。 */
final class SettingsAppearanceSection {
    private static final String SAMPLE = "每一种语言，都值得被认真呈现。\nGood typography makes a difference.";
    private final SettingsUi ui;
    private final Runnable manage;

    SettingsAppearanceSection(SettingsUi ui, Runnable manage) {
        this.ui = ui;
        this.manage = manage;
    }

    SceneNode build() {
        SceneNode choices =
                SceneGrid.create(
                                ui.context,
                                () -> new SceneGrid.Policy(ui.px(220), ui.px(16), ui.px(12)))
                        .add(
                                SettingsStyle.themeChoice(
                                        ui,
                                        "浅色外观",
                                        false,
                                        () -> ui.model.setTheme(ThemeMode.LIGHT)))
                        .add(
                                SettingsStyle.themeChoice(
                                        ui, "深色外观", true, () -> ui.model.setTheme(ThemeMode.DARK)));
        SceneNode theme =
                ui.stack(12, 8)
                        .add(ui.heading("界面主题", 15))
                        .add(
                                ui.text("设置页即时预览；应用后全局生效，保存后重启保留。", 13, true)
                                        .bind(
                                                NodeBinding.value(
                                                        ui.shortWindow, (n, v) -> n.visible(!v))))
                        .add(choices);
        SceneNode summary =
                ui.stack(6)
                        .add(
                                ui.text(
                                        () ->
                                                ui.model.sources().get().isEmpty()
                                                        ? "未配置字体候选"
                                                        : ui.sourceName(
                                                                        ui.model
                                                                                .sources()
                                                                                .get()
                                                                                .get(0)
                                                                                .declaration())
                                                                + (ui.model
                                                                                .sources()
                                                                                .get()
                                                                                .get(0)
                                                                                .declaration()
                                                                                .startsWith(
                                                                                        "builtin:")
                                                                        ? " · 内置"
                                                                        : " · 文件"),
                                        15,
                                        false))
                        .add(ui.text(ui::sourceCounts, 12, true));
        SceneNode fonts =
                ui.stack(10)
                        .add(ui.heading("字体来源", 15))
                        .add(ui.text("按顺序尝试；当前来源缺少字形时，继续使用下一个来源。", 13, true))
                        .add(
                                ui.card(
                                        ui.inset(
                                                SettingsLayout.split(
                                                        ui,
                                                        summary,
                                                        ui.actions(
                                                                ui.button(
                                                                        "管理字体来源",
                                                                        manage,
                                                                        SceneAppearance.Role
                                                                                .OUTLINE))),
                                                16)))
                        .add(
                                ui.motion.reveal(
                                        ui.text("字体来源草稿 · 应用后生效", 12, true),
                                        () ->
                                                !ui.declarations()
                                                        .equals(
                                                                ui.configuration
                                                                        .current()
                                                                        .candidates())))
                        .add(ui.errorText(ui.model.fontError()));
        return ui.stack(20, 12).add(theme).add(ui.line()).add(fonts).add(ui.line()).add(sample());
    }

    private SceneNode sample() {
        SceneEditState sample =
                new SceneEditState(ui.context.domain(), SAMPLE, SceneEditState.Mode.MULTI_LINE);
        SceneNode label = ui.heading("文字样本", 15),
                description = ui.text("使用当前生效字体；内容不会保存，字体草稿应用后才会改变这里。", 12, true);
        ReadableSignal<SceneTheme> theme =
                () ->
                        ui.colors
                                .get()
                                .source
                                .withTextStyle(
                                        new TextStyle("default", ui.px(18), ui.colors.get().text));
        SceneNode editor =
                SceneTextArea.create(
                                ui.context,
                                sample,
                                theme,
                                () -> false,
                                s -> sample.accept(s, s.text),
                                SceneTextField.SubmitPolicy.CONFIRM,
                                () -> SceneTextArea.TabMode.NAVIGATE,
                                false)
                        .bind(
                                NodeBinding.value(
                                        ui.scale,
                                        (n, s) ->
                                                n.size(
                                                        Size.fill(1),
                                                        Size.fixed(Math.round(100 * s)))))
                        .bind(
                                NodeBinding.semantics(
                                        () ->
                                                SceneSemantics.builder()
                                                        .labelledBy(label)
                                                        .describedBy(description)
                                                        .build()));
        SceneNode field = ui.field(editor),
                unavailable = ui.text("未配置生效字体来源，当前没有可用的文字样本。", 14, true);
        ui.context.own(field::destroy);
        ui.context.own(unavailable::destroy);
        SceneNode display =
                ui.stack(0)
                        .bind(
                                NodeBinding.children(
                                        () ->
                                                Collections.singletonList(
                                                        ui.configuration
                                                                        .current()
                                                                        .candidates()
                                                                        .isEmpty()
                                                                ? unavailable
                                                                : field)));
        return ui.stack(10)
                .add(
                        SettingsLayout.split(
                                ui,
                                label,
                                ui.actions(
                                        ui.button(
                                                "重置",
                                                () -> sample.reset(SAMPLE),
                                                SceneAppearance.Role.GHOST))))
                .add(description)
                .add(display);
    }
}
