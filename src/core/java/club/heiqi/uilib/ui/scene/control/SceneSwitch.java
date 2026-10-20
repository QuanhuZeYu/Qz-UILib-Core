package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 布尔来源的开关；动作即时读取来源，是否接受意图由业务决定。 */
public final class SceneSwitch {
    private SceneSwitch() { }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Boolean> checked, ReadableSignal<String> name, Consumer<Boolean> change) {
        return create(page, checked, name, page.theme(), change);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Boolean> checked, ReadableSignal<String> name,
            Consumer<Boolean> change, SceneTextPolicy policy) {
        return create(page, checked, name, page.theme(), change, policy);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Boolean> checked, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change) {
        return create(page, checked, name, theme, change, true);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Boolean> checked, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change, SceneTextPolicy policy) {
        return create(page, checked, name, theme, change, true, policy);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Boolean> checked, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change, boolean showLabel) {
        return create(page, checked, name, theme, change, showLabel, SceneTextPolicy.WRAP);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Boolean> checked, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change, boolean showLabel, SceneTextPolicy policy) {
        Objects.requireNonNull(checked, "开关来源"); Objects.requireNonNull(change, "改变意图");
        return SceneSymbolControl.node(page, name, new Control(theme, showLabel, checked, policy),
                () -> change.accept(!Objects.requireNonNull(checked.get(), "开关状态")));
    }
    private static final class Control extends SceneSymbolControl {
        private final ReadableSignal<Boolean> checked;
        private final SceneControlMotion track = new SceneControlMotion();
        private ReadableSignal<Float> position;
        Control(ReadableSignal<SceneTheme> theme, boolean showLabel, ReadableSignal<Boolean> checked, SceneTextPolicy policy) {
            super(theme, showLabel, policy); this.checked = checked;
        }
        @Override void installPresentation(SceneNode node) {
            track.install(node, () -> theme.get().feedbackMotion,
                    self -> trackColor(SceneControlMotion.enabled(self)), "switch.track");
            position = SceneControlMotion.progress(node, theme, () -> on() ? 1f : 0f, true);
        }
        private boolean on() { return Objects.requireNonNull(checked.get(), "开关状态"); }
        private int trackColor(boolean enabled) {
            SceneTheme style = theme.get();
            return !enabled ? style.disabledText : on() ? style.focus : style.disabled;
        }
        @Override public SceneSemantics semantics(SceneNode node) {
            return super.semantics(node).toBuilder().role(SceneSemantics.Role.SWITCH).checked(on()).build();
        }
        @Override int symbolWidth(SceneNode node) { return Math.round(symbolSize(node) * 1.8f); }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(theme, checked); }
        @Override void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color, boolean enabled) {
            // 可用资格是事实输入，不能从正常/禁用文字颜色是否相等反推。
            ScenePaintShapes.rounded(target, box, clip, track.color(trackColor(enabled)), box.height / 2);
            int inset = Math.max(1, box.height / 7), diameter = Math.max(0, Math.min(box.height - inset * 2, box.width - inset * 2));
            float fraction = Math.max(0, Math.min(1, position.get()));
            int travel = Math.max(0, box.width - inset * 2 - diameter);
            LayoutBox thumb = new LayoutBox(box.x + inset + Math.round(travel * fraction),
                    box.y + (box.height - diameter) / 2, diameter, diameter);
            ScenePaintShapes.rounded(target, thumb, clip, color, diameter / 2);
        }
    }
}
