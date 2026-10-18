package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** runtime唯一焦点权威。范围进入/返回按spec6.3.1选择；普通失活、清除不补选。
 * 复用声明树和挂载代际，恢复记录只在覆盖期间持有，不持有第二棵输入树。 */
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
    // 仅记录已发出的控件通知，不参与焦点资格或路由决定；owner是唯一权威。
    private MountHandle notifiedOwner;
    private static final class FocusLoss {
        final SceneNode node;
        final boolean eligible;
        FocusLoss(SceneNode node, boolean eligible) { this.node = node; this.eligible = eligible; }
    }
    private final java.util.ArrayDeque<FocusLoss> pendingFocusLosses = new java.util.ArrayDeque<>();
    private boolean closed, rangesDirty = true;
    private club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Cursor navigation;
    private Range navigationRange;
    private boolean navigationReveal, navigationDirectional;

    SceneFocusRouter(SceneRuntime runtime) {
        this.runtime = runtime;
        value = runtime.domain().signal(null);
        retained = runtime.domain().signal(java.util.Collections.emptyList());
        retainedView = retained::get;
        keyboardCue=runtime.domain().signal(true);
        view = value::get; // 只读包装保留依赖追踪，不暴露可强转的写端。
        ranges.add(new Range(null));
    }

    /** 无焦点为null，读取只观察，不结算声明或执行选择。 */
    public ReadableSignal<SceneNode> focused() {
        runtime.domain().checkAccess();
        return view;
    }

    /** 一次实际输入范围的只读有效性联系；内容继续显示不延长已结束的模态代际。 */
    public final class RangeHandle {
        private final Range range;
        private RangeHandle(Range range) { this.range = range; }
        public boolean active() {
            runtime.domain().checkAccess();
            return !closed && ranges.contains(range) && (range.modal == null || alive(range));
        }
        /** 原范围仍为唯一当前范围；被内层覆盖的active范围不能继续消费旧KEY。 */
        public boolean current() { runtime.domain().checkAccess(); return active() && top() == range; }
    }

    /** 捕获当前范围，不选择焦点；异步呈现意图可据此拒绝已结束范围的旧请求。 */
    public RangeHandle captureRange() {
        runtime.domain().checkWrite();
        revalidate();
        return new RangeHandle(top());
    }

    /** 当前owner及被覆盖范围的合法恢复记录；供按需视图有限保留，不是另一份焦点权威。 */
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

    /** 域内受控请求，可从handler调用；无效/异runtime/旧token返回false，不激活业务。 */
    public boolean request(MountHandle target) {
        runtime.domain().checkWrite();
        Objects.requireNonNull(target, "target");
        if (closed) return false;
        revalidate();
        if (!local(target) || !runtime.interactive(target.node())) return false;
        cancelPending();
        pendingReveal = null;
        setOwner(target);
        return true;
    }

    /** 控件的显式键盘导航意图复用唯一owner，并只为本意图显露目标；普通request仍不追焦。 */
    public boolean navigateTo(MountHandle target){
        runtime.domain().checkWrite();Objects.requireNonNull(target);if(!local(target)||!target.node().focusable())return false;keyboardInput();if(!request(target))return false;pendingReveal=owner;return true;
    }

    /** 按spec6.2当前范围的逻辑前序循环；调用方先结算声明。返回是否有有效候选，
     * 单项仍返回true但不重复通知，无焦点正向取首项/反向取末项；不激活控件。 */
    public boolean navigate(boolean backward) {
        runtime.domain().checkWrite();
        keyboardInput();
        if (closed) return false;
        revalidate();
        SceneNode from = owner == null ? null : owner.node();
        cancelPending();
        beginNavigation(from, backward, true);
        prepareNavigation();
        return navigation != null || owner != null;
    }

    /** 四方向以当前正式几何选目标，边缘保持焦点；无焦点仍沿现有默认逻辑入口。 */
    public boolean navigate(club.heiqi.uilib.ui.scene.input.SceneNavigationEvent direction) {
        runtime.domain().checkWrite();
        if (!java.util.Objects.requireNonNull(direction, "direction").directional())
            throw new IllegalArgumentException("需要四方向导航");
        keyboardInput();
        if (closed) return false;
        revalidate();
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

    /** 激活唯一当前焦点，不补选或改变owner；标准键盘按钮复用同一业务click路径。 */
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

    /** 清除当前焦点并取消当前待进入选择；不取消被覆盖外层的合法恢复记录。 */
    public void clear() {
        runtime.domain().checkWrite();
        if (!closed) cancelPending();
        pendingReveal = null;
        setOwner(null);
    }

    private void cancelPending() {
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
            // 记录失焦当刻的原因；终止preedit的Signal可能使下一批布局失效，不能把失活改判为正常BLUR。
            if (before != null) pendingFocusLosses.add(new FocusLoss(before, runtime.interactive(before)));
            runtime.invalidateComposition(before);
        }
        owner = target;
        publishRetained();
        value.set(after);
        notifyControls();
        if (before != after) runtime.invalidatePaint();
    }

    private void notifyControls() {
        // Signal订阅与控件失焦回调均可同步request；每次回调后重新读取唯一owner。
        // 先推进通知游标再调用扩展，嵌套转移不会覆盖已经发布的新通知。
        while (true) {
            // 实际离开按发生顺序保存，Signal订阅中离开再返回也不能吞掉BLUR提交。
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

    /** 仅呈现准备消费；读取焦点不推进。失活、换owner或卸载会取消旧意图，
     * 无法修复的裁剪也只尽力一次，不在后续内容变化或用户滚动时追随。 */
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
                // 未显示的暂态范围没有owner时，不能覆盖合法旧记录；已结束范围的owner也不能写进外层。
                if (previous == previousTop && local(owner) && contains(previous, owner.node()))
                    previous.saved = owner;
                List<SceneNode> chain = new ArrayList<>();
                for (SceneNode node = active; node != null; node = node.parent())
                    if (node.modal() && runtime.locallyInteractive(node) && index(node) < 0) chain.add(0, node);
                for (SceneNode node : chain) ranges.add(new Range(runtime.captureMount(node)));
                entering = true;
            }
        }
        // 只删除被覆盖记录不会改变前台范围，不能重选其焦点或覆盖主动clear。
        // modal属性关闭后目标可能仍是非模态可交互节点，但真正返回仍释放旧owner。
        if (top() != previousTop) {
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

    /** 声明结算结束才选目标；无待转换则不遍历候选树，清除不会被下一帧补回。 */
    void prepareScopes() {
        runtime.domain().checkWrite();
        if (closed) return;
        revalidate();
        Range range = top();
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
        cancelNavigation();
        try {
            setOwner(null);
        } finally {
            ranges.clear();
        }
    }
}
