package club.heiqi.uilib.ui.scene.control;

/** 局部控件的尺寸角色，不改变外层Size声明。 */
public enum SceneControlSize {
    SMALL(.9f, .8f), REGULAR(1, 1), LARGE(1.15f, 1.25f);
    final float textScale, spacingScale;
    SceneControlSize(float textScale, float spacingScale) { this.textScale = textScale; this.spacingScale = spacingScale; }
}
