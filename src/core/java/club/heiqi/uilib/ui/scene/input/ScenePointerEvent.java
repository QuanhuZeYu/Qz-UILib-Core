package club.heiqi.uilib.ui.scene.input;

/** 无平台、不可变的单指针输入；坐标已由宿主转换到scene统一像素。
 * 宿主必须递交所有按钮的DOWN/UP。按钮0是标准激活键，其余按钮仅占位并吞尾。
 * 逐项按采集顺序递交，焦点、IME、键盘与拖放沿各自接口表达。 */
public final class ScenePointerEvent {
    public enum Kind {
        DOWN,
        MOVE,
        UP,
        CANCEL,
        ALL_BUTTONS_RELEASED,
        SCROLL,
        SYNTHETIC_ACTIVATION
    }

    public final Kind kind;

    /** DOWN/UP为非负物理按钮号；其他事件为-1。 */
    public final int button;

    public final int x, y, delta;
    public final double worldX, worldY, preciseX, preciseY;
    private final club.heiqi.uilib.ui.scene.geometry.SceneTransform frame;
    /** 宿主采集时刻，速度按事件实际采集间隔估算。 */
    public final long timeNanos;
    public enum ScrollPhase { DISCRETE, UPDATE, END, MOMENTUM }
    public final ScrollPhase scrollPhase;

    private ScenePointerEvent(Kind kind, int button, int x, int y, int delta) {
        this(kind, button, x, y, delta, System.nanoTime(), ScrollPhase.DISCRETE);
    }
    private ScenePointerEvent(Kind kind, int button, int x, int y, int delta, long timeNanos, ScrollPhase phase) {
        this(kind, button, x, y, delta, timeNanos, phase, x, y, club.heiqi.uilib.ui.scene.geometry.SceneTransform.IDENTITY);
    }
    private ScenePointerEvent(Kind kind, int button, double x, double y, int delta, long timeNanos, ScrollPhase phase,
            double worldX, double worldY, club.heiqi.uilib.ui.scene.geometry.SceneTransform frame) {
        this.worldX = worldX; this.worldY = worldY; preciseX = x; preciseY = y; this.frame = frame;
        this.timeNanos = timeNanos; this.scrollPhase = java.util.Objects.requireNonNull(phase, "滚动输入阶段");
        this.kind = kind;
        this.button = button;
        this.x = (int) Math.round(x);
        this.y = (int) Math.round(y);
        this.delta = delta;
    }

    public ScenePointerEvent at(long timeNanos) { return new ScenePointerEvent(kind, button, preciseX, preciseY, delta, timeNanos, scrollPhase, worldX, worldY, frame); }
    /** 从原 world 精确采样直接映射到当前坐标系。 */
    public ScenePointerEvent in(club.heiqi.uilib.ui.scene.layout.SceneGeometryView geometry) {
        club.heiqi.uilib.ui.scene.geometry.SceneTransform next = geometry.coordinateTransform();
        if (next.equals(frame)) return this;
        club.heiqi.uilib.ui.scene.geometry.SceneTransform inverse = next.inverse();
        if (inverse == null) throw new IllegalArgumentException("指针目标坐标系不可逆，必须先取消联系");
        return new ScenePointerEvent(kind, button, inverse.x(worldX, worldY), inverse.y(worldX, worldY), delta,
                timeNanos, scrollPhase, worldX, worldY, next);
    }
    public ScenePointerEvent world() {
        return frame.identity() ? this : new ScenePointerEvent(kind, button, worldX, worldY, delta, timeNanos, scrollPhase,
                worldX, worldY, club.heiqi.uilib.ui.scene.geometry.SceneTransform.IDENTITY);
    }
    /** 递交宿主直接滚动序列，MOMENTUM 沿用宿主已有动量。 */
    public static ScenePointerEvent scrollGesture(int x, int y, int delta, ScrollPhase phase, long timeNanos) {
        return new ScenePointerEvent(Kind.SCROLL, -1, x, y, delta, timeNanos, phase);
    }

    /** 按下建立手势所有权，激活由合法释放处理。 */
    public static ScenePointerEvent down(int button, int x, int y) {
        return button(Kind.DOWN, button, x, y);
    }

    /** 释放时按当前共同几何核验原主键与目标，合法完整段才激活。 */
    public static ScenePointerEvent up(int button, int x, int y) {
        return button(Kind.UP, button, x, y);
    }

    private static ScenePointerEvent button(Kind kind, int button, int x, int y) {
        if (button < 0) throw new IllegalArgumentException("物理按钮号不能为负");
        return new ScenePointerEvent(kind, button, x, y, 0);
    }

    /** 移动更新当前位置，沿起手 owner 接续。 */
    public static ScenePointerEvent move(int x, int y) {
        return new ScenePointerEvent(Kind.MOVE, -1, x, y, 0);
    }

    /** 窗口失焦/关闭时取消owner；仍按下的按钮继续吞尾至UP或确认全部释放。 */
    public static ScenePointerEvent cancel() {
        return new ScenePointerEvent(Kind.CANCEL, -1, 0, 0, 0);
    }

    /** 宿主确认全部按钮已实际释放后，同步结束失焦丢 UP 留下的物理占位。
     * 设备真实释放是递交前提，恢复焦点仍须等待该事实。 */
    public static ScenePointerEvent allButtonsReleased() {
        return new ScenePointerEvent(Kind.ALL_BUTTONS_RELEASED, -1, 0, 0, 0);
    }

    /** 滚轮按当前有效范围独立路由。 */
    public static ScenePointerEvent scroll(int x, int y, int delta) {
        return new ScenePointerEvent(Kind.SCROLL, -1, x, y, delta);
    }

    /** 空闲时按坐标执行完整 synthetic 点击，包含 clickFocus 与业务激活。
     * 已有物理段时整项吞掉，覆盖取消尾部、空白起手与第二键。 */
    public static ScenePointerEvent syntheticActivation(int x, int y) {
        return new ScenePointerEvent(Kind.SYNTHETIC_ACTIVATION, -1, x, y, 0);
    }
}
