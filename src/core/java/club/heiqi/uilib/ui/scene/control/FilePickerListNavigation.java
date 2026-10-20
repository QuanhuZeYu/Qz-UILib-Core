package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.nio.file.Path;
import java.util.List;

/** 按当前页的逻辑顺序导航；屏外准备与显露归共享集合，字段和组字仍由原编辑目标处理。 */
final class FilePickerListNavigation extends SceneAppearance.Flow {
    private final FilePickerSelection selection;
    private final SceneVirtualList<FilePickerDirectory.Entry, Path> listing;
    private final SceneTypeahead typeahead = new SceneTypeahead(800_000_000L, 128);
    FilePickerListNavigation(FilePickerSelection selection, SceneVirtualList<FilePickerDirectory.Entry, Path> listing) {
        this.selection = selection; this.listing = listing;
    }
    @Override public boolean editActivator(SceneNode node, SceneNode focused, SceneEditEvent event) {
        boolean inListing = false;
        for (SceneNode at = focused; at != null; at = at.parent()) if (at == listing.node()) { inListing = true; break; }
        if (!inListing || event.compositionEvent || event.compositionSource != null || focused.control() == null
                || focused.control().acceptsTextInput(focused)) return false;
        if (event.command == null) return event.text != null && !event.text.isEmpty();
        switch (event.command) {
            case UP: case DOWN: case HOME: case END: case PAGE_UP: case PAGE_DOWN: return true;
            default: return false;
        }
    }
    @Override public boolean edit(SceneNode node, SceneEditEvent event, SceneGeometryView geometry) {
        if (selection.busy.get()) return true;
        List<FilePickerDirectory.Entry> entries = selection.entries.get();
        if (entries.isEmpty()) return true;
        SceneNode focused = node.runtime().focusRouter().focused().get();
        int current = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).name().equals(focused.text())) {
            current = i; break;
        }
        int pageSize = 1;
        SceneNode row = focused;
        while (row.parent() != null && row.parent() != listing.node()) row = row.parent();
        SceneGeometryView.Entry viewport = geometry.get(listing.node()), item = geometry.get(row);
        if (viewport != null && item != null)
            pageSize = Math.max(1, viewport.box.height / Math.max(1, item.box.height + listing.node().gap()));
        Integer next = next(node, entries, current, event, pageSize);
        if (next == null) return false;
        if (next >= 0) listing.focus(entries.get(next).path);
        return true;
    }
    private Integer next(SceneNode node, List<FilePickerDirectory.Entry> entries, int current, SceneEditEvent event, int pageSize) {
        if (event.command == null) {
            int found = typeahead.find(entries, FilePickerDirectory.Entry::name, current, event.text, node.runtime().inputNanos());
            return found < 0 ? current : found;
        }
        switch (event.command) {
            case UP: return Math.max(0, current - 1);
            case DOWN: return Math.min(entries.size() - 1, current + 1);
            case HOME: return 0;
            case END: return entries.size() - 1;
            case PAGE_UP: return Math.max(0, current - pageSize);
            case PAGE_DOWN: return Math.min(entries.size() - 1, current + pageSize);
            default: return null;
        }
    }
}
