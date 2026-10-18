package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneFocusTraversal;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** 行视图的拥有与呈现协调；纯度量留在索引，实际节点始终走同一runtime。 */
final class VirtualCollectionController<T, K> implements SceneControl {
    private static final class Row<T, K> {
        final K key;
        final Signal<T> value;
        final SceneNode node;
        Row(K key, Signal<T> value, SceneNode node) { this.key = key; this.value = value; this.node = node; }
    }

    private final ScenePageContext page;
    private final SceneRuntime runtime;
    private final ReadableSignal<List<T>> source;
    private final Function<T, K> key;
    private final Function<ReadableSignal<T>, SceneNode> declaration;
    private final ReadableSignal<SceneVirtualList.Options> options;
    private final VirtualHeightIndex<K> heights = new VirtualHeightIndex<>();
    private final Map<K, T> values = new HashMap<>();
    private final Map<K, Row<T, K>> rows = new LinkedHashMap<>();
    private final Signal<Long> viewRevision;
    private final Map<SceneNode, Integer> measured = new HashMap<>();
    private List<K> order = Collections.emptyList();
    private SceneNode root;
    private int width, height, offset, estimate, gap;
    private long metrics = Long.MIN_VALUE, revision;
    private K requested, navigationKey, anchorKey;
    private long anchorWithin, lastScrollIntent;
    private K pendingAnchor;
    private long pendingWithin, pendingIntent;
    private boolean anchorPending;
    private boolean focusRequest, requestIssued;
    private club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter.RangeHandle requestRange;
    private boolean dataChanged;
    private List<T> published;
    private int measuredWidth = -1;
    private boolean active, endRequested;
    private long endIntent;
    private K endTarget;
    private club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter.RangeHandle endRange;

    VirtualCollectionController(ScenePageContext page, ReadableSignal<List<T>> source, Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration, ReadableSignal<SceneVirtualList.Options> options) {
        this.page = page; runtime = page.runtime(); this.source = source; this.key = key;
        this.declaration = declaration; this.options = options;
        viewRevision = page.domain().signal(0L);
    }

    void attach(SceneNode node) {
        root = node;
        node.own(this::dispose);
        node.bind((ownerRuntime, self, owner) -> {
            active = true;
            published = null; metrics = Long.MIN_VALUE;
            Observation observation = page.domain().observe(owner, this::settle,
                    () -> runtime.invalidateBinding(root));
            owner.own(runtime.observeGeometry(root, this::geometry));
            return new BindingActivation() {
                public void refresh() { observation.refresh(); }
                public void close() {
                    active = false; cancelRequest(); navigationKey = anchorKey = pendingAnchor = null;
                    anchorPending = false; cancelEnd();
                    anchorWithin = lastScrollIntent = 0; observation.close();
                    width = height = offset = 0; measured.clear(); measuredWidth = -1;
                }
            };
        });
    }

    private void settle() {
        viewRevision.get();
        List<SceneNode> retainedFocus = runtime.focusRouter().retainedFocus().get();
        SceneNode pressed = runtime.pointerRouter().pressedTarget().get();
        SceneVirtualList.Options policy = Objects.requireNonNull(options.get(), "集合选项");
        int nextEstimate = policy.estimate > 0 ? policy.estimate
                : Math.max(1, Math.round(page.theme().get().textStyle.logicalEm * 3));
        long nextMetrics = runtime.metricsRevision().get();
        List<T> current = Objects.requireNonNull(source.get(), "集合数据");
        boolean changed = current != published;
        boolean reset = nextMetrics != metrics || estimate != nextEstimate || measuredWidth != width;
        if (changed) {
            List<K> next = new ArrayList<>(current.size());
            Map<K, T> nextValues = new HashMap<>();
            for (T value : current) {
                K identity = Objects.requireNonNull(key.apply(value), "集合键");
                if (nextValues.containsKey(identity))
                    throw new IllegalArgumentException("集合业务键重复: " + root.id() + " / " + identity);
                nextValues.put(identity, value); next.add(identity);
                T before = values.get(identity);
                if (!Objects.equals(before, value)) { beginAnchor(); heights.invalidate(identity); }
            }
            dataChanged = !order.equals(next);
            if (dataChanged) anchorPending = false;
            values.clear(); values.putAll(nextValues); order = next;
            published = current;
        }
        if (changed || reset || gap != root.gap()) {
            if (!dataChanged && (reset || gap != root.gap())) beginAnchor();
            heights.reconcile(order, nextEstimate, root.gap(), reset);
            metrics = nextMetrics; estimate = nextEstimate; gap = root.gap(); measuredWidth = width;
        }
        if (requested != null && (!values.containsKey(requested) || !runtime.interactive(root)
                || requestRange == null || !requestRange.active())) cancelRequest();
        if (endRequested && (order.isEmpty() || !Objects.equals(endTarget, order.get(order.size() - 1))
                || !runtime.interactive(root) || endRange == null || !endRange.active())) cancelEnd();
        Set<K> needed = new LinkedHashSet<>();
        int prefetch = policy.overscan >= 0 ? policy.overscan : height;
        int from = heights.at(Math.max(0L, (long) offset - prefetch));
        long end = (long) offset + height + prefetch;
        for (int i = from; i < heights.size() && heights.start(i) < end; i++) needed.add(heights.key(i));
        // 旧视口锚点在度量换代后仍须取得真实新行高，不能先被估算窗口淘汰。
        if (anchorPending && pendingAnchor != null && values.containsKey(pendingAnchor)) needed.add(pendingAnchor);
        if (endRequested) needed.add(endTarget);
        if (requested != null) needed.add(requested);
        if (navigationKey != null && values.containsKey(navigationKey)) needed.add(navigationKey);
        for (Row<T, K> row : rows.values()) {
            boolean retained = contains(row.node, pressed);
            for (SceneNode focused : retainedFocus) retained |= contains(row.node, focused);
            if (values.containsKey(row.key) && retained) needed.add(row.key);
        }
        List<SceneNode> nextNodes = new ArrayList<>();
        Map<K, Row<T, K>> nextRows = new LinkedHashMap<>();
        List<SceneNode> acquired = new ArrayList<>();
        try {
            for (K identity : needed) {
                Row<T, K> row = rows.get(identity);
                if (row == null || row.node.destroyed()) {
                    Signal<T> value = page.domain().signal(values.get(identity));
                    SceneNode child = Objects.requireNonNull(declaration.apply(value), "集合行声明");
                    if (child.destroyed() || child.runtime() != null || child.parent() != null)
                        throw new IllegalArgumentException("集合行不能重复拥有: " + identity);
                    acquired.add(child);
                    row = new Row<>(identity, value, child);
                } else row.value.set(values.get(identity));
                nextRows.put(identity, row);
            }
            // 集合内真实逻辑顺序与几何、Tab共用；有限保留行不被追加到错误位置。
            List<K> sorted = new ArrayList<>(nextRows.keySet());
            sorted.sort((left, right) -> Integer.compare(heights.position(left), heights.position(right)));
            for (K identity : sorted) nextNodes.add(nextRows.get(identity).node);
            runtime.reconcile(root, nextNodes, this, true);
            rows.clear(); for (K identity : sorted) rows.put(identity, nextRows.get(identity));
        } catch (RuntimeException | Error failure) {
            // 尚未交给正式树的声明同样已取得资源，失败时结束它们，保留原构造错误。
            for (SceneNode child : acquired) if (child.runtime() == null && child.parent() == null) {
                try { child.destroy(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private static boolean contains(SceneNode row, SceneNode target) {
        for (SceneNode cursor = target; cursor != null; cursor = cursor.parent()) if (cursor == row) return true;
        return false;
    }

    @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) {
        return Arrays.asList(viewRevision, runtime.metricsRevision());
    }

    @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
        return new MeasuredSize(0, 0);
    }

    @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
        int padding = node.padding();
        int available = Math.max(0, allocated.width - padding * 2);
        measured.clear();
        List<ChildPlacements.Entry> result = new ArrayList<>();
        Set<SceneNode> flow = new java.util.HashSet<>(children.flowChildren());
        for (Row<T, K> row : rows.values()) {
            int position = heights.position(row.key);
            if (position < 0) continue;
            if (!flow.contains(row.node)) { measured.put(row.node, -1); continue; }
            MeasuredSize actual = children.measureChild(row.node, new Constraints(available, Integer.MAX_VALUE));
            int rowWidth = row.node.width().mode == Size.Mode.FILL ? row.node.width().constrain(available) : actual.width;
            measured.put(row.node, actual.height);
            long y = (long) padding + heights.start(position);
            if (y > Integer.MAX_VALUE) throw new IllegalArgumentException("集合行位置超出scene范围: " + row.key);
            result.add(new ChildPlacements.Entry(row.node, new LayoutBox(padding, (int)y, rowWidth, actual.height)));
        }
        long extent = (long) heights.extent() + padding * 2L;
        if (extent > Integer.MAX_VALUE) throw new IllegalArgumentException("集合内容高度超出scene范围");
        return new ChildPlacements(result, new MeasuredSize(allocated.width, (int)extent));
    }

    private void geometry(SceneGeometryView geometry) {
        if (!active || root.runtime() != runtime) return;
        if (geometry == null) { cancelRequest(); cancelEnd(); return; }
        SceneGeometryView.Entry view = geometry.get(root);
        if (view == null) { cancelRequest(); cancelEnd(); return; }
        if (requested != null && (requestRange == null || !requestRange.active() || !runtime.interactive(root))) cancelRequest();
        int nextWidth = Math.max(0, view.box.width - root.padding() * 2);
        int nextHeight = Math.max(0, view.box.height - root.padding() * 2);
        int nextOffset = view.scrollOffset;
        long intent = runtime.scrollIntentRevision(root);
        if (anchorPending && intent != pendingIntent) anchorPending = false;
        if (endRequested && (intent != endIntent || endRange == null || !endRange.active() || !runtime.interactive(root))) cancelEnd();
        if (nextWidth != width && !dataChanged) beginAnchor();
        // 锚点以独立意图跨估算换代保存；范围限制不等于一次用户滚动。
        boolean positionChanged = nextOffset != offset;
        boolean changed = nextWidth != width || nextHeight != height || positionChanged || gap != root.gap();
        boolean corrected = false;
        for (Row<T, K> row : rows.values()) {
            Integer actual = measured.get(row.node);
            if (actual != null) corrected |= actual < 0 ? heights.omit(row.key) : heights.measure(row.key, actual);
        }
        if (corrected && !dataChanged && intent == lastScrollIntent) beginAnchor();
        width = nextWidth; height = nextHeight; offset = nextOffset;
        if (endRequested && !order.isEmpty()) {
            // 校正只使用原ScrollPosition；尾行本次真实测量到齐后，等待总范围也结算到同代。
            anchorPending = false;
            runtime.correctScrollPosition(root, endIntent, view.scrollMaximum);
            offset = runtime.scrollOffset(root);
            Row<T, K> last = rows.get(endTarget);
            SceneGeometryView.Entry placed = last == null ? null : geometry.get(last.node);
            boolean rangePrepared = true;
            for (int i = heights.at(offset); i < heights.size() && heights.start(i) < (long)offset + height; i++)
                rangePrepared &= rows.containsKey(heights.key(i)) && heights.known(heights.key(i));
            boolean endVisible = placed != null
                    ? (long)placed.box.y + placed.box.height + root.padding() <= (long)view.box.y + view.box.height
                    : last != null && Integer.valueOf(-1).equals(measured.get(last.node));
            if (!corrected && rangePrepared && endVisible && offset == view.scrollMaximum) {
                cancelEnd(); changed = true;
            }
        } else if (requested != null) {
            Row<T, K> row = rows.get(requested);
            SceneGeometryView.Entry placed = row == null ? null : geometry.get(row.node);
            if (placed != null) {
                if (!requestIssued) {
                    SceneNode target = focusRequest ? firstFocusable(row.node) : row.node;
                    if (target != null) {
                        if (focusRequest) runtime.focusRouter().navigateTo(runtime.captureMount(target));
                        else runtime.requestInputReveal(target);
                    }
                    requestIssued = true;
                }
                // 显露意图要到唯一位置所有者真正推进后才释放准备行，不能在reveal前销毁它。
                boolean visible = placed.box.y < (long)placed.clip.y + placed.clip.height
                        && (long)placed.box.y + placed.box.height > placed.clip.y;
                if (focusRequest || visible) {
                    cancelRequest(); changed = true;
                }
            }
        } else if (anchorPending && heights.known(pendingAnchor)) {
            int position = heights.position(pendingAnchor);
            if (position >= 0 && heights.height(position) > 0) {
                long desired = heights.start(position) + Math.min(Math.max(0, pendingWithin), heights.height(position) - 1L);
                if (desired <= Integer.MAX_VALUE && runtime.correctScrollPosition(root, pendingIntent, (int)desired))
                    offset = runtime.scrollOffset(root);
            }
            anchorPending = false;
        }
        if (!anchorPending) {
            int anchorIndex = heights.at(offset);
            anchorKey = anchorIndex < heights.size() ? heights.key(anchorIndex) : null;
            anchorWithin = anchorKey == null ? 0 : offset - heights.start(anchorIndex);
        }
        lastScrollIntent = runtime.scrollIntentRevision(root);
        dataChanged = false;
        if (changed || corrected) viewRevision.set(++revision);
    }

    private void beginAnchor() {
        if (anchorPending || anchorKey == null || offset <= 0) return;
        pendingAnchor = anchorKey; pendingWithin = anchorWithin;
        pendingIntent = runtime.scrollIntentRevision(root); anchorPending = true;
    }

    private void cancelRequest() { requested = null; requestRange = null; focusRequest = requestIssued = false; }
    private void cancelEnd() { endRequested = false; endRange = null; endTarget = null; }

    private SceneNode firstFocusable(SceneNode node) {
        if (!runtime.interactive(node)) return null;
        if (node.focusable() && runtime.interactive(node)) return node;
        for (SceneNode child : node.children()) { SceneNode found = firstFocusable(child); if (found != null) return found; }
        return null;
    }

    @Override public SceneFocusTraversal focusTraversal(SceneNode node) {
        return this::beginTraversal;
    }

    private SceneFocusTraversal.Cursor beginTraversal(SceneNode from, boolean backward) {
        K initialKey = null;
        for (Row<T, K> row : rows.values()) if (contains(row.node, from)) { initialKey = row.key; break; }
        final K fromKey = initialKey;
        return new SceneFocusTraversal.Cursor() {
            int position = fromKey == null ? (backward ? heights.size() - 1 : 0) : heights.position(fromKey);
            SceneNode after = from;
            K candidate;
            boolean closed;
            public SceneFocusTraversal.Result prepare() {
                if (closed || !active) return SceneFocusTraversal.Result.end();
                while (position >= 0 && position < heights.size()) {
                    if (candidate == null) candidate = heights.key(position);
                    if (!values.containsKey(candidate))
                        return SceneFocusTraversal.Result.cancelled();
                    Row<T, K> row = rows.get(candidate);
                    if (row == null || row.node.runtime() != runtime) {
                        if (!Objects.equals(navigationKey, candidate)) {
                            navigationKey = candidate; viewRevision.set(++revision);
                        }
                        return SceneFocusTraversal.Result.waiting();
                    }
                    List<SceneNode> targets = new ArrayList<>();
                    collectFocusable(row.node, targets);
                    int previous = after == null ? -1 : targets.indexOf(after);
                    int next = previous < 0 ? (backward ? targets.size() - 1 : 0) : previous + (backward ? -1 : 1);
                    if (next >= 0 && next < targets.size())
                        return SceneFocusTraversal.Result.ready(targets.get(next));
                    position = heights.position(candidate) + (backward ? -1 : 1);
                    candidate = null; after = null;
                }
                return SceneFocusTraversal.Result.end();
            }
            public void close() {
                if (closed) return;
                closed = true;
                if (navigationKey != null) { navigationKey = null; viewRevision.set(++revision); }
            }
        };
    }

    private void collectFocusable(SceneNode node, List<SceneNode> targets) {
        if (!runtime.interactive(node)) return;
        if (node.focusable()) targets.add(node);
        for (SceneNode child : node.children()) collectFocusable(child, targets);
    }

    @Override public void scrollPositionRequested(SceneNode node, SceneNode.Axis axis, int requested,
            SceneScrollViewport.Snapshot previous) {
        if (axis != SceneNode.Axis.COLUMN || !active) return;
        if (requested >= previous.maximum && previous.maximum > 0 && !order.isEmpty()) {
            endRequested = true; endIntent = runtime.scrollIntentRevision(root); endTarget = order.get(order.size() - 1);
            endRange = runtime.focusRouter().captureRange(); anchorPending = false;
            viewRevision.set(++revision);
        } else cancelEnd();
    }

    boolean request(K identity, boolean focus) {
        page.domain().checkWrite();
        Objects.requireNonNull(identity, "key");
        if (!active || !runtime.interactive(root) || !values.containsKey(identity)) return false;
        cancelEnd();
        requested = identity; requestRange = runtime.focusRouter().captureRange();
        focusRequest = focus; requestIssued = false; viewRevision.set(++revision);
        return true;
    }

    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) { }

    private void dispose() {
        rows.clear(); values.clear(); measured.clear(); heights.clear(); order = Collections.emptyList();
        published = null; requested = null;
    }
}
