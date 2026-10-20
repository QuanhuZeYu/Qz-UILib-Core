package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import java.util.Collections;
import java.util.Objects;

/** 名称、提示、命中与向量标记共用正式几何；不建立内部输入所有权。 */
abstract class SceneSymbolControl implements SceneControl {
    final ReadableSignal<SceneTheme> theme;
    private final boolean showLabel;
    private final SceneTextPolicy policy;
    private final SceneControlMotion motion = new SceneControlMotion();
    private TextLayout source, presented;
    private int presentWidth, presentHeight;

    SceneSymbolControl(ReadableSignal<SceneTheme> theme, boolean showLabel) {
        this(theme, showLabel, SceneTextPolicy.WRAP);
    }
    SceneSymbolControl(ReadableSignal<SceneTheme> theme, boolean showLabel, SceneTextPolicy policy) {
        this.theme = Objects.requireNonNull(theme); this.showLabel = showLabel;
        this.policy = Objects.requireNonNull(policy, "文案策略");
    }
    static SceneNode node(ScenePageContext page, ReadableSignal<String> name, SceneSymbolControl control, Runnable action) {
        Objects.requireNonNull(page).domain().checkWrite();
        Objects.requireNonNull(name); Objects.requireNonNull(action);
        SceneNode node = new SceneNode().control(control).size(Size.natural(), Size.natural())
                .textPolicy(control.policy == SceneTextPolicy.WRAP ? TextPolicy.NORMAL : TextPolicy.NO_WRAP)
                .focusable(true).clickFocus(true).onClick(action);
        node.bind(NodeBinding.value(() -> Objects.requireNonNull(name.get(), "控件名称"), SceneNode::text));
        node.bind(NodeBinding.value(control.theme, (n, t) -> {
            SceneControlMetrics metrics = t.metrics(SceneControlMetrics.Kind.SYMBOL);
            n.defaultStyle(metrics.textStyle).defaultPadding(metrics.padding).defaultGap(control.showLabel ? metrics.iconGap : 0);
        }));
        control.motion.install(node, () -> control.theme.get().feedbackMotion, self -> SceneControlMotion.target(self, control.theme.get()));
        control.installPresentation(node);
        return node;
    }
    static SceneNode graphic(ScenePageContext page, SceneSymbolControl control) {
        Objects.requireNonNull(page).domain().checkWrite();
        SceneNode node = new SceneNode().control(control).size(Size.natural(), Size.natural())
                .bind(NodeBinding.value(control.theme, (n, t) -> n.defaultStyle(t.metrics(SceneControlMetrics.Kind.SYMBOL).textStyle)));
        control.installPresentation(node);
        return node;
    }
    /** 子类只装配自己的呈现目标；真实状态、语义和意图仍读原来源。 */
    void installPresentation(SceneNode node) { }

    /** 装饰不额外朗读；正式符号按钮沿完整名称和原click动作。 */
    @Override public SceneSemantics semantics(SceneNode node) {
        if (node.clickAction() == null) return SceneSemantics.builder(SceneSemantics.Role.IMAGE).excluded(true).actions().build();
        SceneSemantics.Builder result = SceneSemantics.builder(SceneSemantics.Role.BUTTON).name(node.text());
        if (node.focusable()) result.actions(SceneSemantics.Action.FOCUS, SceneSemantics.Action.ACTIVATE);
        else result.actions(SceneSemantics.Action.ACTIVATE);
        return result.build();
    }
    int symbolSize(SceneNode node) { return theme.get().metrics(SceneControlMetrics.Kind.SYMBOL, node.style()).iconSize; }
    int symbolWidth(SceneNode node) { return symbolSize(node); }
    private int labelOffset(SceneNode node) { return symbolWidth(node) + node.gap(); }
    @Override public TextLayout layoutText(SceneNode node, SceneTextMeasurer text, int width) {
        return text.layout(showLabel ? node.text() : "", node.style(), Math.max(0, width - (showLabel ? labelOffset(node) : 0)), node.textPolicy());
    }
    @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
        TextLayout label = layoutText(node, text, limits.availableWidth);
        float width = 0, height = 0;
        for (TextLayout.Line line : label.lines()) {
            width = Math.max(width, SceneTextPresentation.right(line));
            height = Math.max(height, SceneTextPresentation.bottom(line));
            if (policy == SceneTextPolicy.ELLIPSIS_SINGLE_LINE) break;
        }
        return new MeasuredSize(showLabel ? labelOffset(node) + (int) Math.ceil(width) : symbolWidth(node),
                Math.max(symbolSize(node), (int) Math.ceil(height)));
    }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        return new ChildPlacements(Collections.emptyList());
    }
    @Override public TextLayout presentText(SceneNode node, TextLayout full, int width, int height, SceneTextMeasurer measurer) {
        width = Math.max(0, width - (showLabel ? labelOffset(node) : 0));
        if (source != full || presentWidth != width || presentHeight != height) {
            source = full; presentWidth = width; presentHeight = height;
            presented = SceneTextPresentation.fit(full, width, height, policy, measurer);
        }
        return presented;
    }
    @Override public java.util.List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Collections.singletonList(theme); }
    abstract void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color, boolean enabled);
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        SceneGeometryView.Entry e = geometry.get(node); if (e == null) return;
        SceneTheme style = theme.get(); boolean enabled = SceneControlMotion.enabled(node);
        boolean hover = enabled && node.runtime().pointerRouter().hovered(geometry) == node;
        boolean pressed = enabled && node.runtime().pressed(node);
        if (node.clickAction() != null) club.heiqi.uilib.ui.scene.paint.ScenePaintShapes.rounded(target, e.box, e.clip,
                motion.color(!enabled ? style.disabled : pressed ? style.pressed : hover ? style.hover : style.button), Math.min(e.box.width, e.box.height) / 2);
        if (enabled && node.runtime().focusRouter().focusVisible(node)) SceneControlPaint.border(target, e.box, e.clip, style.focus);
        LayoutBox inner = SceneControlPaint.inner(e.box, node.padding());
        int side = Math.min(symbolSize(node), Math.min(inner.width, inner.height));
        int markWidth = Math.min(inner.width, Math.max(0, Math.round((float) side * symbolWidth(node) / symbolSize(node))));
        LayoutBox mark = new LayoutBox(inner.x + (showLabel ? 0 : (inner.width - markWidth) / 2), inner.y + (inner.height - side) / 2, markWidth, side);
        symbol(mark, target, e.clip, enabled ? node.style().color : style.disabledText, enabled);
        if (showLabel) {
            LayoutBox label = new LayoutBox(inner.x + labelOffset(node), inner.y, Math.max(0, inner.width - labelOffset(node)), inner.height);
            LayoutBox clip = policy == SceneTextPolicy.OVERFLOW ? e.clip : e.clip == null ? label : e.clip.intersect(label);
            TextLayout paint = enabled ? e.text : e.text.withStyle(e.text.style.withColor(style.disabledText));
            target.add(PaintCommand.text(label, clip, paint));
        }
    }
    @Override public SceneTooltip.Hint tooltip(SceneNode node, SceneGeometryView geometry) {
        if (node.clickAction() == null || node.text().isEmpty()) return null;
        if (!showLabel) return new SceneTooltip.Hint(SceneTooltip.Content.text(node.text()), new SceneTooltip.Options(), theme.get());
        SceneGeometryView.Entry entry = geometry.get(node);
        if (source == null || presented == source || entry == null || entry.text != presented) return null;
        String full = source.originalText == null ? source.logicalText : source.originalText;
        return new SceneTooltip.Hint(SceneTooltip.Content.text(full), new SceneTooltip.Options().width(Float.MAX_VALUE), theme.get());
    }
}
