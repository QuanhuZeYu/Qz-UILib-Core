package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 本次正式几何的局部值片段；只复用绘制结果，不保存另一份布局或推进控件。 */
final class ScenePaintFragments {
    static final class Fragment {
        PaintPlan content, childOverlay, after, overlay, tooltip;
        SceneNode fallbackChild;
        boolean paintChildren;
    }
    static final class Capture {
        final SceneNode root;
        final List<SceneGeometryView.Entry> source = new ArrayList<>();
        final Map<SceneGeometryView.Group, List<SceneGeometryView.Entry>> groups = new LinkedHashMap<>();
        Capture(SceneNode root) { this.root = root; }
    }
    final SceneGeometryView geometry;
    final List<SceneGeometryView.Group> groups;
    final List<Capture> captures = new ArrayList<>();
    final long revision, metrics;
    boolean presenceCaptured, mainPlanCollected;
    private final Map<SceneNode, Fragment> fragments = new IdentityHashMap<>();
    private final Map<SceneNode, List<Capture>> owners = new IdentityHashMap<>();
    private final Map<SceneNode, Capture> roots = new IdentityHashMap<>();
    private final Map<SceneNode, SceneGeometryView> locals = new IdentityHashMap<>();

    ScenePaintFragments(SceneGeometryView geometry, long revision, long metrics, List<SceneNode> candidates) {
        this.geometry = geometry; this.revision = revision; this.metrics = metrics; groups = geometry.groups();
        for (SceneNode node : candidates) if (geometry.get(node) != null) {
            Capture capture = new Capture(node); roots.put(node, capture); captures.add(capture);
        }
        // 每个页条目只按其真实祖先归属分发；嵌套候选的成本对应实际要保留的片段大小。
        if (captures.isEmpty()) return;
        for (SceneGeometryView.Entry entry : geometry.sourceOrdered())
            for (Capture capture : owners(entry.node)) capture.source.add(entry);
        for (SceneGeometryView.Group group : groups)
            for (SceneGeometryView.Entry entry : group.entries)
                for (Capture capture : owners(entry.node))
                    capture.groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(entry);
    }
    private List<Capture> owners(SceneNode node) {
        if (node == null) return Collections.emptyList();
        List<Capture> known = owners.get(node);
        if (known != null) return known;
        List<Capture> inherited = owners(node.parent());
        Capture own = roots.get(node);
        if (own == null) known = inherited;
        else { known = new ArrayList<>(inherited); known.add(own); }
        owners.put(node, known); return known;
    }
    Fragment get(SceneNode node) { return fragments.computeIfAbsent(node, ignored -> new Fragment()); }

    SceneGeometryView local(SceneNode node, SceneGeometryView fallback) {
        SceneGeometryView known = locals.get(node);
        if (known != null) return known;
        SceneGeometryView world = geometry.get(node) == null ? fallback.world() : geometry.world();
        SceneGeometryView local = world.asControl(node);
        SceneGeometryView.Entry entry = world.get(node);
        SceneGeometryView.Entry own = entry.projected(entry.transform, ownClip(entry)).in(entry.inverse, true);
        // 控件只捕获原始局部画面；外层clip在组合主绘/退场时施加，不能烘焙进片段。
        // ordered/world仍是当批完整输入事实，不能用备用或内在裁剪重判hover。
        SceneGeometryView result = new SceneGeometryView() {
            public Entry get(SceneNode candidate) { return candidate == node ? own : local.get(candidate); }
            public SceneGeometryView world() { return world; }
            public club.heiqi.uilib.ui.scene.geometry.SceneTransform coordinateTransform() { return local.coordinateTransform(); }
            public SceneGeometryView asControl(SceneNode candidate) { return candidate == node ? this : world.asControl(candidate); }
            public List<Entry> ordered() { return local.ordered(); }
            public List<Entry> sourceOrdered() { return local.sourceOrdered(); }
            public List<Group> groups() { return local.groups(); }
            public LayoutBox viewport() { return local.viewport(); }
            public SceneNode activeModal() { return local.activeModal(); }
            public SceneNode layerBoundary(SceneNode candidate) { return local.layerBoundary(candidate); }
            public SceneNode logicalScope(SceneNode candidate) { return local.logicalScope(candidate); }
            public boolean layerSourceAvailable(SceneNode candidate) { return local.layerSourceAvailable(candidate); }
            public club.heiqi.uilib.ui.scene.control.SceneLayer.Policy layerPolicy(SceneNode candidate) { return local.layerPolicy(candidate); }
        };
        locals.put(node, result); return result;
    }
    static SceneClipRegion ownClip(SceneGeometryView.Entry entry) {
        SceneClipRegion clip = entry.ownWorldClip;
        return entry.node.scrollable() ? clip.intersect(SceneClipRegion.rectangle(entry.layoutBox).transformed(entry.transform)) : clip;
    }
}
