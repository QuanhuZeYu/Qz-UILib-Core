package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 消费已准备几何，收集控件与备用内容；输入结算、布局发布及呈现代际仍归帧管线。 */
final class ScenePaintCollector {
    private final SceneLayoutEngine layout;
    private final club.heiqi.uilib.ui.scene.runtime.SceneRuntime runtime;
    private final Map<SceneNode, club.heiqi.uilib.ui.scene.runtime.PresencePaint> presenceSnapshots = new IdentityHashMap<>();
    private SceneNode capturePresenceRoot;
    Map<SceneNode, club.heiqi.uilib.ui.scene.runtime.PresencePaint> presenceSnapshots() { return presenceSnapshots; }
    void clear() { presenceSnapshots.clear(); capturePresenceRoot = null; }
    private final SceneTextMeasurer text;
    private long nextPaintNanos=Long.MAX_VALUE;
    long nextPaintNanos() { return nextPaintNanos; }

    ScenePaintCollector(club.heiqi.uilib.ui.scene.runtime.SceneRuntime runtime, SceneLayoutEngine layout, SceneTextMeasurer text) {
        this.runtime = runtime;
        this.layout = layout;
        this.text = text;
    }

    PaintPlan collect(SceneGeometryView geometry, long metricsRevision) {
        nextPaintNanos=Long.MAX_VALUE;
        PaintPlanBuilder content = new PaintPlanBuilder(), overlay = new PaintPlanBuilder();
        // 捕获标记覆盖整页所有组，避免备用modal后代在后续组无条件再绘制。
        Set<SceneNode> captured = identitySet();
        for (SceneGeometryView.Group group : geometry.groups()) {
            PaintLayers layers = collectEntries(group.entries, geometry, metricsRevision, captured);
            append(content, layers.content);
            append(overlay, layers.overlay);
        }
        capturePresence(geometry, metricsRevision);
        append(content, runtime.retainedPresencePaint(geometry));
        // 控件内容保持树序；overlay在全页内容之后保持原overlay树序。
        append(content, overlay.build());
        return content.build();
    }

    /** 每个输入仍捕获当前退场片段；未显示的整页输出可等真正读取或回放时生成。 */
    void capturePresence(SceneGeometryView geometry, long metricsRevision) {
        presenceSnapshots.clear();
        for (SceneNode node : runtime.presenceCaptureNodes()) {
            if (geometry.get(node) == null) continue;
            List<SceneGeometryView.Entry> subtree = new ArrayList<>();
            for (SceneGeometryView.Entry entry : geometry.sourceOrdered()) if (inSubtree(entry.node, node)) subtree.add(entry);
            SceneGeometryView intrinsic = intrinsicCapture(node, subtree, geometry);
            List<club.heiqi.uilib.ui.scene.runtime.PresencePaint.Part> parts = new ArrayList<>(), overlays = new ArrayList<>();
            capturePresenceRoot = node;
            try {
                Set<SceneNode> capturedPresence = identitySet();
                for (SceneGeometryView.Group group : geometry.groups()) {
                    List<SceneGeometryView.Entry> entries = new ArrayList<>();
                    for (SceneGeometryView.Entry entry : group.entries) if (inSubtree(entry.node, node)) entries.add(intrinsic.get(entry.node));
                    if (entries.isEmpty()) continue;
                    PaintLayers layer = collectEntries(entries, intrinsic, metricsRevision, capturedPresence, node.parent());
                    boolean escaped = group.layer != null && inSubtree(group.layer, node);
                    parts.add(new club.heiqi.uilib.ui.scene.runtime.PresencePaint.Part(layer.content, escaped));
                    overlays.add(new club.heiqi.uilib.ui.scene.runtime.PresencePaint.Part(layer.overlay, escaped));
                }
                parts.addAll(overlays);
                presenceSnapshots.put(node, new club.heiqi.uilib.ui.scene.runtime.PresencePaint(parts));
            }
            finally { capturePresenceRoot = null; }
        }
    }

    private static final class PaintLayers {
        final PaintPlan content, overlay;

        PaintLayers(PaintPlan content, PaintPlan overlay) {
            this.content = content;
            this.overlay = overlay;
        }
    }

    private PaintLayers collectEntries(
            List<SceneGeometryView.Entry> ordered, SceneGeometryView geometry,
            long metricsRevision, Set<SceneNode> captured) {
        return collectEntries(ordered, geometry, metricsRevision, captured, null);
    }

    private PaintLayers collectEntries(List<SceneGeometryView.Entry> ordered, SceneGeometryView geometry,
            long metricsRevision, Set<SceneNode> captured, SceneNode opacityStop) {
        PaintPlanBuilder content = new PaintPlanBuilder(), overlay = new PaintPlanBuilder();
        Map<SceneNode, PaintPlan> childOverlays = new IdentityHashMap<>();
        ArrayDeque<SceneNode> open = new ArrayDeque<>();
        for (SceneGeometryView.Entry candidate : ordered) {
            SceneGeometryView.Entry entry = geometry.world().get(candidate.node);
            if (entry == null) continue;
            if (insideCaptured(entry.node, captured)) continue;
            // 只消费已定位条目；按祖先关系关闭内容栈，不重走children补出幻影。
            while (!open.isEmpty() && !inSubtree(entry.node, open.peek()))
                paintAfterChildren(open.pop(), geometry, content, opacityStop);
            open.push(entry.node);
            SceneGeometryView local = geometry.asControl(entry.node);
            SceneGeometryView.Entry localEntry = local.get(entry.node);
            PaintPlan.Transformation affine = PaintPlan.transformation(entry.transform,
                    entry.transform.identity() && (entry.worldClip.rectangular() || entry.worldClip.unbounded())
                            ? club.heiqi.uilib.ui.scene.geometry.SceneClipRegion.UNBOUNDED : entry.worldClip);
            if (entry.node.control() == null) {
                PaintPlanBuilder own = new PaintPlanBuilder();
                paintUncontrolled(localEntry, own);
                append(content, transform(affine, opacity(entry.node, own.build(), opacityStop)));
                continue;
            }
            entry.node.control().preparePaint(entry.node, local, text, metricsRevision);
            PaintLayers fallback = captureFallback(localEntry, local, metricsRevision, captured);
            PaintPlanBuilder own = new PaintPlanBuilder();
            entry.node.control().paint(entry.node, local, own,
                    fallback == null ? null : fallback.content);
            PaintPlan ownPlan = own.build();
            PaintPlan.Presentation projection = projection(entry.node, opacityStop);
            append(content, transform(affine, projection.apply(ownPlan)));
            if (!entry.node.control().paintChildren(entry.node))
                for (SceneNode child : entry.node.children()) captured.add(child);
            if (fallback != null && !fallback.overlay.commands().isEmpty())
                childOverlays.put(entry.node, transform(affine, projection.apply(branchOverlay(ownPlan, fallback))));
        }
        while (!open.isEmpty()) paintAfterChildren(open.pop(), geometry, content, opacityStop);
        for (SceneGeometryView.Entry candidate : ordered) {
            SceneGeometryView.Entry entry = geometry.world().get(candidate.node);
            if (entry == null) continue;
            if (insideCaptured(entry.node, captured)) continue;
            if(entry.node.runtime()!=null)entry.node.runtime().paintTooltip(entry.node,overlay);
            if(entry.node.control()==null)continue;
            PaintPlanBuilder ownOverlay = new PaintPlanBuilder();
            entry.node.control().paintOverlay(entry.node, geometry.asControl(entry.node), ownOverlay, text);
            append(overlay, worldPlan(entry, opacity(entry.node, ownOverlay.build(), opacityStop)));
            PaintPlan childOverlay = childOverlays.get(entry.node);
            if (childOverlay != null) append(overlay, childOverlay);
            LayoutBox visible=entry.clip==null?entry.box:entry.box.intersect(entry.clip);
            if(visible.width>0&&visible.height>0)nextPaintNanos=Math.min(nextPaintNanos,entry.node.control().nextPaintNanos(entry.node));
        }
        return new PaintLayers(content.build(), overlay.build());
    }

    private PaintLayers captureFallback(
            SceneGeometryView.Entry entry, SceneGeometryView geometry,
            long metricsRevision, Set<SceneNode> captured) {
        SceneNode child = entry.node.control().paintFallbackChild(entry.node);
        if (child == null) return null;
        if (child.parent() != entry.node)
            throw new IllegalArgumentException("占位必须是控件直属逻辑子树");
        SceneLayoutEngine.rejectContentLayers(entry.node, child, "image fallback");
        captured.add(child);
        SceneGeometryView context = geometry;
        List<SceneGeometryView.Entry> source = geometry.sourceOrdered();
        boolean worldCapture = geometry.get(child) != null;
        if (!worldCapture) {
            SceneGeometry local = layout.captureSubtree(child, entry.layoutBox, geometry.world().viewport(), entry.transform, entry.worldClip);
            source = local.ordered();
            context = fallbackGeometry(local, geometry);
        }
        // 可见备用子树可能横跨modal组，沿当批来源顺序完整闭合内容栈。
        List<SceneGeometryView.Entry> entries = new ArrayList<>();
        for (SceneGeometryView.Entry candidate : source)
            if (inSubtree(candidate.node, child)) entries.add(candidate);
        // 每次备用捕获独立消费子树，不继承外层已标记的captured根。
        PaintLayers result = collectEntries(entries, context, metricsRevision, identitySet(), entry.node);
        // 可见和离屏捕获都保留世界事实，嵌入IMAGE前只归回图像frame一次。
        if (entry.inverse != null && !entry.transform.identity()) {
            PaintPlan.Transformation local = PaintPlan.transformation(entry.inverse, club.heiqi.uilib.ui.scene.geometry.SceneClipRegion.UNBOUNDED);
            return new PaintLayers(local.apply(result.content), local.apply(result.overlay));
        }
        return result;
    }

    /** 退场片段只冻结自身裁剪；祖先视口在回放时按当前几何裁决，不能随内容一起平移。 */
    private static SceneGeometryView intrinsicCapture(SceneNode root, List<SceneGeometryView.Entry> entries, SceneGeometryView current) {
        Map<SceneNode, SceneGeometryView.Entry> intrinsic = new IdentityHashMap<>();
        for (SceneGeometryView.Entry entry : entries) {
            club.heiqi.uilib.ui.scene.geometry.SceneClipRegion clip = club.heiqi.uilib.ui.scene.geometry.SceneClipRegion.UNBOUNDED;
            SceneNode boundary = current.layerBoundary(entry.node);
            for (SceneNode at = entry.node; at != null && at != root.parent(); at = at.parent()) {
                if (!at.clip() && !at.scrollable() && at.shapeClip() == null) { if (at == boundary) break; else continue; }
                SceneGeometryView.Entry ancestor = current.get(at);
                if (ancestor == null) continue;
                clip = clip.intersect(ancestor.ownWorldClip);
                if (at.scrollable()) clip = clip.intersect(club.heiqi.uilib.ui.scene.geometry.SceneClipRegion.rectangle(ancestor.layoutBox).transformed(ancestor.transform));
                if (at == boundary) break;
            }
            intrinsic.put(entry.node, entry.projected(entry.transform, clip));
        }
        return new SceneGeometryView() {
            private final Map<SceneNode, SceneGeometryView> views = new IdentityHashMap<>();
            private List<Entry> source;
            public SceneGeometryView asControl(SceneNode node) { return views.computeIfAbsent(node, key -> SceneGeometryView.super.asControl(key)); }
            public Entry get(SceneNode node) { Entry own = intrinsic.get(node); return own == null ? current.get(node) : own; }
            // 输入与遮挡仍用原批完整范围，只有本片段绘制的get使用内在裁剪。
            public List<Entry> ordered() { return current.ordered(); }
            public List<Entry> sourceOrdered() {
                if (source == null) {
                    List<Entry> entries = new ArrayList<>();
                    for (Entry entry : current.sourceOrdered()) entries.add(get(entry.node));
                    source = java.util.Collections.unmodifiableList(entries);
                }
                return source;
            }
            public LayoutBox viewport() { return current.viewport(); }
            public SceneNode activeModal() { return current.activeModal(); }
            public SceneNode layerBoundary(SceneNode node) { return current.layerBoundary(node); }
            public SceneNode logicalScope(SceneNode node) { return current.logicalScope(node); }
            public boolean layerSourceAvailable(SceneNode node) { return current.layerSourceAvailable(node); }
            public club.heiqi.uilib.ui.scene.control.SceneLayer.Policy layerPolicy(SceneNode node) { return current.layerPolicy(node); }
        };
    }

    private static SceneGeometryView fallbackGeometry(
            SceneGeometry local, SceneGeometryView geometry) {
        // get提供备用区域，ordered仍为主输入事实，不能以备用子集重新裁决hover。
        return new SceneGeometryView() {
            private final Map<SceneNode, SceneGeometryView> views = new IdentityHashMap<>();
            public SceneGeometryView asControl(SceneNode node) { return views.computeIfAbsent(node, key -> SceneGeometryView.super.asControl(key)); }
            @Override
            public Entry get(SceneNode node) {
                Entry value = local.get(node);
                return value == null ? geometry.world().get(node) : value;
            }

            @Override
            public List<Entry> ordered() { return geometry.world().ordered(); }

            @Override
            public List<Entry> sourceOrdered() { return local.sourceOrdered(); }

            @Override
            public LayoutBox viewport() { return geometry.world().viewport(); }
        };
    }

    private static PaintPlan branchOverlay(PaintPlan own, PaintLayers fallback) {
        for (PaintCommand command : own.commands())
            if (command.kind == PaintCommand.Kind.IMAGE
                    && command.pendingPaint == fallback.content
                    && command.failedPaint == fallback.content)
                return new PaintPlanBuilder().add(PaintCommand.imageBranchOverlay(
                        command, fallback.overlay, fallback.overlay)).build();
        // CPU没有内容时控件已无条件选择备用；有IMAGE时只投影同一次原图选择。
        return fallback.overlay;
    }

    private static void paintUncontrolled(SceneGeometryView.Entry entry, PaintPlanBuilder target) {
        if ((entry.node.background() >>> 24) != 0)
            target.add(PaintCommand.rect(entry.box, entry.clip, entry.node.background()));
        if (entry.text.lines().isEmpty()) return;
        int padding = entry.node.padding();
        target.add(PaintCommand.text(new LayoutBox(
                entry.box.x + padding, entry.box.y + padding,
                Math.max(0, entry.box.width - padding * 2),
                Math.max(0, entry.box.height - padding * 2)), entry.clip, entry.text));
    }

    private void paintAfterChildren(
            SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target, SceneNode opacityStop) {
        if (node.control() != null) {
            PaintPlanBuilder own = new PaintPlanBuilder();
            node.control().paintAfterChildren(node, geometry.asControl(node), own);
            SceneGeometryView.Entry entry = geometry.get(node);
            append(target, worldPlan(entry, opacity(node, own.build(), opacityStop)));
        }
    }

    private static PaintPlan transform(PaintPlan.Transformation transform, PaintPlan plan) { return transform.apply(plan); }
    private static PaintPlan worldPlan(SceneGeometryView.Entry entry, PaintPlan plan) {
        return plan.transformed(entry.transform, entry.transform.identity() && (entry.worldClip.rectangular() || entry.worldClip.unbounded())
                ? club.heiqi.uilib.ui.scene.geometry.SceneClipRegion.UNBOUNDED : entry.worldClip);
    }
    private PaintPlan opacity(SceneNode node, PaintPlan plan, SceneNode stop) { return projection(node, stop).apply(plan); }
    private PaintPlan.Presentation projection(SceneNode node, SceneNode stop) {
        float opacity = 1;
        for (SceneNode at = node; at != stop && at != null; at = at.parent())
            opacity *= at.opacity() * (at == capturePresenceRoot ? 1 : runtime.presenceOpacity(at));
        return PaintPlan.presentation(0, 0, opacity, null);
    }

    private static void append(PaintPlanBuilder target, PaintPlan plan) {
        for (PaintCommand command : plan.commands()) target.add(command);
    }

    private static Set<SceneNode> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
    }

    private static boolean insideCaptured(SceneNode node, Set<SceneNode> captured) {
        if (captured.isEmpty()) return false;
        for (SceneNode current = node; current != null; current = current.parent())
            if (captured.contains(current)) return true;
        return false;
    }

    private static boolean inSubtree(SceneNode node, SceneNode root) {
        for (SceneNode current = node; current != null; current = current.parent())
            if (current == root) return true;
        return false;
    }
}
