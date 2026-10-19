package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/** 节点代际与所属约束结果共同存活；排列可跨批复用，历史按本次实际请求收敛。 */
final class SceneMeasureDependencies {
    private static final class Stamp {
        final long revision;
        final Size width, height;
        final Map<Long, int[]> measurements = new HashMap<>();
        final Set<Long> activeRequests = new HashSet<>();
        Stamp(long revision, Size width, Size height) { this.revision = revision; this.width = width; this.height = height; }
        boolean matches(long next, Size w, Size h) { return revision == next && same(width, w) && same(height, h); }
    }
    private final Map<SceneNode, Stamp> stamps = new IdentityHashMap<>();
    private final Set<Stamp> active = Collections.newSetFromMap(new IdentityHashMap<Stamp, Boolean>());
    void clear() { stamps.clear(); active.clear(); }
    void resetMeasurements() {
        for (Stamp stamp : stamps.values()) {
            stamp.measurements.clear();
            stamp.activeRequests.clear();
        }
        active.clear();
    }
    boolean update(Set<SceneNode> mounted, SceneLayoutPresentation presentation, ToLongFunction<SceneNode> revisions) {
        boolean changed = stamps.keySet().retainAll(mounted);
        for (SceneNode node : mounted) {
            long revision = revisions.applyAsLong(node);
            Size width = presentation == null ? node.width() : presentation.size(node, SceneNode.Axis.ROW);
            Size height = presentation == null ? node.height() : presentation.size(node, SceneNode.Axis.COLUMN);
            Stamp previous = stamps.get(node);
            if (previous == null || !previous.matches(revision, width, height)) {
                changed = true;
                stamps.put(node, new Stamp(revision, width, height));
            }
        }
        return changed;
    }
    int[] request(SceneNode node, int width, int height) {
        Stamp stamp = stamps.computeIfAbsent(node, key -> new Stamp(0, node.width(), node.height()));
        if (active.add(stamp)) stamp.activeRequests.clear();
        long key = constraintKey(width, height);
        stamp.activeRequests.add(key);
        return stamp.measurements.get(key);
    }
    void remember(SceneNode node, int width, int height, int[] measured) {
        stamps.get(node).measurements.put(constraintKey(width, height), measured);
    }
    int[] positioned(SceneNode node, int width, int height) {
        Stamp stamp = stamps.get(node);
        return stamp == null ? null : stamp.measurements.get(constraintKey(width, height));
    }
    void endRequests() {
        // 父测量命中会省略子请求；只结算实际访问过的节点，不能误删其余仍有效的子度量。
        // 当前请求保留到下一次定位或失效，纯绘制投影仍可读取本次已定位的全部约束。
        for (Stamp stamp : active) {
            stamp.measurements.keySet().retainAll(stamp.activeRequests);
            stamp.activeRequests.clear();
        }
        active.clear();
    }
    private static long constraintKey(int width, int height) {
        return ((long) width << 32) | (height & 0xffffffffL);
    }
    private static boolean same(Size a, Size b) {
        return a == b || a.mode == b.mode && a.value == b.value && a.min == b.min && a.max == b.max;
    }
}
