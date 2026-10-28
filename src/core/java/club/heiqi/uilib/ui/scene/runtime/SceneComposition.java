package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 一次组字联系的身份与终止；草稿和焦点仍分别归编辑状态与唯一路由器。
 * 按编辑规格第 2 节共同核验来源、挂载、状态与基线，事件始终归原联系。 */
final class SceneComposition {
    private static final class Link {
        final SceneCompositionSource source;
        final MountHandle mount;
        final SceneEditState state;
        final Object baseline;

        Link(SceneCompositionSource source, MountHandle mount, SceneEditState state) {
            this.source = source;
            this.mount = mount;
            this.state = state;
            baseline = state.inputBaseline();
        }
    }

    private final SceneRuntime runtime;
    private Link active;

    SceneComposition(SceneRuntime runtime) { this.runtime = runtime; }

    /** null 表示原来源或起始目标失效，此事件在原联系内结束消费。 */
    SceneNode target(SceneEditEvent event, SceneNode focused) {
        if (event.compositionPhase == SceneEditEvent.CompositionPhase.BEGIN) {
            if (!event.compositionSource.beginOnce()) return null;
            invalidate(null);
            if (!accepts(focused)) return null;
            active = new Link(event.compositionSource, runtime.captureMount(focused),
                    ((SceneCompositionTarget) focused.control()).compositionState());
        } else if (active == null || event.compositionSource != active.source) return null;
        return active.mount.node();
    }

    private boolean accepts(SceneNode node) {
        return node != null && runtime.interactive(node) && node.control() != null
                && node.control().acceptsTextInput(node)
                && node.control() instanceof SceneCompositionTarget;
    }

    void revalidate() {
        runtime.domain().checkAccess();
        Link link = active;
        if (link == null) return;
        SceneNode node = link.mount.node();
        if (!link.mount.active() || runtime.focusRouter().focused().get() != node || !accepts(node)
                || ((SceneCompositionTarget) node.control()).compositionState() != link.state
                || link.state.inputBaseline() != link.baseline)
            invalidate(null);
    }

    /** 先结束原身份，再按基线代际清理其预编辑呈现。 */
    void invalidate(SceneNode target) {
        runtime.domain().checkWrite();
        Link link = active;
        if (link == null || target != null && link.mount.node() != target) return;
        active = null;
        if (link.state.inputBaseline() == link.baseline) link.state.composition("", false);
    }

    boolean activeFor(SceneNode target) {
        revalidate();
        return active != null && active.mount.node() == target || target.control() instanceof SceneCompositionTarget
                && ((SceneCompositionTarget) target.control()).compositionState().snapshot().get().composing;
    }

    /** UPDATE(false)结束preedit呈现后，真实联系仍保留应用快捷键，直到COMMIT/CANCEL。 */
    boolean reservesShortcut() {
        revalidate();
        if (active != null) return true;
        SceneNode focused = runtime.focusRouter().focused().get();
        return accepts(focused) && ((SceneCompositionTarget) focused.control()).compositionState().snapshot().get().composing;
    }

    /** 最终事件后结束仍匹配该来源的原联系，回调失败也执行清理。 */
    void finish(SceneEditEvent event) {
        if (active != null && active.source == event.compositionSource
                && (event.compositionPhase == SceneEditEvent.CompositionPhase.COMMIT
                    || event.compositionPhase == SceneEditEvent.CompositionPhase.CANCEL))
            invalidate(null);
    }
}
