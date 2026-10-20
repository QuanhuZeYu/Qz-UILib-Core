package club.heiqi.uilib.ui.settings;

import java.util.*;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 工作区只组合分类、共同操作和关闭意图；不复制模型或字体编辑器状态。 */
final class SettingsWorkspace {
    private enum Section {
        APPEARANCE("外观", "设置工作区主题、字体回退顺序与文字样本。", SceneIconButton.Symbol.SLIDERS),
        MINECRAFT("Minecraft 接入", "管理原版文字与 UILib 字体服务的接入。", SceneIconButton.Symbol.MONITOR),
        FILE("配置文件", "查看当前配置来源，显式保存或重新加载。", SceneIconButton.Symbol.FILE);
        final String title, description;
        final SceneIconButton.Symbol icon;

        Section(String title, String description, SceneIconButton.Symbol icon) {
            this.title = title;
            this.description = description;
            this.icon = icon;
        }
    }

    private enum Confirmation {
        NONE,
        CLOSE,
        RELOAD
    }

    private final SettingsUi ui;
    private final Runnable leave;
    private final Signal<Section> section;
    private final Signal<Confirmation> confirmation;
    private final SceneCommand save, apply, discard;
    private final SettingsFontDialog fonts;
    private SceneNode body;
    private Runnable confirmedLeave;

    SettingsWorkspace(
            ScenePageContext context, SettingsConfiguration configuration, Runnable leave) {
        this.leave = leave;
        SettingsModel model = new SettingsModel(configuration, context.scope());
        ui = new SettingsUi(context, configuration, model);
        section = context.domain().signal(Section.APPEARANCE);
        confirmation = context.domain().signal(Confirmation.NONE);
        fonts = new SettingsFontDialog(ui);
        save = SceneCommand.of(() -> !model.dirty().get() && model.unsaved().get(), model::save);
        apply = SceneCommand.of(model.dirty(), model::apply);
        discard = SceneCommand.of(model.dirty(), model::discard);
    }

    SceneNode build() {
        SceneNode content =
                ui.stack(16, 8)
                        .add(ui.heading("设置", 28))
                        .add(
                                ui.text("管理界面外观、字体来源与当前宿主的本地配置。", 14, true)
                                        .bind(
                                                NodeBinding.value(
                                                        ui.shortWindow, (n, v) -> n.visible(!v))))
                        .add(ui.line())
                        .add(notices());
        content.add(
                panel(Section.APPEARANCE, new SettingsAppearanceSection(ui, fonts::open).build()));
        if (ui.configuration.hasMinecraft())
            content.add(panel(Section.MINECRAFT, SettingsMinecraftSection.build(ui)));
        content.add(panel(Section.FILE, SettingsFileSection.build(ui, this::reloadRequested)));
        SceneScrollArea.Area scrolling = ui.scrollArea(content);
        body = scrolling.viewport(SceneNode.Axis.COLUMN);
        SceneNode shell =
                SettingsLayout.workspace(
                                ui, toolbar(), navigation(), scrolling.node(), footer(), ledger())
                        .bind(NodeBinding.value(ui.colors, (n, p) -> n.background(p.background)));
        return ui.stack(0)
                .size(Size.fill(1), Size.fill(1))
                .control(new SettingsLayout.Layers())
                .add(shell)
                .add(fonts.build())
                .add(confirmDialog(Confirmation.CLOSE))
                .add(confirmDialog(Confirmation.RELOAD))
                .onEscape(
                        () -> {
                            closeRequested(leave);
                            return true;
                        })
                .bind(
                        NodeBinding.shortcuts(
                                SceneShortcut.Scope.PAGE, SceneShortcut.primary(SceneKey.S, save)));
    }

    private SceneNode toolbar() {
        SceneNode brand = ui.text("QZ UILib / 工作区设置", 12, true).size(Size.fill(1), Size.natural());
        return ui.row(8)
                .add(brand)
                .add(
                        ui.action(
                                () -> ui.model.theme().get().isDark() ? "浅色模式" : "深色模式",
                                SceneIconButton.Symbol.THEME,
                                () ->
                                        ui.model.setTheme(
                                                ui.model.theme().get().isDark()
                                                        ? ThemeMode.LIGHT
                                                        : ThemeMode.DARK),
                                SceneAppearance.Role.GHOST))
                .add(ui.button("返回", () -> closeRequested(leave), SceneAppearance.Role.GHOST));
    }

    private SceneNode navigation() {
        SceneNode rail =
                ui.inset(
                                ui.stack(24)
                                        .size(Size.fill(1), Size.fill(1))
                                        .clip(true)
                                        .scrollable(true),
                                20)
                        .bind(NodeBinding.value(ui.colors, (n, p) -> n.background(p.surface)));
        rail.add(ui.stack(4).add(ui.heading("QZ UILib", 20)).add(ui.text("工作区设置", 12, true)));
        SceneNode categories = ui.stack(6).add(ui.text("偏好设置", 12, true));
        for (Section target : sections()) {
            ReadableSignal<Boolean> selected = () -> section.get() == target;
            SceneNode entry =
                    ui.row(10)
                            .add(ui.icon(target.icon))
                            .add(
                                    ui.text(
                                            () -> target.title + (changed(target) ? " · 未应用" : ""),
                                            14,
                                            false));
            categories.add(
                    ui.action(
                            () -> target.title,
                            entry,
                            () -> navigate(target),
                            SceneAppearance.Role.ENTRY,
                            selected));
        }
        rail.add(categories)
                .add(ui.line())
                .add(ui.text("本地工作区", 13, false))
                .add(ui.text("本地配置 · 显式保存", 12, true));
        rail.bind(NodeBinding.value(ui.compact, (n, v) -> n.visible(!v)));
        SceneNode selector =
                SceneSelect.create(
                                ui.context,
                                () -> {
                                    List<SceneSelect.Option<Section>> options = new ArrayList<>();
                                    for (Section target : sections())
                                        options.add(
                                                new SceneSelect.Option<>(
                                                        target,
                                                        () ->
                                                                target.title
                                                                        + (changed(target)
                                                                                ? " · 未应用"
                                                                                : "")));
                                    return options;
                                },
                                section,
                                this::navigate,
                                () -> "设置分类",
                                () -> ui.colors.get().source)
                        .size(Size.fill(1), Size.natural())
                        .bind(NodeBinding.value(ui.compact, SceneNode::visible));
        return ui.stack(0)
                .bind(
                        NodeBinding.value(
                                ui.compact,
                                (n, v) -> n.size(Size.fill(1), v ? Size.natural() : Size.fill(1))))
                .add(rail)
                .add(selector);
    }

    private List<Section> sections() {
        return ui.configuration.hasMinecraft()
                ? Arrays.asList(Section.values())
                : Arrays.asList(Section.APPEARANCE, Section.FILE);
    }

    private boolean changed(Section target) {
        SettingsConfiguration.Snapshot live = ui.configuration.current();
        if (target == Section.MINECRAFT) return ui.model.replace().get() != live.replace();
        if (target == Section.APPEARANCE)
            return ui.model.theme().get() != live.theme()
                    || !ui.declarations().equals(live.candidates());
        return false;
    }

    private void navigate(Section target) {
        section.set(target);
        SceneScrollViewport.of(ui.context, body).moveToPixels(0);
    }

    private SceneNode panel(Section target, SceneNode fields) {
        return ui.motion.reveal(
                ui.stack(12, 8)
                        .add(ui.heading(target.title, 21))
                        .add(
                                ui.text(target.description, 13, true)
                                        .bind(
                                                NodeBinding.value(
                                                        ui.shortWindow, (n, v) -> n.visible(!v))))
                        .add(fields),
                () -> section.get() == target);
    }

    private SceneNode notices() {
        SceneNode external =
                ui.card(
                        ui.inset(
                                ui.stack(8)
                                        .add(ui.heading("当前生效配置已变化", 14))
                                        .add(ui.text("你的草稿已保留。可以继续编辑并应用，或放弃草稿以采用当前值。", 13, true))
                                        .add(
                                                ui.actions(
                                                        ui.button(
                                                                "放弃草稿并同步",
                                                                ui.model::discard,
                                                                SceneAppearance.Role.GHOST))),
                                14));
        SceneNode resource =
                ui.stack(6)
                        .add(ui.heading("字体资源更新失败", 14))
                        .add(ui.errorText(ui.configuration.resourceError()))
                        .add(ui.text("当前配置值保持已发布状态。修正来源后重新应用或从文件加载。", 12, true));
        return ui.stack(12)
                .add(ui.motion.reveal(external, ui.model.externalChanged()))
                .add(
                        ui.motion.reveal(
                                resource, () -> !ui.configuration.resourceError().get().isEmpty()));
    }

    private SceneNode footer() {
        SceneNode status =
                ui.stack(5)
                        .add(ui.text(this::stage, 14, false))
                        .add(ui.text(this::stageDescription, 12, true))
                        .add(operationFeedback());
        SceneNode controls =
                ui.actions(
                        ui.button("放弃编辑", discard, SceneAppearance.Role.GHOST),
                        ui.button("保存到文件", save, SceneAppearance.Role.OUTLINE),
                        ui.button("应用全部更改", apply, SceneAppearance.Role.PRIMARY));
        return ui.stack(10)
                .add(ui.line())
                .add(SettingsLayout.split(ui, status, controls))
                .clip(true)
                .scrollable(true);
    }

    private String stage() {
        if (ui.model.dirty().get()) return "有未应用的更改";
        return ui.model.unsaved().get() ? "已应用，未保存" : "已保存";
    }

    private String stageDescription() {
        if (ui.model.dirty().get()) return ui.changes() + " · 先应用更改，才能保存到文件";
        return ui.model.unsaved().get() ? "当前运行使用新值；保存后写入文件。" : "当前运行与最近读取或保存的值一致。";
    }

    private SceneNode operationFeedback() {
        Signal<String> transientMessage = ui.context.domain().signal("");
        ReadableSignal<SettingsModel.Feedback> feedback = ui.model.feedback();
        SceneNode success =
                ui.text(transientMessage, 12, true)
                        .bind(
                                NodeBinding.transientValue(
                                        () ->
                                                feedback.get() != null && feedback.get().success
                                                        ? feedback.get()
                                                        : null,
                                        3_000_000_000L,
                                        (n, value) ->
                                                transientMessage.set(
                                                        value == null ? "" : value.text)));
        // 成功提示短暂显示；失败沿原反馈保留，低高度也不隐藏错误，滚动保证可达。
        return ui.stack(5)
                .add(ui.errorText(this::operationError))
                .add(ui.motion.reveal(success, () -> !transientMessage.get().isEmpty()));
    }

    private String operationError() {
        String error = ui.model.error().get();
        if (!error.isEmpty()) return error;
        SettingsModel.Feedback feedback = ui.model.feedback().get();
        return feedback != null && !feedback.success ? feedback.text : "";
    }

    private SceneNode ledger() {
        return ui.inset(
                        ui.stack(12)
                                .add(ui.heading("本次更改", 14))
                                .add(ui.line())
                                .add(
                                        ui.text(
                                                () ->
                                                        ui.model.dirty().get()
                                                                ? ui.changes()
                                                                : "暂无未应用的更改",
                                                13,
                                                true))
                                .add(
                                        ui.motion.reveal(
                                                ui.text(this::fontDifference, 12, true),
                                                () ->
                                                        !ui.declarations()
                                                                .equals(
                                                                        ui.configuration
                                                                                .current()
                                                                                .candidates())))
                                .add(ui.text("应用后更新当前运行\n保存后写入文件", 12, true)),
                        8)
                .clip(true)
                .bind(
                        NodeBinding.value(
                                () ->
                                        ui.context.viewportWidth().get() >= ui.px(1440)
                                                && !ui.shortWindow.get(),
                                SceneNode::visible));
    }

    private String fontDifference() {
        int live = ui.configuration.current().candidates().size(),
                draft = ui.model.sources().get().size();
        return live == draft ? "字体来源：顺序或内容已调整" : "字体来源：" + live + " → " + draft + " 个";
    }

    void closeRequested(Runnable action) {
        if (fonts.closeRequested()) return;
        if (confirmation.get() != Confirmation.NONE) {
            cancelConfirmation();
            return;
        }
        if (ui.model.dirty().get() || ui.model.unsaved().get()) {
            confirmedLeave = action;
            confirmation.set(Confirmation.CLOSE);
        } else action.run();
    }

    void reloadRequested() {
        fonts.end();
        confirmedLeave = null;
        if (ui.model.dirty().get() || ui.model.unsaved().get())
            confirmation.set(Confirmation.RELOAD);
        else {
            confirmation.set(Confirmation.NONE);
            ui.model.reload();
        }
    }

    private void cancelConfirmation() {
        confirmedLeave = null;
        confirmation.set(Confirmation.NONE);
    }

    private SceneNode confirmDialog(Confirmation kind) {
        SceneNode cancel =
                ui.button("继续编辑", this::cancelConfirmation, SceneAppearance.Role.OUTLINE);
        SceneNode action =
                ui.button(
                        kind == Confirmation.CLOSE ? "不保存并离开" : "放弃更改并加载",
                        () -> {
                            Runnable destination = confirmedLeave;
                            confirmedLeave = null;
                            confirmation.set(Confirmation.NONE);
                            if (kind == Confirmation.CLOSE) {
                                ui.model.discard();
                                destination.run();
                            } else ui.model.reload();
                        },
                        SceneAppearance.Role.PRIMARY);
        SceneNode header =
                ui.inset(
                        ui.stack(0)
                                .add(
                                        ui.heading(
                                                kind == Confirmation.CLOSE
                                                        ? "离开配置工作区？"
                                                        : "从文件重新加载？",
                                                20)),
                        20);
        SceneNode explanation =
                ui.inset(
                        ui.stack(12)
                                .add(
                                        ui.text(
                                                kind == Confirmation.CLOSE
                                                        ? "未应用的草稿将被放弃；已经应用的值保留在当前运行中。离开不会保存文件。"
                                                        : "未应用的草稿及已应用但未保存的更改，将被文件中的配置替换。不会保存文件。",
                                                14,
                                                true)),
                        20);
        return SettingsDialogFrame.create(
                ui,
                kind == Confirmation.CLOSE ? "离开配置工作区" : "重新加载配置",
                540,
                310,
                header,
                ui.scroll(explanation),
                ui.inset(ui.actions(cancel, action), 16),
                () -> confirmation.get() == kind,
                cancel,
                this::cancelConfirmation);
    }
}
