package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.List;

/** 同批正式矩形的方向排序；采用方向规格所引用的Norigin corners有限相邻偏好。 */
public final class SceneFocusGeometry {
    private SceneFocusGeometry() { }
    private static final class Candidate {
        final SceneNode node;
        final long priority, forward;
        Candidate(SceneNode node, LayoutBox from, LayoutBox to, SceneNavigationEvent direction) {
            this.node = node;
            boolean vertical = direction == SceneNavigationEvent.UP || direction == SceneNavigationEvent.DOWN;
            boolean positive = direction == SceneNavigationEvent.RIGHT || direction == SceneNavigationEvent.DOWN;
            long a = vertical ? from.y : from.x, b = vertical ? to.y : to.x;
            long as = vertical ? from.height : from.width, bs = vertical ? to.height : to.width;
            long ca = vertical ? from.x : from.y, cb = vertical ? to.x : to.y;
            long cas = vertical ? from.width : from.height, cbs = vertical ? to.width : to.height;
            forward = (2 * b + bs - 2 * a - as) * (positive ? 1 : -1);
            long overlap = Math.max(0, Math.min(ca + cas, cb + cbs) - Math.max(ca, cb));
            boolean adjacent = overlap > 0 && overlap * 5 >= cas;
            long main = Math.abs(positive ? b - a - as : a - b - bs);
            long cross = Math.min(Math.min(Math.abs(cb - ca), Math.abs(cb - ca - cas)),
                    Math.min(Math.abs(cb + cbs - ca), Math.abs(cb + cbs - ca - cas)));
            // 参考先对相邻/斜向分别选主副距离，再给予相邻5倍权重。
            // 统一乘5后以long精确比较；没有任意交集压倒所有距离的无限优先级。
            priority = adjacent ? main * 5 + cross + 1 : (cross * 5 + main + 1) * 5;
        }
    }

    /** 输入为逻辑顺序，稳定排序保留等分先后；裁剪不删除滚动正文。 */
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
        candidates.sort((left, right) -> Long.compare(left.priority, right.priority));
        List<SceneNode> result = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) result.add(candidate.node);
        return result;
    }
}
