package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 维护 runtime 唯一焦点，按声明树与挂载代际核验资格。
 * 范围进入或返回按核心规格选择目标，普通失活与清除保留空焦点；覆盖期间持有合法恢复记录。 */
public final class SceneFocusRouter {
    private static final class Range {
        final MountHandle modal; // null为页面；页面非模态根共享同一输入范围。
        MountHandle saved;
        boolean pending = true, returning;

        Range(MountHandle modal) {
            this.modal = modal;
        }
    }

    private final SceneRuntime runtime;
    private final Signal<SceneNode> value;
    private final ReadableSignal<SceneNode> view;
    private final Signal<List<SceneNode>> retained;
    private final ReadableSignal<List<SceneNode>> retainedView;
    private final Signal<Boolean> keyboardCue;
    private final List<Range> ranges = new ArrayList<>();
    private MountHandle owner, pendingReveal;
    // 记录控件通知进度，实际焦点始终读取 owner。
    private MountHandle notifiedOwner;
    private static final class FocusLoss {
        final SceneNode node;
        final boolean eligible;
        FocusLoss(SceneNode node, boolean eligible) { this.node = node; this.eligible = eligible; }
    }
    private final java.util.ArrayDeque<FocusLoss> pendingFocusLosses = new java.util.ArrayDeque<>();
    private boolean closed, rangesDirty = true;
    private long intentRevision;
    private club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Cursor navigation;
    private Range navigationRange;
    private boolean navigationReveal, navigationDirectional;
    private ScopeExit scopeExit;
    private static final class ScopeExit {
        final Range closing, parent; final MountHandle anchor; final boolean backward;
        ScopeExit(Range closing, Range parent, MountHandle anchor, boolean backward) {
            this.closing = closing; this.parent = parent; this.anchor = anchor; this.backward = backward;
        }
    }

    SceneFocusRouter(SceneRuntime runtime) {
        this.runtime = runtime;
        value = runtime.domain().signal(null);
        retained = runtime.domain().signal(java.util.Collections.emptyList());
        retainedView = retained::get;
        // 初始自动逻辑焦点保持安静；真实键盘或辅助导航再显示线索，见主题规格。
        keyboardCue=runtime.domain().signal(false);
        view = value::get; // 只读包装保留依赖追踪，写端由路由器持有。
        ranges.add(new Range(null));
    }

    /** 只读观察当前焦点，无焦点时为 null。 */
    public ReadableSignal<SceneNode> focused() {
        runtime.domain().checkAccess();
        return view;
    }

    /** 按原范围及模态代际提供只读有效性联系。 */
    public final class RangeHandle {
        private final Range range;
        private RangeHandle(Range range) { this.range = range; }
        public boolean active() {
            runtime.domain().checkAccess();
            return !closed && ranges.contains(range) && (range.modal == null || alive(range));
        }
        /** 核验原范围仍有效且位于当前输入栈顶。 */
        public boolean current() { runtime.domain().checkAccess(); return active() && top() == range; }
    }

    /** 捕获当前输入范围联系，供异步意图核验原范围有效性。 */
    public RangeHandle captureRange() {
        runtime.domain().checkWrite();
        revalidate();
        return new RangeHandle(top());
    }

    /** 待完成焦点意图的单向许可；完成自身导航沿用此意图，同步回调中的新意图优先。 */
    public final class IntentHandle implements java.util.function.BooleanSupplier {
        private final Range range;
        private final long revision;
        private IntentHandle(Range range, long revision) { this.range = range; this.revision = revision; }
        @Override public boolean getAsBoolean() {
            runtime.domain().checkAccess();
            return !closed && revision == intentRevision && top() == range
                    && (range.modal == null || alive(range));
        }
        public boolean navigateTo(MountHandle target) {
            runtime.domain().checkWrite();
            if (!getAsBoolean()) return false;
            keyboardInput();
            if (!request(target, this)) return false;
            pendingReveal = owner;
            return true;
        }
    }

    /** 开始一次待完成焦点请求；新请求、导航或真实范围切换永久结束旧意图，自动失焦保留它。 */
    public IntentHandle beginIntent() {
        runtime.domain().checkWrite();
        revalidate();
        cancelPending();
        pendingReveal = null;
        return new IntentHandle(top(), ++intentRevision);
    }

    /** 投影当前 owner 与被覆盖范围的合法恢复目标，供按需视图有限保留。 */
    public ReadableSignal<List<SceneNode>> retainedFocus() {
        runtime.domain().checkAccess(); return retainedView;
    }

    private void publishRetained() {
        List<SceneNode> next = new ArrayList<>();
        if (!closed) {
            if (local(owner)) next.add(owner.node());
            for (Range range : ranges)
                if (local(range.saved) && !next.contains(range.saved.node())) next.add(range.saved.node());
        }
        if (!retained.get().equals(next)) retained.set(java.util.Collections.unmodifiableList(next));
    }

    /** 逻辑焦点只有一个；显示线索由最近的有效输入方式派生，文字编辑始终保留。 */
    public boolean focusVisible(SceneNode node) {
        runtime.domain().checkAccess();
        return value.get()==node&&(keyboardCue.get()||node.control()!=null&&node.control().acceptsTextInput(node));
    }
    void pointerInput() { setKeyboardCue(false); }
    void keyboardInput() { setKeyboardCue(true); }
    private void setKeyboardCue(boolean next) {
        runtime.domain().checkWrite();
        if(keyboardCue.get()!=next) {keyboardCue.set(next);runtime.invalidatePaint();}
    }

    /** 在域内请求合法挂载焦点，核验失败返回 false；业务激活由单独入口处理。 */
    public boolean request(MountHandle target) { return request(target, null); }

    private boolean request(MountHandle target, IntentHandle intent) {
        runtime.domain().checkWrite();
        Objects.requireNonNull(target, "target");
        if (closed) return false;
        revalidate();
        if (!local(target) || !runtime.interactive(target.node()) || intent != null && !intent.getAsBoolean()) return false;
        if (intent == null) intentRevision++;
        cancelPending();
        pendingReveal = null;
        setOwner(target);
        return intent == null || intent.getAsBoolean() && owner == target;
    }

    /** 按显式键盘导航意图切焦点并请求一次必要显露，普通 request 只切焦点。 */
    public boolean navigateTo(MountHandle target){
        runtime.domain().checkWrite();Objects.requireNonNull(target);if(!local(target)||!target.node().focusable())return false;keyboardInput();if(!request(target))return false;pendingReveal=owner;return true;
    }

    /** 按指针悬停意图切焦点，并采用指针输入线索。 */
    public boolean pointTo(MountHandle target) {
        runtime.domain().checkWrite();
        if (!request(target)) return false;
        pointerInput(); return true;
    }

    /** 按spec6.2当前范围的逻辑前序循环；调用方先结算声明。返回是否有有效候选，
     * 单项返回 true 并复用原焦点，无焦点时按方向选择首项或末项；此入口仅导航。 */
    public boolean navigate(boolean backward) {
        runtime.domain().checkWrite();
        keyboardInput();
        if (closed) return false;
        revalidate();
        intentRevision++;
        SceneNode scope = top().modal == null ? pagePolicy() : top().modal.node();
        if (runtime.dispatchScopeNavigation(scope, backward)) { cancelNavigation(); return true; }
        SceneNode from = owner == null ? null : owner.node();
        cancelPending();
        beginNavigation(from, backward, true);
        prepareNavigation();
        return navigation != null || owner != null;
    }

    /** 菜单的本次Tab关闭意图：声明退出结算后从原触发器继续同一外层逻辑序列。 */
    public boolean leaveScope(MountHandle modal, MountHandle anchor, boolean backward) {
        runtime.domain().checkWrite(); revalidate();
        if (closed || ranges.size() < 2 || !modal.active() || top().modal == null
                || top().modal.node() != modal.node() || !local(anchor)) return false;
        intentRevision++;
        cancelNavigation();
        scopeExit = new ScopeExit(top(), ranges.get(ranges.size() - 2), anchor, backward);
        return true;
    }

    /** 四方向以当前正式几何选目标，边缘保持焦点；无焦点仍沿现有默认逻辑入口。 */
    public boolean navigate(club.heiqi.uilib.ui.scene.input.SceneNavigationEvent direction) {
        runtime.domain().checkWrite();
        if (!java.util.Objects.requireNonNull(direction, "direction").directional())
            throw new IllegalArgumentException("需要四方向导航");
        keyboardInput();
        if (closed) return false;
        revalidate();
        intentRevision++;
        MountHandle from = owner;
        cancelPending(); navigationRange = top();
        List<SceneNode> roots = navigationRange.modal == null ? runtime.roots()
                : java.util.Collections.singletonList(navigationRange.modal.node());
        navigation = from == null ? new SceneLogicalNavigation(runtime, roots, null, false)
                : new SceneSpatialNavigation(runtime, roots, from, direction);
        navigationReveal = navigationDirectional = true;
        prepareNavigation();
        return navigation != null || owner != null;
    }

    private void beginNavigation(SceneNode from, boolean backward, boolean reveal) {
        cancelNavigation();
        navigationRange = top();
        List<SceneNode> roots = navigationRange.modal == null ? runtime.roots()
                : java.util.Collections.singletonList(navigationRange.modal.node());
        navigation = new SceneLogicalNavigation(runtime, roots, from, backward);
        navigationReveal = reveal;
    }

    private void prepareNavigation() {
        club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Cursor pending = navigation;
        if (pending == null) return;
        if (navigationRange != top()) { cancelNavigation(); return; }
        club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Result result = pending.prepare();
        if (pending != navigation) return;
        if (result.status == club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Result.Status.WAITING) return;
        if (result.status == club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Result.Status.CANCELLED) {
            cancelNavigation(); return;
        }
        boolean reveal = navigationReveal, directional = navigationDirectional;
        MountHandle next = result.target == null ? null : candidate(top(), result.target);
        cancelNavigation();
        if (directional && next == null) return;
        setOwner(next);
        if (reveal) pendingReveal = owner;
    }

    private void cancelNavigation() {
        club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Cursor pending = navigation;
        navigation = null; navigationRange = null; navigationReveal = navigationDirectional = false;
        if (pending != null) pending.close();
    }

    /** 通过共同 click 路径激活当前焦点，当前 owner 保持原值。 */
    public boolean activate() {
        runtime.domain().checkWrite();
        keyboardInput();
        if (closed) return false;
        revalidate();
        if (owner == null
                || !runtime.interactive(owner.node())
                || owner.node().clickAction() == null) return false;
        runtime.dispatchClick(owner.node());
        return true;
    }

    /** 清除当前焦点与待进入选择，被覆盖外层的合法恢复记录继续保留。 */
    public void clear() {
        runtime.domain().checkWrite();
        if (!closed) { intentRevision++; cancelPending(); }
        pendingReveal = null;
        setOwner(null);
    }

    /** 批次失败时结束待执行范围退出意图，当前焦点与合法恢复记录继续沿原规则维护。 */
    void cancelScopeExit() { scopeExit = null; }

    private void cancelPending() {
        scopeExit = null;
        cancelNavigation();
        Range range = top();
        range.pending = false;
        range.saved = null;
        range.returning = false;
    }

    private void setOwner(MountHandle target) {
        if (owner != target) pendingReveal = null;
        SceneNode before = owner == null ? null : owner.node();
        SceneNode after = target == null ? null : target.node();
        if (before != after) {
            // 按失焦当刻资格记录原因，后续 preedit 清理引发的失效沿用该原因。
            if (before != null) pendingFocusLosses.add(new FocusLoss(before, runtime.interactive(before)));
            runtime.invalidateComposition(before);
        }
        owner = target;
        // owner 转换后先结束失效许可、发布事实并通知，资格扩展失败沿原通道保留首错。
        Throwable failure = runtime.keyFocusChanged(after);
        try { publishRetained(); }
        catch (RuntimeException | Error error) { failure = SceneRuntime.collectFailure(failure, error); }
        try { value.set(owner == null ? null : owner.node()); }
        catch (RuntimeException | Error error) { failure = SceneRuntime.collectFailure(failure, error); }
        try { notifyControls(); }
        catch (RuntimeException | Error error) { failure = SceneRuntime.collectFailure(failure, error); }
        if (before != after) runtime.invalidatePaint();
        SceneRuntime.throwFailure(failure);
    }

    private void notifyControls() {
        // Signal订阅与控件失焦回调均可同步request；每次回调后重新读取唯一owner。
        // 先推进通知游标再调用扩展，重入转移从最新通知进度继续。
        while (true) {
            // 按发生顺序处理实际离开，即使订阅同步切回仍结算原 BLUR。
            FocusLoss loss = pendingFocusLosses.poll();
            if (loss != null) {
                SceneNode lost = loss.node;
                if (notifiedOwner != null && notifiedOwner.node() == lost) notifiedOwner = null;
                if (lost.control() != null)
                    runtime.dispatchControlFocus(lost, false,
                            loss.eligible && !closed && runtime.alive() && runtime.interactive(lost));
                continue;
            }
            SceneNode current = owner == null ? null : owner.node();
            if (current == (notifiedOwner == null ? null : notifiedOwner.node())) return;
            notifiedOwner = owner;
            if (current != null && current.control() != null)
                runtime.dispatchControlFocus(current, true, true);
        }
    }

    /** 呈现准备消费一次显露意图，失活、换 owner 或卸载时结束。
     * 裁剪仅按本次几何尽力处理，后续滚动使用独立意图。 */
    MountHandle takeReveal() {
        runtime.domain().checkWrite();
        revalidate();
        MountHandle target = pendingReveal;
        pendingReveal = null;
        return target != null
                        && target == owner
                        && local(target)
                        && runtime.interactive(target.node())
                ? target
                : null;
    }

    boolean owns(MountHandle target) {
        return !closed && target == owner && local(target) && runtime.interactive(target.node());
    }

    private Range top() {
        return ranges.get(ranges.size() - 1);
    }

    private boolean local(MountHandle target) {
        return target != null
                && target.belongsTo(runtime)
                && target.active()
                && target.node().focusable()
                && runtime.locallyInteractive(target.node());
    }

    private boolean contains(Range range, SceneNode target) {
        if (range.modal == null) return true;
        for (SceneNode node = target; node != null; node = node.parent())
            if (node == range.modal.node()) return true;
        return false;
    }

    void rangeChanged() {
        runtime.domain().checkAccess();
        rangesDirty = true;
    }

    /** 真实资格变化即时释放及取消保存记录；恢复资格不复活已取消的token。 */
    void revalidate() {
        runtime.domain().checkAccess();
        if (closed) return;
        for (Range range : ranges)
            if (range.saved != null
                    && (!local(range.saved) || !contains(range, range.saved.node())))
                range.saved = null;
        if (rangesDirty && runtime.scopeFactsReady()) {
            rangesDirty = false;
            syncRanges();
        }
        if (owner != null && (!local(owner) || !runtime.interactive(owner.node()))) setOwner(null);
        runtime.revalidateComposition();
        publishRetained();
    }

    private boolean alive(Range range) {
        return range.modal.active()
                && range.modal.node().modal()
                && runtime.locallyInteractive(range.modal.node());
    }

    private int index(SceneNode modal) {
        for (int i = 1; i < ranges.size(); i++)
            if (ranges.get(i).modal.node() == modal && ranges.get(i).modal.active()) return i;
        return -1;
    }

    private void syncRanges() {
        // 隐藏或卸载外层时，树归属使其所属内层也失活；独立根模态可各自结束。
        Range previousTop = top();
        for (int i = ranges.size() - 1; i > 0; i--) if (!alive(ranges.get(i))) ranges.remove(i);
        SceneNode active = runtime.activeModal();
        boolean entering = false;
        if (active == null) {
            if (ranges.size() > 1) ranges.subList(1, ranges.size()).clear();
        } else {
            int found = index(active);
            if (found >= 0) {
                if (ranges.size() > found + 1) ranges.subList(found + 1, ranges.size()).clear();
            } else {
                Range previous = top();
                // 仅保存仍属原栈顶范围的合法 owner，其他暂态沿用已有恢复记录。
                if (previous == previousTop && local(owner) && contains(previous, owner.node()))
                    previous.saved = owner;
                List<SceneNode> chain = new ArrayList<>();
                for (SceneNode node = active; node != null; node = node.parent())
                    if (node.modal() && runtime.locallyInteractive(node) && index(node) < 0) chain.add(0, node);
                for (SceneNode node : chain) ranges.add(new Range(runtime.captureMount(node)));
                entering = true;
            }
        }
        // 仅在栈顶范围实际变化时启动进入或返回选择。
        // modal属性关闭后目标可能仍是非模态可交互节点，但真正返回仍释放旧owner。
        if (top() != previousTop) {
            intentRevision++;
            cancelNavigation();
            top().pending = true;
            top().returning = !entering;
            if (owner != null) setOwner(null);
        }
        if (runtime.roots().isEmpty()) {
            ranges.clear();
            ranges.add(new Range(null));
        }
    }

    /** 声明结算后处理待进入范围；稳定范围保留当前焦点及显式清除结果。 */
    void prepareScopes() {
        runtime.domain().checkWrite();
        if (closed) return;
        revalidate();
        Range range = top();
        if (scopeExit != null && !ranges.contains(scopeExit.closing)) {
            ScopeExit exit = scopeExit; scopeExit = null;
            if (range == exit.parent && local(exit.anchor) && runtime.interactive(exit.anchor.node())
                    && contains(range, exit.anchor.node())) {
                range.pending = range.returning = false; range.saved = null;
                beginNavigation(exit.anchor.node(), exit.backward, true); prepareNavigation(); return;
            }
        }
        if (navigation != null) { prepareNavigation(); return; }
        if (!range.pending || runtime.roots().isEmpty()) return;
        range.pending = false;
        SceneNode policy = range.modal == null ? pagePolicy() : range.modal.node();
        MountHandle next = null;
        if (owner != null) next = owner; // 尚未prepare前的显式请求优先保留。
        if (next == null && range.returning && policy != null)
            next = candidate(range, policy.restoreFocus());
        if (next == null
                && range.returning
                && local(range.saved)
                && contains(range, range.saved.node())
                && runtime.interactive(range.saved.node())) next = range.saved;
        if (next == null && policy != null) next = candidate(range, policy.initialFocus());
        range.saved = null;
        range.returning = false;
        if (next != null) setOwner(next);
        else {
            // 默认首目标也按完整逻辑顺序准备；范围进入/返回保持不显露。
            beginNavigation(null, false, false);
            prepareNavigation();
        }
    }

    private SceneNode pagePolicy() {
        for (SceneNode root : runtime.roots()) if (!root.modal()) return root;
        return null;
    }

    private MountHandle candidate(Range range, SceneNode node) {
        return node != null
                        && node.runtime() == runtime
                        && node.focusable()
                        && contains(range, node)
                        && runtime.interactive(node)
                ? runtime.captureMount(node)
                : null;
    }

    void close() {
        runtime.domain().checkWrite();
        if (closed) return;
        closed = true;
        scopeExit = null;
        cancelNavigation();
        try {
            setOwner(null);
        } finally {
            ranges.clear();
        }
    }
}
