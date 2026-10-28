package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneHitTester;

import java.util.HashSet;
import java.util.Set;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 维护 runtime 唯一指针手势，复用 HitTester 与挂载 token。
 * 操作限所属 ReactiveDomain，帧管线在路由前准备同代几何。
 * owner 失活时释放目标，物理集合保留至实际释放，以吞掉本段第二键尾部。 */
public final class ScenePointerRouter implements AutoCloseable {
    private final SceneRuntime runtime;
    private final club.heiqi.uilib.ui.reactive.Signal<SceneNode> hoverTarget, pressedTarget;
    public club.heiqi.uilib.ui.reactive.ReadableSignal<SceneNode> hoverTarget() { return hoverTarget; }
    public club.heiqi.uilib.ui.reactive.ReadableSignal<SceneNode> pressedTarget() { return pressedTarget; }
    private final SceneHitTester hits = new SceneHitTester();
    private final SceneWheelRouting wheel;
    private final Set<Integer> buttons = new HashSet<>();
    private MountHandle owner;
    private Object ownerTarget;
    private boolean capturedContact;
    SceneNode inspectionPressed() { return owner != null && owner.active() ? owner.node() : null; }
    SceneNode inspectionCaptured() { return capturedContact ? inspectionPressed() : null; }
    SceneNode inspectionHovered() { return hoverTarget.get(); }
    java.util.List<Integer> inspectionButtons() {
        java.util.List<Integer> result = new java.util.ArrayList<>(buttons); java.util.Collections.sort(result); return result;
    }
    private int x, y;
    private boolean closed, positionKnown;
    private long positionRevision;
    private final HoverProjection hover = new HoverProjection(), passive = new HoverProjection();
    private SceneNode paintedPassiveTarget;
    private SceneGeometryView preparedGeometry;
    private final java.util.Set<HoverInterest> hoverInterests = new java.util.LinkedHashSet<>();
    private final java.util.Map<SceneNode, Integer> hoverNodes = new java.util.IdentityHashMap<>();

    /** 将正式被动命中投影到逻辑祖先，供挂载期悬停订阅使用。 */
    final class HoverInterest implements AutoCloseable {
        final SceneNode node;
        final Runnable changed;
        boolean inside;
        HoverInterest(SceneNode node,Runnable changed) {this.node=node;this.changed=changed;}
        @Override public void close() {
            if (!hoverInterests.remove(this)) return;
            int count = hoverNodes.get(node);
            if (count == 1) hoverNodes.remove(node); else hoverNodes.put(node, count - 1);
            passive.clear();
        }
    }
    HoverInterest observeHover(SceneNode node,Runnable changed) {
        HoverInterest interest=new HoverInterest(node,changed);hoverInterests.add(interest);
        hoverNodes.put(node, hoverNodes.getOrDefault(node, 0) + 1); passive.clear(); return interest;
    }
    private void publishHover(SceneGeometryView geometry) {
        SceneNode next = hovered(geometry), nextPassive = passiveHovered(geometry);
        // 绘制依赖命中目标，坐标本身只用于路由与提示计时。同区域高频 MOVE 不重建整页计划。
        if (hoverTarget.get() != next || paintedPassiveTarget != nextPassive) runtime.invalidatePaint();
        hoverTarget.set(next);
        paintedPassiveTarget = nextPassive;
        if(hoverInterests.isEmpty())return;
        SceneNode target = closed || !positionKnown ? null : passive.within(geometry.world());
        for(HoverInterest interest:new java.util.ArrayList<>(hoverInterests)) {
            boolean inside=false;
            for(SceneNode cursor=target;cursor!=null;cursor=cursor.parent())
                if(cursor==interest.node) {inside=true;break;}
            if(interest.inside!=inside) {interest.inside=inside;interest.changed.run();}
        }
    }

    /** 同一呈现中的控件共用当前指针命中投影。
     * 几何身份、声明/交互代际和坐标共同失效，仍由正式HitTester裁决。只保留各一种当前查询。 */
    private final class HoverProjection {
        private SceneGeometryView geometry;
        private long revision;
        private int px, py;
        private SceneNode node, within;
        SceneNode within(SceneGeometryView next) { get(next, true); return within; }
        SceneNode get(SceneGeometryView next, boolean passiveQuery) {
            long nextRevision = runtime.revision();
            if (geometry != next || revision != nextRevision || px != x || py != y) {
                if (passiveQuery) {
                    SceneHitTester.PassiveTargets targets = hits.passiveTargets(runtime, next, x, y, hoverNodes::containsKey);
                    node = targets.control; within = targets.within;
                } else node = hits.hit(runtime, next, x, y);
                geometry = next; revision = nextRevision; px = x; py = y;
            }
            return node;
        }
        void clear() { geometry = null; node = null; within = null; }
    }

    /** 将当前指针位置投影为有效范围内的悬停目标。 */
    public SceneNode hovered(SceneGeometryView geometry) {
        runtime.domain().checkAccess();
        return closed || !positionKnown ? null : hover.get(geometry.world(), false);
    }

    /** 与 click 命中分开；仅投影当前指针、范围及同代 geometry。 */
    public SceneNode passiveHovered(SceneGeometryView geometry) {
        runtime.domain().checkAccess();
        return closed || !positionKnown ? null : passive.get(geometry.world(), true);
    }

    long positionRevision(){return positionRevision;}
    SceneNode tooltipHovered(SceneGeometryView geometry){
        if(closed||!positionKnown)return null;
        return passive.within(geometry.world());
    }
    /** 按已发布提示矩形与连接走廊保留被动提示，click/wheel 继续使用正式命中。 */
    boolean observesTooltip(club.heiqi.uilib.ui.scene.layout.LayoutBox anchor,club.heiqi.uilib.ui.scene.layout.LayoutBox popup){
        if(closed||!positionKnown)return false;
        if(popup.contains(x,y))return true;
        if(popup.y+popup.height<=anchor.y||popup.y>=anchor.y+anchor.height){
            int lo=Math.min(popup.y+popup.height,anchor.y+anchor.height),hi=Math.max(popup.y,anchor.y);
            int left=Math.min(popup.x,anchor.x),right=Math.max(popup.x+popup.width,anchor.x+anchor.width);
            return y>=lo&&y<=hi&&x>=left&&x<=right;
        }
        int lo=Math.min(popup.x+popup.width,anchor.x+anchor.width),hi=Math.max(popup.x,anchor.x);
        int top=Math.min(popup.y,anchor.y),bottom=Math.max(popup.y+popup.height,anchor.y+anchor.height);
        return x>=lo&&x<=hi&&y>=top&&y<=bottom;
    }

    public boolean pressed(SceneNode node) {
        runtime.domain().checkAccess();
        return !closed && owner != null && owner.active() && owner.node() == node;
    }

    ScenePointerRouter(SceneRuntime runtime) {
        this.runtime = runtime;
        hoverTarget = runtime.domain().signal(null);
        pressedTarget = runtime.domain().signal(null);
        wheel = new SceneWheelRouting(runtime, hits);
    }

    /** owner是按下呈现的变化来源；所有释放出口只失效paint，保留物理尾部。 */
    private void setOwner(MountHandle target) {
        if (owner == target) return;
        owner = target;
        pressedTarget.set(target == null ? null : target.node());
        if (target == null) { ownerTarget = null; capturedContact = false; }
        runtime.invalidatePaint();
    }

    /** 先结束 owner 再通知内部清理，使取消支持重入核验。 */
    private void cancelOwner() {
        MountHandle cancelled = owner;
        setOwner(null);
        if (cancelled != null) runtime.dispatchControlCaptureLost(cancelled.node());
    }

    /** 几何准备后重验 owner 资格，失活时结束原捕获并保留物理占位。 */
    public void prepare(SceneGeometryView geometry) {
        geometry = geometry.world();
        checkAccess();
        if (preparedGeometry != geometry) { hover.clear(); passive.clear(); preparedGeometry = geometry; }
        revalidate();
        if (owner != null && (geometry.get(owner.node()) == null ? !ownedCaptureScope(owner.node()) : geometry.get(owner.node()).inverse == null)) cancelOwner();
        wheel.prepare(geometry, runtime.inputNanos());
        publishHover(geometry);
    }

    /** 生命周期与声明生效后即时复核当前 owner 及原有效范围。
     * 资格失效即结束原捕获，后续恢复须等待新的完整物理段。 */
    void revalidate() {
        runtime.domain().checkAccess();
        wheel.revalidate();
        if (owner != null
                && (!owner.active()
                        || !captureInteractive(owner.node())
                        || !runtime.pointerTargetValid(owner.node(), ownerTarget))) cancelOwner();
    }

    boolean captureInteractive(SceneNode node) {
        return runtime.interactive(node) || ownedCaptureScope(node);
    }

    private boolean ownedCaptureScope(SceneNode node) {
        if (!runtime.locallyInteractive(node) || node.control() == null) return false;
        SceneNode scope = runtime.domain().readOnly(() -> node.control().capturedPointerScope(node));
        if (scope == null || scope != runtime.activeModal()) return false;
        for (SceneNode parent = scope.parent(); parent != null; parent = parent.parent())
            if (parent == node) return true;
        return false;
    }

    /** 将释放坐标交给原 owner 更新位置意图，原物理段保持至 routeUp。
     * 帧管线结算本操作与 Effect 后重新 prepare，再正式派发 UP。 */
    public void prepareRelease(ScenePointerEvent event, SceneGeometryView geometry) {
        checkAccess();
        prepare(geometry);
        if (event.kind != ScenePointerEvent.Kind.UP || event.button != 0
                || !buttons.contains(event.button) || owner == null || !owner.active()) return;
        updatePointerPosition(event);
        runtime.prepareControlPointerRelease(owner.node(), event, geometry);
    }

    /** 按采集顺序准备当前范围并分派事件，所有阶段共用本路由器的唯一手势状态。 */
    public void route(ScenePointerEvent event, SceneGeometryView geometry) {
        geometry = geometry.world(); event = event.world();
        checkAccess();
        prepare(geometry);
        if (event.kind == ScenePointerEvent.Kind.DOWN || event.kind == ScenePointerEvent.Kind.CANCEL) runtime.cancelScrollMotions();
        if (event.kind == ScenePointerEvent.Kind.MOVE || event.kind == ScenePointerEvent.Kind.SCROLL)
            wheel.pointerMoved(event.x, event.y);
        else wheel.clear();
        updatePointerPosition(event);
        if(event.kind==ScenePointerEvent.Kind.MOVE||event.kind==ScenePointerEvent.Kind.CANCEL)positionRevision++;
        if(event.kind==ScenePointerEvent.Kind.DOWN||event.kind==ScenePointerEvent.Kind.SYNTHETIC_ACTIVATION)runtime.tooltips().pointerPressed();
        try {
            switch (event.kind) {
                case DOWN:
                    routeDown(event, geometry);
                    return;
                case MOVE:
                    routeMove(event, geometry);
                    return;
                case UP:
                    routeUp(event, geometry);
                    return;
                case CANCEL:
                    cancelOwner();
                    return;
                case ALL_BUTTONS_RELEASED:
                    releasePhysicalButtons();
                    return;
                case SCROLL:
                    routeScroll(event, geometry);
                    return;
                case SYNTHETIC_ACTIVATION:
                    routeSyntheticActivation(event, geometry);
                    return;
                default:
                    throw new AssertionError(event.kind);
            }
        } finally {
            publishHover(geometry);
        }
    }

    /** 交互投影只使绘制失效；取消丢弃位置，物理全部释放仍保留最后位置。 */
    private void updatePointerPosition(ScenePointerEvent event) {
        if (event.kind == ScenePointerEvent.Kind.CANCEL) {
            positionKnown = false;
            hover.clear();
            passive.clear();
            runtime.invalidatePaint();
            return;
        }
        if (event.kind == ScenePointerEvent.Kind.ALL_BUTTONS_RELEASED) return;
        positionKnown = true;
        x = event.x;
        y = event.y;
        if (event.kind == ScenePointerEvent.Kind.DOWN || event.kind == ScenePointerEvent.Kind.UP)
            runtime.invalidatePaint();
    }

    /** 每次新按下都占物理位置，只有空闲时的主键能建立手势或清除外部焦点。 */
    private void routeDown(ScenePointerEvent event, SceneGeometryView geometry) {
        boolean idle = buttons.isEmpty();
        if (!buttons.add(event.button)) return;
        if (!idle || event.button != 0) return;
        SceneHitTester.Target target = hits.target(runtime, geometry, x, y);
        if (target == null) {
            blurOutsideFocus(geometry);
            return;
        }
        runtime.focusRouter().pointerInput();
        SceneNode captured = capturePointerTarget(target, geometry);
        dispatchCapturedDown(captured, event, geometry);
    }

    /** 只沿命中子树选择明确的祖先 activator；无声明时保留原子控件及其目标身份。 */
    private SceneNode capturePointerTarget(SceneHitTester.Target target, SceneGeometryView geometry) {
        SceneNode hit = target.node;
        Object identity = target.identity;
        for (SceneNode ancestor = hit.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (!runtime.interactive(ancestor) || ancestor.control() == null) continue;
            SceneNode candidate = ancestor;
            Object delegated = runtime.domain().readOnly(() ->
                    candidate.control().pointerActivator(candidate, target.node, geometry.asControl(candidate),
                            (int) Math.round(geometry.mapX(candidate, x, y)), (int) Math.round(geometry.mapY(candidate, x, y))));
            if (delegated != null && runtime.pointerTargetValid(candidate, delegated)) {
                hit = candidate;
                identity = delegated;
                break;
            }
        }
        ownerTarget = identity;
        setOwner(runtime.captureMount(hit));
        return hit;
    }

    private void dispatchCapturedDown(SceneNode captured, ScenePointerEvent event, SceneGeometryView geometry) {
        if (captured.control() == null || !captured.control().capturesPointer()) return;
        capturedContact = true;
        if (captured.clickFocus() && captured.focusable())
            runtime.focusRouter().request(runtime.captureMount(captured));
        runtime.dispatchControlPointer(captured, event, geometry);
    }

    private void routeMove(ScenePointerEvent event, SceneGeometryView geometry) {
        if (owner != null && owner.active())
            runtime.dispatchControlPointer(owner.node(), event, geometry);
    }

    /** 已知主键释放时核验原目标，handler 前结束 owner，确保本段只激活一次。 */
    private void routeUp(ScenePointerEvent event, SceneGeometryView geometry) {
        if (!buttons.remove(event.button)) return;
        if (event.button != 0 || owner == null) return;
        MountHandle activation = owner;
        Object identity = ownerTarget;
        setOwner(null);
        if (activation.active())
            runtime.dispatchControlPointer(activation.node(), event, geometry);
        SceneHitTester.Target release = hits.target(runtime, geometry, x, y);
        if (activation.active()
                && release != null
                && release.node == activation.node()
                && release.identity == identity)
            runtime.dispatchPointerTarget(activation.node(), identity);
    }

    private void releasePhysicalButtons() {
        try {
            cancelOwner();
        } finally {
            buttons.clear();
        }
    }

    private void routeScroll(ScenePointerEvent event, SceneGeometryView geometry) {
        wheel.route(event, geometry);
    }

    private void routeSyntheticActivation(ScenePointerEvent event, SceneGeometryView geometry) {
        if (!buttons.isEmpty()) return;
        SceneHitTester.Target target = hits.target(runtime, geometry, event.x, event.y);
        if (target != null) runtime.dispatchPointerTarget(target.node, target.identity);
    }

    /** 首次主键空命中时按同代 clip 与 viewport 处理失焦。
     * 物理占位保留至释放，尾部只结束本段。 */
    private void blurOutsideFocus(SceneGeometryView geometry) {
        if (!geometry.viewport().contains(x, y)) return;
        SceneNode modal = runtime.activeModal();
        if (modal != null && !geometry.contains(modal, x, y)) {
            boolean owned = false;
            for (SceneGeometryView.Entry entry : geometry.ordered())
                if (geometry.layerBoundary(entry.node) != null && runtime.interactive(entry.node) && geometry.contains(entry.node, x, y)) {
                    owned = true;
                    break;
                }
            if (!owned) return;
        }
        runtime.focusRouter().pointerInput();
        SceneNode focused = runtime.focusRouter().focused().get();
        if (focused != null && !geometry.contains(focused, x, y)) runtime.focusRouter().clear();
    }

    private boolean inside(SceneGeometryView.Entry entry) {
        return entry != null && entry.box.contains(x, y)
                && (entry.clip == null || entry.clip.contains(x, y));
    }

    /** handler 失败后登记本批完整物理尾部，业务原异常由管线传播。
     * 第二 DOWN 保留物理占位，后续帧继续吞到实际释放。 */
    public void discardAfterFailure(ScenePointerEvent event) {
        runtime.domain().checkAccess();
        wheel.clear();
        try { cancelOwner(); }
        finally {
            // 清理扩展失败后仍保留完整物理尾部。
            if (!closed) {
                switch (event.kind) {
                    case DOWN:
                        buttons.add(event.button);
                        break;
                    case UP:
                        buttons.remove(event.button);
                        break;
                    case ALL_BUTTONS_RELEASED:
                        buttons.clear();
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** 当前范围捕获消费 ESC 后结束目标，保留物理占位至实际释放。 */
    boolean cancelOnEscape() {
        checkAccess();
        wheel.clear();
        revalidate();
        if (owner == null || owner.node().control() == null
                || !owner.node().control().cancelCaptureOnEscape(owner.node())) return false;
        cancelOwner();
        return true;
    }

    boolean idle() {
        runtime.domain().checkAccess();
        return !closed && buttons.isEmpty();
    }

    private void checkAccess() {
        runtime.domain().checkAccess();
        if (closed) throw new IllegalStateException("指针路由器已释放");
    }

    /** runtime 关闭时取消目标并解除按钮引用，业务意图仅由正常激活路径提交。 */
    @Override
    public void close() {
        runtime.domain().checkAccess();
        wheel.clear();
        try { cancelOwner(); }
        finally {
            buttons.clear();
            hover.clear(); passive.clear(); preparedGeometry = null;
            hoverInterests.clear(); hoverNodes.clear();
            closed = true;
        }
    }
}
