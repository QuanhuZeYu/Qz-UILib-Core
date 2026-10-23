package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 工作区 UI 与路径使用服务本代已准备的独立具名链；样本仍消费当前配置候选。 */
final class SettingsTypography {
    private SettingsTypography() {}

    static TextStyle pathStyle(int em, int color) {
        return new TextStyle("settings-path", em, color);
    }

}
