package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 同一代不可变几何与绘制计划；下一次呈现不改动既有对象。 */
public final class ScenePresentation {
    public final long generation;
    public final SceneGeometry geometry;
    public final PaintPlan paint;
    public final club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot semantics;

    public ScenePresentation(long generation, SceneGeometry geometry, PaintPlan paint) {
        this(generation, geometry, paint, club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot.EMPTY);
    }

    public ScenePresentation(long generation, SceneGeometry geometry, PaintPlan paint, club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot semantics) {
        this.semantics = java.util.Objects.requireNonNull(semantics, "语义快照");
        this.generation = generation;
        this.geometry = geometry;
        this.paint = paint;
    }
}
