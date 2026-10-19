package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/** 排列可跨批复用内容度量；实际内容代际或当前分配尺寸改变才结束旧度量。 */
final class SceneMeasureDependencies {
    private static final class Stamp {
        final long revision;
        final Size width, height;
        Stamp(long revision, Size width, Size height) { this.revision = revision; this.width = width; this.height = height; }
        boolean matches(long next, Size w, Size h) { return revision == next && same(width, w) && same(height, h); }
    }
    private final Map<SceneNode, Stamp> stamps = new IdentityHashMap<>();
    void clear() { stamps.clear(); }
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
    private static boolean same(Size a, Size b) {
        return a == b || a.mode == b.mode && a.value == b.value && a.min == b.min && a.max == b.max;
    }
}
