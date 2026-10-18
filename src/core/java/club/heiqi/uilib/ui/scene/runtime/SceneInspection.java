package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;
import club.heiqi.uilib.ui.scene.geometry.SceneMesh;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot;
import club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.*;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticId;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 只汇总原失效与执行点；默认关闭，无日志树、历史节点或另一路输入。 */
final class SceneInspection {
    private final SceneRuntime runtime;
    private Observation source;
    private club.heiqi.uilib.ui.reactive.Owner sourceScope;
    private boolean recording;
    private int depth;
    private final Set<Invalidation> pending = new LinkedHashSet<>(), preparing = new LinkedHashSet<>();
    private long measured, arranged, collected;
    private Preparation latest = Preparation.EMPTY;
    SceneInspection(SceneRuntime runtime) { this.runtime = runtime; }
    void recording(ReadableSignal<Boolean> enabled) {
        if (sourceScope != null) sourceScope.close();
        sourceScope = runtime.inspectionOwner().child();
        source = runtime.domain().observe(sourceScope, () -> {
            recording = runtime.domain().readOnly(() -> Boolean.TRUE.equals(enabled.get()));
            if (!recording) clear();
        }, () -> { if (source != null) source.refresh(); });
        source.refresh();
    }
    void record(SceneNode node, Category category) {
        if (!recording) return;
        SceneSemanticId id = runtime.inspectionId(node);
        (depth == 0 ? pending : preparing).add(new Invalidation(id, id == null ? null : node.id(), category));
    }
    void begin() {
        if (depth++ != 0 || !recording) return;
        preparing.addAll(pending); pending.clear(); measured = arranged = collected = 0;
    }
    void measured() { if (recording) measured++; }
    void arranged() { if (recording) arranged++; }
    void collected() { if (recording) collected++; }
    void end(boolean success, long generation) {
        if (--depth != 0 || !recording) return;
        if (!success) pending.addAll(preparing);
        else if (!preparing.isEmpty() || measured != 0 || arranged != 0 || collected != 0)
            latest = new Preparation(generation, new ArrayList<>(preparing), measured, arranged, collected);
        preparing.clear(); measured = arranged = collected = 0;
    }
    SceneInspectionSnapshot snapshot(ScenePresentation presentation, long metrics) {
        SceneGeometryView geometry = presentation.geometry;
        List<Entry> entries = new ArrayList<>();
        for (SceneNode node : runtime.roots()) append(node, geometry, presentation.semantics, entries);
        ScenePointerRouter pointer = runtime.pointerRouter();
        SceneNode pressed = pointer.inspectionPressed();
        if (pressed == null) pressed = runtime.keyboardPressedTarget().get();
        return new SceneInspectionSnapshot(Status.READY, presentation.generation, runtime.revision(), runtime.geometryRevision(), metrics,
                entries, runtime.inspectionId(runtime.focusRouter().focused().get()), runtime.inspectionId(pointer.inspectionHovered()),
                runtime.inspectionId(pressed), runtime.inspectionId(pointer.inspectionCaptured()), runtime.inspectionId(runtime.activeModal()),
                pointer.inspectionButtons(), recording, latest, new ArrayList<>(pending));
    }
    private void append(SceneNode node, SceneGeometryView geometry, SceneSemanticSnapshot semantics, List<Entry> entries) {
        SceneSemanticId id = runtime.inspectionId(node); if (id == null) return;
        SceneGeometryView.Entry entry = geometry.get(node); SceneSemanticSnapshot.Entry semantic = semantics.get(id);
        entries.add(new Entry(id, node.id(), runtime.inspectionId(node.parent()), runtime.inspectionId(geometry.layerBoundary(node)),
                runtime.inspectionId(geometry.logicalScope(node)), node.visible(), node.enabled(), runtime.interactive(node),
                node.control() == null ? "" : node.control().getClass().getName(), semantic == null ? "" : java.util.Objects.toString(semantic.values.name, ""),
                semantic == null ? null : semantic.values.role, entry == null ? null : entry.layoutBox, entry == null ? null : entry.worldBounds,
                entry == null ? SceneTransform.IDENTITY : entry.transform, entry == null ? SceneClipRegion.EMPTY : entry.worldClip,
                entry == null ? SceneMesh.EMPTY : geometry.visibleRegion(node), entry == null ? runtime.scrollOffset(node) : entry.scrollOffset,
                entry == null ? 0 : entry.scrollMaximum, node.style(), node.padding(), node.gap(), node.styleSource(), node.paddingSource(), node.gapSource(),
                node.control() == null ? -1 : node.control().inspectionLogicalItemCount(node),
                node.control() == null ? -1 : node.control().inspectionInstantiatedItemCount(node)));
        for (SceneNode child : node.children()) append(child, geometry, semantics, entries);
        for (SceneNode decoration : runtime.scrollDecorations(node)) append(decoration, geometry, semantics, entries);
    }
    private void clear() { pending.clear(); preparing.clear(); latest = Preparation.EMPTY; measured = arranged = collected = 0; }
    void close() { if (sourceScope != null) { sourceScope.close(); sourceScope = null; } source = null; recording = false; clear(); }
}
