package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/** 轴端口和 Entry 适配共用范围发布与输入接管订阅，位置由 runtime 维护。 */
final class SceneScrollObservers {
    private final Set<Observation<?>> observers = new LinkedHashSet<>();
    private final Set<InputObservation> inputs = new LinkedHashSet<>();
    private SceneGeometryView publishedGeometry;
    private long publishedRevision;
    <T> AutoCloseable observe(T initial, Function<SceneGeometryView, T> read, BiPredicate<T, T> same, Consumer<T> changed) {
        // 初值回调成功后再登记订阅，使失败路径保持资源归属完整。
        changed.accept(initial);
        Observation<T> observation = new Observation<>(initial, read, same, changed);
        observers.add(observation); publishedGeometry = null;
        return () -> observers.remove(observation);
    }
    void publish(SceneGeometryView geometry, long revision) {
        if (publishedGeometry == geometry && publishedRevision == revision) return;
        publishedGeometry = geometry; publishedRevision = revision;
        for (Observation<?> observation : new ArrayList<>(observers))
            if (observers.contains(observation)) observation.publish(geometry);
    }
    AutoCloseable observeInput(SceneNode viewport, Runnable takeover) {
        InputObservation observation = new InputObservation(viewport, takeover);
        inputs.add(observation);
        return () -> inputs.remove(observation);
    }
    /** 输入接管独立于范围发布；限位、布局与呈现写回走范围通道，null 沿全局取消路径。 */
    void input(SceneNode viewport) {
        Throwable failure = null;
        for (InputObservation observation : new ArrayList<>(inputs)) {
            if (!inputs.contains(observation) || observation.notifying || viewport != null && observation.viewport != viewport) continue;
            observation.notifying = true;
            try { observation.takeover.run(); }
            catch (RuntimeException | Error next) {
                if (failure == null) failure = next;
                else if (next != failure && !java.util.Arrays.asList(failure.getSuppressed()).contains(next)) failure.addSuppressed(next);
            } finally { observation.notifying = false; }
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
    void clear() { observers.clear(); inputs.clear(); publishedGeometry = null; }
    private static final class InputObservation {
        final SceneNode viewport;
        final Runnable takeover;
        boolean notifying;
        InputObservation(SceneNode viewport, Runnable takeover) { this.viewport = viewport; this.takeover = takeover; }
    }
    private static final class Observation<T> {
        private final Function<SceneGeometryView, T> read;
        private final BiPredicate<T, T> same;
        private final Consumer<T> changed;
        private T current;
        Observation(T initial, Function<SceneGeometryView, T> read, BiPredicate<T, T> same, Consumer<T> changed) {
            current = initial; this.read = read; this.same = same; this.changed = changed;
        }
        void publish(SceneGeometryView geometry) {
            T next = read.apply(geometry);
            if (current == null ? next == null : next != null && same.test(current, next)) return;
            current = next; changed.accept(next);
        }
    }
}
