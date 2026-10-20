package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 只维护文件选择特有的条目与面板定位，共同视觉来自 SceneAppearance。 */
final class FilePickerStyle extends SceneAppearance {
    FilePickerStyle(ScenePageContext page, ReadableSignal<SceneTheme> theme) {
        super(page, theme);
    }

    SceneNode entry(
            ReadableSignal<FilePickerDirectory.Entry> entry,
            ReadableSignal<Boolean> selected,
            Runnable activate) {
        SceneNode name = SceneTextControl.create(
                () -> entry.get().name(),
                () -> colors.get().theme(1, colors.get().text, 0),
                SceneTextPolicy.ELLIPSIS_SINGLE_LINE, false,
                () -> entry.get().directory ? SceneIconButton.Symbol.FOLDER : SceneIconButton.Symbol.FILE)
                .size(Size.fill(1), Size.natural());
        SceneNode content = club.heiqi.uilib.ui.scene.layout.SceneLayouts.split(
                name, ellipsis(() -> metadata(entry.get()), .76f, true).padding(4),
                () -> new club.heiqi.uilib.ui.scene.layout.SceneLayouts.SplitOptions(
                        Math.round(colors.get().textStyle.logicalEm * 18), 8));
        SceneNode row = action(() -> entry.get().name(), content, activate, Role.ENTRY, selected)
                .size(Size.fill(1), Size.natural());
        // 名称、图标和元数据共用一个动作根；窄窗第二行也遵循同一点击与键盘焦点。
        return SceneTooltip.attach(page, row,
                () -> SceneTooltip.Content.text(entry.get().name()),
                () -> new SceneTooltip.Options(), () -> colors.get().source);
    }

    private static String metadata(FilePickerDirectory.Entry entry) {
        String type =
                entry.directory
                        ? "文件夹"
                        : entry.type().isEmpty()
                                ? "文件"
                                : entry.type().toUpperCase(java.util.Locale.ROOT) + " 文件";
        String size =
                entry.directory
                        ? ""
                        : entry.size < 0
                                ? " · 大小未知"
                                : " · " + size(entry.size);
        String date =
                entry.modified < 0
                        ? ""
                        : " · "
                                + java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                        .withZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.Instant.ofEpochMilli(entry.modified));
        return type + size + date;
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit + 1 < units.length);
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
