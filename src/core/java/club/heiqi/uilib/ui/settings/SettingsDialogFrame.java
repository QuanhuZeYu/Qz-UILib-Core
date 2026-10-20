package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneDialog;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 设置页只选择尺寸与区域；公共弹窗承接布局、呈现和输入范围。 */
final class SettingsDialogFrame {
    static SceneNode create(
            SettingsUi ui,
            String name,
            int width,
            int height,
            SceneNode header,
            SceneNode body,
            SceneNode footer,
            ReadableSignal<Boolean> visible,
            SceneNode initial,
            Runnable cancel,
            SceneNode... attached) {
        return SceneDialog.create(
                ui.context,
                () -> ui.colors.get().source,
                visible,
                () -> new SceneDialog.Options(name, ui.px(width), ui.px(height), ui.px(12)),
                new SceneDialog.Content(header, body, footer, initial, cancel, attached));
    }

    private SettingsDialogFrame() {}
}
