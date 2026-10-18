package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneFocusTraversal;
import club.heiqi.uilib.ui.scene.input.SceneFocusGeometry;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
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
    private final ReadableSignal<?> measurementCondition;
    private final ReadableSignal<SceneControlMetrics> controlMetrics;
    private Object measuredCondition;
    private final VirtualHeightIndex<K> heights = new VirtualHeightIndex<>();
    private final Map<K, T> values = new HashMap<>();
    private final Map<K, Row<T, K>> rows = new LinkedHashMap<>();
    private final Signal<Long> viewRevision, logicalRevision;
    private long logicalClock;
    private SceneSemantics.Role semanticRole = SceneSemantics.Role.LIST, itemSemanticRole = SceneSemantics.Role.LIST_ITEM;
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
    private MountHandle requestTarget;
    private Function<SceneNode, SceneNode> focusTarget;
    private club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter.RangeHandle requestRange;
    private boolean dataChanged;
    private List<T> published;
    private int measuredWidth = -1;
    private SceneGeometryView stableGeometry;
    private boolean active, endRequested;
    private long endIntent;
    private K endTarget;
    private club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter.RangeHandle endRange;

    VirtualCollectionController(ScenePageContext page, ReadableSignal<List<T>> source, Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration, ReadableSignal<SceneVirtualList.Options> options,
            ReadableSignal<?> measurementCondition, ReadableSignal<SceneControlMetrics> controlMetrics) {
        this.page = page; runtime = page.runtime(); this.source = source; this.key = key;
        this.declaration = declaration; this.options = options; this.measurementCondition = measurementCondition;
        this.controlMetrics = Objects.requireNonNull(controlMetrics, "集合角色度量");
        viewRevision = page.domain().signal(0L); logicalRevision = page.domain().signal(0L);
    }

    /** 复合控件沿真实行提供所属角色，不再嵌入一层普通LIST/LIST_ITEM语义。 */
    void semanticRoles(SceneSemantics.Role collection, SceneSemantics.Role item) { semanticRole = collection; itemSemanticRole = item; }
    int logicalPosition(K identity) { logicalRevision.get(); return heights.position(identity) + 1; }
    int logicalCount() { logicalRevision.get(); return order.size(); }
    @Override public SceneSemantics semantics(SceneNode node) {
        logicalRevision.get();
        return semanticRole == null ? null : SceneSemantics.builder(semanticRole).count(order.size()).build();
    }
    private SceneSemantics itemSemantics(K identity, SceneNode node) {
        int position = logicalPosition(identity);
        if (itemSemanticRole == null || position <= 0) return null;
        // 集合只补普通内容的条目角色；正式控件的角色、状态和动作仍由其真实描述决定。
        SceneSemantics content = node.control() == null ? null : node.control().semantics(node);
        SceneSemantics.Role role = content == null ? null : content.values.role;
        return SceneSemantics.builder(role == null || role == SceneSemantics.Role.LABEL ? itemSemanticRole : null)
                .position(position, order.size()).build();
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
                    width = height = offset = 0; measured.clear(); measuredWidth = -1; stableGeometry = null;
                }
            };
        });
    }

    private void settle() {
        viewRevision.get();
        List<SceneNode> retainedFocus = runtime.focusRouter().retainedFocus().get();
        SceneNode pressed = runtime.pointerRouter().pressedTarget().get();
        SceneVirtualList.Options policy = Objects.requireNonNull(options.get(), "集合选项");
        SceneControlMetrics currentMetrics = controlMetrics.get();
        int nextEstimate = policy.estimate > 0 ? policy.estimate : currentMetrics.rowHeightEstimate;
        long nextMetrics = runtime.metricsRevision().get();
        List<T> current = Objects.requireNonNull(source.get(), "集合数据");
        boolean changed = current != published;
        // 显式估算不代表实际行高；密度改变内距时也必须结束屏外旧高度，沿原锚点重新准备。
        Object nextCondition = Arrays.asList(measurementCondition.get(), currentMetrics.layoutKey());
        boolean conditionChanged = !Objects.equals(measuredCondition, nextCondition);
        boolean reset = nextMetrics != metrics || estimate != nextEstimate || measuredWidth != width || conditionChanged;
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
            metrics = nextMetrics; estimate = nextEstimate; gap = root.gap(); measuredWidth = width; measuredCondition = nextCondition;
            if (conditionChanged) viewRevision.set(++revision);
            if (changed) logicalRevision.set(++logicalClock);
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
                    // 只为当前真实行注册低优先默认；调用方的局部角色/名称覆盖保持原顺序。
                    child.bind((ownerRuntime, self, owner) -> ownerRuntime.bindDefaultSemantics(self, () -> itemSemantics(identity, self)));
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

    private void geometry(SceneGeometryView worldGeometry) {
        SceneGeometryView geometry = worldGeometry == null ? null : worldGeometry.asControl(root);
        if (!active || root.runtime() != runtime) return;
        if (geometry == null) { stableGeometry = null; cancelRequest(); cancelEnd(); return; }
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
                    SceneNode target = focusRequest ? focusTarget.apply(row.node) : row.node;
                    if (focusRequest && target != null && (!target.focusable() || !contains(row.node, target)
                            || target.runtime() != runtime || !runtime.interactive(target))) target = null;
                    if (target == null) { cancelRequest(); changed = true; }
                    else {
                        requestTarget = runtime.captureMount(target); requestIssued = true;
                        if (focusRequest) runtime.focusRouter().navigateTo(requestTarget);
                        else runtime.requestInputReveal(target);
                    }
                }
                if (requestTarget != null) {
                    SceneNode target = requestTarget.node();
                    if (!requestTarget.active() || !runtime.interactive(target) || !contains(row.node, target)) {
                        cancelRequest(); changed = true;
                    } else {
                        // focus以原实际目标完成，reveal以整行完成；和正式显露使用同一区域及尽力边界。
                        LayoutBox region = runtime.domain().readOnly(() -> SceneGeometry.revealRegion(target, root, geometry));
                        LayoutBox window = SceneGeometry.revealWindow(root, geometry);
                        boolean reached = region != null && (region.width == 0 || region.height == 0
                                || window.width == 0 || window.height == 0
                                || SceneGeometry.revealDelta(region.y, region.height, window.y, window.height) == 0);
                        boolean rangePrepared = !corrected;
                        for (int i = heights.at(offset); i < heights.size() && heights.start(i) < (long)offset + height; i++)
                            rangePrepared &= rows.containsKey(heights.key(i)) && heights.known(heights.key(i));
                        // 首次显露后的邻行校正仍会移动目标，完成后不因用户滚动重发本请求。
                        if (reached && rangePrepared) { cancelRequest(); changed = true; }
                        else if (!reached) runtime.requestInputReveal(target);
                    }
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
        stableGeometry = corrected ? null : geometry;
        if (changed || corrected) viewRevision.set(++revision);
    }

    private void beginAnchor() {
        if (anchorPending || anchorKey == null || offset <= 0) return;
        pendingAnchor = anchorKey; pendingWithin = anchorWithin;
        pendingIntent = runtime.scrollIntentRevision(root); anchorPending = true;
    }

    private void cancelRequest() { requested = null; requestRange = null; requestTarget = null; focusTarget = null; focusRequest = requestIssued = false; }
    private void cancelEnd() { endRequested = false; endRange = null; endTarget = null; }

    private SceneNode firstFocusable(SceneNode node) {
        if (!runtime.interactive(node)) return null;
        if (node.focusable() && runtime.interactive(node)) return node;
        for (SceneNode child : node.children()) { SceneNode found = firstFocusable(child); if (found != null) return found; }
        return null;
    }

    @Override public SceneFocusTraversal focusTraversal(SceneNode node) {
        return new SceneFocusTraversal() {
            public Cursor begin(SceneNode from, boolean backward) { return beginTraversal(from, backward); }
            public DirectionalCursor beginDirection(SceneNode from, SceneNavigationEvent direction, LayoutBox origin) {
                return beginDirectional(from, direction, origin);
            }
        };
    }

    private SceneFocusTraversal.Cursor beginTraversal(SceneNode from, boolean backward) {
        K initialKey = null;
        for (Row<T, K> row : rows.values()) if (contains(row.node, from)) { initialKey = row.key; break; }
        return beginTraversal(from, backward, initialKey);
    }

    SceneFocusTraversal.Cursor beginAt(K key, boolean backward) { return beginAt(key, backward, true); }

    SceneFocusTraversal.Cursor beginAt(K key, boolean backward, boolean reveal) {
        SceneFocusTraversal.Cursor delegate = beginTraversal(null, backward, Objects.requireNonNull(key, "起点键"));
        return new SceneFocusTraversal.Cursor() {
            public SceneFocusTraversal.Result prepare() {
                SceneFocusTraversal.Result result = delegate.prepare();
                if (reveal && result.status == SceneFocusTraversal.Result.Status.READY) runtime.requestInputReveal(result.target);
                return result;
            }
            public void close() { delegate.close(); }
        };
    }

    private SceneFocusTraversal.Cursor beginTraversal(SceneNode from, boolean backward, K fromKey) {
        return new SceneFocusTraversal.Cursor() {
            int position;
            SceneNode after = from;
            K candidate;
            SceneFocusTraversal.Cursor rowCursor;
            boolean initialized, closed;
            public SceneFocusTraversal.Result prepare() {
                if (closed || !active) return SceneFocusTraversal.Result.end();
                if (published == null) return SceneFocusTraversal.Result.waiting();
                if (!initialized) {
                    initialized = true; position = fromKey == null ? (backward ? heights.size() - 1 : 0) : heights.position(fromKey);
                }
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
                    if (rowCursor == null) rowCursor = runtime.beginFocusTraversal(row.node, after, backward);
                    SceneFocusTraversal.Result result = rowCursor.prepare();
                    if (result.status != SceneFocusTraversal.Result.Status.END) return result;
                    rowCursor.close(); rowCursor = null;
                    position = heights.position(candidate) + (backward ? -1 : 1);
                    candidate = null; after = null;
                }
                return SceneFocusTraversal.Result.end();
            }
            public void close() {
                if (closed) return;
                closed = true;
                try { if (rowCursor != null) rowCursor.close(); }
                finally {
                    rowCursor = null;
                    if (navigationKey != null) { navigationKey = null; viewRevision.set(++revision); }
                }
            }
        };
    }

    private SceneFocusTraversal.DirectionalCursor beginDirectional(SceneNode from, SceneNavigationEvent direction, LayoutBox origin) {
        K initial = null;
        for (Row<T, K> row : rows.values()) if (contains(row.node, from)) { initial = row.key; break; }
        final K fromKey = initial;
        final boolean layoutVertical = direction == SceneNavigationEvent.UP || direction == SceneNavigationEvent.DOWN;
        return new SceneFocusTraversal.DirectionalCursor() {
            int position = fromKey == null ? Math.min(heights.size() - 1, heights.at(offset)) : heights.position(fromKey);
            K candidate;
            boolean first = true, closed;
            SceneFocusTraversal.DirectionalCursor nested;
            club.heiqi.uilib.ui.scene.runtime.MountHandle nestedBoundary;
            final Set<SceneNode> exhausted = new java.util.HashSet<>(), ordinary = new java.util.HashSet<>();
            public SceneFocusTraversal.Result prepare(SceneGeometryView worldGeometry) {
                SceneGeometryView geometry = worldGeometry.world();
                if (closed || !active || !runtime.interactive(root)) return SceneFocusTraversal.Result.cancelled();
                if (fromKey != null && !values.containsKey(fromKey)) return SceneFocusTraversal.Result.cancelled();
                if (geometry.asControl(root) != stableGeometry) return SceneFocusTraversal.Result.waiting();
                club.heiqi.uilib.ui.scene.geometry.SceneTransform frame = geometry.get(root).transform;
                double projection = direction == SceneNavigationEvent.UP ? -frame.d : direction == SceneNavigationEvent.DOWN ? frame.d
                        : direction == SceneNavigationEvent.LEFT ? -frame.c : frame.c;
                boolean vertical = frame.identity() ? layoutVertical : Math.abs(projection) > Math.ulp(frame.maximumScale()) * 4;
                int step = projection < 0 ? -1 : 1;
                LayoutBox start = geometry.get(from) == null ? origin : geometry.get(from).box;
                if (first && fromKey == null) {
                    // 外部入口先比较当前呈现区域内的真实目标，不把不可见预取行当作默认入口。
                    List<SceneNode> visible = new ArrayList<>();
                    for (Row<T, K> row : rows.values()) {
                        SceneGeometryView.Entry entry = geometry.get(row.node);
                        if (entry != null && !geometry.visibleRegion(row.node).empty()) collectFocusable(row.node, visible);
                    }
                    List<SceneNode> ordered = SceneFocusGeometry.ordered(geometry, start, direction, visible);
                    if (!ordered.isEmpty()) return SceneFocusTraversal.Result.ready(ordered.get(0));
                    if (step < 0) position = Math.min(heights.size() - 1, heights.at((long)offset + height));
                }
                first = false;
                while (position >= 0 && position < heights.size()) {
                    if (candidate == null) candidate = heights.key(position);
                    if (!values.containsKey(candidate)) return SceneFocusTraversal.Result.cancelled();
                    Row<T, K> row = rows.get(candidate);
                    if (!Objects.equals(navigationKey, candidate)) { navigationKey = candidate; viewRevision.set(++revision); }
                    if (row == null || row.node.runtime() != runtime) return SceneFocusTraversal.Result.waiting();
                    if (geometry.get(row.node) == null && !Integer.valueOf(-1).equals(measured.get(row.node)))
                        return SceneFocusTraversal.Result.waiting();
                    if (nested != null) {
                        if (!nestedBoundary.active() || !runtime.interactive(nestedBoundary.node())) return SceneFocusTraversal.Result.cancelled();
                        SceneFocusTraversal.Result result = nested.prepare(geometry);
                        if (closed) return SceneFocusTraversal.Result.cancelled();
                        if (result.status != SceneFocusTraversal.Result.Status.END) return result;
                        exhausted.add(nestedBoundary.node()); nested.close(); nested = null; nestedBoundary = null;
                    }
                    Map<SceneNode, SceneFocusTraversal> targets = new LinkedHashMap<>();
                    collectDirectional(row.node, from, exhausted, ordinary, targets);
                    List<SceneNode> ordered = SceneFocusGeometry.ordered(geometry, start, direction, targets.keySet());
                    if (!ordered.isEmpty()) {
                        SceneNode target = ordered.get(0);
                        SceneFocusTraversal traversal = targets.get(target);
                        if (traversal == null) return SceneFocusTraversal.Result.ready(target);
                        nested = traversal.beginDirection(from, direction, start);
                        if (nested == null) ordinary.add(target);
                        else nestedBoundary = runtime.captureMount(target);
                        continue;
                    }
                    if (fromKey != null && !vertical) return SceneFocusTraversal.Result.end();
                    position = heights.position(candidate) + step; candidate = null; exhausted.clear(); ordinary.clear();
                }
                return SceneFocusTraversal.Result.end();
            }
            public void close() {
                if (closed) return;
                closed = true;
                try { if (nested != null) nested.close(); }
                finally {
                    nested = null; nestedBoundary = null; exhausted.clear(); ordinary.clear();
                    if (navigationKey != null) { navigationKey = null; viewRevision.set(++revision); }
                }
            }
        };
    }

    private void collectDirectional(SceneNode node, SceneNode from, Set<SceneNode> exhausted, Set<SceneNode> ordinary,
            Map<SceneNode, SceneFocusTraversal> targets) {
        if (!runtime.interactive(node) || exhausted.contains(node)) return;
        SceneFocusTraversal traversal = node.control() == null ? null : node.control().focusTraversal(node);
        // 更内层已由唯一搜索先行耗尽，外层不可重新进入原边界或只看其有限可见行。
        if (traversal != null && contains(node, from)) return;
        if (node != from && node.focusable()) targets.put(node, null);
        if (traversal != null && !ordinary.contains(node)) {
            if (!node.focusable()) targets.put(node, traversal);
        } else for (SceneNode child : node.children()) collectDirectional(child, from, exhausted, ordinary, targets);
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

    boolean request(K identity, boolean focus) { return request(identity, focus, this::firstFocusable); }

    boolean request(K identity, boolean focus, Function<SceneNode, SceneNode> focusTarget) {
        page.domain().checkWrite();
        Objects.requireNonNull(identity, "key");
        if (!active || !runtime.interactive(root) || !values.containsKey(identity)) return false;
        cancelEnd();
        requested = identity; requestRange = runtime.focusRouter().captureRange();
        focusRequest = focus; this.focusTarget = Objects.requireNonNull(focusTarget, "焦点目标准备");
        requestIssued = false; requestTarget = null; viewRevision.set(++revision);
        return true;
    }

    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) { }

    private void dispose() {
        rows.clear(); values.clear(); measured.clear(); heights.clear(); order = Collections.emptyList();
        published = null; requested = null;
    }
}
