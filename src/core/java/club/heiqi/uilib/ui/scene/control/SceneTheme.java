package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;

/** 不可变主题及其度量来源；密度合同见specs/scene/2026-10-18-uilib-density-spec.md。 */
public final class SceneTheme {
    private static final MotionSpec DEFAULT_FEEDBACK = MotionSpec.tween(140_000_000L, MotionSpec.SMOOTH);
    private static final MotionSpec DEFAULT_SPATIAL = MotionSpec.spring(.28, .82);
    public final TextStyle textStyle;
    public final MotionSpec feedbackMotion, spatialMotion;
    public final int panel, button, hover, pressed, focus, disabled, disabledText;
    public final int tooltip, tooltipText, padding, gap;
    public final SceneDensity density;
    public final SceneControlSize controlSize;
    private final TextStyle baseTextStyle;
    private final int basePadding, baseGap;
    private final boolean inheritedText, inheritedPadding, inheritedGap;

    /** 旧构造的字体和像素是作者值；角色只解析尚未明确指定的控件度量。 */
    public SceneTheme(TextStyle textStyle, int panel, int button, int hover, int pressed, int focus,
            int disabled, int disabledText, int tooltip, int tooltipText, int padding, int gap) {
        this(textStyle, panel, button, hover, pressed, focus, disabled, disabledText, tooltip, tooltipText,
                padding, gap, DEFAULT_FEEDBACK, DEFAULT_SPATIAL, SceneDensity.COMFORTABLE, SceneControlSize.REGULAR,
                false, false, false);
    }
    private SceneTheme(TextStyle textStyle, int panel, int button, int hover, int pressed, int focus,
            int disabled, int disabledText, int tooltip, int tooltipText, int padding, int gap,
            MotionSpec feedback, MotionSpec spatial, SceneDensity density, SceneControlSize size,
            boolean inheritedText, boolean inheritedPadding, boolean inheritedGap) {
        this.density = Objects.requireNonNull(density, "控件密度");
        this.controlSize = Objects.requireNonNull(size, "控件尺寸角色");
        this.feedbackMotion = Objects.requireNonNull(feedback, "主题反馈动效");
        this.spatialMotion = Objects.requireNonNull(spatial, "主题空间动效");
        this.baseTextStyle = Objects.requireNonNull(textStyle, "textStyle");
        if (padding < 0 || gap < 0) throw new IllegalArgumentException("主题间距不能为负");
        this.basePadding = padding; this.baseGap = gap;
        this.inheritedText = inheritedText; this.inheritedPadding = inheritedPadding; this.inheritedGap = inheritedGap;
        float scale = density.textScale * size.textScale;
        this.textStyle = inheritedText && scale != 1 ? textStyle.withEm(textStyle.logicalEm * scale) : textStyle;
        this.padding = inheritedPadding ? spacing(padding) : padding;
        this.gap = inheritedGap ? spacing(gap) : gap;
        this.panel = panel; this.button = button; this.hover = hover; this.pressed = pressed; this.focus = focus;
        this.disabled = disabled; this.disabledText = disabledText; this.tooltip = tooltip; this.tooltipText = tooltipText;
    }
    static SceneTheme defaults(TextStyle style, int panel, int button, int hover, int pressed, int focus,
            int disabled, int disabledText, int tooltip, int tooltipText, int padding, int gap) {
        return new SceneTheme(style, panel, button, hover, pressed, focus, disabled, disabledText, tooltip, tooltipText,
                padding, gap, DEFAULT_FEEDBACK, DEFAULT_SPATIAL, SceneDensity.COMFORTABLE, SceneControlSize.REGULAR,
                true, true, true);
    }
    public static SceneTheme light() {
        return defaults(new TextStyle("default", 18, 0xFF20252B).withDefaultHighlightColor(0x88FFD54F),
                0xFFF4F6F8, 0xFFE2E8EF, 0xFFD1E1F2, 0xFFB9D1EB, 0xFF246DB5, 0xFFE4E6E9, 0xFF777D85,
                0xFF20252B, 0xFFFFFFFF, 8, 8);
    }
    public static SceneTheme dark() {
        return defaults(new TextStyle("default", 18, 0xFFF1F4F8).withDefaultHighlightColor(0x887A5A16),
                0xFF20252B, 0xFF37414D, 0xFF465C74, 0xFF294866, 0xFF80BFFF, 0xFF30353D, 0xFF8C949F,
                0xFFF1F4F8, 0xFF20252B, 8, 8);
    }
    public SceneTheme withDensity(SceneDensity value) {
        return copy(baseTextStyle, inheritedText, feedbackMotion, spatialMotion, value, controlSize);
    }
    public SceneTheme withControlSize(SceneControlSize value) {
        return copy(baseTextStyle, inheritedText, feedbackMotion, spatialMotion, density, value);
    }
    public SceneTheme withMotion(MotionSpec feedback, MotionSpec spatial) {
        return copy(baseTextStyle, inheritedText, feedback, spatial, density, controlSize);
    }
    public SceneTheme withTextStyle(TextStyle style) {
        return copy(style, false, feedbackMotion, spatialMotion, density, controlSize);
    }
    /** 只替换角色色；字号、间距的声明来源及密度、尺寸和动效角色完整延续。 */
    public SceneTheme withColors(int panel, int button, int hover, int pressed, int focus,
            int disabled, int disabledText, int tooltip, int tooltipText) {
        return new SceneTheme(baseTextStyle, panel, button, hover, pressed, focus, disabled, disabledText,
                tooltip, tooltipText, basePadding, baseGap, feedbackMotion, spatialMotion, density, controlSize,
                inheritedText, inheritedPadding, inheritedGap);
    }
    private SceneTheme copy(TextStyle style, boolean inheritText, MotionSpec feedback, MotionSpec spatial,
            SceneDensity density, SceneControlSize size) {
        return new SceneTheme(style, panel, button, hover, pressed, focus, disabled, disabledText, tooltip, tooltipText,
                basePadding, baseGap, feedback, spatial, density, size, inheritText, inheritedPadding, inheritedGap);
    }
    /** 现代视觉只映射配色和自己的默认基准，保留上游字体来源与角色。 */
    SceneTheme appearance(TextStyle style, int panel, int button, int hover, int pressed, int focus,
            int disabled, int disabledText, int tooltip, int tooltipText, int padding, int gap, boolean inheritPadding) {
        return new SceneTheme(style, panel, button, hover, pressed, focus, disabled, disabledText, tooltip, tooltipText,
                padding, gap, feedbackMotion, spatialMotion, density, controlSize, inheritedText, inheritPadding, true);
    }
    TextStyle baseTextStyle() { return baseTextStyle; }
    int symbolPadding() { int value = Math.max(4, basePadding / 2); return inheritedPadding ? spacing(value) : value; }
    int spacing(int value) { return Math.round(value * density.spacingScale * controlSize.spacingScale); }
    public SceneControlMetrics metrics(SceneControlMetrics.Kind kind) { return metrics(kind, textStyle); }
    /** effectiveStyle为节点已经解析的完整字体，不再按角色缩放。 */
    public SceneControlMetrics metrics(SceneControlMetrics.Kind kind, TextStyle effectiveStyle) {
        return new SceneControlMetrics(this, Objects.requireNonNull(kind, "控件族"), Objects.requireNonNull(effectiveStyle, "有效字体"));
    }
}
