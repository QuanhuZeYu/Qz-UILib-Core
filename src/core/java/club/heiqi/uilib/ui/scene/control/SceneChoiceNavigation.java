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
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** 仅共享选择控件的目标顺序与输入，呈现和业务选择来源仍由各自控件持有。 */
final class SceneChoiceNavigation<T, K> implements SceneControl {
    private final ScenePageContext page;
    private final ReadableSignal<List<T>> source;
    private final Function<T, K> key;
    private final Predicate<T> enabled;
    private final Map<K, SceneNode> nodes;
    private final ReadableSignal<K> selected;
    private final Consumer<K> change;
    private final boolean vertical;
    SceneChoiceNavigation(ScenePageContext page, ReadableSignal<List<T>> source, Function<T, K> key, Predicate<T> enabled,
            Map<K, SceneNode> nodes, ReadableSignal<K> selected, Consumer<K> change, boolean vertical) {
        this.page = page; this.source = source; this.key = key; this.enabled = enabled; this.nodes = nodes;
        this.selected = selected; this.change = change; this.vertical = vertical;
    }
    private List<SceneNode> available() {
        List<SceneNode> result = new ArrayList<>();
        for (T option : Objects.requireNonNull(source.get(), "选项来源")) {
            SceneNode item = nodes.get(key.apply(option));
            if (enabled.test(option) && item != null && item.runtime() == page.runtime() && item.focusable() && page.runtime().interactive(item)) result.add(item);
        }
        return result;
    }
    private K keyOf(SceneNode target) {
        if (target == null) return null;
        for (T option : source.get()) if (nodes.get(key.apply(option)) == target && enabled.test(option)) return key.apply(option);
        return null;
    }
    private boolean accepts(SceneNode focused) { return keyOf(focused) != null && available().contains(focused); }
    void select(K key) {
        if (accepts(nodes.get(key)) && !Objects.equals(selected.get(), key)) change.accept(key);
    }
    private boolean moves(SceneEditEvent event) {
        return event.command == SceneEditEvent.Command.LEFT || event.command == SceneEditEvent.Command.RIGHT
                || vertical && (event.command == SceneEditEvent.Command.UP || event.command == SceneEditEvent.Command.DOWN)
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
    @Override public SceneFocusTraversal focusTraversal(SceneNode root) {
        return (from, backward) -> new SceneFocusTraversal.Cursor() {
            private boolean closed;
            public SceneFocusTraversal.Result prepare() {
                // 边界可以含专用滚动区；其内部显式条不成为标签与面板间的额外Tab停靠。
                if (closed || from != null) return SceneFocusTraversal.Result.end();
                List<SceneNode> choices = available(); if (choices.isEmpty()) return SceneFocusTraversal.Result.end();
                SceneNode target = page.runtime().focusRouter().focused().get();
                if (!choices.contains(target)) target = nodes.get(selected.get());
                if (!choices.contains(target)) target = choices.get(vertical && backward ? choices.size() - 1 : 0);
                return SceneFocusTraversal.Result.ready(target);
            }
            public void close() { closed = true; }
        };
    }
    @Override public boolean editFallback(SceneNode root, SceneNode focused, SceneEditEvent event, SceneGeometryView geometry) {
        return accepts(focused) && page.runtime().focusRouter().focused().get() == focused && move(event);
    }
    @Override public SceneEditContinuation editContinuation(SceneNode root, SceneNode focused, SceneEditEvent event) {
        if (!moves(event) || !accepts(focused)) return null;
        return new SceneEditContinuation() {
            public boolean accepts(SceneNode actual) { return SceneChoiceNavigation.this.accepts(actual); }
            public boolean repeat(SceneEditEvent next) { return move(next); }
        };
    }
    @Override public MeasuredSize measure(SceneNode root, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
    @Override public MeasuredSize measure(SceneNode root, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
        return SceneFlowLayout.measure(root, limits, children);
    }
    @Override public ChildPlacements arrange(SceneNode root, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(root, box, children); }
    @Override public void paint(SceneNode root, SceneGeometryView geometry, PaintPlanBuilder paint) { }
}
