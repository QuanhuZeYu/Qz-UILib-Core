package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 一次轨道翻页的有限位置意图；运动不占主动输入帧，位置仍回原视口限位。 */
final class SceneScrollPage {
    private static final MotionSpec MOTION = MotionSpec.tween(160_000_000L,
            value -> 1 - Math.pow(1 - value, 3));
    private final SceneRuntime runtime;
    private final SceneScrollViewport viewport;
    private final MotionValue position = new MotionValue();
    private MotionPolicy policy;
    private MotionSpec strategy;

    SceneScrollPage(SceneRuntime runtime, SceneScrollViewport viewport) {
        this.runtime = runtime; this.viewport = viewport;
    }
    boolean active() { return position.active(); }
    void begin(SceneScrollViewport.Snapshot view, int direction) {
        long now = runtime.inputNanos();
        int target = (int) Math.max(0L, Math.min(view.maximum, (long) view.offset + (long) direction * view.extent));
        position.snap(view.offset, now);
        position.retarget(target, strategy(), now);
        publish();
    }
    void constrain(int maximum) {
        if (!active()) return;
        double previous = position.value();
        position.constrain(0, maximum, runtime.inputNanos());
        if (previous != position.value()) publish();
    }
    void policy() {
        if (!active()) return;
        position.retarget(position.target(), strategy(), runtime.inputNanos());
        publish();
    }
    void advance(long now) {
        if (!active()) return;
        position.retarget(position.target(), strategy(), now);
        publish();
    }
    private MotionSpec strategy() {
        MotionPolicy current = runtime.motionPolicy().get();
        if (current != policy) { policy = current; strategy = current.resolve(MOTION, true); }
        return strategy;
    }
    private void publish() { viewport.moveToPixels((int) Math.round(position.value())); }
    void stop() { position.snap(position.value(), runtime.inputNanos()); }
}
