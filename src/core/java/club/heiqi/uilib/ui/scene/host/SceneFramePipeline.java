package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
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
    private final SceneKeyboardInput keyboard;

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

        static FollowUp key() { return new FollowUp(Kind.KEY, null, null); }

        static FollowUp navigation(SceneNavigationEvent event) {
            return new FollowUp(Kind.NAVIGATION, null, Objects.requireNonNull(event, "fallback"));
        }
    }

    private static final class QueuedInput {
        final InputKind kind;
        final ScenePointerEvent pointer;
        final SceneNavigationEvent navigation;
        final SceneEditEvent edit;
        final SceneKeyboardInput.Input key;
        SceneFocusRouter.RangeHandle directionRange;
        final FollowUp followUp;
        boolean consumed;
        club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest semantic;
        SceneSemanticCompletion semanticCompletion;
        club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result semanticOutcome;

        private QueuedInput(InputKind kind, ScenePointerEvent pointer, SceneNavigationEvent navigation,
                            SceneEditEvent edit, FollowUp followUp, SceneKeyboardInput.Input key) {
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
            return new QueuedInput(InputKind.KEY, null, null, null, FollowUp.key(), new SceneKeyboardInput.Input(event, fallback));
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
    private static final long INPUT_INTERVAL_NANOS = (1_000_000_000L + 299) / 300;
    private boolean inputSampled;
    private long lastInputNanos;

    private static final class InputBatch {
        final SceneRuntime.InputFrame activeInput = new SceneRuntime.InputFrame();
        final List<QueuedInput> events;
        final List<java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot>> inspectionRequests;
        final boolean independent, advanceInput;
        final long frameNanos;
        boolean failed, released;

        InputBatch(List<QueuedInput> events, boolean independent, long frameNanos, boolean advanceInput,
                List<java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot>> inspectionRequests) {
            this.events = events; this.inspectionRequests = inspectionRequests;
            this.independent = independent; this.frameNanos = frameNanos; this.advanceInput = advanceInput;
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
        synchronized (inputs) { pending = new ArrayList<>(inspections); }
        finishInspections(pending, snapshot, failure);
    }

    private void finishInspections(List<java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot>> pending,
            club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot snapshot, Throwable failure) {
        synchronized (inputs) { inspections.removeAll(pending); }
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
    private SceneGeometry inputGeometry, capturedGeometry;
    private long inputRevision = -1, inputGeometryRevision = -1, inputMetrics = -1, capturedRevision = -1;
    private int inputWidth = -1, inputHeight = -1;
    private club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot publishedSemantics;
    private long revision = -1, metrics = -1, generation;
    private int width = -1, height = -1;
    private long preparedNanos;

    public SceneFramePipeline(SceneRuntime runtime, SceneHost host, SceneTextMeasurer measurer) {
        this.runtime = runtime;
        this.host = host;
        this.text=measurer;
        this.keyboard = new SceneKeyboardInput(runtime);
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
        if (isClosed()) return;
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
            long now = inspecting && inputGeometry != null ? preparedNanos : host.frameNanos();
            boolean advance = !inspecting && (!inputSampled || now - lastInputNanos >= INPUT_INTERVAL_NANOS);
            // 连续采样可等到下个输入周期；边界事件带着完整前序立即结算，不合并MOVE或越序。
            boolean boundary = !advance && inputs.stream().anyMatch(event -> event.kind != InputKind.POINTER
                    || event.pointer.kind != ScenePointerEvent.Kind.MOVE);
            if (advance || boundary || inspecting) while (!inputs.isEmpty()) events.add(inputs.remove());
            if (advance) { inputSampled = true; lastInputNanos = now; }
            boolean independent = pendingIndependent > 0
                    || events.stream().anyMatch(QueuedInput::requiresTopLevelSettlement);
            if (independent) pendingIndependent++;
            return new InputBatch(events, independent, now, advance,
                    inspecting ? new ArrayList<>(inspections) : java.util.Collections.emptyList());
        }
    }

    private void preparePointerRelease(InputBatch batch, QueuedInput event) {
        if (batch.failed || isClosed() || !runtime.alive()) return;
        SceneGeometry geometry = prepareInput(batch.frameNanos);
        if (geometry != null) runtime.pointerRouter().prepareRelease(event.pointer, geometry);
    }

    private void route(InputBatch batch, QueuedInput event) {
        if (batch.failed) return;
        ScenePointerRouter router = runtime.pointerRouter();
        if (isClosed() || !runtime.alive()) {
            event.discard(router);
            return;
        }
        SceneGeometryView geometry = prepareInput(batch.frameNanos);
        if (geometry == null) { event.discard(router); return; }
        runtime.inspectionReason(null, club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.Category.INPUT);
        switch (event.kind) {
            case SEMANTIC:
                event.semanticOutcome = runtime.dispatchSemantic(event.semantic, geometry)
                        ? club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result.APPLIED
                        : club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result.REJECTED;
                break;
            case FOCUS_LOST:
                keyboard.clear();
                try { router.route(ScenePointerEvent.cancel(), geometry); }
                finally { runtime.focusRouter().clear(); }
                break;
            case KEY:
                event.consumed = keyboard.route(event.key);
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
                } else keyboard.navigate(event.navigation);
                break;
            case ESCAPE:
                event.consumed = runtime.dispatchEscape();
                break;
        }
    }

    private void followUp(InputBatch batch, QueuedInput event) {
        if (batch.failed || isClosed() || !runtime.alive() || event.consumed) return;
        // 沿当前事件结算，不能重新排到下一帧而越过已采集的文字。
        switch (event.followUp.kind) {
            case KEY:
                // Effect 可能改变范围；准备后仍由原按键续段核验，不转投新范围。
                if (prepareInput(batch.frameNanos) != null) keyboard.afterEffects(event.key);
                break;
            case HOST:
                event.followUp.host.run();
                break;
            case NAVIGATION:
                if (event.directionRange != null) {
                    if (prepareInput(batch.frameNanos) == null) return;
                    if (!event.directionRange.current() || runtime.dispatchKeyCommand(SceneKeyInterpreter.direction(event.followUp.navigation))) return;
                }
                keyboard.navigate(event.followUp.navigation);
                break;
        }
    }

    private void failBatch(InputBatch batch, int firstDiscard, Throwable cause) {
        batch.failed = true;
        finishInspections(null, cause);
        for (QueuedInput input : batch.events) if (input.semanticCompletion != null) input.semanticCompletion.finish(null, cause);
        keyboard.clear();
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
                ScenePresentation current = replay && batch.advanceInput ? prepare(batch.frameNanos, batch.activeInput) : prepare(batch.frameNanos);
                if (current == null) {
                    finishInspections(batch.inspectionRequests, club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.CLOSED, null);
                    return;
                }
                if (replay) host.replay(current.paint);
                // 只有检查批次覆盖了请求前序；更早已排队的普通回放不能完成后来请求。
                if (!batch.inspectionRequests.isEmpty())
                    finishInspections(batch.inspectionRequests, runtime.inspect(current, metrics), null);
            } else if (!batch.failed) finishInspections(batch.inspectionRequests, club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.CLOSED, null);
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
        ScenePresentation current = prepare(inputGeometry == null ? host.frameNanos() : preparedNanos);
        if (current == null) throw new IllegalStateException("页面已结束");
        return current;
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("帧管线已释放");
    }

    private boolean available() { return !isClosed() && runtime.alive(); }

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
                            keyboard.clear();
                            ScenePointerRouter router = runtime.pointerRouter();
                            // close不执行业务，但不能丢掉已采集的第二键；另一管线仍共享该物理段。
                            Throwable failure = null;
                            for (QueuedInput event : pending) try { event.discard(router); }
                            catch (RuntimeException | Error next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
                            try { router.discardAfterFailure(ScenePointerEvent.cancel()); }
                            catch (RuntimeException | Error next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
                            try { host.semanticsChanged(runtime.clearSemantics()); }
                            catch (RuntimeException | Error next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
                            finally { presentation = null; inputGeometry = capturedGeometry = null; publishedSemantics = null; paint.clear(); layout.clearCaches(); }
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

    /** 输入只要求当前几何、语义与退场片段；整页 PaintPlan 留给 current/replay。 */
    private SceneGeometry prepareInput(long frameNanos) {
        runtime.inspectionBegin(); boolean success = false;
        try {
            SceneGeometry geometry = prepareFacts(frameNanos);
            if (geometry == null) return null;
            long nextMetrics = inputMetrics;
            runtime.prepareTooltips(geometry, text, nextMetrics);
            if (capturedRevision != runtime.revision() || capturedGeometry != geometry || System.nanoTime() >= paint.nextPaintNanos()) {
                runtime.domain().readOnly(() -> { paint.capturePresence(geometry, nextMetrics); return null; });
                runtime.capturePresencePaint(paint.presenceSnapshots(), geometry);
                capturedRevision = runtime.revision(); capturedGeometry = geometry;
            }
            publishSemantics(runtime.semanticSnapshot(geometry));
            // 宿主通知和owner取消允许同步关闭页面；准备出口只交付仍可路由的事实。
            if (!available()) return null;
            runtime.pointerRouter().prepare(geometry);
            success = true; return available() ? geometry : null;
        } finally { runtime.inspectionEnd(success, generation); }
    }
    private void publishSemantics(club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot semantic) {
        if (publishedSemantics != semantic) { publishedSemantics = semantic; host.semanticsChanged(semantic); }
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

    /** 输入与可观察呈现共用环境、声明及几何；此处不收集整页绘制，也不通知外部宿主。 */
    private SceneGeometry prepareFacts(long frameNanos) {
        if (!available()) return null;
        preparedNanos = frameNanos;
        int w = host.width(), h = host.height();
        runtime.prepareViewport(w, h);
        long nextMetrics = host.metricsRevision();
        runtime.prepareMetricsRevision(nextMetrics);
        runtime.preparePresentation(frameNanos);
        if (!available()) return null;
        SceneGeometry geometry = prepareGeometry(frameNanos, w, h, nextMetrics);
        return available() ? geometry : null;
    }

    private ScenePresentation prepareCurrent(long frameNanos, SceneRuntime.InputFrame activeInput, boolean advanceInput) {
        SceneGeometry geometry = prepareFacts(frameNanos);
        if (geometry == null) return null;
        if (activeInput != null) {
            boolean changed = prepareActiveInput(geometry, frameNanos, activeInput, advanceInput);
            if (!available()) return null;
            if (changed) return prepare(frameNanos, activeInput, false);
        }
        runtime.prepareTooltips(geometry, text, inputMetrics);
        publishPresentation(geometry, inputWidth, inputHeight, inputMetrics);
        if (!available()) return null;
        runtime.pointerRouter().prepare(presentation.geometry);
        return available() ? presentation : null;
    }

    /** 声明、范围与主动显露使用同一收敛循环；不在绘制收集中补排版。 */
    private SceneGeometry prepareGeometry(long frameNanos, int w, int h, long nextMetrics) {
        boolean geometryDirty = geometryDirty(w, h, nextMetrics);
        SceneGeometry prepared = geometryDirty ? null : inputGeometry;
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
        if (inputGeometry == null || inputRevision != runtime.revision() || prepared != inputGeometry) {
            SceneGeometry located = prepared;
            prepared = runtime.domain().readOnly(() -> layout.refreshTextStyles(located));
            runtime.prepareInputGeometry(prepared);
        }
        inputGeometry = prepared; inputRevision = runtime.revision(); inputGeometryRevision = runtime.geometryRevision();
        inputWidth = w; inputHeight = h; inputMetrics = nextMetrics;
        return prepared;
    }

    private boolean geometryDirty(int w, int h, long nextMetrics) {
        return inputGeometry == null || inputGeometryRevision != runtime.geometryRevision()
                || inputWidth != w || inputHeight != h || inputMetrics != nextMetrics;
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
        if (changedPaint) {
            runtime.capturePresencePaint(paint.presenceSnapshots(), geometry);
            capturedRevision = runtime.revision(); capturedGeometry = geometry;
        }
        presentation = new ScenePresentation(++generation, geometry, plan, semantic);
        revision = runtime.revision();
        width = w;
        height = h;
        metrics = nextMetrics;
        publishSemantics(semantic);
    }
}
