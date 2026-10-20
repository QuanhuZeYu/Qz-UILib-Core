package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 宿主提供选项才装配；不将 Minecraft 类型或默认值带入共享配置核心。 */
final class SettingsMinecraftSection {
    static SceneNode build(SettingsUi ui) {
        SceneNode toggle =
                SceneSwitch.create(
                        ui.context,
                        ui.model.replace(),
                        () -> "接管原版文字绘制",
                        () -> ui.colors.get().source,
                        next -> ui.model.setReplace(next),
                        false);
        SceneNode state =
                ui.actions(
                        ui.text(() -> ui.model.replace().get() ? "已开启" : "已关闭", 13, true), toggle);
        return ui.stack(20)
                .add(
                        SceneField.create(
                                ui.context,
                                () -> ui.colors.get().source,
                                () ->
                                        new SceneField.Labels(
                                                "接管原版文字绘制", "聊天、原版界面等文字入口使用 UILib 字体服务。", "", true),
                                new SceneField.Content(state, toggle, null)))
                .add(ui.line())
                .add(ui.text("UILib 页面始终使用自身的字体与绘制服务。此选项只控制原版文字适配。", 13, true))
                .add(ui.text("应用后更新当前运行选项；保存后用于下次启动。", 13, true))
                .add(
                        ui.text(
                                () -> "宿主默认：" + (ui.configuration.defaultReplace() ? "开启" : "关闭"),
                                12,
                                true))
                .add(
                        ui.actions(
                                ui.button(
                                        "恢复默认",
                                        ui.model::resetMinecraft,
                                        SceneAppearance.Role.GHOST)));
    }

    private SettingsMinecraftSection() {}
}
