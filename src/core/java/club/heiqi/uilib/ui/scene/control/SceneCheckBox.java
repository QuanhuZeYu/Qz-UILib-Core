package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 显示三态，发布布尔意图；全选范围由业务决定，沿标准按钮输入合同。 */
public final class SceneCheckBox {
    public enum State { UNCHECKED, CHECKED, MIXED }
    private SceneCheckBox() {}
    public static SceneNode create(ScenePageContext page, ReadableSignal<State> state, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change) {
        return create(page, state, name, theme, change, true);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<State> state, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change, SceneTextPolicy policy) {
        return create(page, state, name, theme, change, true, policy);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<State> state, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change, boolean showLabel) {
        return create(page, state, name, theme, change, showLabel, SceneTextPolicy.WRAP);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<State> state, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change, boolean showLabel, SceneTextPolicy policy) {
        Objects.requireNonNull(state); Objects.requireNonNull(change);
        return SceneSymbolControl.node(page, name, new Control(theme, showLabel, state, policy),
                () -> change.accept(Objects.requireNonNull(state.get(), "复选状态") != State.CHECKED));
    }
    private static final class Control extends SceneSymbolControl {
        private final ReadableSignal<State> state;
        private ReadableSignal<Float> check, mixed;
        Control(ReadableSignal<SceneTheme> theme, boolean showLabel, ReadableSignal<State> state, SceneTextPolicy policy) {
            super(theme, showLabel, policy); this.state = state;
        }
        @Override void installPresentation(SceneNode node) {
            check = SceneControlMotion.progress(node, theme,
                    () -> Objects.requireNonNull(state.get(), "复选状态") == State.CHECKED ? 1f : 0f, false);
            mixed = SceneControlMotion.progress(node, theme,
                    () -> Objects.requireNonNull(state.get(), "复选状态") == State.MIXED ? 1f : 0f, false);
        }
        @Override public SceneSemantics semantics(SceneNode node) {
            State current = Objects.requireNonNull(state.get(), "复选状态");
            return super.semantics(node).toBuilder().role(SceneSemantics.Role.CHECKBOX)
                    .checked(current == State.CHECKED).mixed(current == State.MIXED).build();
        }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(theme, state); }
        @Override void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color, boolean enabled) {
            SceneIconGlyph.paint(SceneIconButton.Symbol.CHECK_BOX, box, target, clip, color);
            // 两个标记各自从当前可见权重续接；交叉期间不创造第四个业务状态。
            SceneIconGlyph.paint(SceneIconButton.Symbol.CHECK, box, target, clip,
                    SceneMotion.alpha(color, Math.max(0, Math.min(1, check.get()))));
            SceneIconGlyph.paint(SceneIconButton.Symbol.MINUS, box, target, clip,
                    SceneMotion.alpha(color, Math.max(0, Math.min(1, mixed.get()))));
        }
    }
}
