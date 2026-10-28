package club.heiqi.uilib.ui.scene.control;

/** 纯计算局部内容视口范围，位置与输入归所属控件。 */
final class SceneControlViewport {
    private SceneControlViewport() {}

    static int clamp(int offset, int maximum) {
        return Math.max(0, Math.min(offset, maximum));
    }

    static int moved(int offset, int maximum, int pixels) {
        return (int) Math.max(0L, Math.min(maximum, (long) offset + pixels));
    }
    static int wheelPixels(int delta, int step) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, -(long) delta * step));
    }

    static int scrolled(int offset, int maximum, int delta, int step) {
        return (int) Math.max(0L, Math.min((long) maximum, offset - (long) delta * step));
    }
}
