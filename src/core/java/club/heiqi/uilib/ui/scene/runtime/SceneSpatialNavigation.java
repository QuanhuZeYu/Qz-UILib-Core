package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.input.SceneFocusGeometry;
import club.heiqi.uilib.ui.scene.input.SceneFocusTraversal;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 唯一焦点路由拥有的一次空间搜索；复合边界END继续候选，CANCELLED结束原联系。 */
final class SceneSpatialNavigation implements SceneFocusTraversal.Cursor {
    private final SceneRuntime runtime;
    private final List<SceneNode> roots, enclosing = new ArrayList<>();
    private final MountHandle source;
    private final SceneNavigationEvent direction;
    private final Set<SceneNode> exhausted = new HashSet<>(), ordinary = new HashSet<>();
    private int inside;
    private SceneFocusTraversal.DirectionalCursor cursor;
    private MountHandle boundary;
    private boolean closed;

    SceneSpatialNavigation(SceneRuntime runtime, List<SceneNode> roots, MountHandle source, SceneNavigationEvent direction) {
        this.runtime = runtime; this.roots = new ArrayList<>(roots); this.source = source; this.direction = direction;
        for (SceneNode node = source.node(); node != null; node = node.parent())
            if (traversal(node) != null) enclosing.add(node);
    }

    private SceneFocusTraversal traversal(SceneNode node) {
        return node.control() == null ? null : node.control().focusTraversal(node);
    }

    @Override public SceneFocusTraversal.Result prepare() {
        if (closed || !source.active() || !runtime.interactive(source.node())) return SceneFocusTraversal.Result.cancelled();
        SceneGeometryView geometry = runtime.navigationGeometry();
        if (geometry == null) return SceneFocusTraversal.Result.waiting();
        SceneGeometryView.Entry entry = geometry.get(source.node());
        if (entry == null) return SceneFocusTraversal.Result.cancelled();
        LayoutBox origin = entry.box;
        while (true) {
            if (cursor != null) {
                if (!boundary.active() || !runtime.interactive(boundary.node())) return SceneFocusTraversal.Result.cancelled();
                SceneFocusTraversal.Result result = cursor.prepare(geometry);
                if (closed) return SceneFocusTraversal.Result.cancelled();
                if (result.status == SceneFocusTraversal.Result.Status.WAITING
                        || result.status == SceneFocusTraversal.Result.Status.CANCELLED) return result;
                if (result.status == SceneFocusTraversal.Result.Status.READY) {
                    SceneNode target = result.target;
                    if (target.runtime() != runtime || !target.focusable() || !runtime.interactive(target)
                            || !contains(boundary.node(), target)) return SceneFocusTraversal.Result.cancelled();
                    if (geometry.get(target) == null)
                        throw new IllegalStateException("方向目标准备完成但无当批几何: " + target.id());
                    return result;
                }
                exhausted.add(boundary.node()); cursor.close(); cursor = null; boundary = null;
            }
            if (inside < enclosing.size()) {
                SceneNode node = enclosing.get(inside++);
                if (!begin(node, geometry, origin)) ordinary.add(node);
                continue;
            }
            Map<SceneNode, SceneFocusTraversal> candidates = new LinkedHashMap<>();
            for (SceneNode root : roots) collect(root, candidates);
            List<SceneNode> ordered = SceneFocusGeometry.ordered(geometry, origin, direction, candidates.keySet());
            if (ordered.isEmpty()) return SceneFocusTraversal.Result.end();
            SceneNode next = ordered.get(0);
            if (candidates.get(next) == null) return SceneFocusTraversal.Result.ready(next);
            if (!begin(next, geometry, origin)) ordinary.add(next);
        }
    }

    private boolean begin(SceneNode node, SceneGeometryView geometry, LayoutBox origin) {
        SceneFocusTraversal traversal = traversal(node);
        if (traversal == null) return false;
        SceneFocusTraversal.DirectionalCursor next = traversal.beginDirection(source.node(), direction, origin);
        if (next == null) return false;
        if (closed) { next.close(); return true; }
        boundary = runtime.captureMount(node); cursor = next;
        return true;
    }

    private void collect(SceneNode node, Map<SceneNode, SceneFocusTraversal> result) {
        if (exhausted.contains(node) || !runtime.locallyInteractive(node)) return;
        boolean focusable = node != source.node() && node.focusable() && runtime.interactive(node);
        if (focusable) result.put(node, null);
        SceneFocusTraversal traversal = traversal(node);
        if (traversal != null && !ordinary.contains(node)) {
            if (!focusable && runtime.interactive(node)) result.put(node, traversal);
        } else for (SceneNode child : node.children()) collect(child, result);
    }

    private static boolean contains(SceneNode root, SceneNode node) {
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (cursor == root) return true;
        return false;
    }

    @Override public void close() {
        closed = true;
        try { if (cursor != null) cursor.close(); }
        finally { cursor = null; boundary = null; roots.clear(); enclosing.clear(); exhausted.clear(); ordinary.clear(); }
    }
}
