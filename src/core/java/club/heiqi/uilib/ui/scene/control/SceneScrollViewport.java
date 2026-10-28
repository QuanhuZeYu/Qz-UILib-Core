package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/** 适配已有位置所有者，提供某轴视口事实与位置意图。 */
public final class SceneScrollViewport {
    /** 当批正式几何中的内容窗口与范围；null快照表示当前不具备该轴视口。 */
    public static final class Snapshot {
        public final LayoutBox box, clip;
        public final int offset, maximum, extent;
        private final SceneNode.Axis axis;
        public Snapshot(LayoutBox box, LayoutBox clip, int offset, int maximum, int extent) {
            this(box, clip, offset, maximum, extent, null);
        }
        private Snapshot(LayoutBox box, LayoutBox clip, int offset, int maximum, int extent, SceneNode.Axis axis) {
            this.box = Objects.requireNonNull(box, "box"); this.clip = clip;
            this.offset = offset; this.maximum = maximum; this.extent = extent; this.axis = axis;
        }
        public boolean sameRange(Snapshot other) {
            return other != null && axis == other.axis && offset == other.offset && maximum == other.maximum && extent == other.extent;
        }
    }
    private final SceneRuntime runtime;
    private final SceneNode node;
    private final Supplier<SceneNode.Axis> axis;
    private final BooleanSupplier enabled;
    public SceneScrollViewport(SceneRuntime runtime, SceneNode node, SceneNode.Axis axis) {
        this(runtime, node, () -> Objects.requireNonNull(axis, "axis"), () -> true);
        Objects.requireNonNull(axis, "axis");
    }
    private SceneScrollViewport(SceneRuntime runtime, SceneNode node, Supplier<SceneNode.Axis> axis, BooleanSupplier enabled) {
        this.runtime = Objects.requireNonNull(runtime, "runtime"); this.node = Objects.requireNonNull(node, "node");
        this.axis = axis; this.enabled = enabled;
    }
    /** 普通Node适配跟随声明轴；显式指定axis的端口保持固定。 */
    public static SceneScrollViewport of(ScenePageContext context, SceneNode node) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return new SceneScrollViewport(context.runtime(), node, node::axis, () -> true);
    }
    SceneScrollViewport enabled(BooleanSupplier enabled) { return new SceneScrollViewport(runtime, node, axis, enabled); }
    public boolean belongsTo(SceneRuntime candidate) { return runtime == candidate; }
    public static SceneScrollViewport of(ScenePageContext context, SceneNode node, SceneNode.Axis axis) {
        Objects.requireNonNull(context, "context").domain().checkWrite(); return new SceneScrollViewport(context.runtime(), node, axis);
    }
    public SceneNode node() { return node; }
    public SceneNode.Axis axis() { return axis.get(); }
    public Snapshot snapshot(SceneGeometryView geometry) {
        if (!enabled.getAsBoolean()) return null;
        SceneNode.Axis current = axis();
        Snapshot view = runtime.scrollSnapshot(node, current, geometry);
        return view == null ? null : new Snapshot(view.box, view.clip, view.offset, view.maximum, view.extent, current);
    }
    /** 定位阶段从原位置所有者与当批几何读取内容事实。 */
    Snapshot layoutSnapshot(SceneGeometryView geometry) {
        return enabled.getAsBoolean() ? runtime.scrollLayoutSnapshot(node, axis(), geometry) : null;
    }
    public int moveByPixels(int pixels) { return enabled.getAsBoolean() ? runtime.scrollByPixels(node, axis(), pixels) : 0; }
    /** 将绝对位置意图交给原所有者，按其当前偏移求差。 */
    public int moveToPixels(int offset) { return enabled.getAsBoolean() ? runtime.scrollToPixels(node, axis(), offset) : 0; }
    public AutoCloseable observe(Consumer<Snapshot> changed) { return runtime.observeScroll(this, changed); }
}
