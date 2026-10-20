package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 活动后的有限等待与淡出；只拥有 alpha，不持有滚动位置或输入时钟。 */
final class SceneScrollVisibility {
    final Signal<Float> opacity;
    private final SceneRuntime runtime;
    private final ReadableSignal<SceneScrollbar.Options> source;
    private final MotionValue alpha = new MotionValue();
    private boolean hovered, areaHovered, dragging, waiting;
    private long fadeAt, fadeMillis = -1;
    private MotionSpec fade;
    private MotionPolicy policy;

    SceneScrollVisibility(SceneRuntime runtime, ReadableSignal<SceneScrollbar.Options> source) {
        this.runtime = runtime; this.source = source;
        opacity = runtime.domain().signal(0f);
    }
    void activity() {
        alpha.snap(1, runtime.inputNanos()); opacity.set(1f);
        schedule();
    }
    void hover(boolean inside) {
        hovered = inside;
        if (inside) activity(); else schedule();
    }
    void areaHover(boolean inside) {
        areaHovered = inside;
        if (inside) activity(); else schedule();
    }
    void configure() { schedule(); }
    void drag(boolean active) {
        dragging = active;
        if (active) activity(); else schedule();
    }
    private void schedule() {
        alpha.snap(opacity.get(), runtime.inputNanos());
        SceneScrollbar.Options options = source.get();
        fadeAt = runtime.inputNanos() + options.hideDelayMillis * 1_000_000L;
        waiting = options.autoHide && !hovered && !areaHovered && !dragging && opacity.get() > 0;
        if (waiting && options.hideDelayMillis == 0) advance(runtime.inputNanos());
    }
    boolean active() { return waiting || alpha.active(); }
    float alpha() { return source.get().autoHide ? opacity.get() : 1f; }
    private MotionSpec motion() {
        long duration = source.get().fadeMillis;
        MotionPolicy current = runtime.motionPolicy().get();
        if (duration != fadeMillis || current != policy) {
            fadeMillis = duration; policy = current;
            fade = current.resolve(MotionSpec.tween(duration * 1_000_000L, MotionSpec.LINEAR), false);
        }
        return fade;
    }
    void policy() {
        if (!alpha.active()) return;
        alpha.retarget(0, motion(), runtime.inputNanos());
        opacity.set((float) alpha.value());
    }
    void advance(long now) {
        if (waiting) {
            if (now - fadeAt < 0) return;
            waiting = false;
            // 等待是交互期限；迟到帧按实际经过时间采样，不重新延长淡出。
            alpha.snap(opacity.get(), fadeAt);
            alpha.retarget(0, motion(), fadeAt);
        }
        if (alpha.active()) alpha.retarget(0, motion(), now);
        opacity.set((float) alpha.value());
    }
    // 范围归零只结束所属时间线，正式悬停事实在恢复范围后继续消费。
    void stop() { dragging = waiting = false; alpha.snap(0, runtime.inputNanos()); opacity.set(0f); }
    void close() { hovered = areaHovered = false; stop(); }
}
