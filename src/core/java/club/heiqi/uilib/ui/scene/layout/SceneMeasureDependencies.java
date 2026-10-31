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
        boolean matches(long next, Size w, Size h) { return revision == next && width.equals(w) && height.equals(h); }
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
    void update(Set<SceneNode> mounted, SceneLayoutPresentation presentation, ToLongFunction<SceneNode> revisions) {
        stamps.keySet().retainAll(mounted);
        Set<SceneNode> changed = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        for (SceneNode node : mounted) {
            long revision = revisions.applyAsLong(node);
            Size width = presentation == null ? node.width() : presentation.size(node, SceneNode.Axis.ROW);
            Size height = presentation == null ? node.height() : presentation.size(node, SceneNode.Axis.COLUMN);
            Stamp previous = stamps.get(node);
            if (previous == null || !previous.matches(revision, width, height)) {
                changed.add(node);
                stamps.put(node, new Stamp(revision, width, height));
            }
        }
        // 呈现尺寸可在尺寸动效中变化而不改逻辑测量代际，祖先的自然测量仍依赖它。
        // 同批只结束这些祖先的请求，其他分支继续按原约束复用。
        Set<SceneNode> invalidated = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        for (SceneNode source : changed) {
            for (SceneNode node = source; node != null && invalidated.add(node); node = node.parent()) {
                Stamp stamp = stamps.get(node);
                if (stamp == null) break;
                stamp.measurements.clear();
                stamp.activeRequests.clear();
                active.remove(stamp);
            }
        }
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
        // 父测量命中时复用子度量，本次只收敛实际访问节点的请求。
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
}
