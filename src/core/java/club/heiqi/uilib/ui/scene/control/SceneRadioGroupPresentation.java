package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneEditContinuation;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneFocusTraversal;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 组根直接拥有选项、正式Flow与导航；轨道只添加同根表面，不复制布局属性。 */
final class SceneRadioGroupPresentation extends SceneAppearance.Surface {
    private final SceneChoiceNavigation<?, ?> navigation;
    private final ReadableSignal<SceneRadioGroup.Variant> variant;
    private final ReadableSignal<SceneAppearance.Palette> colors;

    private SceneRadioGroupPresentation(SceneChoiceNavigation<?, ?> navigation,
            ReadableSignal<SceneRadioGroup.Variant> variant, ReadableSignal<SceneAppearance.Palette> colors) {
        super(colors, SceneAppearance.Role.GHOST, null, () -> false);
        this.navigation = navigation; this.variant = variant; this.colors = colors;
    }

    static void install(SceneNode group, SceneChoiceNavigation<?, ?> navigation,
            ReadableSignal<SceneRadioGroup.Variant> variant, ReadableSignal<SceneAppearance.Palette> colors) {
        SceneRadioGroupPresentation control = new SceneRadioGroupPresentation(navigation, variant, colors);
        group.control(control);
        group.bind(NodeBinding.value(() -> {
            SceneControlMetrics metrics = colors.get().source.metrics(SceneControlMetrics.Kind.TABS);
            return variant.get() == SceneRadioGroup.Variant.SEGMENTED ? Math.max(1, metrics.padding / 3) : 0;
        }, SceneNode::defaultPadding));
        group.bind(NodeBinding.value(() -> {
            SceneControlMetrics metrics = colors.get().source.metrics(SceneControlMetrics.Kind.SYMBOL);
            return variant.get() == SceneRadioGroup.Variant.SEGMENTED ? Math.max(1, metrics.iconGap / 3) : metrics.gap;
        }, SceneNode::defaultGap));
        control.install(group);
    }

    @Override int fill(SceneNode node) {
        return variant.get() == SceneRadioGroup.Variant.SEGMENTED ? colors.get().surface : 0;
    }
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
        if (variant.get() == SceneRadioGroup.Variant.SEGMENTED) super.paint(node, geometry, paint);
    }
    @Override public SceneFocusTraversal focusTraversal(SceneNode root) { return navigation.focusTraversal(root); }
    @Override public boolean editFallback(SceneNode root, SceneNode focused, SceneEditEvent event, SceneGeometryView geometry) {
        return navigation.editFallback(root, focused, event, geometry);
    }
    @Override public SceneEditContinuation editContinuation(SceneNode root, SceneNode focused, SceneEditEvent event) {
        return navigation.editContinuation(root, focused, event);
    }
}
