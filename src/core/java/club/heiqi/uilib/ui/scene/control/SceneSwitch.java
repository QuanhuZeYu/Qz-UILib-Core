package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 布尔来源的开关；动作即时读取来源，是否接受意图由业务决定。 */
public final class SceneSwitch {
    private SceneSwitch() { }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Boolean> checked, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change) {
        return create(page, checked, name, theme, change, true);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Boolean> checked, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Consumer<Boolean> change, boolean showLabel) {
        Objects.requireNonNull(checked, "开关来源"); Objects.requireNonNull(change, "改变意图");
        return SceneSymbolControl.node(page, name, new Control(theme, showLabel, checked),
                () -> change.accept(!Objects.requireNonNull(checked.get(), "开关状态")));
    }
    private static final class Control extends SceneSymbolControl {
        private final ReadableSignal<Boolean> checked;
        Control(ReadableSignal<SceneTheme> theme, boolean showLabel, ReadableSignal<Boolean> checked) {
            super(theme, showLabel); this.checked = checked;
        }
        @Override int symbolWidth(SceneNode node) { return Math.round(symbolSize(node) * 1.8f); }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(theme, checked); }
        @Override void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color) {
            SceneTheme style = theme.get(); boolean on = Objects.requireNonNull(checked.get(), "开关状态");
            int track = color == style.disabledText ? style.disabledText : on ? style.focus : style.disabled;
            ScenePaintShapes.rounded(target, box, clip, track, box.height / 2);
            int inset = Math.max(1, box.height / 7), diameter = Math.max(0, Math.min(box.height - inset * 2, box.width - inset * 2));
            LayoutBox thumb = new LayoutBox(on ? box.x + box.width - inset - diameter : box.x + inset,
                    box.y + (box.height - diameter) / 2, diameter, diameter);
            ScenePaintShapes.rounded(target, thumb, clip, color, diameter / 2);
        }
    }
}
