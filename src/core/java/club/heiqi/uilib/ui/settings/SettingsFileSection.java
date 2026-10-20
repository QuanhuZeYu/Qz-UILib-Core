package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;

/** 文件信息只消费宿主路径与配置事实，不解析文件或创建监视器。 */
final class SettingsFileSection {
    static SceneNode build(SettingsUi ui, Runnable reload) {
        SceneNode pathLabel = ui.heading("文件路径", 15);
        SceneEditState path =
                new SceneEditState(ui.context.domain(), ui.configuration.path().toString());
        SceneNode pathField =
                SceneTextField.create(
                                ui.context,
                                path,
                                () ->
                                        ui.colors
                                                .get()
                                                .source
                                                .withTextStyle(
                                                        SettingsTypography.pathStyle(
                                                                ui.px(13), ui.colors.get().text)),
                                () -> true,
                                s -> {},
                                SceneTextField.SubmitPolicy.CONFIRM,
                                () -> "",
                                false)
                        .bind(
                                NodeBinding.semantics(
                                        () ->
                                                SceneSemantics.builder()
                                                        .labelledBy(pathLabel)
                                                        .build()));
        SceneNode file =
                ui.stack(12)
                        .add(pathLabel)
                        .add(ui.field(pathField))
                        .add(ui.text("文件格式：UTF-8 YAML", 12, true))
                        .add(
                                ui.text(
                                        () ->
                                                ui.model.unsaved().get()
                                                        ? "当前生效配置尚未保存"
                                                        : "当前生效配置与最近读取或保存的值一致",
                                        13,
                                        false))
                        .add(ui.text("保存沿配置核心保留用户注释。", 12, true))
                        .add(ui.actions(ui.button("从文件重新加载", reload, SceneAppearance.Role.OUTLINE)))
                        .add(ui.text("使用文件中的值替换当前运行；有未应用或未保存的更改时会先确认。", 12, true))
                        .add(ui.errorText(ui.model.fileError()));
        SceneNode current =
                ui.stack(8)
                        .add(
                                ui.text(
                                        () ->
                                                "全局主题："
                                                        + (ui.configuration
                                                                        .current()
                                                                        .theme()
                                                                        .isDark()
                                                                ? "深色"
                                                                : "浅色"),
                                        13,
                                        false));
        if (ui.configuration.hasMinecraft())
            current.add(
                    ui.text(
                            () ->
                                    "接管原版文字绘制："
                                            + (ui.configuration.current().replace() ? "开启" : "关闭"),
                            13,
                            true));
        current.add(
                ui.text(
                        () ->
                                ui.configuration.current().candidates().isEmpty()
                                        ? "未配置字体候选（空列表）"
                                        : "字体候选",
                        13,
                        true));
        SceneNode fonts =
                ui.stack(10)
                        .add(
                                ui.path(
                                        () ->
                                                String.join(
                                                        "\n",
                                                        ui.configuration.current().candidates()),
                                        12));
        current.add(
                ui.scroll(fonts)
                        .bind(
                                NodeBinding.value(
                                        ui.scale,
                                        (n, s) ->
                                                n.size(
                                                        Size.fill(1),
                                                        Size.fixed(Math.round(200 * s))))));
        return ui.stack(24)
                .add(file)
                .add(ui.line())
                .add(ui.heading("当前生效值", 16))
                .add(ui.text("只读表示；未应用草稿不显示在这里。", 12, true))
                .add(current);
    }

    private SettingsFileSection() {}
}
