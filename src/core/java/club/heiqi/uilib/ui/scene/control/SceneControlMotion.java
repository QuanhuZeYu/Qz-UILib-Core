package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** 标准控件共享有限呈现通道，只订阅真实状态/交互投影和主题，不永久 tick 或重测文本。 */
final class SceneControlMotion {
    private Signal<Integer> color;
    void install(SceneNode node, ReadableSignal<MotionSpec> spec, ToIntFunction<SceneNode> target) { install(node, spec, target, "control.fill"); }
    void install(SceneNode node, ReadableSignal<MotionSpec> spec, ToIntFunction<SceneNode> target, String channel) {
        node.bind((runtime, self, owner) -> {
            color = runtime.domain().signal(target.applyAsInt(self));
            return SceneMotion.color(() -> {
                runtime.interactionRevision().get();
                runtime.pointerRouter().hoverTarget().get();
                runtime.pointerRouter().pressedTarget().get();
                runtime.keyboardPressedTarget().get();
                return target.applyAsInt(self);
            }, () -> self.feedbackMotion() == null ? spec.get() : self.feedbackMotion(),
                    channel, (n, value) -> { color.set(value); runtime.invalidatePaint(); }).activate(runtime, self, owner);
        });
    }
    /** 只读呈现投影复用原轨迹；初挂/重挂读当前目标，卸载不保留旧进度或绑定。
     * spatial仅分类用户动效限制；显式feedbackMotion仍优先覆盖该控件的全部标准反馈。 */
    static ReadableSignal<Float> progress(SceneNode node, ReadableSignal<SceneTheme> theme,
            ReadableSignal<Float> target, boolean spatial) {
        Objects.requireNonNull(node, "控件"); Objects.requireNonNull(theme, "主题");
        Progress projection = new Progress(Objects.requireNonNull(target, "呈现目标"));
        Object channel = new Object();
        node.bind((runtime, self, owner) -> {
            Signal<Float> value = runtime.domain().signal(target.get());
            projection.current = value;
            owner.own(() -> { if (projection.current == value) projection.current = null; });
            BindingActivation active = NodeBinding.motion(target, () -> self.feedbackMotion() != null
                            ? self.feedbackMotion() : spatial ? theme.get().spatialMotion : theme.get().feedbackMotion,
                    spatial, channel, (n, next) -> {
                        value.set(next);
                        runtime.invalidatePaint();
                    }).activate(runtime, self, owner);
            return new BindingActivation() {
                public void refresh() { active.refresh(); }
                public void close() {
                    try { active.close(); }
                    finally { if (projection.current == value) projection.current = null; }
                }
            };
        });
        return projection;
    }
    private static final class Progress implements ReadableSignal<Float> {
        private final ReadableSignal<Float> target;
        private Signal<Float> current;
        Progress(ReadableSignal<Float> target) { this.target = target; }
        public Float get() { return current == null ? target.get() : current.get(); }
    }
    int color(int fallback) { return color == null ? fallback : color.get(); }
    static boolean enabled(SceneNode node) {
        for (SceneNode at = node; at != null; at = at.parent()) if (!at.enabled()) return false;
        return true;
    }
    static int target(SceneNode node, SceneTheme theme) {
        if (!enabled(node)) return theme.disabled;
        if (node.runtime() == null) return theme.button;
        if (node.runtime().pressed(node)) return theme.pressed;
        return node.runtime().pointerRouter().hoverTarget().get() == node ? theme.hover : theme.button;
    }
}
