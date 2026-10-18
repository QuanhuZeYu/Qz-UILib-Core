package club.heiqi.uilib.ui.scene.control;

/** 同类控件的排布密度；坐标仍为原始scene像素。 */
public enum SceneDensity {
    COMPACT(.9f, .75f), COMFORTABLE(1, 1);
    final float textScale, spacingScale;
    SceneDensity(float textScale, float spacingScale) { this.textScale = textScale; this.spacingScale = spacingScale; }
}
