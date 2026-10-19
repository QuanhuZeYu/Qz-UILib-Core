package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter.RangeHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** 集合尚未完成的位置意图；实际位置与焦点仍分别由运行时和唯一路由持有。 */
final class VirtualCollectionPosition<K> {
    private static final class Anchor<K> {
        final K key;
        final long within, intent;
        Anchor(K key, long within, long intent) { this.key = key; this.within = within; this.intent = intent; }
    }
    private static final class End<K> {
        final K key;
        final long intent;
        final RangeHandle range;
        End(K key, long intent, RangeHandle range) { this.key = key; this.intent = intent; this.range = range; }
    }
    private static final class Reveal<K> {
        final K key;
        final boolean focus;
        final Function<SceneNode, SceneNode> select;
        final RangeHandle range;
        MountHandle target;
        Reveal(K key, boolean focus, Function<SceneNode, SceneNode> select, RangeHandle range) {
            this.key = key; this.focus = focus; this.select = select; this.range = range;
        }
    }

    private final SceneRuntime runtime;
    private final SceneNode root;
    private final VirtualHeightIndex<K> heights;
    private Anchor<K> observed, correction;
    private long lastIntent;
    private End<K> end;
    private Reveal<K> reveal;

    VirtualCollectionPosition(SceneRuntime runtime, SceneNode root, VirtualHeightIndex<K> heights) {
        this.runtime = runtime; this.root = root; this.heights = heights;
    }

    void reset() { observed = correction = null; lastIntent = 0; end = null; reveal = null; }
    void unavailable() { end = null; reveal = null; }
    void dataReordered() { correction = null; }
    boolean endPending() { return end != null; }
    boolean revealPending() { return reveal != null; }
    K endKey() { return end == null ? null : end.key; }
    K revealKey() { return reveal == null ? null : reveal.key; }
    long observedIntent() { return lastIntent; }

    void beginAnchor(int offset) {
        if (correction == null && observed != null && observed.key != null && offset > 0)
            correction = new Anchor<>(observed.key, observed.within, runtime.scrollIntentRevision(root));
    }

    void validate() {
        boolean interactive = runtime.interactive(root);
        if (reveal != null && (heights.position(reveal.key) < 0 || !interactive || !reveal.range.active())) reveal = null;
        if (end != null && (heights.size() == 0 || !Objects.equals(end.key, heights.key(heights.size() - 1))
                || !interactive || !end.range.active())) end = null;
    }

    void observeIntent(long intent) {
        if (correction != null && correction.intent != intent) correction = null;
        if (end != null && (end.intent != intent || !end.range.active() || !runtime.interactive(root))) end = null;
    }

    void retain(Set<K> needed) {
        if (correction != null && heights.position(correction.key) >= 0) needed.add(correction.key);
        if (end != null) needed.add(end.key);
        if (reveal != null) needed.add(reveal.key);
    }

    void request(K key, boolean focus, Function<SceneNode, SceneNode> select) {
        end = null;
        reveal = new Reveal<>(key, focus, Objects.requireNonNull(select, "焦点目标准备"), runtime.focusRouter().captureRange());
    }

    void scrollRequested(int requested, SceneScrollViewport.Snapshot previous) {
        if (requested >= previous.maximum && previous.maximum > 0 && heights.size() > 0) {
            end = new End<>(heights.key(heights.size() - 1), runtime.scrollIntentRevision(root), runtime.focusRouter().captureRange());
            correction = null;
        } else end = null;
    }

    void correctEnd(SceneGeometryView.Entry view) {
        correction = null;
        runtime.correctScrollPosition(root, end.intent, view.scrollMaximum);
    }

    boolean finishEnd(SceneGeometryView geometry, SceneNode row, boolean omitted, boolean prepared, int offset) {
        SceneGeometryView.Entry view = geometry.get(root);
        SceneGeometryView.Entry placed = row == null ? null : geometry.get(row);
        boolean visible = placed == null ? omitted
                : (long)placed.box.y + placed.box.height + root.padding() <= (long)view.box.y + view.box.height;
        if (!prepared || !visible || offset != view.scrollMaximum) return false;
        end = null;
        return true;
    }

    boolean advanceReveal(SceneGeometryView geometry, SceneNode row, boolean omitted, boolean prepared) {
        // 折叠行已有正式的“不参与布局”事实，不会再产生可等待的目标几何。
        if (omitted) { reveal = null; return true; }
        if (row == null || geometry.get(row) == null) return false;
        Reveal<K> request = reveal;
        if (request.target == null && !issueReveal(request, row)) return true;
        SceneNode target = request.target.node();
        if (!request.target.active() || !runtime.interactive(target) || !contains(row, target)) {
            reveal = null;
            return true;
        }
        LayoutBox region = runtime.domain().readOnly(() -> SceneGeometry.revealRegion(target, root, geometry));
        LayoutBox window = SceneGeometry.revealWindow(root, geometry);
        boolean reached = region != null && (region.width == 0 || region.height == 0
                || window.width == 0 || window.height == 0
                || SceneGeometry.revealDelta(region.y, region.height, window.y, window.height) == 0);
        if (reached && prepared) { reveal = null; return true; }
        if (!reached) runtime.requestInputReveal(target);
        return false;
    }

    private boolean issueReveal(Reveal<K> request, SceneNode row) {
        SceneNode target = request.focus ? request.select.apply(row) : row;
        if (reveal != request) return false;
        if (target == null || request.focus && (!target.focusable() || !contains(row, target)
                || target.runtime() != runtime || !runtime.interactive(target))) {
            reveal = null;
            return false;
        }
        request.target = runtime.captureMount(target);
        if (request.focus) runtime.focusRouter().navigateTo(request.target);
        else runtime.requestInputReveal(target);
        // 选择器和同步失焦通知都可替换请求；旧推进不能读取或取消新请求。
        return reveal == request;
    }

    void correctAnchor() {
        if (correction == null || !heights.known(correction.key)) return;
        int position = heights.position(correction.key);
        if (position >= 0 && heights.height(position) > 0) {
            long desired = heights.start(position) + Math.min(Math.max(0, correction.within), heights.height(position) - 1L);
            if (desired <= Integer.MAX_VALUE) runtime.correctScrollPosition(root, correction.intent, (int)desired);
        }
        correction = null;
    }

    void remember(int offset) {
        lastIntent = runtime.scrollIntentRevision(root);
        if (correction != null) return;
        int position = heights.at(offset);
        K key = position < heights.size() ? heights.key(position) : null;
        observed = new Anchor<>(key, key == null ? 0 : offset - heights.start(position), runtime.scrollIntentRevision(root));
    }

    private static boolean contains(SceneNode row, SceneNode target) {
        for (SceneNode cursor = target; cursor != null; cursor = cursor.parent()) if (cursor == row) return true;
        return false;
    }
}
