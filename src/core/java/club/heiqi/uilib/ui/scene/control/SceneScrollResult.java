package club.heiqi.uilib.ui.scene.control;

/** 分别表达事件消费与视图变化，端点消费可复用原排列。 */
public enum SceneScrollResult {
    IGNORED(false, false), CONSUMED(true, false), CHANGED(true, true);
    public final boolean consumed, viewChanged;
    SceneScrollResult(boolean consumed, boolean viewChanged) { this.consumed = consumed; this.viewChanged = viewChanged; }
    public static SceneScrollResult moved(int pixels) { return pixels == 0 ? IGNORED : CHANGED; }
}
