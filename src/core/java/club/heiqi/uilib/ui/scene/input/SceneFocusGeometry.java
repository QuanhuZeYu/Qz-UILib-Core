package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.List;

/** 同批正式矩形的方向排序。资格由唯一路由或复合控件核验，不保存静态邻接表。 */
public final class SceneFocusGeometry {
    private SceneFocusGeometry() { }
    private static final class Candidate {
        final SceneNode node;
        final long beam, gap, cross, forward;
        Candidate(SceneNode node, LayoutBox from, LayoutBox to, SceneNavigationEvent direction) {
            this.node = node;
            boolean vertical = direction == SceneNavigationEvent.UP || direction == SceneNavigationEvent.DOWN;
            boolean positive = direction == SceneNavigationEvent.RIGHT || direction == SceneNavigationEvent.DOWN;
            long a = vertical ? from.y : from.x, b = vertical ? to.y : to.x;
            long as = vertical ? from.height : from.width, bs = vertical ? to.height : to.width;
            long ca = vertical ? from.x : from.y, cb = vertical ? to.x : to.y;
            long cas = vertical ? from.width : from.height, cbs = vertical ? to.width : to.height;
            forward = (2 * b + bs - 2 * a - as) * (positive ? 1 : -1);
            beam = Math.min(ca + cas, cb + cbs) > Math.max(ca, cb) ? 0 : 1;
            gap = Math.max(0, positive ? b - a - as : a - b - bs);
            cross = Math.abs(2 * cb + cbs - 2 * ca - cas);
        }
        int compare(Candidate other) {
            int order = Long.compare(beam, other.beam);
            if (order == 0) order = Long.compare(gap, other.gap);
            if (order == 0) order = Long.compare(cross, other.cross);
            if (order == 0) order = Long.compare(forward, other.forward);
            return order;
        }
    }

    /** 输入顺序为逻辑声明顺序；稳定排序保留完全等距的先后，裁剪不删除滚动正文。 */
    public static List<SceneNode> ordered(SceneGeometryView geometry, LayoutBox origin,
            SceneNavigationEvent direction, Iterable<SceneNode> nodes) {
        if (!direction.directional()) throw new IllegalArgumentException("需要四方向意图");
        List<Candidate> candidates = new ArrayList<>();
        for (SceneNode node : nodes) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || entry.box.width <= 0 || entry.box.height <= 0) continue;
            Candidate candidate = new Candidate(node, origin, entry.box, direction);
            if (candidate.forward > 0) candidates.add(candidate);
        }
        candidates.sort(Candidate::compare);
        List<SceneNode> result = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) result.add(candidate.node);
        return result;
    }
}
