package club.heiqi.uilib.ui.scene.hud;

import java.util.Objects;

/** 不可变位置草稿，偏移使用 UILib framebuffer 像素，scale 仅缩放内容。 */
public final class SceneHudPlacement {
    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public final Anchor anchor;
    public final double offsetX, offsetY;
    public final float scale;

    public SceneHudPlacement(Anchor anchor, double offsetX, double offsetY, float scale) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Float.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("HUD 偏移须有限，缩放须为有限正数");
        this.offsetX = offsetX == 0 ? 0 : offsetX;
        this.offsetY = offsetY == 0 ? 0 : offsetY;
        this.scale = scale;
    }

    @Override public boolean equals(Object value) {
        if (!(value instanceof SceneHudPlacement)) return false;
        SceneHudPlacement other = (SceneHudPlacement) value;
        return anchor == other.anchor && offsetX == other.offsetX && offsetY == other.offsetY && scale == other.scale;
    }

    @Override public int hashCode() { return Objects.hash(anchor, offsetX, offsetY, scale); }
}
