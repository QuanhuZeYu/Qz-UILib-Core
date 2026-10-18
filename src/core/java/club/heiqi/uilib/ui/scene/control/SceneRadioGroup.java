package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditContinuation;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
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
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** 单一选择来源和单Tab入口；方向只改变原路由焦点并发布业务选择意图。 */
public final class SceneRadioGroup<K> {
    public static final class Option<K> {
        public final K key;
        public final ReadableSignal<String> name;
        public final ReadableSignal<Boolean> enabled;
        public Option(K key, ReadableSignal<String> name, ReadableSignal<Boolean> enabled) {
            this.key = Objects.requireNonNull(key, "选项键"); this.name = Objects.requireNonNull(name, "名称");
            this.enabled = Objects.requireNonNull(enabled, "可用来源");
        }
        public Option(K key, ReadableSignal<String> name) { this(key, name, () -> true); }
    }
    private final ScenePageContext page;
    private final ReadableSignal<List<Option<K>>> options;
    private final ReadableSignal<K> selected;
    private final Consumer<K> change;
    private final Map<K, SceneNode> nodes = new HashMap<>();
    private final SceneNode node;

    private SceneRadioGroup(ScenePageContext page, ReadableSignal<List<Option<K>>> options, ReadableSignal<K> selected,
            Consumer<K> change, ReadableSignal<SceneTheme> theme) {
        this.page = page; this.options = options; this.selected = selected; this.change = change;
        node = new SceneNode().size(Size.fill(1), Size.natural()).control(new GroupControl());
        node.bind(NodeBinding.value(() -> theme.get().gap, SceneNode::gap));
        node.bind(NodeBinding.repeat(options, option -> option.key, current -> {
            K key = current.get().key;
            ReadableSignal<Boolean> checked = () -> Objects.equals(selected.get(), current.get().key);
            SceneNode item = SceneSymbolControl.node(page, () -> current.get().name.get(), new Mark(theme, checked), () -> select(key));
            item.size(Size.fill(1), Size.natural()).bind(NodeBinding.value(() -> current.get().enabled.get(), SceneNode::enabled));
            nodes.put(key, item); item.own(() -> nodes.remove(key, item)); return item;
        }));
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneRadioGroup<>(page, Objects.requireNonNull(options, "选项来源"), Objects.requireNonNull(selected, "选中来源"),
                Objects.requireNonNull(change, "选择意图"), Objects.requireNonNull(theme, "主题"));
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change) { return create(page, options, selected, change, page.theme()); }
    public SceneNode node() { return node; }

    private List<SceneNode> available() {
        List<SceneNode> result = new ArrayList<>();
        for (Option<K> option : Objects.requireNonNull(options.get(), "选项来源")) {
            SceneNode item = nodes.get(option.key);
            if (option.enabled.get() && item != null && item.runtime() == page.runtime() && item.focusable() && page.runtime().interactive(item)) result.add(item);
        }
        return result;
    }
    private K keyOf(SceneNode target) {
        if (target == null) return null;
        for (Option<K> option : options.get()) if (nodes.get(option.key) == target && option.enabled.get()) return option.key;
        return null;
    }
    private boolean accepts(SceneNode focused) { return keyOf(focused) != null && available().contains(focused); }
    private void select(K key) {
        SceneNode item = nodes.get(key);
        if (accepts(item) && !Objects.equals(selected.get(), key)) change.accept(key);
    }
    private static boolean moves(SceneEditEvent event) {
        return event.command == SceneEditEvent.Command.LEFT || event.command == SceneEditEvent.Command.RIGHT
                || event.command == SceneEditEvent.Command.UP || event.command == SceneEditEvent.Command.DOWN
                || event.command == SceneEditEvent.Command.HOME || event.command == SceneEditEvent.Command.END;
    }
    private boolean move(SceneEditEvent event) {
        List<SceneNode> choices = available(); SceneNode focused = page.runtime().focusRouter().focused().get();
        int index = choices.indexOf(focused); if (index < 0 || !moves(event)) return false;
        int target = event.command == SceneEditEvent.Command.HOME ? 0 : event.command == SceneEditEvent.Command.END ? choices.size() - 1
                : Math.floorMod(index + (event.command == SceneEditEvent.Command.LEFT || event.command == SceneEditEvent.Command.UP ? -1 : 1), choices.size());
        SceneNode next = choices.get(target); SceneFocusRouter.RangeHandle range = page.runtime().focusRouter().captureRange();
        if (page.runtime().focusRouter().navigateTo(page.runtime().captureMount(next)) && range.current()
                && page.runtime().focusRouter().focused().get() == next) select(keyOf(next));
        return true;
    }
    private final class GroupControl implements SceneControl {
        public SceneFocusTraversal focusTraversal(SceneNode root) {
            return (from, backward) -> new SceneFocusTraversal.Cursor() {
                private boolean closed;
                public SceneFocusTraversal.Result prepare() {
                    if (closed || from != null && keyOf(from) != null) return SceneFocusTraversal.Result.end();
                    List<SceneNode> choices = available(); if (choices.isEmpty()) return SceneFocusTraversal.Result.end();
                    SceneNode target = page.runtime().focusRouter().focused().get();
                    if (!choices.contains(target)) target = nodes.get(selected.get());
                    if (!choices.contains(target)) target = choices.get(0);
                    return SceneFocusTraversal.Result.ready(target);
                }
                public void close() { closed = true; }
            };
        }
        public boolean editFallback(SceneNode root, SceneNode focused, SceneEditEvent event, SceneGeometryView geometry) {
            return accepts(focused) && page.runtime().focusRouter().focused().get() == focused && move(event);
        }
        public SceneEditContinuation editContinuation(SceneNode root, SceneNode focused, SceneEditEvent event) {
            if (!moves(event) || !accepts(focused)) return null;
            return new SceneEditContinuation() {
                public boolean accepts(SceneNode actual) { return SceneRadioGroup.this.accepts(actual); }
                public boolean repeat(SceneEditEvent next) { return move(next); }
            };
        }
        public MeasuredSize measure(SceneNode root, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public MeasuredSize measure(SceneNode root, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(root, limits, children);
        }
        public ChildPlacements arrange(SceneNode root, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(root, box, children); }
        public void paint(SceneNode root, SceneGeometryView geometry, PaintPlanBuilder paint) { }
    }
    private static final class Mark extends SceneSymbolControl {
        private final ReadableSignal<Boolean> checked;
        Mark(ReadableSignal<SceneTheme> theme, ReadableSignal<Boolean> checked) { super(theme, true); this.checked = checked; }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(theme, checked); }
        @Override void symbol(LayoutBox box, PaintPlanBuilder paint, LayoutBox clip, int color) {
            ScenePaintShapes.rounded(paint, box, clip, color, box.width / 2);
            int edge = Math.max(1, box.width / 9);
            LayoutBox inside = new LayoutBox(box.x + edge, box.y + edge, Math.max(0, box.width - edge * 2), Math.max(0, box.height - edge * 2));
            ScenePaintShapes.rounded(paint, inside, clip, theme.get().panel, inside.width / 2);
            if (checked.get()) {
                int inset = Math.max(edge + 1, box.width / 4);
                LayoutBox dot = new LayoutBox(box.x + inset, box.y + inset, Math.max(0, box.width - inset * 2), Math.max(0, box.height - inset * 2));
                ScenePaintShapes.rounded(paint, dot, clip, color, dot.width / 2);
            }
        }
    }
}
