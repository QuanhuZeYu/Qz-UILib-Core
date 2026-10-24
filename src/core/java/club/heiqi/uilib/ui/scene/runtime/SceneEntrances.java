package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.motion.EntranceOptions;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 入场只持有挂载呈现层；普通属性、共同几何与被动退场各保留原权威。 */
final class SceneEntrances {
    private final SceneRuntime runtime;
    private final Map<SceneNode, Active> bindings = new IdentityHashMap<>();
    private final List<Active> moving = new ArrayList<>();
    private final Map<SceneNode, Boolean> availability = new IdentityHashMap<>();
    private long rangeRevision = Long.MIN_VALUE;
    private boolean visibilityDirty;

    SceneEntrances(SceneRuntime runtime) { this.runtime = runtime; }

    BindingActivation bind(SceneNode node, ReadableSignal<EntranceOptions> source, Owner owner) {
        Owner scope = owner.child();
        try {
            Active active = new Active(node, source, scope);
            bindings.put(node, active);
            return active;
        } catch (RuntimeException | Error failure) {
            try { scope.close(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    SceneTransform transform(SceneNode node) {
        Active state = bindings.get(node);
        return state == null ? SceneTransform.IDENTITY : state.transform;
    }

    float opacity(SceneNode node) {
        Active state = bindings.get(node);
        return state == null ? 1 : state.opacity;
    }

    /** 显隐绑定均已结算后只检查活跃入场，完成者不再参与逐帧资格扫描。 */
    void settleVisibility() {
        long revision = runtime.interactionRevision().get();
        if (rangeRevision == revision && !visibilityDirty) return;
        rangeRevision = revision; visibilityDirty = false;
        try {
            for (int i = moving.size() - 1; i >= 0; i--) {
                Active state = moving.get(i);
                if (!available(state.node)) state.finish(runtime.inputNanos());
            }
        } finally { availability.clear(); }
    }

    private boolean available(SceneNode node) {
        if (node == null) return true;
        Boolean known = availability.get(node);
        if (known != null) return known;
        boolean result = node.runtime() == runtime && node.visible() && !node.destroyed() && available(node.parent());
        availability.put(node, result);
        return result;
    }

    private void removeMoving(Active state) {
        if (state.movingIndex < 0) return;
        int index = state.movingIndex;
        Active last = moving.remove(moving.size() - 1);
        if (last != state) { moving.set(index, last); last.movingIndex = index; }
        state.movingIndex = -1;
    }

    private final class Active implements BindingActivation, SceneTransitions.Step {
        private final SceneNode node;
        private final Owner scope;
        private final Observation observation;
        private final MotionValue x = new MotionValue(), y = new MotionValue(), alpha = new MotionValue();
        private EntranceOptions options;
        private MotionSpec spatial, feedback;
        private SceneTransform transform = SceneTransform.IDENTITY;
        private float opacity = 1;
        private boolean initialized, closed;
        private int movingIndex = -1;

        Active(SceneNode node, ReadableSignal<EntranceOptions> source, Owner scope) {
            this.node = node; this.scope = scope;
            scope.own(runtime.claimMotion(node, "enter", this));
            observation = runtime.domain().observe(scope, () -> {
                options = Objects.requireNonNull(source.get(), "入场策略");
                MotionPolicy policy = runtime.motionPolicy().get();
                spatial = policy.resolve(options.spatial, true);
                feedback = policy.resolve(options.feedback, false);
            }, () -> runtime.invalidateBinding(node));
        }

        public void refresh() {
            if (closed) return;
            try {
                observation.refresh();
                if (spatial.kind == MotionSpec.Kind.DECAY || feedback.kind == MotionSpec.Kind.DECAY)
                    throw new IllegalArgumentException("入场不能使用自由惯性: node=" + node.id());
                long now = runtime.inputNanos();
                if (!initialized) {
                    initialized = true;
                    x.snap(options.offsetX, now); y.snap(options.offsetY, now); alpha.snap(0, now);
                }
                x.retarget(0, spatial, now); y.retarget(0, spatial, now); alpha.retarget(1, feedback, now);
                publish(); schedule();
            } catch (RuntimeException | Error failure) { failed(failure); throw failure; }
        }

        public boolean advance(long now) {
            if (closed) return false;
            try {
                x.advance(now); y.advance(now); alpha.advance(now);
                publish();
                boolean active = active();
                if (!active) removeMoving(this);
                return active;
            } catch (RuntimeException | Error failure) { failed(failure); throw failure; }
        }

        private boolean active() { return x.active() || y.active() || alpha.active(); }

        private void schedule() {
            if (active()) {
                if (movingIndex < 0) { movingIndex = moving.size(); moving.add(this); visibilityDirty = true; }
                runtime.transitions().start(this);
            } else { removeMoving(this); runtime.transitions().stop(this); }
        }

        private void finish(long now) {
            try {
                x.snap(0, now); y.snap(0, now); alpha.snap(1, now);
                publish(); schedule();
            } catch (RuntimeException | Error failure) { failed(failure); throw failure; }
        }

        private void publish() {
            // 入场保留局部小数位移，和普通平移及作者仿射在共同几何中组合。
            double nextX = x.value(), nextY = y.value();
            if (transform.tx != nextX || transform.ty != nextY) {
                transform = SceneTransform.translation(nextX, nextY); runtime.invalidateGeometry(node);
            }
            float nextOpacity = (float) Math.max(0, Math.min(1, alpha.value()));
            if (opacity != nextOpacity) { opacity = nextOpacity; runtime.invalidatePaint(node); }
        }

        private void failed(Throwable failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        }

        public void close() {
            if (closed) return;
            closed = true; removeMoving(this); runtime.transitions().stop(this);
            bindings.remove(node, this);
            try { scope.close(); } finally {
                if (!transform.identity()) runtime.invalidateGeometry(node);
                else if (opacity != 1) runtime.invalidatePaint(node);
            }
        }
    }
}
