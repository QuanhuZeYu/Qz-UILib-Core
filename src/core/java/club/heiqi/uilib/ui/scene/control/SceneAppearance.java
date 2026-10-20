package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 页面共用的现代视觉词汇；颜色与文字单一派生，输入/编辑/布局仍归正式控件。 */
public class SceneAppearance {
    public static final int LIGHT_CANVAS = 0xfffafafa, DARK_CANVAS = 0xff09090b;
    public static final int CONTROL_RADIUS = 8,
            CARD_RADIUS = 12,
            CONTROL_PADDING = 10,
            GAP = 12,
            PAGE_PADDING = 28,
            MODAL_SCRIM = 0x80000000;

    public enum Role {
        PANEL,
        CARD,
        OUTLINE,
        PRIMARY,
        GHOST,
        ENTRY,
        FIELD
    }

    public static final class Palette {
        public final SceneTheme source;
        public final boolean dark;
        public final int canvas, selected, accent, error;
        public final club.heiqi.uilib.ui.scene.text.TextStyle textStyle;
        private final club.heiqi.uilib.ui.scene.text.TextStyle typography;
        public final int background, muted, surface, border, text, primary, onPrimary, hover;

        public Palette(boolean dark) {
            this(dark ? SceneTheme.dark() : SceneTheme.light());
        }

        public Palette(SceneTheme source) {
            this.source = source;
            textStyle = club.heiqi.uilib.ui.scene.text.font.UiFontSources.style(source.textStyle);
            typography =
                    club.heiqi.uilib.ui.scene.text.font.UiFontSources.style(source.baseTextStyle());
            int color = source.panel;
            dark =
                    ((color >>> 16 & 255) * 299 + (color >>> 8 & 255) * 587 + (color & 255) * 114)
                            < 128000;
            canvas = dark ? DARK_CANVAS : LIGHT_CANVAS;
            selected = dark ? 0xff27272a : 0xffe4e4e7;
            accent = dark ? 0xffd4d4d8 : 0xff52525b;
            error = dark ? 0xfff87171 : 0xffdc2626;
            background = dark ? 0xff09090b : 0xffffffff;
            surface = dark ? 0xff18181b : 0xfffafafa;
            border = dark ? 0xff27272a : 0xffe4e4e7;
            text = dark ? 0xfffafafa : 0xff18181b;
            muted = dark ? 0xffa1a1aa : 0xff71717a;
            primary = text;
            onPrimary = background;
            hover = dark ? 0xff27272a : 0xfff4f4f5;
        }

        public SceneTheme editorTheme() {
            return source.appearance(
                    typography.withColor(text),
                    0,
                    0,
                    0x409494a3,
                    0,
                    text,
                    0,
                    muted,
                    primary,
                    onPrimary,
                    11,
                    8,
                    true);
        }

        public SceneTheme theme(float scale, int color, int padding) {
            return mappedTheme(scale, color, padding, false);
        }

        SceneTheme defaultTheme(float scale, int color, int padding) {
            return mappedTheme(scale, color, padding, true);
        }

        private SceneTheme mappedTheme(
                float scale, int color, int padding, boolean inheritPadding) {
            return source.appearance(
                    typography.withEm(typography.logicalEm * scale).withColor(color),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    muted,
                    primary,
                    onPrimary,
                    padding,
                    8,
                    inheritPadding);
        }
    }

    protected final ScenePageContext page;
    public final club.heiqi.uilib.ui.reactive.Owner scope;
    public final ReadableSignal<Palette> colors;

    public SceneAppearance(ScenePageContext page, ReadableSignal<SceneTheme> theme) {
        this.page = page;
        scope = page.scope().child();
        colors = page.domain().computed(scope, () -> new Palette(theme.get()));
    }

    public static SceneNode column(int gap) {
        return new SceneNode().size(Size.fill(1), Size.natural()).gap(gap);
    }

    public static SceneNode row(int gap) {
        return column(gap).axis(SceneNode.Axis.ROW).control(new Flow(true));
    }

    public SceneNode label(ReadableSignal<String> text, float scale, boolean muted) {
        return SceneLabel.create(
                page,
                text,
                () -> colors.get().theme(scale, muted ? colors.get().muted : colors.get().text, 0));
    }

    public SceneNode error(ReadableSignal<String> message) {
        return SceneLabel.create(
                        page,
                        () -> message.get() == null ? "" : message.get(),
                        () -> colors.get().theme(.9f, colors.get().error, 0))
                .bind(
                        NodeBinding.value(
                                () -> message.get() != null && !message.get().isEmpty(),
                                SceneNode::visible));
    }

    public SceneNode ellipsis(ReadableSignal<String> text, float scale, boolean muted) {
        return SceneLabel.create(
                page,
                text,
                () -> colors.get().theme(scale, muted ? colors.get().muted : colors.get().text, 0),
                SceneTextPolicy.ELLIPSIS_SINGLE_LINE);
    }

    public SceneNode title(String text) {
        return SceneLabel.create(
                page,
                () -> text,
                () ->
                        colors.get()
                                .theme(1.4f, colors.get().text, 0)
                                .withTextStyle(
                                        colors.get()
                                                .textStyle
                                                .withEm(colors.get().textStyle.logicalEm * 1.4f)
                                                .withColor(colors.get().text)
                                                .withFontStyle(
                                                        new FontStyle(
                                                                600, FontStyle.Posture.NORMAL))));
    }

    public SceneNode icon(SceneIconButton.Symbol symbol) {
        return SceneIconButton.graphic(
                        page, () -> symbol, () -> colors.get().theme(1, colors.get().muted, 0))
                .bind(
                        NodeBinding.value(
                                () -> Math.round(colors.get().source.textStyle.logicalEm * 1.25f),
                                (n, s) -> n.size(Size.fixed(s), Size.fixed(s))));
    }

    public SceneNode action(String text, SceneIconButton.Symbol icon, Runnable action, Role role) {
        return action(() -> text, icon, action, role);
    }

    public SceneNode action(
            String text,
            SceneIconButton.Symbol icon,
            Runnable action,
            Role role,
            SceneTextPolicy policy) {
        return action(() -> text, icon, action, role, policy);
    }

    public SceneNode action(
            String text, SceneIconButton.Symbol icon, SceneCommand command, Role role) {
        return action(() -> text, icon, command, role);
    }

    public SceneNode action(
            String text,
            SceneIconButton.Symbol icon,
            SceneCommand command,
            Role role,
            SceneTextPolicy policy) {
        return action(() -> text, icon, command, role, policy);
    }

    public SceneNode action(
            ReadableSignal<String> text, SceneIconButton.Symbol icon, Runnable action, Role role) {
        return action(text, icon, action, role, SceneTextPolicy.WRAP);
    }

    /** 文字策略与标准按钮一致；导航或路径需单行时在声明末尾明确选择。 */
    public SceneNode action(
            ReadableSignal<String> text,
            SceneIconButton.Symbol icon,
            Runnable action,
            Role role,
            SceneTextPolicy policy) {
        return SceneButton.createModern(
                page, text, colors, action, role, icon == null ? null : () -> icon, policy);
    }

    public SceneNode action(
            ReadableSignal<String> text,
            SceneIconButton.Symbol icon,
            SceneCommand command,
            Role role) {
        return action(text, icon, command, role, SceneTextPolicy.WRAP);
    }

    public SceneNode action(
            ReadableSignal<String> text,
            SceneIconButton.Symbol icon,
            SceneCommand command,
            Role role,
            SceneTextPolicy policy) {
        return SceneControlBindings.command(
                action(text, icon, command::tryExecute, role, policy), command);
    }

    /** 内容只负责布局和显示；名称、焦点、激活、选中与反馈仍由唯一标准动作根承担。 */
    public SceneNode action(
            ReadableSignal<String> name,
            SceneNode content,
            Runnable action,
            Role role,
            ReadableSignal<Boolean> selected) {
        return SceneButton.createModern(page, name, content, colors, action, role, selected);
    }

    public SceneNode action(
            ReadableSignal<String> name,
            SceneNode content,
            SceneCommand command,
            Role role,
            ReadableSignal<Boolean> selected) {
        return SceneControlBindings.command(
                action(name, content, command::tryExecute, role, selected), command);
    }

    public static SceneTheme theme(boolean dark) {
        Palette p = new Palette(dark);
        return SceneTheme.defaults(
                p.textStyle.withEm(15).withColor(p.text),
                p.background,
                p.surface,
                p.hover,
                p.selected,
                p.accent,
                p.surface,
                p.muted,
                p.primary,
                p.onPrimary,
                CONTROL_PADDING,
                GAP);
    }

    public static SceneNode button(
            ScenePageContext page,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            Runnable action,
            Role role) {
        return button(page, text, theme, action, role, SceneTextPolicy.WRAP);
    }

    public static SceneNode button(
            ScenePageContext page,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            Runnable action,
            Role role,
            SceneTextPolicy policy) {
        club.heiqi.uilib.ui.reactive.Owner owner = page.scope().child();
        ReadableSignal<Palette> colors =
                page.domain().computed(owner, () -> new Palette(theme.get()));
        return SceneButton.createModern(page, text, colors, action, role, null, policy).own(owner);
    }

    public static SceneNode button(
            ScenePageContext page,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            SceneCommand command,
            Role role) {
        return button(page, text, theme, command, role, SceneTextPolicy.WRAP);
    }

    public static SceneNode button(
            ScenePageContext page,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            SceneCommand command,
            Role role,
            SceneTextPolicy policy) {
        return SceneControlBindings.command(
                button(page, text, theme, command::tryExecute, role, policy), command);
    }

    public static SceneNode button(
            ScenePageContext page,
            ReadableSignal<String> name,
            SceneNode content,
            ReadableSignal<SceneTheme> theme,
            Runnable action,
            Role role,
            ReadableSignal<Boolean> selected) {
        club.heiqi.uilib.ui.reactive.Owner owner = page.scope().child();
        ReadableSignal<Palette> colors =
                page.domain().computed(owner, () -> new Palette(theme.get()));
        return SceneButton.createModern(page, name, content, colors, action, role, selected)
                .own(owner);
    }

    public static SceneNode button(
            ScenePageContext page,
            ReadableSignal<String> name,
            SceneNode content,
            ReadableSignal<SceneTheme> theme,
            SceneCommand command,
            Role role,
            ReadableSignal<Boolean> selected) {
        return SceneControlBindings.command(
                button(page, name, content, theme, command::tryExecute, role, selected), command);
    }

    public SceneNode card(SceneNode content) {
        Surface surface = new Surface(colors, Role.CARD, null, () -> false);
        SceneNode node = column(0).control(surface).add(content);
        surface.install(node);
        return node;
    }

    /**
     * 字段外表面包含正文及底部滚动条。正式带框输入在声明期将装饰交给此表面，
     * 保留原主题、动效和编辑节点的显式 style/padding；无框输入继续由现代外表面装饰。
     */
    public SceneNode field(SceneNode editor) {
        SceneNode node = column(0).size(Size.fill(1), Size.natural()).add(editor);
        SceneFieldPaint decoration = SceneFieldPaint.take(editor, node);
        ReadableSignal<SceneTheme> fieldTheme = decoration == null ? () -> colors.get().source : decoration.theme();
        // 显式条的挂载与原字段归属一致；move 后真实卸载，释放其对自动条的 claim。
        node.add(row(0).size(Size.fill(1), Size.natural()).bind(NodeBinding.repeat(
                () -> {
                    page.runtime().interactionRevision().get();
                    return SceneFieldPaint.contains(node, editor)
                            ? java.util.Collections.singletonList(Boolean.TRUE)
                            : java.util.Collections.emptyList();
                }, value -> value, value -> {
                    SceneNode scrollbar = SceneScrollbar.create(
                            page, SceneScrollViewport.of(page, editor, SceneNode.Axis.ROW),
                            () -> new SceneScrollbar.Options().dimensions(4, 24, 1)
                                    .colors(0, colors.get().border, colors.get().muted),
                            () -> colors.get().editorTheme()).size(Size.fill(1), Size.natural());
                    // 同一视口的底条只占圆角内的安全宽度，不覆盖正文。
                    return row(0).size(Size.fill(1), Size.natural())
                            .add(fieldGutter(fieldTheme)).add(scrollbar).add(fieldGutter(fieldTheme));
                })));
        Surface surface = new Surface(colors, Role.FIELD, editor, () -> false, decoration);
        node.control(surface);
        surface.install(node);
        return node;
    }

    private static SceneNode fieldGutter(ReadableSignal<SceneTheme> theme) {
        return new SceneNode().size(Size.fixed(0), Size.fixed(0))
                .bind(NodeBinding.value(theme, (node, value) ->
                        node.size(Size.fixed(value.metrics(SceneControlMetrics.Kind.FIELD).radius), Size.fixed(0))));
    }

    public SceneNode line() {
        return column(0)
                .size(Size.fill(1), Size.fixed(1))
                .bind(NodeBinding.value(colors, (n, p) -> n.background(p.border)));
    }

    static class Flow implements SceneControl {
        final boolean centered;

        public Flow() {
            this(false);
        }

        public Flow(boolean centered) {
            this.centered = centered;
        }

        public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        public MeasuredSize measure(
                SceneNode n, Constraints c, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(n, c, children);
        }

        public ChildPlacements arrange(SceneNode n, LayoutBox box, SceneChildMeasurer children) {
            ChildPlacements flow = SceneFlowLayout.arrange(n, box, children);
            if (!centered) return flow;
            List<ChildPlacements.Entry> result = new ArrayList<>();
            for (ChildPlacements.Entry item : flow.entries()) {
                LayoutBox b = item.relativeBox;
                result.add(
                        new ChildPlacements.Entry(
                                item.child,
                                new LayoutBox(
                                        b.x,
                                        n.padding()
                                                + Math.max(
                                                        0,
                                                        (box.height - n.padding() * 2 - b.height)
                                                                / 2),
                                        b.width,
                                        b.height)));
            }
            return new ChildPlacements(result);
        }

        public void paint(SceneNode n, SceneGeometryView g, PaintPlanBuilder paint) {}
    }

    static class Surface extends Flow {
        private final ReadableSignal<Palette> colors;
        private final ReadableSignal<Boolean> selected;
        private final SceneNode target;
        private final Role role;
        private final SceneControlMotion motion = new SceneControlMotion();
        private final SceneFieldPaint decoration;
        private final SceneFieldPaint.Frame fieldFrame;
        private int fieldRadius;

        public Surface(
                ReadableSignal<Palette> colors,
                Role role,
                SceneNode target,
                ReadableSignal<Boolean> selected) {
            this(colors, role, target, selected, null);
        }

        Surface(ReadableSignal<Palette> colors, Role role, SceneNode target,
                ReadableSignal<Boolean> selected, SceneFieldPaint decoration) {
            this.colors = colors;
            this.role = role;
            this.target = target;
            this.selected = selected;
            this.decoration = decoration;
            fieldFrame = role == Role.FIELD && decoration == null ? new SceneFieldPaint.Frame() : null;
        }

        void install(SceneNode node) {
            if (fieldFrame != null)
                node.bind(NodeBinding.value(colors, (self, value) -> fieldRadius = value.source.metrics(SceneControlMetrics.Kind.FIELD).radius));
            if (decoration == null) motion.install(node, () -> colors.get().source.feedbackMotion, this::fill);
        }

        public List<ReadableSignal<?>> paintDependencies(SceneNode n) {
            return decoration == null ? Arrays.asList(colors, selected) : Arrays.asList(colors, selected, decoration.theme());
        }

        private boolean focused() {
            if (target == null || target.runtime() == null) return false;
            // 只读编辑仍可定位/选择；外表面读取同一个逻辑焦点，不改变路由器的输入方式语义。
            if (role == Role.FIELD && target.control() instanceof club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget)
                return target.runtime().focusRouter().focused().get() == target;
            return target.runtime().focusRouter().focusVisible(target);
        }

        @Override public void preparePaint(SceneNode node, SceneGeometryView geometry, SceneTextMeasurer text, long revision) {
            if (decoration != null) decoration.prepare(node, geometry);
            else if (fieldFrame != null && SceneFieldPaint.contains(node, target)) {
                Palette p = colors.get();
                fieldFrame.prepare(geometry.get(node), geometry, fieldRadius, motion.color(fill(node)), focused() ? p.muted : p.border);
            }
        }

        private boolean enabled() {
            for (SceneNode at = target; at != null; at = at.parent())
                if (!at.enabled()) return false;
            return true;
        }

        int fill(SceneNode node) {
            Palette p = colors.get();
            boolean active = enabled();
            SceneNode hit =
                    target == null || target.runtime() == null
                            ? null
                            : target.runtime().pointerRouter().hoverTarget().get();
            boolean hover = false;
            for (SceneNode at = hit; at != null; at = at.parent())
                if (at == target) {
                    hover = true;
                    break;
                }
            boolean pressed =
                    target != null && target.runtime() != null && target.runtime().pressed(target);
            int fill =
                    role == Role.PRIMARY
                            ? p.primary
                            : role == Role.GHOST || role == Role.ENTRY ? 0 : p.background;
            if (role == Role.CARD && (node.background() >>> 24) != 0) fill = node.background();
            if (role == Role.ENTRY && selected.get()) fill = p.hover;
            if ((hover || pressed) && active) fill = role == Role.PRIMARY ? p.muted : p.hover;
            return active ? fill : p.surface;
        }

        public void paint(SceneNode n, SceneGeometryView geometry, PaintPlanBuilder paint) {
            if (decoration != null) decoration.paint(n, paint);
            else if (fieldFrame != null) {
                if (SceneFieldPaint.contains(n, target)) fieldFrame.paint(paint);
            }
            else paint(n, geometry, paint, motion.color(fill(n)));
        }

        void paint(
                SceneNode n, SceneGeometryView geometry, PaintPlanBuilder paint, int animatedFill) {
            SceneGeometryView.Entry e = geometry.get(n);
            if (e == null) return;
            Palette p = colors.get();
            boolean focused = focused();
            int fill = animatedFill;
            int edge =
                    focused
                            ? p.muted
                            : role == Role.OUTLINE
                                            || role == Role.FIELD
                                            || role == Role.PANEL
                                            || role == Role.CARD
                                    ? p.border
                                    : fill;
            if (role == Role.PANEL) {
                for (int spread = 6; spread >= 2; spread -= 2)
                    ScenePaintShapes.rounded(
                            paint,
                            new LayoutBox(
                                    e.box.x - spread,
                                    e.box.y - spread / 2,
                                    e.box.width + spread * 2,
                                    e.box.height + spread * 2),
                            e.clip,
                            0x08000000,
                            p.source.metrics(SceneControlMetrics.Kind.PANEL).radius + spread);
            }
            int radius =
                    p.source.metrics(
                                    role == Role.PANEL
                                            ? SceneControlMetrics.Kind.PANEL
                                            : role == Role.CARD
                                                    ? SceneControlMetrics.Kind.CARD
                                                    : SceneControlMetrics.Kind.MODERN_BUTTON)
                            .radius;
            ScenePaintShapes.rounded(paint, e.box, e.clip, edge, radius);
            ScenePaintShapes.rounded(
                    paint,
                    new LayoutBox(
                            e.box.x + 1,
                            e.box.y + 1,
                            Math.max(0, e.box.width - 2),
                            Math.max(0, e.box.height - 2)),
                    e.clip,
                    fill,
                    radius - 1);
        }
    }
}
