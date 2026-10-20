package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 只维护文件选择特有的条目与面板定位，共同视觉来自 SceneAppearance。 */
final class FilePickerStyle extends SceneAppearance {
    final ReadableSignal<SceneTheme> controls;

    FilePickerStyle(ScenePageContext page, ReadableSignal<SceneTheme> theme) {
        super(page, theme);
        controls = page.domain().computed(scope, () -> {
            Palette p = colors.get();
            return p.theme(1, p.text, p.source.spacing(8)).withColors(
                    0, 0, p.hover, p.selected, p.accent, p.surface, p.muted, p.primary, p.onPrimary);
        });
    }

    SceneNode iconAction(SceneIconButton.Symbol symbol, String name, Runnable action) {
        return SceneIconButton.create(page, symbol, () -> name, controls, action)
                .bind(NodeBinding.value(controls, (node, value) -> {
                    int side = Math.max(value.spacing(32), Math.round(value.textStyle.logicalEm * 2));
                    node.size(Size.fixed(side), Size.fixed(side));
                }));
    }

    NodeBinding spacing(int padding, int gap) {
        return NodeBinding.value(controls, (node, value) ->
                node.padding(value.spacing(padding)).gap(value.spacing(gap)));
    }

    ReadableSignal<SceneLayouts.SplitOptions> splitOptions(float minimumEm, int gap) {
        return page.domain().computed(scope, () -> new SceneLayouts.SplitOptions(
                Math.round(controls.get().textStyle.logicalEm * minimumEm), controls.get().spacing(gap)));
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
                .size(Size.fill(3), Size.natural());
        // 次信息保持单行省略，不让窄窗每个文件都变成双行；完整信息由同一动作的提示提供。
        SceneNode content = row(0).bind(spacing(0, 8))
                .add(name)
                .add(ellipsis(() -> metadata(entry.get()), .76f, true)
                        .size(Size.fill(2), Size.natural()));
        SceneNode row = action(() -> entry.get().name(), content, activate, Role.ENTRY, selected)
                .size(Size.fill(1), Size.natural());
        // 名称、图标和元数据共用一个动作根；提示只补充可读信息，不建立第二焦点。
        return SceneTooltip.attach(page, row,
                () -> SceneTooltip.Content.text(entry.get().name() + "\n" + metadata(entry.get())),
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
