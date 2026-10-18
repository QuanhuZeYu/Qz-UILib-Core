package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** 业务层级的展开投影，有限呈现与主目标准备仍由原行集合负责。 */
public final class SceneTreeView<T, K> {
    public static final class Entry<T, K> {
        public final T value;
        public final K key, parent;
        public final int depth;
        public final boolean branch, expanded;
        Entry(T value, K key, K parent, int depth, boolean branch, boolean expanded) {
            this.value = value; this.key = key; this.parent = parent; this.depth = depth; this.branch = branch; this.expanded = expanded;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry<?, ?> item = (Entry<?, ?>)other;
            return Objects.equals(value, item.value) && Objects.equals(key, item.key) && Objects.equals(parent, item.parent)
                    && depth == item.depth && branch == item.branch && expanded == item.expanded;
        }
        @Override public int hashCode() { return Objects.hash(value, key, parent, depth, branch, expanded); }
    }
    public static final class Expansion<K> {
        public final K key; public final boolean expanded;
        private Expansion(K key, boolean expanded) { this.key = key; this.expanded = expanded; }
    }
    public static final class Options {
        private final int indent;
        private final SceneVirtualList.Options rows;
        public Options() { this(-1, new SceneVirtualList.Options()); }
        private Options(int indent, SceneVirtualList.Options rows) { this.indent = indent; this.rows = rows; }
        public Options indentPixels(int value) {
            if (value < 0) throw new IllegalArgumentException("树缩进不能为负");
            return new Options(value, rows);
        }
        public Options rows(SceneVirtualList.Options rows) { return new Options(indent, Objects.requireNonNull(rows, "行选项")); }
    }

    private final ScenePageContext page;
    private final ReadableSignal<TreeIndex<T, K>> structure;
    private final ReadableSignal<List<Entry<T, K>>> visible;
    private final ReadableSignal<Set<K>> expanded;
    private final Consumer<Expansion<K>> expansion;
    private final ReadableSignal<Options> options;
    private final ReadableSignal<SceneTheme> theme;
    private final Function<ReadableSignal<Entry<T, K>>, SceneNode> factory;
    private final SceneVirtualList<Entry<T, K>, K> rows;
    private final Map<K, SceneNode> targets = new HashMap<>();

    private SceneTreeView(ScenePageContext page, ReadableSignal<List<T>> source, Function<T, K> key, Function<T, K> parent,
            ReadableSignal<Set<K>> expanded, Consumer<Expansion<K>> expansion, Function<ReadableSignal<Entry<T, K>>, SceneNode> factory,
            ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        this.page = page; this.expanded = expanded; this.expansion = expansion; this.factory = factory; this.options = options; this.theme = theme;
        Owner owner = page.scope().child();
        structure = page.domain().computed(owner, () -> new TreeIndex<>(source.get(), key, parent));
        visible = page.domain().computed(owner, () -> structure.get().project(expanded.get()));
        ReadableSignal<List<Object>> measure = page.domain().computed(owner,
                () -> Arrays.asList(indent(), theme.get().gap, theme.get().textStyle.logicalEm));
        rows = SceneVirtualList.measured(page, visible, entry -> entry.key, this::row, () -> options.get().rows, measure);
        rows.node().own(owner);
    }

    private int indent() { return options.get().indent < 0 ? Math.max(1, Math.round(theme.get().textStyle.logicalEm * 1.25f)) : options.get().indent; }
    private SceneNode row(ReadableSignal<Entry<T, K>> entry) {
        K key = entry.get().key;
        SceneNode content = Objects.requireNonNull(factory.apply(entry), "树行工厂产物");
        SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural());
        targets.put(key, content); row.own(() -> targets.remove(key, content));
        SceneNode space = new SceneNode().size(Size.fixed(0), Size.fixed(0));
        space.bind(NodeBinding.value(() -> {
            long width = (long)entry.get().depth * indent();
            if (width > Integer.MAX_VALUE) throw new IllegalArgumentException("树缩进超出scene范围: " + key);
            return (int)width;
        }, (self, width) -> self.size(Size.fixed(width), Size.fixed(0))));
        SceneCommand toggle = SceneCommand.of(() -> {
            TreeIndex.Item<T, K> item = structure.get().items.get(key); return item != null && !item.children.isEmpty();
        }, () -> requestExpansion(key, !expanded.get().contains(key)));
        SceneNode marker = SceneIconButton.create(page, () -> entry.get().expanded ? SceneIconButton.Symbol.CHEVRON_DOWN : SceneIconButton.Symbol.CHEVRON_RIGHT,
                () -> entry.get().expanded ? "折叠" : "展开", theme, toggle::tryExecute).focusable(false).clickFocus(false);
        marker.bind(NodeBinding.value(() -> entry.get().branch, SceneNode::visible));
        SceneNode slot = new SceneNode().size(Size.fixed(24), Size.natural()).add(marker);
        slot.bind(NodeBinding.value(() -> Math.max(1, Math.round(theme.get().textStyle.logicalEm * 2)),
                (self, width) -> self.size(Size.fixed(width), Size.natural())));
        row.bind(NodeBinding.value(() -> theme.get().gap, SceneNode::gap));
        return row.control(new RowControl(content, entry)).add(space).add(slot).add(content);
    }

    private void requestExpansion(K key, boolean open) {
        TreeIndex.Item<T, K> item = structure.get().items.get(key);
        if (item != null && !item.children.isEmpty() && expanded.get().contains(key) != open) expansion.accept(new Expansion<>(key, open));
    }

    public static <T, K> SceneTreeView<T, K> create(ScenePageContext page, ReadableSignal<List<T>> source,
            Function<T, K> key, Function<T, K> parent, ReadableSignal<Set<K>> expanded, Consumer<Expansion<K>> expansion,
            Function<ReadableSignal<Entry<T, K>>, SceneNode> factory) {
        return create(page, source, key, parent, expanded, expansion, factory, Options::new, page.theme());
    }
    public static <T, K> SceneTreeView<T, K> create(ScenePageContext page, ReadableSignal<List<T>> source,
            Function<T, K> key, Function<T, K> parent, ReadableSignal<Set<K>> expanded, Consumer<Expansion<K>> expansion,
            Function<ReadableSignal<Entry<T, K>>, SceneNode> factory, ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneTreeView<>(page, Objects.requireNonNull(source, "树来源"), Objects.requireNonNull(key, "树键"),
                Objects.requireNonNull(parent, "父键"), Objects.requireNonNull(expanded, "展开来源"), Objects.requireNonNull(expansion, "展开意图"),
                Objects.requireNonNull(factory, "行工厂"), Objects.requireNonNull(options, "选项"), Objects.requireNonNull(theme, "主题"));
    }
    public SceneNode node() { return rows.node(); }
    public SceneVirtualList<Entry<T, K>, K> rows() { return rows; }
    public ReadableSignal<List<Entry<T, K>>> visibleRows() { return visible; }
    public boolean reveal(K key) { return rows.reveal(key); }
    public boolean focus(K key) { return rows.focus(key, ignored -> targets.get(key)); }

    private final class RowControl implements SceneControl {
        private final SceneNode content;
        private final ReadableSignal<Entry<T, K>> entry;
        RowControl(SceneNode content, ReadableSignal<Entry<T, K>> entry) { this.content = content; this.entry = entry; }
        public boolean editFallback(SceneNode node, SceneNode focused, SceneEditEvent event, SceneGeometryView geometry) {
            if (focused != content || content.control() instanceof SceneCompositionTarget
                    || content.control() != null && content.control().acceptsTextInput(content)) return false;
            if (event.command != SceneEditEvent.Command.LEFT && event.command != SceneEditEvent.Command.RIGHT) return false;
            TreeIndex.Item<T, K> item = structure.get().items.get(entry.get().key);
            if (item == null) return false;
            boolean open = !item.children.isEmpty() && expanded.get().contains(item.key);
            if (event.command == SceneEditEvent.Command.RIGHT) {
                if (!item.children.isEmpty()) { if (!open) requestExpansion(item.key, true); else focus(item.children.get(0)); }
            } else if (open) requestExpansion(item.key, false);
            else if (item.parent != null) focus(item.parent);
            return true;
        }
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(node, limits, children);
        }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(node, box, children); }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) { }
    }
}
