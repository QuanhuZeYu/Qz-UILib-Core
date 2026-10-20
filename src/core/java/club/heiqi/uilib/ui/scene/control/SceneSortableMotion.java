package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 让位和落下共用活跃呈现调度，运动不拥有业务顺序或输入会话。 */
final class SceneSortableMotion<T, K, C> {
    static final class Offset {
        static final Offset ZERO = new Offset(0, 0);
        final int x, y;
        Offset(int x, int y) { this.x = x; this.y = y; }
    }
    private static final class Landing {
        final SceneSortableTrack x, y, width, height, elevation;
        private SceneNode anchor;
        private double baseX, baseY, worldX, worldY;
        Landing(LayoutBox from, double vx, double vy, SceneSortableTrack.Strategy strategy, long now) {
            x = new SceneSortableTrack(from.x, vx, from.x, strategy, now);
            y = new SceneSortableTrack(from.y, vy, from.y, strategy, now);
            width = new SceneSortableTrack(from.width, 0, from.width, strategy, now);
            height = new SceneSortableTrack(from.height, 0, from.height, strategy, now);
            elevation = new SceneSortableTrack(1, 0, 0, strategy, now);
        }
        void retarget(LayoutBox to, SceneNode parent, SceneGeometryView geometry, SceneSortableTrack.Strategy strategy, long now) {
            rebase(parent, geometry, to);
            x.retarget(to.x, strategy, now); y.retarget(to.y, strategy, now);
            width.retarget(to.width, strategy, now); height.retarget(to.height, strategy, now);
            elevation.retarget(0, strategy, now);
        }
        /** 与让位相同，插槽保留父局部基准；父滚动只换基，不成为新的落下目标或速度。 */
        private void rebase(SceneNode nextAnchor, SceneGeometryView geometry, LayoutBox slot) {
            SceneGeometryView.Entry parent = geometry.get(nextAnchor);
            if (parent == null || parent.inverse == null) { anchor = null; return; }
            SceneGeometryView.Entry previous = anchor == null ? null : geometry.get(anchor);
            if (previous != null) {
                double px = previous.layoutBox.x - scrollX(previous) + baseX;
                double py = previous.layoutBox.y - scrollY(previous) + baseY;
                x.shift(previous.transform.x(px, py) - worldX); y.shift(previous.transform.y(px, py) - worldY);
            }
            baseX = parent.inverse.x(slot.x, slot.y) - parent.layoutBox.x + scrollX(parent);
            baseY = parent.inverse.y(slot.x, slot.y) - parent.layoutBox.y + scrollY(parent);
            anchor = nextAnchor; worldX = slot.x; worldY = slot.y;
        }
        private static int scrollX(SceneGeometryView.Entry entry) { return entry.node.axis() == SceneNode.Axis.ROW ? entry.scrollOffset : 0; }
        private static int scrollY(SceneGeometryView.Entry entry) { return entry.node.axis() == SceneNode.Axis.ROW ? 0 : entry.scrollOffset; }
        void policy(SceneSortableTrack.Strategy strategy, long now) {
            x.retarget(x.target(), strategy, now); y.retarget(y.target(), strategy, now);
            width.retarget(width.target(), strategy, now); height.retarget(height.target(), strategy, now);
            elevation.retarget(0, strategy, now);
        }
        boolean active() { return x.active() || y.active() || width.active() || height.active() || elevation.active(); }
        LayoutBox box() { return new LayoutBox(x.integer(), y.integer(), Math.max(0, width.integer()), Math.max(0, height.integer())); }
    }
    final Signal<SceneSortableInteraction.Feedback<T, K, C>> preview;
    final Signal<Boolean> busy;
    final Signal<Float> elevation;
    private final SceneSortableModel<T, K, C> model;
    private final SceneSortableFlights<T, K, C> flights;
    private SceneSortableTrack.Strategy reorderStrategy, dropStrategy;
    private final ScenePointerVelocity samples = new ScenePointerVelocity();
    private Object sampleToken;
    private Landing landing;
    private AutoCloseable scheduled, geometryObservation;
    // 活跃期间只引用最近一次正式发布的几何；插槽仍由各轨迹的父局部 Anchor 换基。
    private SceneGeometryView currentGeometry;
    private boolean observingGeometry;
    SceneSortableMotion(SceneSortableModel<T, K, C> model) {
        this.model = model; flights = new SceneSortableFlights<>(model);
        preview = model.context.domain().signal(null); busy = model.context.domain().signal(false);
        elevation = model.context.domain().signal(1f);
        configure(model.options());
    }
    void geometryChanged(SceneGeometryView geometry) { synchronize(geometry); }
    void positionChanged(SceneSortableSelection.Position<T, K, C> from, SceneSortableSelection.Position<T, K, C> to) {
        if (!SceneSortableSelection.same(from, to)) prepare();
    }
    private void prepare() { flights.prepare(); publishActivity(); }
    void prepareIfShowing() { if (preview.get() != null) prepare(); }
    void sample(Object token, int x, int y, long timeNanos) {
        if (sampleToken != token) { samples.clear(); sampleToken = token; }
        samples.sample(x, y, timeNanos);
    }
    void show(SceneSortableInteraction.Feedback<T, K, C> value) {
        stopLanding(); preview.set(value); elevation.set(1f); publishActivity();
    }
    private void configure(SceneSortable.Options<T, C> options) {
        MotionPolicy policy = model.context.motionPolicy().get();
        reorderStrategy = SceneSortableTrack.Strategy.resolve(options.transition, options.reorderMotion, policy);
        dropStrategy = SceneSortableTrack.Strategy.resolve(options.dropAnimation, options.dropMotion, policy);
    }
    void finish() {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        if (value == null || landing != null) return;
        prepare(); long now = model.runtime.inputNanos();
        // 采集时间只负责估速；落下仍从同批呈现时间启动，不能拿合批延迟稀释速度。
        long eventTime = samples.time();
        landing = new Landing(value.box, samples.speed(true, eventTime), samples.speed(false, eventTime), dropStrategy, now);
        samples.clear(); sampleToken = null;
        if (!landing.active()) endPreview();
        publishActivity();
    }
    boolean ghost(SceneSortableModel.RowEntry<T, K, C> row) {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        return value != null && value.row == row;
    }
    void synchronize(SceneGeometryView geometry) {
        try {
            currentGeometry = geometry.world();
            advance(currentGeometry, model.runtime.inputNanos());
        } catch (RuntimeException | Error failure) { failed(failure); throw failure; }
    }
    private boolean frame(long now) {
        try {
            if (currentGeometry != null) advance(currentGeometry, now);
            return busy.get();
        } catch (RuntimeException | Error failure) { failed(failure); throw failure; }
    }
    private void advance(SceneGeometryView geometry, long now) {
        flights.synchronize(geometry, this::ghost, reorderStrategy, now);
        synchronizeLanding(geometry, now);
        publishActivity();
        if (preview.get() != null || flights.active()) flights.remember(geometry);
    }
    private void synchronizeLanding(SceneGeometryView geometry, long now) {
        if (landing == null) return;
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        SceneGeometryView.Entry target = value == null ? null : geometry.get(value.row.declaration.content);
        if (target == null || !model.runtime.interactive(value.row.node)) endPreview();
        else { landing.retarget(target.box, value.row.placed.get().declaration.items, geometry, dropStrategy, now); publishLanding(); }
    }
    /** 只在来源通知后解析策略；逐帧重求 supplier 的等价有限曲线会反复重启轨迹。
     *  声明阶段先采样旧轨迹再切策略，同一时刻的几何同步不重复推进。 */
    void policyChanged(SceneSortable.Options<T, C> options) {
        try {
            configure(options);
            long now = model.runtime.inputNanos();
            flights.policy(reorderStrategy, now);
            if (landing != null) { landing.policy(dropStrategy, now); publishLanding(); }
            publishActivity();
        } catch (RuntimeException | Error failure) { failed(failure); throw failure; }
    }
    private void publishLanding() {
        if (!landing.active()) { endPreview(); return; }
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        elevation.set((float) Math.max(0, Math.min(1, landing.elevation.value())));
        LayoutBox box = landing.box(), old = value.box;
        if (box.x != old.x || box.y != old.y || box.width != old.width || box.height != old.height)
            preview.set(new SceneSortableInteraction.Feedback<>(value.token, value.row, null, box));
    }
    private void publishActivity() {
        boolean active = landing != null || flights.active();
        busy.set(active);
        if (active && scheduled == null) scheduled = model.runtime.animate(this::frame);
        else if (!active) stopSchedule();
        if (active || preview.get() != null) observeGeometry();
        else { flights.forget(); stopGeometry(); }
    }
    private void observeGeometry() {
        if (geometryObservation != null || observingGeometry) return;
        observingGeometry = true;
        try {
            geometryObservation = model.runtime.observeGeometry(model.root, geometry -> {
                if (geometry != null) currentGeometry = geometry.world();
                // observe 的首个同步通知仍可能是重排前的几何，不能提前消耗 pending 插槽。
                if (observingGeometry) return;
                if (geometry == null) clear(); else synchronize(geometry);
            });
        } finally { observingGeometry = false; }
    }
    private void stopSchedule() {
        AutoCloseable previous = scheduled; scheduled = null;
        close(previous, "排序呈现调度释放失败");
    }
    private void stopGeometry() {
        AutoCloseable previous = geometryObservation; geometryObservation = null; currentGeometry = null;
        close(previous, "排序几何观察释放失败");
    }
    private static void close(AutoCloseable resource, String message) {
        if (resource == null) return;
        try { resource.close(); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException(message, failure); }
    }
    private void stopLanding() { landing = null; }
    private void endPreview() { stopLanding(); preview.set(null); samples.clear(); sampleToken = null; }
    /** 普通呈现已脱离输入帧；曲线/观察失败必须由本实例结束资源，不能等输入取消。 */
    private void failed(Throwable failure) {
        try { clear(); } catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
    }
    void clear() {
        stopLanding(); samples.clear(); sampleToken = null;
        // 先解除可再次调用的登记；每项独立清理，Signal 外部观察者失败不能截断其余资源。
        Throwable failure = cleanup(null, this::stopSchedule);
        failure = cleanup(failure, this::stopGeometry);
        failure = cleanup(failure, flights::clear);
        failure = cleanup(failure, () -> preview.set(null));
        failure = cleanup(failure, () -> busy.set(false));
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw new IllegalStateException("排序呈现释放失败", failure);
    }
    private static Throwable cleanup(Throwable failure, AutoCloseable action) {
        try { action.close(); } catch (Throwable next) {
            if (failure == null) return next;
            if (next != failure) failure.addSuppressed(next);
        }
        return failure;
    }
}
