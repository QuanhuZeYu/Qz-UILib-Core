package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditContinuation;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneFocusTraversal;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
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
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** 选择器和动作菜单的所属弹出列表；业务选择、焦点和滚动都保留原权威。 */
final class ScenePopupList<T, K> {
    private final ScenePageContext page;
    private final ReadableSignal<List<T>> source, available;
    private final Function<T, K> key;
    private final Function<T, String> label;
    private final Predicate<T> enabled;
    private final ReadableSignal<K> initial;
    private final Consumer<T> confirm;
    private final ReadableSignal<SceneTheme> theme;
    private final Signal<Object> opening;
    private final SceneNode button, node;

    static <T, K> ReadableSignal<List<T>> checked(ScenePageContext page, Owner owner, ReadableSignal<List<T>> source, Function<T, K> key) {
        return page.domain().computed(owner, () -> {
            List<T> result = new ArrayList<>(Objects.requireNonNull(source.get(), "选项来源")); Set<K> keys = new HashSet<>();
            for (T item : result) if (!keys.add(Objects.requireNonNull(key.apply(item), "选项键")))
                throw new IllegalArgumentException("弹出选项键重复: " + key.apply(item));
            return Collections.unmodifiableList(result);
        });
    }
    ScenePopupList(ScenePageContext page, Owner owner, ReadableSignal<List<T>> source, Function<T, K> key, Function<T, String> label,
            Predicate<T> enabled, ReadableSignal<K> initial, ReadableSignal<String> title, Consumer<T> confirm, ReadableSignal<SceneTheme> theme) {
        this.page = page; this.source = source; this.key = key; this.label = label; this.enabled = enabled;
        this.initial = initial; this.confirm = confirm; this.theme = theme; opening = page.domain().signal(null);
        available = page.domain().computed(owner, () -> {
            List<T> choices = new ArrayList<>(); for (T item : source.get()) if (enabled.test(item)) choices.add(item);
            return Collections.unmodifiableList(choices);
        });
        button = SceneButton.createWithIcon(page, title, theme, this::open, SceneTextPolicy.ELLIPSIS_SINGLE_LINE,
                () -> SceneIconButton.Symbol.CHEVRON_DOWN);
        button.bind(NodeBinding.repeat(() -> opening.get() == null ? Collections.emptyList() : Collections.singletonList(opening.get()),
                token -> token, token -> new Popup(token.get()).root));
        node = new SceneNode().size(Size.fill(1), Size.natural()).control(new Trigger()).own(owner).add(button);
        node.bind((runtime, self, scope) -> new BindingActivation() {
            public void refresh() { }
            public void close() { opening.set(null); }
        });
    }
    SceneNode node() { return node; }
    private void open() { if (opening.get() == null) opening.set(new Object()); }
    private void close(Object token) { if (opening.get() == token) opening.set(null); }
    private int rowHeight() { return Math.max(1, Math.round(theme.get().textStyle.logicalEm * 1.4f) + theme.get().padding * 2); }
    private T current(K identity) {
        for (T item : source.get()) if (Objects.equals(key.apply(item), identity)) return item;
        return null;
    }
    private boolean sourcePresent() {
        if (button.runtime() != page.runtime()) return false;
        for (SceneNode cursor = button; cursor != null; cursor = cursor.parent()) {
            if (!cursor.visible() || !cursor.enabled() || cursor.destroyed()) return false;
            SceneLayer.Policy policy = cursor.control() == null ? null : cursor.control().layerPolicy(cursor);
            if (policy != null && policy.participation == SceneLayer.Participation.PASSIVE) return false;
        }
        return true;
    }
    private class Flow implements SceneControl {
        public MeasuredSize measure(SceneNode n, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public MeasuredSize measure(SceneNode n, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(n, limits, children);
        }
        public ChildPlacements arrange(SceneNode n, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(n, box, children); }
        public void paint(SceneNode n, SceneGeometryView geometry, PaintPlanBuilder paint) { }
    }
    private final class Trigger extends Flow {
        public boolean editFallback(SceneNode n, SceneNode focused, SceneEditEvent event, SceneGeometryView geometry) {
            if (focused != button || event.command != SceneEditEvent.Command.DOWN) return false;
            open(); return true;
        }
    }

    private final class Popup extends Flow {
        final Object token, outside = new Object();
        final Map<SceneNode, K> identities = new IdentityHashMap<>();
        final SceneVirtualList<T, K> rows;
        final SceneNode root, empty;
        Popup(Object token) {
            this.token = token;
            rows = SceneVirtualList.create(page, source, key, item -> {
                K identity = key.apply(item.get());
                SceneNode action = SceneButton.create(page, () -> label.apply(item.get()), theme, () -> {
                    T live = current(identity);
                    if (opening.get() != token || live == null || !enabled.test(live)) return;
                    close(token); confirm.accept(live);
                });
                action.bind(NodeBinding.value(() -> enabled.test(item.get()), SceneNode::enabled));
                identities.put(action, identity); action.own(() -> identities.remove(action)); return action;
            }, () -> new SceneVirtualList.Options().estimatedRowHeight(rowHeight()).overscan(1));
            empty = SceneLabel.create(page, () -> "暂无可选项", theme).size(Size.fill(1), Size.natural());
            empty.bind(NodeBinding.value(() -> source.get().isEmpty(), SceneNode::visible));
            root = new SceneNode().size(Size.natural(), Size.natural()).modal(true).clip(false).control(this)
                    .onEscape(() -> { close(token); return true; }).add(rows.node()).add(empty);
            root.bind(NodeBinding.value(theme, (self, value) -> self.padding(value.padding)));
            root.bind((runtime, self, scope) -> {
                MountHandle sourceMount = runtime.captureMount(button); SceneNode pageRoot = button;
                while (pageRoot.parent() != null) pageRoot = pageRoot.parent();
                // 注册会同步提供旧快照；新层尚未投影，来源判断从下一次同批几何发布开始。
                final boolean[] subscribing = {true};
                scope.own(runtime.observeGeometry(pageRoot, geometry -> {
                    if (!subscribing[0] && opening.get() == token && (!sourceMount.active() || !sourcePresent()
                            || geometry == null || !geometry.layerSourceAvailable(root))) close(token);
                }));
                subscribing[0] = false;
                final boolean[] present = {true}; final Observation[] observer = new Observation[1];
                observer[0] = page.domain().observe(scope, () -> { runtime.interactionRevision().get(); present[0] = sourcePresent(); }, () -> {
                    observer[0].refresh(); if (!present[0]) close(token);
                });
                observer[0].refresh();
                return new BindingActivation() {
                    public void refresh() { }
                    public void close() { ScenePopupList.this.close(token); }
                };
            });
        }
        @Override public SceneLayer.Policy layerPolicy(SceneNode n) { return new SceneLayer.Policy(theme.get().gap, SceneLayer.Participation.INTERACTIVE); }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode n) { return Arrays.asList(theme, source, page.viewportHeight()); }
        @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode n) { return Collections.singletonList(theme); }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode n) { return Collections.singletonList(theme); }
        @Override public MeasuredSize measure(SceneNode n, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            int width = Math.min(limits.availableWidth, Math.max(1, Math.round(theme.get().textStyle.logicalEm * 18)));
            // 自然高控件收到无界正文约束；本弹层必须显式借用当批页面高度限制自己的列表容量。
            int viewportBody = Math.max(0, page.viewportHeight().get() - n.padding() * 2);
            int height = Math.min(Math.min(limits.availableHeight, viewportBody), rowHeight() * Math.max(1, Math.min(8, source.get().size())));
            for (SceneNode child : children.flowChildren()) children.measureChild(child, new Constraints(width, height));
            return new MeasuredSize(width, height);
        }
        @Override public ChildPlacements arrange(SceneNode n, LayoutBox box, SceneChildMeasurer children) {
            int padding = n.padding(); LayoutBox inner = new LayoutBox(padding, padding, Math.max(0, box.width - padding * 2), Math.max(0, box.height - padding * 2));
            List<ChildPlacements.Entry> result = new ArrayList<>();
            for (SceneNode child : children.flowChildren()) { children.measureChild(child, new Constraints(inner.width, inner.height)); result.add(new ChildPlacements.Entry(child, inner)); }
            return new ChildPlacements(result);
        }
        @Override public void paint(SceneNode n, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry = geometry.get(n); SceneTheme value = theme.get();
            ScenePaintShapes.rounded(paint, entry.box, entry.clip, value.disabled, Math.max(1, value.padding));
            ScenePaintShapes.rounded(paint, new LayoutBox(entry.box.x + 1, entry.box.y + 1, Math.max(0, entry.box.width - 2), Math.max(0, entry.box.height - 2)),
                    entry.clip, value.panel, Math.max(1, value.padding - 1));
        }
        @Override public Object pointerTarget(SceneNode n, SceneGeometryView geometry, int x, int y) {
            SceneGeometryView.Entry entry = geometry.get(n);
            return entry != null && geometry.viewport().contains(x, y) && !entry.box.contains(x, y) ? outside : null;
        }
        @Override public boolean pointerTargetValid(SceneNode n, Object target) { return opening.get() == token && target == outside; }
        @Override public boolean capturesPointer() { return true; }
        @Override public void pointer(SceneNode n, ScenePointerEvent event, SceneGeometryView geometry) {
            if (event.kind == ScenePointerEvent.Kind.DOWN && event.button == 0) close(token);
        }
        @Override public void activatePointer(SceneNode n, Object target) { if (target == outside) close(token); }
        @Override public boolean navigateScope(SceneNode n, boolean backward) { close(token); return true; }
        @Override public SceneFocusTraversal focusTraversal(SceneNode n) {
            return (from, backward) -> {
                List<T> choices = available.get(); K start = null;
                for (T item : choices) if (Objects.equals(key.apply(item), initial.get())) { start = key.apply(item); break; }
                if (start == null && !choices.isEmpty()) start = key.apply(choices.get(0));
                if (start != null) return rows.beginAt(start, backward);
                return new SceneFocusTraversal.Cursor() {
                    public SceneFocusTraversal.Result prepare() { return SceneFocusTraversal.Result.end(); }
                    public void close() { }
                };
            };
        }
        private boolean accepts(SceneNode actual) {
            K identity = identities.get(actual); if (identity == null || opening.get() != token) return false;
            T item = current(identity); return item != null && enabled.test(item);
        }
        private boolean moves(SceneEditEvent event) {
            return event.command == SceneEditEvent.Command.UP || event.command == SceneEditEvent.Command.DOWN
                    || event.command == SceneEditEvent.Command.HOME || event.command == SceneEditEvent.Command.END;
        }
        private boolean move(SceneEditEvent event) {
            if (!moves(event)) return false;
            SceneNode actual = page.runtime().focusRouter().focused().get(); if (!accepts(actual)) return false;
            List<T> choices = available.get(); K current = identities.get(actual); int index = -1;
            for (int i = 0; i < choices.size(); i++) if (Objects.equals(key.apply(choices.get(i)), current)) { index = i; break; }
            if (index < 0) return false;
            int next = event.command == SceneEditEvent.Command.HOME ? 0 : event.command == SceneEditEvent.Command.END ? choices.size() - 1
                    : Math.floorMod(index + (event.command == SceneEditEvent.Command.UP ? -1 : 1), choices.size());
            rows.focus(key.apply(choices.get(next))); return true;
        }
        @Override public boolean editFallback(SceneNode n, SceneNode focused, SceneEditEvent event, SceneGeometryView geometry) {
            if (event.command == SceneEditEvent.Command.TAB) { close(token); return true; }
            return page.runtime().focusRouter().focused().get() == focused && move(event);
        }
        @Override public SceneEditContinuation editContinuation(SceneNode n, SceneNode focused, SceneEditEvent event) {
            if (!accepts(focused) || !moves(event)) return null;
            return new SceneEditContinuation() {
                public boolean accepts(SceneNode actual) { return Popup.this.accepts(actual); }
                public boolean repeat(SceneEditEvent event) { return move(event); }
            };
        }
    }
}
