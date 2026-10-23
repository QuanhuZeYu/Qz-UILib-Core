package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 共享渲染选项只编辑工作区草稿；宿主沿所属配置发布更新实际绘制。 */
final class SettingsPerformanceSection {
    static SceneNode build(SettingsUi ui) {
        SceneNode toggle =
                SceneSwitch.create(
                        ui.context,
                        ui.model.aggressiveBatching(),
                        () -> "激进合批",
                        () -> ui.colors.get().source,
                        ui.model::setAggressiveBatching,
                        false);
        SceneNode state =
                ui.actions(
                        ui.text(() -> ui.model.aggressiveBatching().get() ? "已开启" : "已关闭", 13, true),
                        toggle);
        return ui.stack(20)
                .add(
                        SceneField.create(
                                ui.context,
                                () -> ui.colors.get().source,
                                () ->
                                        new SceneField.Labels(
                                                "激进合批",
                                                "将同一批次中状态兼容的内容集中绘制，减少纹理切换。可能改变重叠文字、阴影和透明内容的覆盖顺序。默认关闭。",
                                                "",
                                                true),
                                new SceneField.Content(state, toggle, null)))
                .add(ui.line())
                .add(ui.text("应用后更新当前运行选项；保存后用于下次启动。", 13, true))
                .add(ui.actions(ui.button("恢复默认", ui.model::resetPerformance, SceneAppearance.Role.GHOST)));
    }

    private SettingsPerformanceSection() {}
}
