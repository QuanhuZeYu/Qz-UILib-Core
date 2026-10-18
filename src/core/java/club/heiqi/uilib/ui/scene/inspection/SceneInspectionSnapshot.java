package club.heiqi.uilib.ui.scene.inspection;

import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;
import club.heiqi.uilib.ui.scene.geometry.SceneMesh;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticId;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 正式准备边界的不可变检查值；挂载关系沿原opaque ID，不持有活节点或执行入口。 */
public final class SceneInspectionSnapshot {
    public enum Status { READY, CLOSED }
    public enum Source { AUTHOR, CONTROL_DEFAULT, INITIAL }
    public enum Category { BINDING, MEASURE, ARRANGE, RANGE, PAINT, VIEWPORT, RESOURCE, INPUT }
    public static final class Invalidation {
        public final SceneSemanticId id;
        public final UUID nodeId;
        public final Category category;
        public Invalidation(SceneSemanticId id, UUID nodeId, Category category) { this.id = id; this.nodeId = nodeId; this.category = category; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Invalidation)) return false;
            Invalidation value = (Invalidation) other;
            return java.util.Objects.equals(id, value.id) && category == value.category;
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, category); }
    }
    public static final class Preparation {
        public static final Preparation EMPTY = new Preparation(0, Collections.emptyList(), 0, 0, 0);
        public final long generation, measureCount, arrangeCount, paintCollectCount;
        public final List<Invalidation> reasons;
        public Preparation(long generation, List<Invalidation> reasons, long measured, long arranged, long collected) {
            this.generation = generation; this.reasons = immutable(reasons);
            measureCount = measured; arrangeCount = arranged; paintCollectCount = collected;
        }
    }
    public static final class Entry {
        public final SceneSemanticId id, parent, layer, logicalScope;
        public final UUID nodeId;
        public final boolean visible, enabled, interactive;
        public final String controlType, name;
        public final SceneSemantics.Role role;
        public final LayoutBox layoutBox, worldBounds;
        public final SceneTransform transform;
        public final SceneClipRegion effectiveClip;
        public final SceneMesh visibleMesh;
        public final int scrollOffset, scrollMaximum, padding, gap;
        public final TextStyle style;
        public final Source styleSource, paddingSource, gapSource;
        /** -1表示普通控件；未实例化逻辑项不会为检查而构造。 */
        public final int logicalItemCount, instantiatedItemCount;
        public Entry(SceneSemanticId id, UUID nodeId, SceneSemanticId parent, SceneSemanticId layer, SceneSemanticId scope,
                boolean visible, boolean enabled, boolean interactive, String controlType, String name, SceneSemantics.Role role,
                LayoutBox layoutBox, LayoutBox worldBounds, SceneTransform transform, SceneClipRegion clip, SceneMesh visibleMesh,
                int scrollOffset, int scrollMaximum, TextStyle style, int padding, int gap,
                Source styleSource, Source paddingSource, Source gapSource, int logicalItems, int instantiatedItems) {
            this.id = id; this.nodeId = nodeId; this.parent = parent; this.layer = layer; logicalScope = scope;
            this.visible = visible; this.enabled = enabled; this.interactive = interactive; this.controlType = controlType;
            this.name = name; this.role = role; this.layoutBox = layoutBox; this.worldBounds = worldBounds; this.transform = transform;
            effectiveClip = clip; this.visibleMesh = visibleMesh; this.scrollOffset = scrollOffset; this.scrollMaximum = scrollMaximum;
            this.style = style; this.padding = padding; this.gap = gap; this.styleSource = styleSource;
            this.paddingSource = paddingSource; this.gapSource = gapSource; logicalItemCount = logicalItems; instantiatedItemCount = instantiatedItems;
        }
    }
    public static final SceneInspectionSnapshot CLOSED = new SceneInspectionSnapshot(Status.CLOSED, 0, 0, 0, 0,
            Collections.emptyList(), null, null, null, null, null, Collections.emptyList(), false, Preparation.EMPTY, Collections.emptyList());
    public final Status status;
    public final long generation, revision, geometryRevision, metricsRevision;
    public final List<Entry> entries;
    public final SceneSemanticId focused, hovered, pressed, captured, currentModal;
    public final List<Integer> buttons;
    public final boolean recording;
    public final Preparation latest;
    public final List<Invalidation> pending;
    private final Map<SceneSemanticId, Entry> index;
    public SceneInspectionSnapshot(Status status, long generation, long revision, long geometryRevision, long metricsRevision,
            List<Entry> entries, SceneSemanticId focused, SceneSemanticId hovered, SceneSemanticId pressed, SceneSemanticId captured,
            SceneSemanticId modal, List<Integer> buttons, boolean recording, Preparation latest, List<Invalidation> pending) {
        this.status = status; this.generation = generation; this.revision = revision; this.geometryRevision = geometryRevision;
        this.metricsRevision = metricsRevision; this.entries = immutable(entries); this.focused = focused; this.hovered = hovered;
        this.pressed = pressed; this.captured = captured; currentModal = modal; this.buttons = immutable(buttons);
        this.recording = recording; this.latest = latest; this.pending = immutable(pending);
        Map<SceneSemanticId, Entry> map = new LinkedHashMap<>(); for (Entry entry : entries) map.put(entry.id, entry);
        index = Collections.unmodifiableMap(map);
    }
    public Entry get(SceneSemanticId id) { return index.get(id); }
    private static <T> List<T> immutable(List<T> values) { return Collections.unmodifiableList(new ArrayList<>(values)); }
}
