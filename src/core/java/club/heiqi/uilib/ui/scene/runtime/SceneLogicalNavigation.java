package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.input.SceneFocusTraversal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.List;

/** 唯一路由拥有的一次逻辑遍历；仅暂存现存树的普通目标及复合边界。 */
final class SceneLogicalNavigation implements SceneFocusTraversal.Cursor {
    private static final class Target {
        final SceneNode node;
        final SceneFocusTraversal traversal;
        Target(SceneNode node, SceneFocusTraversal traversal) { this.node = node; this.traversal = traversal; }
    }
    private final SceneRuntime runtime;
    private final List<Target> targets = new ArrayList<>();
    private final boolean backward, cyclic;
    private final int limit;
    private int index, visited;
    private SceneNode from;
    private SceneFocusTraversal.Cursor cursor;
    private SceneNode boundary;

    SceneLogicalNavigation(SceneRuntime runtime, List<SceneNode> roots, SceneNode current, boolean backward) {
        this(runtime, roots, current, backward, true);
    }

    SceneLogicalNavigation(SceneRuntime runtime, List<SceneNode> roots, SceneNode current, boolean backward, boolean cyclic) {
        this.runtime = runtime; this.backward = backward; this.cyclic = cyclic;
        for (SceneNode root : roots) collect(root);
        int currentIndex = -1;
        boolean inside = false;
        if (current != null) for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (target.traversal == null && target.node == current) { currentIndex = i; break; }
            if (target.traversal != null && target.node != current && contains(target.node, current)) {
                currentIndex = i; inside = true; from = current; break;
            }
        }
        if (inside) index = currentIndex;
        else if (currentIndex < 0) index = backward ? targets.size() - 1 : 0;
        else index = wrap(currentIndex + (backward ? -1 : 1));
        limit = targets.size() + (inside ? 1 : 0);
    }

    private void collect(SceneNode node) {
        if (!runtime.locallyInteractive(node)) return;
        if (node.focusable() && runtime.interactive(node)) targets.add(new Target(node, null));
        SceneFocusTraversal traversal = node.control() == null ? null : node.control().focusTraversal(node);
        if (traversal != null) targets.add(new Target(node, traversal));
        else for (SceneNode child : node.children()) collect(child);
    }

    private static boolean contains(SceneNode root, SceneNode node) {
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (cursor == root) return true;
        return false;
    }

    @Override public SceneFocusTraversal.Result prepare() {
        while (visited < limit && index >= 0 && index < targets.size()) {
            Target target = targets.get(index);
            if (target.node.runtime() != runtime || !runtime.interactive(target.node)) { advance(); continue; }
            if (target.traversal == null) {
                if (target.node.focusable()) return SceneFocusTraversal.Result.ready(target.node);
                advance(); continue;
            }
            if (cursor == null) {
                boundary = target.node;
                cursor = target.traversal.begin(from, backward);
                from = null;
            }
            SceneFocusTraversal.Result result = cursor.prepare();
            if (result.status == SceneFocusTraversal.Result.Status.WAITING
                    || result.status == SceneFocusTraversal.Result.Status.CANCELLED) return result;
            if (result.status == SceneFocusTraversal.Result.Status.READY) {
                SceneNode ready = result.target;
                if (ready.runtime() == runtime && ready.focusable() && runtime.interactive(ready)
                        && contains(boundary, ready)) return result;
            }
            advance();
        }
        return SceneFocusTraversal.Result.end();
    }

    private int wrap(int value) {
        if (!cyclic || targets.isEmpty()) return value;
        return (value + targets.size()) % targets.size();
    }

    private void advance() {
        if (cursor != null) cursor.close();
        cursor = null; boundary = null; from = null;
        index = wrap(index + (backward ? -1 : 1)); visited++;
    }

    @Override public void close() {
        if (cursor != null) cursor.close();
        cursor = null; targets.clear();
    }
}
