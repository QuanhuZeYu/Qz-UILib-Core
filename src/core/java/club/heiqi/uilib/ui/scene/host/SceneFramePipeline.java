package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.input.SceneKeyInterpreter;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.ScenePointerRouter;
import club.heiqi.uilib.ui.scene.runtime.ScenePresentation;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 事件顺序、脏声明、几何准备与最终绘制由框架协调；状态无需帧提交。 */
public final class SceneFramePipeline implements AutoCloseable {
    private final SceneRuntime runtime;
    private final SceneHost host;
    private final SceneLayoutEngine layout;
    private final ScenePaintCollector paint;
    private final SceneTextMeasurer text;

    private enum InputKind { POINTER, NAVIGATION, EDIT, KEY, FOCUS_LOST, ESCAPE, SEMANTIC }

    /** 后续动作在当前事件及其Effect结算后执行；对象本身不承担输入路由。 */
    private static final class FollowUp {
        enum Kind { HOST, NAVIGATION, KEY }
        final Kind kind;
        final Runnable host;
        final SceneNavigationEvent navigation;

        private FollowUp(Kind kind, Runnable host, SceneNavigationEvent navigation) {
            this.kind = kind;
            this.host = host;
            this.navigation = navigation;
        }

        static FollowUp host(Runnable action) {
            return new FollowUp(Kind.HOST, Objects.requireNonNull(action, "fallback"), null);
        }

        static FollowUp key(Runnable fallback) { return new FollowUp(Kind.KEY, fallback, null); }

        static FollowUp navigation(SceneNavigationEvent event) {
            return new FollowUp(Kind.NAVIGATION, null, Objects.requireNonNull(event, "fallback"));
        }
    }

    private static final class KeySequence {
        enum Use { RESERVED, EDIT, NAVIGATION, PRESS }
        final SceneFocusRouter.RangeHandle range;
        MountHandle editing;
        Use use = Use.RESERVED;
        club.heiqi.uilib.ui.scene.runtime.SceneKeyContinuation continuation;
        void close() { use = Use.RESERVED; if (continuation != null) { continuation.close(); continuation = null; } }
        KeySequence(SceneFocusRouter.RangeHandle range, MountHandle editing) { this.range = range; this.editing = editing; }
    }

    private static final class QueuedInput {
        final InputKind kind;
        final ScenePointerEvent pointer;
        final SceneNavigationEvent navigation;
        final SceneEditEvent edit;
        final SceneKeyEvent key;
        KeySequence keySequence;
        SceneFocusRouter.RangeHandle directionRange;
        final FollowUp followUp;
        boolean consumed;
        club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest semantic;
        SceneSemanticCompletion semanticCompletion;
        club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result semanticOutcome;

        private QueuedInput(InputKind kind, ScenePointerEvent pointer, SceneNavigationEvent navigation,
                            SceneEditEvent edit, FollowUp followUp, SceneKeyEvent key) {
            this.kind = kind;
            this.pointer = pointer;
            this.navigation = navigation;
            this.edit = edit; this.key = key;
            this.followUp = followUp;
        }

        static QueuedInput pointer(ScenePointerEvent event) {
            return new QueuedInput(InputKind.POINTER, Objects.requireNonNull(event, "event"), null, null, null, null);
        }

        static QueuedInput navigation(SceneNavigationEvent event) {
            Objects.requireNonNull(event, "event");
            return new QueuedInput(InputKind.NAVIGATION, null, event, null,
                    event.directional() ? FollowUp.navigation(event) : null, null);
        }

        static QueuedInput edit(SceneEditEvent event, FollowUp followUp) {
            return new QueuedInput(InputKind.EDIT, null, null, Objects.requireNonNull(event, "event"), followUp, null);
        }

        static QueuedInput key(SceneKeyEvent event, Runnable fallback) {
            return new QueuedInput(InputKind.KEY, null, null, null, FollowUp.key(fallback), Objects.requireNonNull(event, "event"));
        }

        static QueuedInput focusLost() {
            return new QueuedInput(InputKind.FOCUS_LOST, null, null, null, null, null);
        }

        static QueuedInput escape(Runnable fallback) {
            return new QueuedInput(InputKind.ESCAPE, null, null, null, FollowUp.host(fallback), null);
        }

        boolean preparesPointerRelease() {
            return kind == InputKind.POINTER && pointer.kind == ScenePointerEvent.Kind.UP && pointer.button == 0;
        }

        static QueuedInput semantic(club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest request,
                SceneSemanticCompletion result) {
            QueuedInput input = new QueuedInput(InputKind.SEMANTIC, null, null, null, null, null);
            input.semantic = request; input.semanticCompletion = result; return input;
        }

        boolean requiresTopLevelSettlement() {
            return kind == InputKind.SEMANTIC || followUp != null || preparesPointerRelease();
        }

        void discard(ScenePointerRouter router) {
            if (semanticCompletion != null) semanticCompletion.finish(club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result.CLOSED, null);
            if (kind == InputKind.POINTER) router.discardAfterFailure(pointer);
        }
    }

    private final ArrayDeque<QueuedInput> inputs = new ArrayDeque<>();
    // 每个中性物理键至多一项；fresh覆盖，失焦/失败/关闭结束，不另持输入或焦点权威。
    private final java.util.Map<SceneKey, KeySequence> keySequences = new java.util.EnumMap<>(SceneKey.class);

    private static final class InputBatch {
        final SceneRuntime.InputFrame activeInput = new SceneRuntime.InputFrame();
        final List<QueuedInput> events;
        final boolean independent;
        final long frameNanos;
        boolean failed, released;

        InputBatch(List<QueuedInput> events, boolean independent, long frameNanos) {
            this.events = events;
            this.independent = independent; this.frameNanos = frameNanos;
        }
    }

    private int pendingIndependent;
    private final java.util.Set<java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot>> inspections = new java.util.LinkedHashSet<>();
    private boolean inspectionScheduled;

    void inspect(java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot> result) {
        synchronized (inputs) {
            if (closed) { result.complete(club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.CLOSED); return; }
            inspections.add(result);
            if (inspectionScheduled) return;
            inspectionScheduled = true;
        }
        runtime.domain().enqueue(() -> {
            synchronized (inputs) { inspectionScheduled = false; if (closed || inspections.isEmpty()) return; }
            frame(false);
        }, failure -> finishInspections(null, failure));
    }

    private void finishInspections(club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot snapshot, Throwable failure) {
        java.util.List<java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot>> pending;
        synchronized (inputs) { pending = new ArrayList<>(inspections); inspections.clear(); }
        for (java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot> result : pending) {
            if (failure != null) result.completeExceptionally(failure); else result.complete(snapshot);
        }
    }

    private void release(InputBatch batch) {
        synchronized (inputs) {
            if (!batch.released) {
                batch.released = true;
                pendingIndependent--;
            }
        }
    }

    private boolean closed;
    private ScenePresentation presentation;
    private long revision = -1, geometryRevision = -1, metrics = -1, generation;
    private int width = -1, height = -1;
    private long preparedNanos;

    public SceneFramePipeline(SceneRuntime runtime, SceneHost host, SceneTextMeasurer measurer) {
        this.runtime = runtime;
        this.host = host;
        this.text=measurer;
        this.layout = new SceneLayoutEngine(measurer, runtime::scrollOffset, runtime::nodeMeasureRevision);
        this.paint = new ScenePaintCollector(runtime, layout, measurer);
        this.layout.inspectionWork(runtime::inspectionMeasured, runtime::inspectionArranged);
    }

    /** 宿主在域外按采集顺序递交typed输入，所有物理按钮均需递交。 */
    public void pointer(ScenePointerEvent event) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.pointer(event));
        }
    }

    /** 宿主失焦与指针、编辑共享采集顺序，不能立即清焦点越过待结算输入。 */
    public void focusLost() {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.focusLost());
        }
    }

    /** 与指针共享采集顺序；不从宿主另建焦点表。 */
    public void navigation(SceneNavigationEvent event) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.navigation(event));
        }
    }

    /** 一个中性键与其后续命令同序处理，编辑Effect成功后才继续。 */
    public void key(SceneKeyEvent event, Runnable fallback) {
        synchronized (inputs) { checkOpen(); inputs.add(QueuedInput.key(event, fallback)); }
    }

    /** 文本与命令使用唯一当前焦点，消费后结算结果再执行宿主fallback。 */
    public void edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent event, Runnable fallback) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.edit(event, fallback == null ? null : FollowUp.host(fallback)));
        }
    }

    /** 控件未消费时在本事件结算后导航/激活，不将fallback重排到下一帧。 */
    public void editOrNavigate(
            club.heiqi.uilib.ui.scene.input.SceneEditEvent event, SceneNavigationEvent fallback) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.edit(event, FollowUp.navigation(fallback)));
        }
    }

    void semantic(club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest request, SceneSemanticCompletion result) {
        synchronized (inputs) {
            if (closed) { result.finish(club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result.CLOSED, null); return; }
            inputs.add(QueuedInput.semantic(Objects.requireNonNull(request), result));
        }
    }

    /** ESC与指针/导航同序；fallback仅在未消费、Effects成功且此管线仍有效时于域内执行。 */
    public void escape(Runnable fallback) {
        synchronized (inputs) {
            checkOpen();
            inputs.add(QueuedInput.escape(fallback));
        }
    }

    /** 完整synthetic activation入口，不是DOWN；已有物理手势时吞掉，不绕过owner。 */
    public void click(int x, int y) {
        pointer(ScenePointerEvent.syntheticActivation(x, y));
    }

    /** 保留现行滚轮行为，与pointer事件共享采集顺序和prepare。 */
    public void scroll(int x, int y, int delta) {
        pointer(ScenePointerEvent.scroll(x, y, delta));
    }

    /**
     * 每事件独立domain.execute→prepare→route，前handler状态影响下一事件。
     * handler失败时取消owner、仅记本批剩余物理尾部并外抛，不丢已采集的第二键。
     */
    public void frame() { frame(true); }

    private void frame(boolean replay) {
        synchronized (inputs) {
            checkOpen();
        }
        host.prepareResources();
        InputBatch batch = takeBatch(!replay);
        for (int i = 0; i < batch.events.size(); i++) {
            final int index = i;
            QueuedInput event = batch.events.get(index);
            // 释放坐标先进入位置意图；独立顶层操作使其 Effect 在 UP 前结算，随后 route 正式重排。
            if (event.preparesPointerRelease())
                submit(batch.independent, () -> preparePointerRelease(batch, event), failure -> failBatch(batch, index, failure));
            // 路由失败仍须记录当前物理事件；后续动作失败时当前事件已结算。
            submit(batch.independent, () -> route(batch, event), failure -> failBatch(batch, index, failure));
            if (event.followUp != null)
                submit(batch.independent, () -> followUp(batch, event), failure -> failBatch(batch, index + 1, failure));
            if (event.semanticCompletion != null)
                submit(batch.independent, () -> { if (!batch.failed) event.semanticCompletion.finish(event.semanticOutcome == null
                        ? club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result.CLOSED : event.semanticOutcome, null); },
                        failure -> failBatch(batch, index + 1, failure));
        }
        Runnable finish = () -> replay(batch, replay);
        submit(batch.independent, finish, failure -> failBatch(batch, batch.events.size(), failure));
    }

    private InputBatch takeBatch(boolean inspecting) {
        List<QueuedInput> events = new ArrayList<>();
        synchronized (inputs) {
            checkOpen();
            while (!inputs.isEmpty()) events.add(inputs.remove());
        }
        // 有后续动作或释放前位置准备的事件需要顶层Effect结算；已排队批次不能被同步批次越过。
        synchronized (inputs) {
            boolean independent = pendingIndependent > 0
                    || events.stream().anyMatch(QueuedInput::requiresTopLevelSettlement);
            if (independent) pendingIndependent++;
            return new InputBatch(events, independent, inspecting && presentation != null ? preparedNanos : System.nanoTime());
        }
    }

    private void preparePointerRelease(InputBatch batch, QueuedInput event) {
        if (batch.failed || isClosed() || !runtime.alive()) return;
        runtime.pointerRouter().prepareRelease(event.pointer, prepare(batch.frameNanos).geometry);
    }

    private void route(InputBatch batch, QueuedInput event) {
        if (batch.failed) return;
        ScenePointerRouter router = runtime.pointerRouter();
        if (isClosed() || !runtime.alive()) {
            event.discard(router);
            return;
        }
        SceneGeometryView geometry = prepare(batch.frameNanos).geometry;
        runtime.inspectionReason(null, club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.Category.INPUT);
        switch (event.kind) {
            case SEMANTIC:
                event.semanticOutcome = runtime.dispatchSemantic(event.semantic, geometry)
                        ? club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result.APPLIED
                        : club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result.REJECTED;
                break;
            case FOCUS_LOST:
                clearKeySequences();
                try { router.route(ScenePointerEvent.cancel(), geometry); }
                finally { runtime.focusRouter().clear(); }
                break;
            case KEY:
                routeKey(event);
                break;
            case EDIT:
                if (event.followUp != null && event.followUp.navigation != null && event.followUp.navigation.directional())
                    event.directionRange = runtime.focusRouter().captureRange();
                event.consumed = runtime.dispatchEdit(event.edit);
                break;
            case POINTER:
                router.route(event.pointer, geometry);
                break;
            case NAVIGATION:
                if (event.navigation.directional()) {
                    event.directionRange = runtime.focusRouter().captureRange();
                    event.consumed = runtime.dispatchKeyEdit(SceneKeyInterpreter.direction(event.navigation)) != SceneRuntime.KeyResult.IGNORED;
                } else navigate(event.navigation);
                break;
            case ESCAPE:
                event.consumed = runtime.dispatchEscape();
                break;
        }
    }

    private void clearKeySequences() {
        for (KeySequence sequence : keySequences.values()) sequence.close();
        keySequences.clear();
    }

    private void routeKey(QueuedInput event) {
        if (event.key.phase == SceneKeyEvent.Phase.UP) {
            event.consumed = true;
            KeySequence sequence = keySequences.remove(event.key.key);
            if (sequence == null) return;
            try {
                if (sequence.use == KeySequence.Use.PRESS && sequence.range.current() && sequence.continuation != null)
                    sequence.continuation.release();
            } finally { sequence.close(); }
            return;
        }
        if (event.key.repeat) {
            KeySequence sequence = keySequences.get(event.key.key);
            event.keySequence = sequence;
            event.consumed = true;
            if (sequence == null) return;
            if (!sequence.range.current()) { sequence.close(); return; }
            if (sequence.continuation != null) { sequence.continuation.repeat(event.key); return; }
            if (sequence.use == KeySequence.Use.NAVIGATION) {
                SceneNavigationEvent direction = SceneKeyInterpreter.navigation(event.key);
                if (direction != null && direction.directional()) {
                    // 空间方向每次先问当次目标；Tab仍延续原逻辑导航，不转为INSERT_TAB。
                    club.heiqi.uilib.ui.scene.node.SceneNode focused = runtime.focusRouter().focused().get();
                    SceneRuntime.KeyResult result = runtime.dispatchKeyEdit(event.key, held -> sequence.continuation = held);
                    if (result != SceneRuntime.KeyResult.IGNORED) {
                        if (result == SceneRuntime.KeyResult.EDITED) {
                            sequence.use = KeySequence.Use.EDIT;
                            sequence.editing = focused == null ? null : runtime.captureMount(focused);
                        }
                        return;
                    }
                }
                event.consumed = false; return;
            }
            if (sequence.use != KeySequence.Use.EDIT || sequence.editing == null || !sequence.editing.active()
                    || runtime.focusRouter().focused().get() != sequence.editing.node()) return;
            // 编辑续段不再查询应用绑定，也不因当前控件未消费而转为导航或宿主动作。
            runtime.dispatchKeyEdit(event.key);
            return;
        }
        KeySequence previous = keySequences.remove(event.key.key);
        if (previous != null) previous.close();
        club.heiqi.uilib.ui.scene.node.SceneNode focused = runtime.focusRouter().focused().get();
        KeySequence sequence = new KeySequence(runtime.focusRouter().captureRange(), focused == null ? null : runtime.captureMount(focused));
        keySequences.put(event.key.key, sequence);
        event.keySequence = sequence;
        SceneRuntime.KeyResult result = runtime.dispatchKeyEdit(event.key, held -> sequence.continuation = held);
        event.consumed = result != SceneRuntime.KeyResult.IGNORED;
        if (result == SceneRuntime.KeyResult.EDITED) sequence.use = KeySequence.Use.EDIT;
    }

    private void followUp(InputBatch batch, QueuedInput event) {
        if (batch.failed || isClosed() || !runtime.alive() || event.consumed) return;
        // 沿当前事件结算，不能重新排到下一帧而越过已采集的文字。
        switch (event.followUp.kind) {
            case KEY:
                // Effect可能打开或结束模态；先准备当前事实，旧键不能转投新范围。
                prepare(batch.frameNanos);
                if (event.keySequence == null || !event.keySequence.range.current()) return;
                if (event.key.repeat) {
                    if (event.keySequence.use == KeySequence.Use.NAVIGATION) {
                        if (runtime.dispatchKeyCommand(event.key)) return;
                        SceneNavigationEvent repeated = SceneKeyInterpreter.navigation(event.key);
                        if (repeated != null) navigate(repeated);
                    }
                    return;
                }
                if (runtime.dispatchKeyCommand(event.key)) return;
                if (event.key.plainSpace()) {
                    event.keySequence.use = KeySequence.Use.PRESS;
                    event.keySequence.continuation = runtime.beginKeyPress(event.keySequence.editing, event.keySequence.range);
                    return;
                }
                SceneNavigationEvent navigation = SceneKeyInterpreter.navigation(event.key);
                if (navigation != null) { event.keySequence.use = KeySequence.Use.NAVIGATION; navigate(navigation); }
                else if (event.followUp.host != null) event.followUp.host.run();
                break;
            case HOST:
                event.followUp.host.run();
                break;
            case NAVIGATION:
                if (event.directionRange != null) {
                    prepare(batch.frameNanos);
                    if (!event.directionRange.current() || runtime.dispatchKeyCommand(SceneKeyInterpreter.direction(event.followUp.navigation))) return;
                }
                navigate(event.followUp.navigation);
                break;
        }
    }

    private void navigate(SceneNavigationEvent event) {
        if (event == SceneNavigationEvent.ACTIVATE) {
            if (!runtime.dispatchEdit(SceneEditEvent.command(SceneEditEvent.Command.ACTIVATE_ONLY, false, null)))
                runtime.focusRouter().activate();
        }
        else if (event.directional()) runtime.focusRouter().navigate(event);
        else runtime.focusRouter().navigate(event == SceneNavigationEvent.PREVIOUS);
    }

    private void failBatch(InputBatch batch, int firstDiscard, Throwable cause) {
        batch.failed = true;
        finishInspections(null, cause);
        for (QueuedInput input : batch.events) if (input.semanticCompletion != null) input.semanticCompletion.finish(null, cause);
        clearKeySequences();
        if (batch.independent) release(batch);
        ScenePointerRouter router = runtime.pointerRouter();
        // 导航后的Effect也可能失败；即使剩余只有导航，旧物理owner仍必须取消。
        Throwable cleanupFailure = null;
        try {
            router.discardAfterFailure(ScenePointerEvent.cancel());
        } catch (RuntimeException | Error failure) {
            cleanupFailure = failure;
        }
        try {
            runtime.cancelInputFrame(batch.activeInput);
        } catch (RuntimeException | Error failure) {
            if (cleanupFailure == null) cleanupFailure = failure;
            else if (failure != cleanupFailure && !java.util.Arrays.asList(cleanupFailure.getSuppressed()).contains(failure))
                cleanupFailure.addSuppressed(failure);
        }
        // captureLost 扩展可能失败；仍须登记完整物理尾部，避免丢 UP 或第二 DOWN。
        for (int tail = firstDiscard; tail < batch.events.size(); tail++) {
            try {
                batch.events.get(tail).discard(router);
            } catch (RuntimeException | Error failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else if (failure != cleanupFailure) cleanupFailure.addSuppressed(failure);
            }
        }
        // 交给原失败边界保留业务原异常，并将清理异常作为 suppressed 传播。
        if (cleanupFailure instanceof Error) throw (Error) cleanupFailure;
        if (cleanupFailure instanceof RuntimeException) throw (RuntimeException) cleanupFailure;
    }

    private void replay(InputBatch batch, boolean replay) {
        try {
            if (!batch.failed && !isClosed() && runtime.alive()) {
                ScenePresentation current = replay ? prepare(batch.frameNanos, batch.activeInput) : prepare(batch.frameNanos);
                if (replay) host.replay(current.paint);
                synchronized (inputs) {
                    if (inspections.isEmpty()) return;
                }
                finishInspections(runtime.inspect(current, metrics), null);
            } else if (!batch.failed) finishInspections(club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.CLOSED, null);
        } finally {
            if (batch.independent) release(batch);
        }
    }

    private void submit(
            boolean independent, Runnable action, java.util.function.Consumer<Throwable> failed) {
        if (independent) runtime.domain().enqueue(action, failed);
        else runtime.domain().execute(action, failed);
    }

    public ScenePresentation current() {
        runtime.domain().checkAccess();
        synchronized (inputs) {
            checkOpen();
        }
        // 读取当前呈现不推进几何动画；下一宿主frame才提供新的时间样本。
        return prepare(presentation == null ? System.nanoTime() : preparedNanos);
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("帧管线已释放");
    }

    private boolean isClosed() {
        synchronized (inputs) {
            return closed;
        }
    }

    /**
     * 页面关闭时丢弃未处理输入并取消owner，不执行frame或replay；不关闭业务runtime。
     * 生命周期所有者随后关闭runtime；重复close安全，pending域任务也不再访问host。
     */
    @Override
    public void close() {
        List<QueuedInput> pending = new ArrayList<>();
        synchronized (inputs) {
            if (closed) return;
            closed = true;
            while (!inputs.isEmpty()) pending.add(inputs.remove());
        }
        finishInspections(club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.CLOSED, null);
        runtime.domain()
                .execute(
                        () -> {
                            clearKeySequences();
                            ScenePointerRouter router = runtime.pointerRouter();
                            // close不执行业务，但不能丢掉已采集的第二键；另一管线仍共享该物理段。
                            Throwable failure = null;
                            for (QueuedInput event : pending) try { event.discard(router); }
                            catch (RuntimeException | Error next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
                            try { router.discardAfterFailure(ScenePointerEvent.cancel()); }
                            catch (RuntimeException | Error next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
                            try { host.semanticsChanged(runtime.clearSemantics()); }
                            catch (RuntimeException | Error next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
                            finally { presentation = null; paint.clear(); layout.clearCaches(); }
                            if (failure instanceof Error) throw (Error) failure;
                            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
                        });
    }

    private SceneGeometry layoutGeometry(int w, int h, long metricsRevision) {
        SceneGeometry geometry =
                runtime.domain()
                        .readOnly(
                                () ->
                                        layout.layout(
                                                runtime.roots(),
                                                w,
                                                h,
                                                metricsRevision,
                                                runtime.wholeMeasureRevision()));
        if (runtime.hasLayoutMotion()) {
            SceneGeometry target = geometry;
            boolean sizes = runtime.prepareLayoutSizes(target);
            SceneGeometry allocated = sizes ? runtime.domain().readOnly(() -> layout.layout(runtime.roots(), w, h,
                    metricsRevision, runtime.wholeMeasureRevision(), runtime.layoutPresentation(false))) : target;
            boolean positions = runtime.prepareLayoutPositions(allocated);
            geometry = positions ? runtime.domain().readOnly(() -> layout.layout(runtime.roots(), w, h,
                    metricsRevision, runtime.wholeMeasureRevision(), runtime.layoutPresentation(true))) : allocated;
            runtime.publishLayoutGeometry(geometry);
        }
        // 布局只读，唯一runtime记录在布局返回后发布；显露重排也复用这一入口。
        runtime.prepareScrollPositions(geometry);
        return geometry;
    }

    private ScenePresentation prepare(long frameNanos) { return prepare(frameNanos, null); }

    private ScenePresentation prepare(long frameNanos, SceneRuntime.InputFrame activeInput) {
        return prepare(frameNanos, activeInput, true);
    }

    private ScenePresentation prepare(long frameNanos, SceneRuntime.InputFrame activeInput, boolean advanceInput) {
        runtime.inspectionBegin();
        boolean success = false;
        try {
            ScenePresentation result = prepareCurrent(frameNanos, activeInput, advanceInput);
            success = true; return result;
        } finally { runtime.inspectionEnd(success, generation); }
    }

    private ScenePresentation prepareCurrent(long frameNanos, SceneRuntime.InputFrame activeInput, boolean advanceInput) {
        preparedNanos = frameNanos;
        int w = host.width(), h = host.height();
        runtime.prepareViewport(w, h);
        long nextMetrics = host.metricsRevision();
        runtime.prepareMetricsRevision(nextMetrics);
        runtime.preparePresentation(frameNanos);
        SceneGeometry geometry = prepareGeometry(frameNanos, w, h, nextMetrics);
        if (activeInput != null && prepareActiveInput(geometry, frameNanos, activeInput, advanceInput))
            return prepare(frameNanos, activeInput, false);
        runtime.prepareTooltips(geometry, text, nextMetrics);
        publishPresentation(geometry, w, h, nextMetrics);
        runtime.pointerRouter().prepare(presentation.geometry);
        return presentation;
    }

    /** 声明、范围与主动显露使用同一收敛循环；不在绘制收集中补排版。 */
    private SceneGeometry prepareGeometry(long frameNanos, int w, int h, long nextMetrics) {
        boolean geometryDirty = geometryDirty(w, h, nextMetrics);
        SceneGeometry prepared = geometryDirty ? null : presentation.geometry;
        long[] arrangedRevision = {runtime.geometryRevision()};
        do {
            if (geometryDirty) {
                prepared = layoutGeometry(w, h, nextMetrics);
                arrangedRevision[0] = runtime.geometryRevision();
            }
            runtime.prepareInputGeometry(prepared);
            // 范围投影沿原绑定结算；只有其真实布局依赖变化才继续求下一批事实。
            runtime.preparePresentation(frameNanos);
            geometryDirty = arrangedRevision[0] != runtime.geometryRevision();
            if (geometryDirty) continue;
            prepared = runtime.revealNavigation(prepared, () -> {
                SceneGeometry updated = layoutGeometry(w, h, nextMetrics);
                arrangedRevision[0] = runtime.geometryRevision();
                runtime.prepareInputGeometry(updated);
                return updated;
            });
            runtime.preparePresentation(frameNanos);
            geometryDirty = arrangedRevision[0] != runtime.geometryRevision();
        } while (geometryDirty);
        if (presentation == null || revision != runtime.revision() || prepared != presentation.geometry) {
            SceneGeometry located = prepared;
            prepared = runtime.domain().readOnly(() -> layout.refreshTextStyles(located));
            runtime.prepareInputGeometry(prepared);
        }
        return prepared;
    }

    private boolean geometryDirty(int w, int h, long nextMetrics) {
        return presentation == null || geometryRevision != runtime.geometryRevision()
                || width != w || height != h || metrics != nextMetrics;
    }

    /** 一批只推进一次时间；后续重排只同步本批参与者，并复用原失败取消记录。 */
    private boolean prepareActiveInput(SceneGeometry geometry, long frameNanos,
            SceneRuntime.InputFrame activeInput, boolean advanceInput) {
        long beforeInput = runtime.revision();
        if (advanceInput) runtime.advanceInputFrame(geometry, frameNanos, activeInput);
        else runtime.refreshInputFrameGeometry(geometry, activeInput);
        // value 绑定可能只登记 dirtyBindings，结算后才能判断几何或绘制失效。
        runtime.preparePresentation(frameNanos);
        return beforeInput != runtime.revision();
    }

    private boolean paintDirty(SceneGeometry geometry, int w, int h, long nextMetrics) {
        return presentation == null || revision != runtime.revision() || geometry != presentation.geometry
                || width != w || height != h || metrics != nextMetrics
                || System.nanoTime() >= paint.nextPaintNanos();
    }

    private void publishPresentation(SceneGeometry geometry, int w, int h, long nextMetrics) {
        club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot semantic = runtime.semanticSnapshot(geometry);
        boolean semanticChanged = presentation == null || presentation.semantics != semantic;
        boolean changedPaint = paintDirty(geometry, w, h, nextMetrics);
        if (!changedPaint && !semanticChanged) return;
        if (changedPaint) runtime.inspectionPaintCollected();
        PaintPlan plan = changedPaint ? runtime.domain().readOnly(() -> paint.collect(geometry, nextMetrics)) : presentation.paint;
        if (changedPaint) runtime.capturePresencePaint(paint.presenceSnapshots(), geometry);
        presentation = new ScenePresentation(++generation, geometry, plan, semantic);
        if (semanticChanged) host.semanticsChanged(semantic);
        revision = runtime.revision();
        geometryRevision = runtime.geometryRevision();
        width = w;
        height = h;
        metrics = nextMetrics;
    }
}
