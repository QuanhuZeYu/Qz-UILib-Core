package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 按宿主提供的中性选项装配 Minecraft 设置区域。 */
final class SettingsMinecraftSection {
    static SceneNode build(SettingsUi ui) {
        return ui.stack(20)
                .add(ui.fields.toggle(() -> "接管原版文字绘制", () -> "聊天、原版界面等文字入口使用 UILib 字体服务。",
                        ui.model.replace(), ui.model::setReplace, () -> true, () -> ""))
                .add(ui.line())
                .add(ui.text("UILib 页面始终使用自身的字体与绘制服务。此选项只控制原版文字适配。", 13, true))
                .add(ui.text("应用后更新当前运行选项；保存后用于下次启动。", 13, true))
                .add(ui.text(() -> "宿主默认：" + (ui.configuration.defaultReplace() ? "开启" : "关闭"), 12, true))
                .add(ui.actions(ui.button("恢复默认", ui.model::resetMinecraft, SceneAppearance.Role.GHOST)));
    }

    private SettingsMinecraftSection() {}
}
