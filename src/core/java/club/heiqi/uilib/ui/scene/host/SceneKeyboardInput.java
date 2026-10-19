package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneKeyInterpreter;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneFocusRouter;
import club.heiqi.uilib.ui.scene.runtime.SceneKeyContinuation;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 物理按键的消费序列与阶段交接；帧管线在原事件及其 Effect 结算后分别调用两个阶段。
 * 有限编辑许可、范围和焦点仍由 runtime 维护。
 * fresh/repeat/UP 的合同见命令规格第 3—4 节。 */
final class SceneKeyboardInput {
    static final class Input {
        final SceneKeyEvent event;
        final Runnable fallback;
        private Sequence sequence;

        Input(SceneKeyEvent event, Runnable fallback) {
            this.event = Objects.requireNonNull(event, "event");
            this.fallback = fallback;
        }
    }

    private static final class Sequence {
        enum Use { RESERVED, EDIT, NAVIGATION, PRESS }
        final SceneFocusRouter.RangeHandle range;
        MountHandle editing;
        Use use = Use.RESERVED;
        SceneKeyContinuation continuation;

        Sequence(SceneFocusRouter.RangeHandle range, MountHandle editing) {
            this.range = range;
            this.editing = editing;
        }

        void close() {
            use = Use.RESERVED;
            if (continuation != null) {
                continuation.close();
                continuation = null;
            }
        }
    }

    private final SceneRuntime runtime;
    // 每个中性物理键至多一项；fresh 覆盖，失焦、失败和关闭结束。
    private final Map<SceneKey, Sequence> sequences = new EnumMap<>(SceneKey.class);

    SceneKeyboardInput(SceneRuntime runtime) {
        this.runtime = runtime;
    }

    boolean route(Input input) {
        SceneKeyEvent event = input.event;
        if (event.phase == SceneKeyEvent.Phase.UP) {
            release(event.key);
            return true;
        }
        if (event.repeat) return repeat(input);
        return begin(input);
    }

    private boolean begin(Input input) {
        Sequence previous = sequences.remove(input.event.key);
        if (previous != null) previous.close();
        SceneNode focused = runtime.focusRouter().focused().get();
        Sequence sequence = new Sequence(runtime.focusRouter().captureRange(),
                focused == null ? null : runtime.captureMount(focused));
        sequences.put(input.event.key, sequence);
        input.sequence = sequence;
        SceneRuntime.KeyResult result = runtime.dispatchKeyEdit(input.event, held -> sequence.continuation = held);
        if (result == SceneRuntime.KeyResult.EDITED) sequence.use = Sequence.Use.EDIT;
        return result != SceneRuntime.KeyResult.IGNORED;
    }

    private boolean repeat(Input input) {
        Sequence sequence = sequences.get(input.event.key);
        input.sequence = sequence;
        if (sequence == null) return true;
        if (!sequence.range.current()) {
            sequence.close();
            return true;
        }
        if (sequence.continuation != null) {
            sequence.continuation.repeat(input.event);
            return true;
        }
        if (sequence.use == Sequence.Use.NAVIGATION) return repeatNavigation(input.event, sequence);
        if (sequence.use == Sequence.Use.EDIT && sequence.editing != null && sequence.editing.active()
                && runtime.focusRouter().focused().get() == sequence.editing.node()) {
            // 编辑续段不重新查询应用绑定，也不转为导航或宿主动作。
            runtime.dispatchKeyEdit(input.event);
        }
        return true;
    }

    private boolean repeatNavigation(SceneKeyEvent event, Sequence sequence) {
        SceneNavigationEvent direction = SceneKeyInterpreter.navigation(event);
        if (direction == null || !direction.directional()) return false;
        // 空间方向每次先问当次目标；Tab 延续原导航，不转为 INSERT_TAB。
        SceneNode focused = runtime.focusRouter().focused().get();
        SceneRuntime.KeyResult result = runtime.dispatchKeyEdit(event, held -> sequence.continuation = held);
        if (result == SceneRuntime.KeyResult.EDITED) {
            sequence.use = Sequence.Use.EDIT;
            sequence.editing = focused == null ? null : runtime.captureMount(focused);
        }
        return result != SceneRuntime.KeyResult.IGNORED;
    }

    private void release(SceneKey key) {
        Sequence sequence = sequences.remove(key);
        if (sequence == null) return;
        try {
            if (sequence.use == Sequence.Use.PRESS && sequence.range.current() && sequence.continuation != null)
                sequence.continuation.release();
        } finally {
            sequence.close();
        }
    }

    /** 仅在编辑未消费且本事件 Effect 成功后调用；调用方已准备当前声明与范围事实。 */
    void afterEffects(Input input) {
        Sequence sequence = input.sequence;
        if (sequence == null || !sequence.range.current()) return;
        if (input.event.repeat) {
            if (sequence.use == Sequence.Use.NAVIGATION && !runtime.dispatchKeyCommand(input.event)) {
                SceneNavigationEvent repeated = SceneKeyInterpreter.navigation(input.event);
                if (repeated != null) navigate(repeated);
            }
            return;
        }
        if (runtime.dispatchKeyCommand(input.event)) return;
        if (input.event.plainSpace()) {
            sequence.use = Sequence.Use.PRESS;
            sequence.continuation = runtime.beginKeyPress(sequence.editing, sequence.range);
            return;
        }
        SceneNavigationEvent navigation = SceneKeyInterpreter.navigation(input.event);
        if (navigation != null) {
            sequence.use = Sequence.Use.NAVIGATION;
            navigate(navigation);
        } else if (input.fallback != null) input.fallback.run();
    }

    /** 旧 editOrNavigate 和直接导航共用原激活与方向规则，不推断不存在的物理键。 */
    void navigate(SceneNavigationEvent event) {
        if (event == SceneNavigationEvent.ACTIVATE) {
            if (!runtime.dispatchEdit(SceneEditEvent.command(SceneEditEvent.Command.ACTIVATE_ONLY, false, null)))
                runtime.focusRouter().activate();
        } else if (event.directional()) runtime.focusRouter().navigate(event);
        else runtime.focusRouter().navigate(event == SceneNavigationEvent.PREVIOUS);
    }

    void clear() {
        for (Sequence sequence : sequences.values()) sequence.close();
        sequences.clear();
    }
}
