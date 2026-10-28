package club.heiqi.uilib.ui.scene.control;

/** 声明控件局部尺寸角色，外层分配沿 Size 声明。 */
public enum SceneControlSize {
    SMALL(.9f, .8f), REGULAR(1, 1), LARGE(1.15f, 1.25f);
    final float textScale, spacingScale;
    SceneControlSize(float textScale, float spacingScale) { this.textScale = textScale; this.spacingScale = spacingScale; }
}
