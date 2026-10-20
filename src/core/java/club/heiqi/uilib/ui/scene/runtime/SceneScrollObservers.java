package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/** 轴端口和旧Entry适配共用范围发布与输入接管的订阅生命周期，不持有位置权威。 */
final class SceneScrollObservers {
    private final Set<Observation<?>> observers = new LinkedHashSet<>();
    private final Set<InputObservation> inputs = new LinkedHashSet<>();
    private SceneGeometryView publishedGeometry;
    private long publishedRevision;
    <T> AutoCloseable observe(T initial, Function<SceneGeometryView, T> read, BiPredicate<T, T> same, Consumer<T> changed) {
        // 初值回调成功才登记，失败不会留下无法释放的订阅。
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
    /** 输入接管与范围发布分离：限位、布局和呈现自身写回都不是新输入。null沿原全局取消路径。 */
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
