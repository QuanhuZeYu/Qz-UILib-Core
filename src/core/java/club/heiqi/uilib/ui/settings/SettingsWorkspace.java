package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import java.util.*;

/** 工作区只组合分类、共同操作和关闭意图；不复制模型或字体编辑器状态。 */
final class SettingsWorkspace {
    private enum Section {
        APPEARANCE("外观", "设置工作区主题、字体回退顺序与文字样本。", SceneIconButton.Symbol.SLIDERS),
        INTERACTION("交互", "设置当前应用的滚轮距离。", SceneIconButton.Symbol.SLIDERS),
        PERFORMANCE("性能", "管理当前运行的绘制性能选项。", SceneIconButton.Symbol.SLIDERS),
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
    private final Signal<Confirmation> confirmation;
    private final SceneCommand save, apply, discard;
    private final SettingsFontDialog fonts;
    private Runnable confirmedLeave;

    SettingsWorkspace(
            ScenePageContext context, SettingsConfiguration configuration, Runnable leave) {
        this.leave = leave;
        SettingsModel model = new SettingsModel(configuration, context.scope());
        ui = new SettingsUi(context, configuration, model);
        confirmation = context.domain().signal(Confirmation.NONE);
        fonts = new SettingsFontDialog(ui);
        save = SceneCommand.of(() -> !configuration.persisting().get() && !model.dirty().get() && model.unsaved().get(), model::save);
        apply = SceneCommand.of(() -> model.dirty().get() && model.scrollStepError().get().isEmpty(), model::apply);
        discard = SceneCommand.of(model.dirty(), model::discard);
    }

    SceneNode build() {
        SettingsTemplate template = new SettingsTemplate(() -> "QZ UILib / 工作区设置")
                .description(() -> "管理界面外观、字体来源、滚轮交互、绘制性能与当前宿主的本地配置。")
                .theme(() -> ui.colors.get().source)
                .labels(() -> "返回", () -> "设置分类")
                .onClose(() -> closeRequested(leave))
                .headerAction(() -> ui.model.theme().get().isDark() ? "浅色模式" : "深色模式",
                        SceneIconButton.Symbol.THEME, SceneCommand.of(() -> true,
                                () -> ui.model.setTheme(ui.model.theme().get().isDark() ? ThemeMode.LIGHT : ThemeMode.DARK)))
                .feedback(context -> status())
                .aside(context -> ledger())
                .action(() -> "放弃编辑", discard, false)
                .action(() -> "保存到文件", save, false)
                .action(() -> "应用全部更改", apply, true);
        for (Section target : sections())
            template.section(target.name(), () -> target.title, () -> target.description, target.icon,
                    () -> changed(target) ? "未应用" : "", context -> fields(target));
        return club.heiqi.uilib.ui.scene.layout.SceneLayouts.overlay()
                .add(template.create(ui.context))
                .add(fonts.build())
                .add(confirmDialog(Confirmation.CLOSE))
                .add(confirmDialog(Confirmation.RELOAD))
                // 模板嵌在工作区根内；页面 ESC 沿首个非模态根进入同一业务关闭意图。
                .onEscape(() -> { closeRequested(leave); return true; })
                .bind(NodeBinding.shortcuts(SceneShortcut.Scope.PAGE, SceneShortcut.primary(SceneKey.S, save)))
                .own(ui.scope);
    }

    private SceneNode fields(Section target) {
        switch (target) {
            case APPEARANCE: return new SettingsAppearanceSection(ui, fonts::open).build();
            case INTERACTION: return SettingsInteractionSection.build(ui);
            case PERFORMANCE: return SettingsPerformanceSection.build(ui);
            case MINECRAFT: return SettingsMinecraftSection.build(ui);
            case FILE: return SettingsFileSection.build(ui, this::reloadRequested);
            default: throw new IllegalArgumentException("未知设置分类：" + target);
        }
    }

    private List<Section> sections() {
        List<Section> available = new ArrayList<>();
        available.add(Section.APPEARANCE);
        if (ui.configuration.hasScrolling()) available.add(Section.INTERACTION);
        if (ui.configuration.hasBatching()) available.add(Section.PERFORMANCE);
        if (ui.configuration.hasMinecraft()) available.add(Section.MINECRAFT);
        available.add(Section.FILE);
        return available;
    }

    private boolean changed(Section target) {
        SettingsConfiguration.Snapshot live = ui.configuration.current();
        if (target == Section.MINECRAFT) return ui.model.replace().get() != live.replace();
        if (target == Section.INTERACTION) return ui.model.scrollStepChanged();
        if (target == Section.PERFORMANCE)
            return ui.model.aggressiveBatching().get() != live.aggressiveBatching();
        if (target == Section.APPEARANCE)
            return ui.model.theme().get() != live.theme()
                    || !ui.declarations().equals(live.candidates());
        return false;
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

    private SceneNode status() {
        return ui.stack(5)
                .add(ui.text(this::stage, 14, false))
                .add(ui.text(this::stageDescription, 12, true))
                .add(notices())
                .add(operationFeedback());
    }

    private String stage() {
        if (ui.configuration.persisting().get()) return "正在读写配置文件";
        if (ui.model.dirty().get()) return "有未应用的更改";
        return ui.model.unsaved().get() ? "已应用，未保存" : "已保存";
    }

    private String stageDescription() {
        if (ui.configuration.persisting().get()) return "文件操作在后台进行，可以继续编辑。";
        if (ui.model.dirty().get()) return ui.changes() + " · 先应用更改，才能保存到文件";
        return ui.model.unsaved().get() ? "当前运行使用新值；保存后写入文件。" : "当前运行与最近读取或保存的值一致。";
    }

    private SceneNode operationFeedback() {
        Signal<String> transientMessage = ui.context.domain().signal("");
        ReadableSignal<SettingsModel.Feedback> feedback = ui.model.feedback();
        SceneNode success =
                SceneNotice.create(
                                ui.context,
                                () -> ui.colors.get().source,
                                () ->
                                        new SceneNotice.Message(
                                                SceneNotice.Kind.SUCCESS,
                                                "",
                                                transientMessage.get()))
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
                .add(
                        SceneNotice.create(
                                ui.context,
                                () -> ui.colors.get().source,
                                () ->
                                        operationError().isEmpty()
                                                ? null
                                                : new SceneNotice.Message(
                                                        SceneNotice.Kind.ERROR,
                                                        "",
                                                        operationError())))
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
