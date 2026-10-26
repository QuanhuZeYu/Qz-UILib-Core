package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 交互偏好复用共同草稿、正式编辑字段与应用保存动作。 */
final class SettingsInteractionSection {
    static SceneNode build(SettingsUi ui) {
        return ui.stack(20, 12)
                .add(ui.fields.text(() -> "滚轮距离", () -> "每格滚轮移动的像素距离；数值越大，滚动越远。",
                        ui.model.scrollStepInput(), ui.model::setScrollStepInput, () -> true, ui.model.scrollStepError()))
                .add(ui.text("应用后所有页面生效，保存后重启保留；独立设置步长的控件保留自身值。", 12, true))
                .add(ui.actions(ui.button("恢复默认距离", ui.model::resetInteraction, SceneAppearance.Role.OUTLINE)));
    }

    private SettingsInteractionSection() {}
}
