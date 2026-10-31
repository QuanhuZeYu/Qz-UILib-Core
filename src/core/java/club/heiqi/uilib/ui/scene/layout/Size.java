package club.heiqi.uilib.ui.scene.layout;

/** logical px 尺寸意图；自身边界同样作用于自然、固定和填充。 */
public final class Size {
    public enum Mode {
        NATURAL,
        FIXED,
        FILL
    }

    public final Mode mode;
    public final int value, min, max;

    private Size(Mode mode, int value, int min, int max) {
        this.mode = mode;
        this.value = value;
        this.min = min;
        this.max = max;
    }

    public static Size natural() {
        return new Size(Mode.NATURAL, 0, 0, Integer.MAX_VALUE);
    }

    public static Size fixed(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("固定尺寸不能为负");
        return new Size(Mode.FIXED, pixels, 0, Integer.MAX_VALUE);
    }

    public static Size fill(int weight) {
        if (weight <= 0) throw new IllegalArgumentException("填充权重必须为正");
        return new Size(Mode.FILL, weight, 0, Integer.MAX_VALUE);
    }

    public Size bounds(int min, int max) {
        if (min < 0 || max < min) throw new IllegalArgumentException("尺寸边界无效");
        return new Size(mode, value, min, max);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Size)) return false;
        Size size = (Size) other;
        return mode == size.mode && value == size.value && min == size.min && max == size.max;
    }

    @Override public int hashCode() {
        int hash = mode.hashCode();
        hash = 31 * hash + value;
        hash = 31 * hash + min;
        return 31 * hash + max;
    }

    public int constrain(int requested) {
        return Math.max(min, Math.min(max, requested));
    }
}
