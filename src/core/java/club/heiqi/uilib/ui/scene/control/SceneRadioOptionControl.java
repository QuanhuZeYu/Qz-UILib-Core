package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TableContent;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 同一选项根动态选择圆点或分段呈现；文本测量、省略及完整名称提示仍归符号控件。 */
final class SceneRadioOptionControl extends SceneSymbolControl {
    private final ReadableSignal<SceneRadioGroup.Variant> variant;
    private final ReadableSignal<SceneAppearance.Palette> colors;
    private final ReadableSignal<Boolean> checked;
    private final SceneTextPolicy policy;
    private ReadableSignal<Float> selected;
    private SceneAppearance.Surface surface;
    private TextLayout centered, centeredSource;
    private int centeredWidth, centeredHeight, centeredColor;

    private SceneRadioOptionControl(ReadableSignal<SceneTheme> theme, ReadableSignal<SceneAppearance.Palette> colors,
            ReadableSignal<SceneRadioGroup.Variant> variant, ReadableSignal<Boolean> checked, SceneTextPolicy policy) {
        super(theme, true, policy);
        this.colors = colors; this.variant = variant; this.checked = checked; this.policy = policy;
    }

    static SceneNode create(ScenePageContext page, ReadableSignal<String> name, ReadableSignal<SceneTheme> theme,
            ReadableSignal<SceneAppearance.Palette> colors, ReadableSignal<SceneRadioGroup.Variant> variant,
            ReadableSignal<Boolean> checked, SceneTextPolicy policy, Runnable action) {
        SceneRadioOptionControl control = new SceneRadioOptionControl(theme, colors, variant, checked, policy);
        return SceneSymbolControl.node(page, name, control, action);
    }

    private boolean segmented() { return variant.get() == SceneRadioGroup.Variant.SEGMENTED; }
    @Override SceneControlMetrics.Kind kind() {
        return segmented() ? SceneControlMetrics.Kind.MODERN_BUTTON : SceneControlMetrics.Kind.SYMBOL;
    }
    @Override int labelGap(SceneControlMetrics metrics) { return segmented() ? 0 : super.labelGap(metrics); }
    @Override void installFeedback(SceneNode node) {
        // 挂载期按当前外观选择目标，两种外观共用原填色通道。
        motion.install(node, () -> theme.get().feedbackMotion,
                self -> segmented() ? surface.fill(self) : SceneControlMotion.target(self, theme.get()));
    }
    @Override int symbolSize(SceneNode node) { return segmented() ? 0 : super.symbolSize(node); }
    @Override int symbolWidth(SceneNode node) { return segmented() ? 0 : super.symbolWidth(node); }

    @Override void installPresentation(SceneNode node) {
        selected = SceneControlMotion.progress(node, theme,
                () -> !segmented() && Objects.requireNonNull(checked.get(), "单选状态") ? 1f : 0f, false);
        surface = new SceneAppearance.Surface(colors, SceneAppearance.Role.GHOST, node, checked) {
            @Override int fill(SceneNode self) {
                if (!segmented()) return 0;
                SceneAppearance.Palette palette = colors.get();
                if (checked.get()) return palette.dark ? palette.selected : palette.background;
                // Surface用内层填色留出焦点轮廓；透明色会露出整块焦点底层。
                int feedback = SceneControlMotion.enabled(self) ? super.fill(self) : 0;
                return (feedback >>> 24) == 0 ? palette.dark ? palette.background : palette.hover : feedback;
            }
        };
    }

    @Override public SceneSemantics semantics(SceneNode node) {
        boolean value = Objects.requireNonNull(checked.get(), "单选状态");
        SceneSemantics.Builder result = super.semantics(node).toBuilder().role(SceneSemantics.Role.RADIO)
                .checked(value).selected(value);
        if (node.focusable()) result.actions(SceneSemantics.Action.FOCUS, SceneSemantics.Action.ACTIVATE, SceneSemantics.Action.SELECT);
        else result.actions(SceneSemantics.Action.ACTIVATE, SceneSemantics.Action.SELECT);
        return result.build();
    }

    @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(variant); }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(theme, colors, checked, variant); }

    @Override public void preparePaint(SceneNode node, SceneGeometryView geometry, SceneTextMeasurer text, long revision) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (!segmented() || entry == null) { centered = centeredSource = null; return; }
        LayoutBox inner = SceneControlPaint.inner(entry.box, node.padding());
        int color = !SceneControlMotion.enabled(node) ? theme.get().disabledText
                : checked.get() ? node.style().color : colors.get().muted;
        if (centeredSource == entry.text && centeredWidth == inner.width
                && centeredHeight == inner.height && centeredColor == color) return;
        centeredSource = entry.text; centeredWidth = inner.width; centeredHeight = inner.height; centeredColor = color;
        TextLayout label = SceneTextPresentation.align(entry.text, inner.width, TableContent.Alignment.CENTER);
        centered = SceneTextPresentation.translate(label, 0, Math.max(0, (inner.height - label.height) / 2));
        centered = centered.withStyle(centered.style.withColor(color));
    }

    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
        if (!segmented()) { super.paint(node, geometry, paint); return; }
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry == null) return;
        // 分段外观独立绘制圆角表面与 focus。
        surface.paint(node, geometry, paint, motion.color(surface.fill(node)));
        LayoutBox inner = SceneControlPaint.inner(entry.box, node.padding());
        LayoutBox clip = policy == SceneTextPolicy.OVERFLOW ? entry.clip
                : entry.clip == null ? inner : entry.clip.intersect(inner);
        TextLayout label = centered == null ? entry.text : centered;
        paint.add(PaintCommand.text(inner, clip, label));
    }

    @Override void symbol(LayoutBox box, PaintPlanBuilder paint, LayoutBox clip, int color, boolean enabled) {
        ScenePaintShapes.rounded(paint, box, clip, color, box.width / 2);
        int edge = Math.max(1, box.width / 9);
        LayoutBox inside = new LayoutBox(box.x + edge, box.y + edge, Math.max(0, box.width - edge * 2), Math.max(0, box.height - edge * 2));
        ScenePaintShapes.rounded(paint, inside, clip, theme.get().panel, inside.width / 2);
        int inset = Math.max(edge + 1, box.width / 4);
        LayoutBox dot = new LayoutBox(box.x + inset, box.y + inset, Math.max(0, box.width - inset * 2), Math.max(0, box.height - inset * 2));
        ScenePaintShapes.rounded(paint, dot, clip,
                SceneMotion.alpha(color, Math.max(0, Math.min(1, selected.get()))), dot.width / 2);
    }
}
