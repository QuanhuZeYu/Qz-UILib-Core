package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneControlPaint.inner;
import static club.heiqi.uilib.ui.scene.control.SceneControlPaint.border;

import java.util.Collections;
import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

/** 标签与按钮共用文本测量/呈现；缓存只持不可变排版，不持节点或旧挂载作用域。 */
final class SceneTextControl implements SceneControl {
    private final ReadableSignal<SceneTheme> theme;
    private final SceneTextPolicy policy;
    private final boolean button;
    private final ReadableSignal<SceneIconButton.Symbol> leadingIcon;
    private boolean trailingIcon, composed;
    private ReadableSignal<Boolean> selected;
    private SceneAppearance.Surface modernSurface;
    private final SceneControlMotion motion = new SceneControlMotion();
    private TextLayout source, presented;
    private int presentWidth, presentHeight;

    private SceneTextControl(
            ReadableSignal<SceneTheme> theme, SceneTextPolicy policy, boolean button,
            ReadableSignal<SceneIconButton.Symbol> leadingIcon) {
        this.theme = theme;
        this.policy = policy;
        this.button = button;
        this.leadingIcon = leadingIcon;
    }

    static SceneNode create(
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            SceneTextPolicy policy,
            boolean button) {
        return create(text,theme,policy,button,null);
    }
    static SceneNode create(ReadableSignal<String> text, ReadableSignal<SceneTheme> theme,
            SceneTextPolicy policy, boolean button, ReadableSignal<SceneIconButton.Symbol> leadingIcon) {
        return create(text, new SceneTextControl(theme, policy, button, leadingIcon));
    }

    private static SceneNode create(ReadableSignal<String> text, SceneTextControl control) {
        return create(text, control, null, null, null);
    }

    private static SceneNode create(ReadableSignal<String> text, SceneTextControl control,
            ReadableSignal<SceneAppearance.Palette> colors, SceneAppearance.Role role,
            ReadableSignal<Boolean> selected) {
        Objects.requireNonNull(text, "text");
        ReadableSignal<SceneTheme> theme = Objects.requireNonNull(control.theme, "theme");
        SceneTextPolicy policy = Objects.requireNonNull(control.policy, "policy");
        SceneNode node =
                new SceneNode()
                        .control(control)
                        .textPolicy(
                                policy == SceneTextPolicy.WRAP
                                        ? TextPolicy.NORMAL
                                        : TextPolicy.NO_WRAP);
        if (colors != null) {
            control.selected = selected;
            control.modernSurface = new SceneAppearance.Surface(colors, role, node,
                    selected == null ? () -> false : selected);
        }
        node.bind(NodeBinding.value(text, SceneNode::text));
        node.bind(
                NodeBinding.value(
                        theme,
                        (target, value) -> {
                            // 绑定属于工厂声明；作者可在挂载前替换节点的测量/外观扩展。
                            SceneControlMetrics metrics = value.metrics(control.kind());
                            target.defaultStyle(metrics.textStyle);
                            if (control.button) target.defaultPadding(metrics.padding).defaultGap(metrics.iconGap);
                        }));
        if (control.button) {
            if (control.modernSurface != null) control.modernSurface.install(node);
            else control.motion.install(node, () -> theme.get().feedbackMotion,
                    self -> SceneControlMotion.target(self, theme.get()));
        }
        return node;
    }

    static SceneNode createTrailing(ReadableSignal<String> text, ReadableSignal<SceneTheme> theme,
            SceneTextPolicy policy, ReadableSignal<SceneIconButton.Symbol> icon) {
        SceneTextControl control = new SceneTextControl(theme, policy, true, Objects.requireNonNull(icon, "尾部图标"));
        control.trailingIcon = true;
        return create(text, control);
    }

    static SceneNode createModern(ReadableSignal<String> text, ReadableSignal<SceneAppearance.Palette> colors,
            SceneAppearance.Role role, ReadableSignal<SceneIconButton.Symbol> icon, SceneTextPolicy policy) {
        SceneTextControl control = modern(colors, role, icon, policy);
        return create(text, control, colors, role, null);
    }

    /** 内容模式从创建起复用标准动作声明，完整名称不参与视觉测量，也不创建隐藏文字按钮。 */
    static SceneNode createModern(ReadableSignal<String> name, SceneNode content,
            ReadableSignal<SceneAppearance.Palette> colors, SceneAppearance.Role role, ReadableSignal<Boolean> selected) {
        SceneTextControl control = modern(colors, role, null, SceneTextPolicy.WRAP);
        control.composed = true;
        return create(name, control, colors, role, Objects.requireNonNull(selected, "selected"))
                .add(Objects.requireNonNull(content, "content"));
    }

    private static SceneTextControl modern(ReadableSignal<SceneAppearance.Palette> colors, SceneAppearance.Role role,
            ReadableSignal<SceneIconButton.Symbol> icon, SceneTextPolicy policy) {
        Objects.requireNonNull(colors, "colors"); Objects.requireNonNull(role, "role");
        return new SceneTextControl(() -> colors.get().defaultTheme(1,
                role == SceneAppearance.Role.PRIMARY ? colors.get().onPrimary : colors.get().text, 10),
                policy, true, icon);
    }
    @Override public SceneSemantics semantics(SceneNode node) {
        SceneSemantics.Builder result = SceneSemantics.builder(button ? SceneSemantics.Role.BUTTON : SceneSemantics.Role.LABEL)
                .name(node.text()).actions();
        if (button && node.clickAction() != null) {
            if (node.focusable()) result.actions(SceneSemantics.Action.FOCUS, SceneSemantics.Action.ACTIVATE);
            else result.actions(SceneSemantics.Action.ACTIVATE);
        }
        if (selected != null) result.selected(selected.get());
        return result.build();
    }

    private SceneControlMetrics.Kind kind() { return !button ? SceneControlMetrics.Kind.LABEL
            : modernSurface == null ? SceneControlMetrics.Kind.BUTTON : SceneControlMetrics.Kind.MODERN_BUTTON; }
    private int iconSize(SceneNode node) { return leadingIcon == null ? 0 : theme.get().metrics(kind(), node.style()).iconSize; }
    private int iconSpace(SceneNode node) { return leadingIcon == null ? 0 : iconSize(node) + node.gap(); }
    @Override public java.util.List<ReadableSignal<?>> measureDependencies(SceneNode node) {
        return Collections.emptyList();
    }
    @Override public java.util.List<ReadableSignal<?>> paintDependencies(SceneNode node) {
        return leadingIcon==null?Collections.singletonList(theme):java.util.Arrays.asList(theme,leadingIcon);
    }
    @Override public TextLayout layoutText(SceneNode node,SceneTextMeasurer text,int width) {
        return text.layout(composed ? "" : node.text(),node.style(),Math.max(0,width-iconSpace(node)),node.textPolicy());
    }
    @Override
    public MeasuredSize measure(
            SceneNode node, Constraints constraints, SceneTextMeasurer measurer) {
        if (composed) return new MeasuredSize(0, 0);
        TextLayout full =
                layoutText(node,measurer,constraints.availableWidth);
        float width = 0, height = 0;
        for (TextLayout.Line line : full.lines()) {
            width = Math.max(width, SceneTextPresentation.right(line));
            height = Math.max(height, SceneTextPresentation.bottom(line));
            if (policy == SceneTextPolicy.ELLIPSIS_SINGLE_LINE) break;
        }
        return new MeasuredSize((int) Math.ceil(width)+iconSpace(node),Math.max(iconSize(node),(int) Math.ceil(height)));
    }

    @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text,
            SceneChildMeasurer children) {
        return composed ? modernSurface.measure(node, constraints, text, children) : measure(node, constraints, text);
    }

    @Override
    public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer childMeasurer) {
        return composed ? modernSurface.arrange(node, allocated, childMeasurer)
                : new ChildPlacements(Collections.emptyList());
    }

    @Override
    public TextLayout presentText(
            SceneNode node, TextLayout full, int width, int height, SceneTextMeasurer measurer) {
        if (composed) return full;
        width=Math.max(0,width-iconSpace(node));
        if (source != full || presentWidth != width || presentHeight != height) {
            source = full;
            presentWidth = width;
            presentHeight = height;
            presented = SceneTextPresentation.fit(full, width, height, policy, measurer);
        }
        return presented;
    }

    @Override
    public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry == null) return;
        SceneTheme style = theme.get();
        SceneRuntime runtime = node.runtime();
        boolean enabled = true;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent())
            enabled &= cursor.enabled();
        boolean focused =
                enabled && runtime != null && runtime.focusRouter().focusVisible(node);
        if(modernSurface!=null)modernSurface.paint(node,geometry,target);
        else if (button) {
            boolean hovered =
                    enabled && runtime != null && runtime.pointerRouter().hovered(geometry) == node;
            boolean pressed = enabled && runtime != null && runtime.pressed(node);
            int color =
                    !enabled
                            ? style.disabled
                            : pressed ? style.pressed : hovered ? style.hover : style.button;
            target.add(PaintCommand.rect(entry.box, entry.clip, motion.color(color)));
            if (focused) border(target, entry.box, entry.clip, style.focus);
        }
        if (composed) return;
        LayoutBox inner = inner(entry.box, node.padding());
        if(leadingIcon!=null){
            int side=Math.min(iconSize(node),Math.min(inner.width,inner.height));
            int x = trailingIcon ? inner.x + inner.width - side : inner.x;
            SceneIconButton.Symbol icon = leadingIcon.get();
            // 已配置的图标位保留预算；null表示当前没有标记，适用于选择器未选项。
            if (icon != null) SceneIconGlyph.paint(icon,
                    new LayoutBox(x,inner.y+Math.max(0,(inner.height-side)/2),side,side),target,entry.clip,
                    enabled?node.style().color:style.disabledText);
            int inset=iconSpace(node);
            inner=new LayoutBox(trailingIcon ? inner.x : inner.x+inset,inner.y,Math.max(0,inner.width-inset),inner.height);
        }
        LayoutBox clip =
                policy == SceneTextPolicy.OVERFLOW
                        ? entry.clip
                        : entry.clip == null ? inner : entry.clip.intersect(inner);
        TextLayout paint = entry.text;
        if (!enabled) paint = paint.withStyle(paint.style.withColor(style.disabledText));
        target.add(PaintCommand.text(inner, clip, paint));
    }

    @Override public SceneTooltip.Hint tooltip(SceneNode node,SceneGeometryView geometry){
        if (!truncated(node, geometry)) return null;
        String full = composed ? node.text() : source.originalText == null ? source.logicalText : source.originalText;
        return new SceneTooltip.Hint(SceneTooltip.Content.text(full),new SceneTooltip.Options().width(Float.MAX_VALUE),theme.get());
    }

    private boolean truncated(SceneNode node, SceneGeometryView geometry) {
        if (composed) return contentTruncated(node, geometry);
        SceneGeometryView.Entry entry = geometry.get(node);
        return source != null && presented != source && entry != null && entry.text == presented;
    }

    /** 组合内容沿标准文字的省略事实返回根完整名称，不暴露内容把手或产生中间提示对象。 */
    private static boolean contentTruncated(SceneNode node, SceneGeometryView geometry) {
        for (SceneNode child : node.children()) {
            if (!child.visible()) continue;
            SceneControl control = child.control();
            if (control instanceof SceneTextControl && ((SceneTextControl) control).truncated(child, geometry)) return true;
            if (!(control instanceof SceneTextControl) && contentTruncated(child, geometry)) return true;
        }
        return false;
    }
}
