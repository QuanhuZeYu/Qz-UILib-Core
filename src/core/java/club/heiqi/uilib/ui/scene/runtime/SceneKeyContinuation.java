package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.input.SceneEditContinuation;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneKeyInterpreter;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 每个中性按键最多拥有一个许可；离组事实不可复活，关闭脱离所属Owner。 */
public final class SceneKeyContinuation implements AutoCloseable {
    private final SceneRuntime runtime;
    private final MountHandle consumer;
    private final SceneControl control;
    private final SceneFocusRouter.RangeHandle range;
    private final SceneEditContinuation action;
    private final SceneEditEvent.Command command;
    private final Owner scope;
    private final Observation qualification;
    private boolean closed, qualified;

    SceneKeyContinuation(SceneRuntime runtime, SceneNode consumer, SceneFocusRouter.RangeHandle range,
            SceneEditContinuation action, SceneEditEvent.Command command, Owner scope) {
        this.runtime = runtime; this.consumer = runtime.captureMount(consumer); this.control = consumer.control();
        this.range = range; this.action = action; this.command = command; this.scope = scope;
        scope.own(() -> { closed = true; runtime.forgetKeyContinuation(this); });
        qualification = runtime.domain().observe(scope, () -> {
            runtime.interactionRevision().get();
            qualified = valid(runtime.focusRouter().focused().get());
        }, this::refresh);
    }
    void refresh() {
        if (closed) return;
        qualification.refresh();
        if (!qualified) close();
    }
    private boolean valid(SceneNode focused) {
        try {
            return runtime.alive() && consumer.active() && consumer.node().control() == control && range.current()
                    && runtime.interactive(consumer.node()) && focused != null && runtime.interactive(focused)
                    && runtime.domain().readOnly(() -> action.accepts(focused));
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    void focusChanged(SceneNode actual) {
        // Signal/Effect可合并离组再返回；这里消费setOwner的实际转换，不能等观察最终值。
        if (!closed && !valid(actual)) close();
    }
    public void repeat(SceneKeyEvent key) {
        runtime.domain().checkWrite();
        refresh();
        if (closed) return;
        if (!valid(runtime.focusRouter().focused().get())) { close(); return; }
        SceneEditEvent event = SceneKeyInterpreter.edit(key);
        if (event == null || event.command != command || !runtime.continueKeyEdit(action, event)) close();
    }
    @Override public void close() {
        runtime.domain().checkWrite();
        if (closed) return;
        closed = true; runtime.forgetKeyContinuation(this); scope.dispose();
    }
}
