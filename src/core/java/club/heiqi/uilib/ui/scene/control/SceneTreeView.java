package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneEditContinuation;
import club.heiqi.uilib.ui.scene.input.SceneFocusTraversal;
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
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
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
    private final ReadableSignal<Set<K>> expanded, disabledKeys;
    private final Consumer<Expansion<K>> expansion;
    private final ReadableSignal<Options> options;
    private final ReadableSignal<SceneTheme> theme;
    private final ReadableSignal<SceneControlMetrics> metrics;
    private final Function<ReadableSignal<Entry<T, K>>, SceneNode> factory;
    private final SceneVirtualList<Entry<T, K>, K> rows;
    private final SceneNode node;
    private final Function<T, String> textValue;
    private final SceneTypeahead typeahead = new SceneTypeahead();
    private final Map<K, SceneNode> targets = new HashMap<>(), rowNodes = new HashMap<>();

    private SceneTreeView(ScenePageContext page, ReadableSignal<List<T>> source, Function<T, K> key, Function<T, K> parent,
            ReadableSignal<Set<K>> expanded, Consumer<Expansion<K>> expansion, Function<ReadableSignal<Entry<T, K>>, SceneNode> factory,
            ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme, Function<T, String> textValue, ReadableSignal<Set<K>> disabledKeys) {
        this.textValue = textValue; this.disabledKeys = disabledKeys;
        this.page = page; this.expanded = expanded; this.expansion = expansion; this.factory = factory; this.options = options; this.theme = theme;
        Owner owner = page.scope().child();
        metrics = page.domain().computed(owner, () -> theme.get().metrics(SceneControlMetrics.Kind.TREE));
        structure = page.domain().computed(owner, () -> new TreeIndex<>(source.get(), key, parent));
        visible = page.domain().computed(owner, () -> structure.get().project(expanded.get()));
        ReadableSignal<List<Object>> measure = page.domain().computed(owner,
                () -> Arrays.asList(indent(), metrics.get().gap, metrics.get().textStyle.logicalEm));
        rows = SceneVirtualList.measured(page, visible, entry -> entry.key, this::row, () -> options.get().rows, measure, metrics);
        rows.semanticRoles(null, null);
        node = new SceneNode().size(Size.fill(1), Size.fill(1)).control(new TreeControl()).add(rows.node()).own(owner);
        node.bind(NodeBinding.value(() -> page.runtime().focusRouter().focused().get(), (self, focused) -> {
            if (primaryKey(focused) == null) typeahead.reset();
        }));
    }

    private int indent() { return options.get().indent < 0 ? metrics.get().treeIndent : options.get().indent; }
    private SceneNode row(ReadableSignal<Entry<T, K>> entry) {
        K key = entry.get().key;
        SceneNode content = Objects.requireNonNull(factory.apply(entry), "树行工厂产物");
        SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).clickFocus(true)
                .onClick(() -> requestExpansion(key, !expanded.get().contains(key)));
        SceneNode primary = content.focusable() && content.enabled() && content.visible() ? content : row.focusable(true);
        targets.put(key, primary); rowNodes.put(key, row);
        row.own(() -> { if (rowNodes.remove(key, row)) targets.remove(key); });
        row.bind(NodeBinding.value(() -> !disabledKeys.get().contains(key), SceneNode::enabled));
        row.bind(NodeBinding.value(() -> {
            page.runtime().interactionRevision().get();
            return content.focusable() && content.enabled() && content.visible();
        }, (self, useContent) -> {
            // 此绑定观察交互修订；同值写入会使其它行反复失效，不能再发布相同资格。
            if (self.focusable() != !useContent) self.focusable(!useContent);
            targets.put(key, useContent ? content : self);
        }));
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
                () -> entry.get().expanded ? "折叠" : "展开", theme, toggle).focusable(false).clickFocus(false);
        marker.bind(NodeBinding.value(() -> entry.get().branch, SceneNode::visible));
        marker.bind(NodeBinding.semantics(() -> SceneSemantics.builder().excluded(true).build()));
        SceneNode slot = new SceneNode().size(Size.fixed(24), Size.natural()).add(marker);
        slot.bind(NodeBinding.value(() -> metrics.get().treeMarkerSlot,
                (self, width) -> self.size(Size.fixed(width), Size.natural())));
        row.bind(NodeBinding.value(() -> metrics.get().gap, SceneNode::defaultGap));
        return row.control(new RowControl(key, entry)).add(space).add(slot).add(content);
    }

    private void requestExpansion(K key, boolean open) {
        TreeIndex.Item<T, K> item = structure.get().items.get(key);
        if (item != null && !disabledKeys.get().contains(key) && !item.children.isEmpty() && expanded.get().contains(key) != open) expansion.accept(new Expansion<>(key, open));
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
                Objects.requireNonNull(factory, "行工厂"), Objects.requireNonNull(options, "选项"), Objects.requireNonNull(theme, "主题"), null, Collections::emptySet);
    }
    /** 名称来自逻辑数据，字符跳转不依赖屏外行已经构造；旧工厂入口仍可直接使用。 */
    public static <T, K> SceneTreeView<T, K> create(ScenePageContext page, ReadableSignal<List<T>> source,
            Function<T, K> key, Function<T, K> parent, ReadableSignal<Set<K>> expanded, Consumer<Expansion<K>> expansion,
            Function<T, String> textValue, Function<ReadableSignal<Entry<T, K>>, SceneNode> factory) {
        return create(page, source, key, parent, expanded, expansion, textValue, factory, Options::new, page.theme());
    }
    public static <T, K> SceneTreeView<T, K> create(ScenePageContext page, ReadableSignal<List<T>> source,
            Function<T, K> key, Function<T, K> parent, ReadableSignal<Set<K>> expanded, Consumer<Expansion<K>> expansion,
            Function<T, String> textValue, Function<ReadableSignal<Entry<T, K>>, SceneNode> factory,
            ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneTreeView<>(page, Objects.requireNonNull(source, "树来源"), Objects.requireNonNull(key, "树键"),
                Objects.requireNonNull(parent, "父键"), Objects.requireNonNull(expanded, "展开来源"), Objects.requireNonNull(expansion, "展开意图"),
                Objects.requireNonNull(factory, "行工厂"), Objects.requireNonNull(options, "选项"), Objects.requireNonNull(theme, "主题"),
                Objects.requireNonNull(textValue, "条目名称"), Collections::emptySet);
    }
    /** 条目资格独立于内容工厂，禁用项仍显示而不参与主项导航。 */
    public static <T, K> SceneTreeView<T, K> create(ScenePageContext page, ReadableSignal<List<T>> source,
            Function<T, K> key, Function<T, K> parent, ReadableSignal<Set<K>> expanded, Consumer<Expansion<K>> expansion,
            ReadableSignal<Set<K>> disabledKeys, Function<T, String> textValue, Function<ReadableSignal<Entry<T, K>>, SceneNode> factory) {
        return create(page, source, key, parent, expanded, expansion, disabledKeys, textValue, factory, Options::new, page.theme());
    }
    public static <T, K> SceneTreeView<T, K> create(ScenePageContext page, ReadableSignal<List<T>> source,
            Function<T, K> key, Function<T, K> parent, ReadableSignal<Set<K>> expanded, Consumer<Expansion<K>> expansion,
            ReadableSignal<Set<K>> disabledKeys, Function<T, String> textValue, Function<ReadableSignal<Entry<T, K>>, SceneNode> factory,
            ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneTreeView<>(page, Objects.requireNonNull(source, "树来源"), Objects.requireNonNull(key, "树键"),
                Objects.requireNonNull(parent, "父键"), Objects.requireNonNull(expanded, "展开来源"), Objects.requireNonNull(expansion, "展开意图"),
                Objects.requireNonNull(factory, "行工厂"), Objects.requireNonNull(options, "选项"), Objects.requireNonNull(theme, "主题"),
                Objects.requireNonNull(textValue, "条目名称"), Objects.requireNonNull(disabledKeys, "禁用键来源"));
    }
    public SceneNode node() { return node; }
    public SceneVirtualList<Entry<T, K>, K> rows() { return rows; }
    public ReadableSignal<List<Entry<T, K>>> visibleRows() { return visible; }
    public boolean reveal(K key) { return rows.reveal(key); }
    public boolean focus(K key) {
        page.domain().checkWrite();
        return !disabledKeys.get().contains(key) && rows.focus(key, ignored -> targets.get(key));
    }

    private K primaryKey(SceneNode focused) {
        if (focused == null || !page.runtime().interactive(focused)) return null;
        for (Map.Entry<K, SceneNode> target : targets.entrySet()) if (target.getValue() == focused) return target.getKey();
        return null;
    }
    private SceneNode containingRow(SceneNode focused) {
        for (SceneNode at = focused; at != null; at = at.parent()) if (rowNodes.containsValue(at)) return at;
        return null;
    }
    private List<Entry<T, K>> enabledRows() {
        Set<K> disabled = disabledKeys.get(); List<Entry<T, K>> result = new ArrayList<>();
        for (Entry<T, K> entry : visible.get()) if (!disabled.contains(entry.key)) result.add(entry);
        return result;
    }
    private boolean navigable(SceneNode focused) {
        K key = primaryKey(focused);
        return key != null && !disabledKeys.get().contains(key) && !(focused.control() instanceof SceneCompositionTarget)
                && (focused.control() == null || !focused.control().acceptsTextInput(focused));
    }
    private boolean move(SceneNode focused, SceneEditEvent event) {
        if (!navigable(focused)) return false;
        K key = primaryKey(focused); List<Entry<T, K>> current = enabledRows();
        int position = -1;
        for (int i = 0; i < current.size(); i++) if (Objects.equals(current.get(i).key, key)) { position = i; break; }
        if (position < 0) return false;
        if (event.command == null) {
            if (textValue == null || event.atomic || event.compositionEvent || event.compositionSource != null || event.text == null) return false;
            int found = typeahead.find(current, item -> Objects.requireNonNull(textValue.apply(item.value), "条目名称"), position, event.text, page.runtime().inputNanos());
            if (found >= 0) focus(current.get(found).key);
            return !event.text.trim().isEmpty() && event.text.codePoints().noneMatch(Character::isISOControl);
        }
        typeahead.reset();
        TreeIndex.Item<T, K> item = structure.get().items.get(key);
        if (item == null) return false;
        boolean open = !item.children.isEmpty() && expanded.get().contains(key);
        switch (event.command) {
            case RIGHT:
                if (!item.children.isEmpty()) {
                    if (!open) requestExpansion(key, true);
                    else for (K child : item.children) if (!disabledKeys.get().contains(child)) { focus(child); break; }
                }
                return true;
            case LEFT:
                if (open) requestExpansion(key, false);
                else {
                    K parent = item.parent;
                    while (parent != null && disabledKeys.get().contains(parent)) parent = structure.get().items.get(parent).parent;
                    if (parent != null) focus(parent);
                }
                return true;
            case UP: if (position > 0) focus(current.get(position - 1).key); return true;
            case DOWN: if (position + 1 < current.size()) focus(current.get(position + 1).key); return true;
            case HOME: focus(current.get(0).key); return true;
            case END: focus(current.get(current.size() - 1).key); return true;
            default: return false;
        }
    }
    private class FlowControl implements SceneControl {
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(node, limits, children);
        }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(node, box, children); }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) { }
    }
    private final class TreeControl extends FlowControl {
        public SceneSemantics semantics(SceneNode self) {
            return SceneSemantics.builder(SceneSemantics.Role.TREE).count(visible.get().size()).build();
        }
        public SceneFocusTraversal focusTraversal(SceneNode self) {
            return (from, backward) -> {
                SceneNode row = containingRow(from);
                // 与复杂TreeItem的Tab用法一致：进入一个条目，再遍历该条目内部，之后离树。
                if (row != null) return page.runtime().beginFocusTraversal(row, from, backward);
                List<Entry<T, K>> enabled = enabledRows();
                return enabled.isEmpty() ? new SceneFocusTraversal.Cursor() {
                    public SceneFocusTraversal.Result prepare() { return SceneFocusTraversal.Result.end(); }
                    public void close() { }
                } : rows.beginAt(enabled.get(0).key, false, false);
            };
        }
        public boolean editFallback(SceneNode self, SceneNode focused, SceneEditEvent event, SceneGeometryView geometry) {
            return move(focused, event);
        }
        public SceneEditContinuation editContinuation(SceneNode self, SceneNode originalFocus, SceneEditEvent event) {
            if (event.command == null || !navigable(originalFocus)) return null;
            switch (event.command) {
                case LEFT: case RIGHT: case UP: case DOWN: case HOME: case END: break;
                default: return null;
            }
            return new SceneEditContinuation() {
                public boolean accepts(SceneNode focused) { return navigable(focused); }
                public boolean repeat(SceneEditEvent next) { return move(page.runtime().focusRouter().focused().get(), next); }
            };
        }
    }
    private final class RowControl extends FlowControl {
        private final K key;
        private final ReadableSignal<Entry<T, K>> entry;
        RowControl(K key, ReadableSignal<Entry<T, K>> entry) { this.key = key; this.entry = entry; }
        public SceneSemantics semantics(SceneNode self) {
            Entry<T, K> current = entry.get();
            TreeIndex.Item<T, K> item = structure.get().items.get(key);
            if (item == null) return null;
            page.runtime().interactionRevision().get();
            SceneNode primary = targets.get(key);
            SceneSemantics.Builder description = SceneSemantics.builder(SceneSemantics.Role.TREE_ITEM)
                    .level(current.depth + 1).position(item.position, item.siblingCount);
            if (textValue != null) description.name(Objects.requireNonNull(textValue.apply(current.value), "条目名称"));
            if (!item.children.isEmpty()) description.expanded(expanded.get().contains(key));
            List<SceneSemantics.Action> actions = new ArrayList<>();
            if (primary != null && primary.focusable() && primary.enabled() && primary.visible()) {
                description.focusTarget(primary); actions.add(SceneSemantics.Action.FOCUS);
                if (primary.clickAction() != null) actions.add(SceneSemantics.Action.ACTIVATE);
            }
            if (!item.children.isEmpty()) actions.add(expanded.get().contains(key) ? SceneSemantics.Action.COLLAPSE : SceneSemantics.Action.EXPAND);
            return description.actions(actions.toArray(new SceneSemantics.Action[0])).build();
        }
        public boolean semanticAction(SceneNode self, SceneSemanticRequest request) {
            if (self.runtime() != page.runtime() || !page.runtime().interactive(self) || disabledKeys.get().contains(key)) return false;
            TreeIndex.Item<T, K> item = structure.get().items.get(key);
            if (item == null) return false;
            switch (request.action) {
                case EXPAND: case COLLAPSE:
                    if (item.children.isEmpty()) return false;
                    requestExpansion(key, request.action == SceneSemantics.Action.EXPAND); return true;
                case ACTIVATE:
                    SceneNode primary = targets.get(key);
                    if (primary == null || primary.runtime() != page.runtime() || !page.runtime().interactive(primary) || primary.clickAction() == null) return false;
                    // 当前语义handler已受原routing保护，直接发布同一主项动作，不能重入输入分发。
                    primary.clickAction().run(); return true;
                default: return false;
            }
        }
        public List<ReadableSignal<?>> paintDependencies(SceneNode self) { return java.util.Collections.singletonList(theme); }
        public void paint(SceneNode self, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry e = geometry.get(self);
            if (targets.get(key) != self || e == null || !page.runtime().focusRouter().focusVisible(self)) return;
            int color = theme.get().focus; LayoutBox b = e.box;
            paint.add(PaintCommand.rect(new LayoutBox(b.x, b.y, b.width, Math.min(1, b.height)), e.clip, color));
            paint.add(PaintCommand.rect(new LayoutBox(b.x, b.y + Math.max(0, b.height - 1), b.width, Math.min(1, b.height)), e.clip, color));
            paint.add(PaintCommand.rect(new LayoutBox(b.x, b.y, Math.min(1, b.width), b.height), e.clip, color));
            paint.add(PaintCommand.rect(new LayoutBox(b.x + Math.max(0, b.width - 1), b.y, Math.min(1, b.width), b.height), e.clip, color));
        }
    }
}
