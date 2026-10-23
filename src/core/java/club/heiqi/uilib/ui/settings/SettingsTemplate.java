package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.*;
import java.util.function.Function;

/** 文档式设置骨架；不持有配置事务，参见 specs/config/uilib-settings-template-spec.md。 */
public final class SettingsTemplate implements ScenePage {
    private final ReadableSignal<String> title;
    private ReadableSignal<String> description = () -> "", notice = () -> "";
    private ReadableSignal<String> closeLabel = () -> "关闭", sectionLabel = () -> "设置分类";
    private Runnable close = () -> {};
    private final List<Section> sections = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private final List<HeaderAction> headerActions = new ArrayList<>();
    private ReadableSignal<SceneTheme> theme;
    private Function<ScenePageContext, SceneNode> feedback, aside;

    public SettingsTemplate(ReadableSignal<String> title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public SettingsTemplate description(ReadableSignal<String> value) {
        description = Objects.requireNonNull(value, "description"); return this;
    }

    public SettingsTemplate section(String id, ReadableSignal<String> title,
            Function<ScenePageContext, SceneNode> content) {
        return section(id, title, () -> "", SceneIconButton.Symbol.SLIDERS, content);
    }

    /** 完整主题同时服务外壳、导航与弹层；页面仍持有预览或发布策略。 */
    public SettingsTemplate theme(ReadableSignal<SceneTheme> value) {
        theme = Objects.requireNonNull(value, "theme"); return this;
    }

    /** 分类元数据只表达当前内容层级，不改变业务草稿或提交策略。 */
    public SettingsTemplate section(String id, ReadableSignal<String> title,
            ReadableSignal<String> description, SceneIconButton.Symbol icon,
            Function<ScenePageContext, SceneNode> content) {
        return section(id, title, description, icon, () -> "", content);
    }

    /** 导航附注来自业务；动作名称和分类标题仍保留原可读身份。 */
    public SettingsTemplate section(String id, ReadableSignal<String> title,
            ReadableSignal<String> description, SceneIconButton.Symbol icon, ReadableSignal<String> annotation,
            Function<ScenePageContext, SceneNode> content) {
        Objects.requireNonNull(id, "id");
        for (Section section : sections)
            if (section.id.equals(id)) throw new IllegalArgumentException("重复设置分类：" + id);
        sections.add(new Section(id, Objects.requireNonNull(title, "title"),
                Objects.requireNonNull(description, "description"), Objects.requireNonNull(icon, "icon"),
                Objects.requireNonNull(annotation, "annotation"), Objects.requireNonNull(content, "content")));
        return this;
    }

    public SettingsTemplate action(ReadableSignal<String> label, SceneCommand command, boolean primary) {
        actions.add(new Action(Objects.requireNonNull(label, "label"),
                Objects.requireNonNull(command, "command"), primary));
        return this;
    }

    /** 页头附加动作与底部操作使用相同命令，不增加页面状态权威。 */
    public SettingsTemplate headerAction(ReadableSignal<String> label, SceneIconButton.Symbol icon, SceneCommand command) {
        headerActions.add(new HeaderAction(Objects.requireNonNull(label, "label"),
                Objects.requireNonNull(icon, "icon"), Objects.requireNonNull(command, "command")));
        return this;
    }

    /** 业务状态和可交互反馈进入模板操作区，低窗沿同一正式端口滚动。 */
    public SettingsTemplate feedback(Function<ScenePageContext, SceneNode> content) {
        feedback = Objects.requireNonNull(content, "feedback"); return this;
    }

    /** 宽屏辅助说明沿模板正文排列；可见性仍由调用方声明。 */
    public SettingsTemplate aside(Function<ScenePageContext, SceneNode> content) {
        aside = Objects.requireNonNull(content, "aside"); return this;
    }

    public SettingsTemplate notice(ReadableSignal<String> value) {
        notice = Objects.requireNonNull(value, "notice"); return this;
    }

    /** 宿主可传正式 localization 的派生文案；同一来源服务可见标签及语义说明。 */
    public SettingsTemplate labels(ReadableSignal<String> closeLabel, ReadableSignal<String> sectionLabel) {
        this.closeLabel = Objects.requireNonNull(closeLabel, "closeLabel");
        this.sectionLabel = Objects.requireNonNull(sectionLabel, "sectionLabel");
        return this;
    }

    public SettingsTemplate onClose(Runnable action) {
        close = Objects.requireNonNull(action, "onClose"); return this;
    }

    @Override public void build(ScenePageContext context) { context.mountOwned(create(context)); }

    public SceneNode create(ScenePageContext context) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        SceneAppearance ui = new SceneAppearance(context, theme == null ? context.theme() : theme);
        SettingsMotion motion = new SettingsMotion(ui.colors);
        ReadableSignal<SceneTheme> editorTheme = () -> ui.colors.get().editorTheme();
        ReadableSignal<Integer> gap = () -> ui.colors.get().source.metrics(SceneControlMetrics.Kind.PANEL).gap;
        ReadableSignal<Boolean> compact = () -> context.viewportWidth().get()
                < Math.round(ui.colors.get().textStyle.logicalEm * 48) + gap.get() * 6;
        Signal<String> selected = context.domain().signal(sections.isEmpty() ? "" : sections.get(0).id);
        SceneNode content = column(gap, 3);
        SceneScrollArea.Area scrolling = SceneScrollArea.create(context, content,
                () -> new SceneScrollArea.Options().autoHide(false)
                        .motion(new ScrollMotionOptions(motion.spatial.get()).maximumDisplacement(24))
                        .colors(0, ui.colors.get().border, ui.colors.get().muted), editorTheme);
        SceneNode viewport = scrolling.viewport(SceneNode.Axis.COLUMN);
        java.util.function.Consumer<String> navigate = id -> {
            selected.set(id);
            SceneScrollViewport.of(context, viewport).moveToPixels(0);
        };
        SceneNode rail = motion.scroll(column(gap, 1).size(Size.fill(1), Size.fill(1)).clip(true).scrollable(true))
                .bind(NodeBinding.value(compact, (n, value) -> n.visible(!value)));
        List<SceneSelect.Option<String>> options = new ArrayList<>();
        // 面板只构造一次；分类动效只结束输入资格，外部草稿及原编辑身份持续保留。
        for (Section section : sections) {
            ReadableSignal<Boolean> active = () -> selected.get().equals(section.id);
            SceneNode entry = SceneAppearance.row(0)
                    .bind(NodeBinding.value(gap, SceneNode::gap))
                    .add(ui.icon(section.icon))
                    .add(ui.label(section::navigationTitle, .95f, false).size(Size.fill(1), Size.natural()));
            rail.add(ui.action(section.title, entry, () -> navigate.accept(section.id), SceneAppearance.Role.ENTRY, active)
                    .size(Size.fill(1), Size.natural()));
            options.add(new SceneSelect.Option<>(section.id, section::navigationTitle));
            content.add(motion.reveal(column(gap, 3)
                    .add(Objects.requireNonNull(section.content.apply(context), "分类内容")), active));
        }
        SceneNode selector = SceneSelect.create(context, () -> options, selected, navigate, sectionLabel, () -> ui.colors.get().source, false)
                .size(Size.fill(1), Size.natural());
        SceneNode compactNavigation = ui.field(selector).bind(NodeBinding.value(compact, SceneNode::visible));
        SceneNode navigation = column(gap, 0).add(rail).add(compactNavigation)
                .bind(NodeBinding.value(compact,
                        (n, value) -> n.size(Size.fill(1), value ? Size.natural() : Size.fill(1))));
        SceneNode brand = SceneAppearance.row(0).bind(NodeBinding.value(gap, SceneNode::gap))
                .add(ui.icon(SceneIconButton.Symbol.SLIDERS))
                .add(heading(ui, title, 1, 600).size(Size.fill(1), Size.natural()));
        SceneNode header = SceneAppearance.row(0).bind(NodeBinding.value(gap, SceneNode::gap)).add(brand);
        for (HeaderAction action : headerActions)
            header.add(ui.action(action.label, action.icon, action.command, SceneAppearance.Role.GHOST));
        header.add(ui.action(closeLabel, SceneIconButton.Symbol.CLOSE, () -> close.run(), SceneAppearance.Role.GHOST));
        SceneNode toolbar = column(gap, 2)
                .add(header)
                .add(ui.line());
        ReadableSignal<String> currentTitle = () -> current(selected.get()) == null ? "" : current(selected.get()).title.get();
        ReadableSignal<String> currentDescription = () -> {
            Section section = current(selected.get());
            return section == null || section.description.get().isEmpty() ? description.get() : section.description.get();
        };
        SceneNode sectionHeader = column(gap, 1)
                .add(heading(ui, currentTitle, 1.45f, 600))
                .add(ui.label(currentDescription, .92f, true)
                        .bind(NodeBinding.value(() -> !currentDescription.get().isEmpty(), SceneNode::visible)))
                .add(ui.line())
                .add(new SceneNode().bind(NodeBinding.value(gap,
                        (node, value) -> node.size(Size.fill(1), Size.fixed(value)))));
        SceneNode body = SceneLayouts.frame(sectionHeader, scrolling.node(), new SceneNode().size(Size.fill(1), Size.fixed(0)));
        SceneNode buttons = SceneLayouts.wrap(gap).size(Size.natural(), Size.natural());
        // 只安排视觉主次；各按钮仍执行业务提供的同一 SceneCommand。
        for (boolean primary : new boolean[] {false, true})
            for (Action action : actions) if (action.primary == primary)
                buttons.add(ui.action(action.label, null, action.command,
                        primary ? SceneAppearance.Role.PRIMARY : SceneAppearance.Role.OUTLINE));
        SceneNode status = column(gap, 1);
        if (feedback != null) status.add(Objects.requireNonNull(feedback.apply(context), "操作反馈"));
        status.add(motion.reveal(ui.label(notice, .9f, true), () -> !notice.get().isEmpty()));
        SceneNode operations = SceneLayouts.split(status, buttons,
                () -> new SceneLayouts.SplitOptions(Math.round(ui.colors.get().textStyle.logicalEm * 10), gap.get() * 2));
        SceneNode footer = column(gap, 2).add(ui.line()).add(operations).clip(true).scrollable(true);
        SceneNode supplement = aside == null ? null : Objects.requireNonNull(aside.apply(context), "辅助说明");
        return SettingsLayout.template(ui.colors, compact, toolbar, navigation, body, footer, supplement)
                .bind(NodeBinding.value(ui.colors, (n, palette) -> n.background(palette.canvas)))
                .onEscape(() -> { close.run(); return true; })
                .own(ui.scope);
    }

    private Section current(String id) {
        for (Section section : sections) if (section.id.equals(id)) return section;
        return null;
    }

    private static SceneNode column(ReadableSignal<Integer> gap, int units) {
        return SceneAppearance.column(0).bind(NodeBinding.value(() -> gap.get() * units, SceneNode::gap));
    }

    private static SceneNode heading(SceneAppearance ui, ReadableSignal<String> title, float scale, int weight) {
        return ui.label(title, scale, false).bind(NodeBinding.value(ui.colors, (node, palette) -> node.style(
                palette.textStyle.withEm(palette.textStyle.logicalEm * scale).withColor(palette.text)
                        .withFontStyle(new FontStyle(weight, FontStyle.Posture.NORMAL)))));
    }

    private static final class Section {
        final String id;
        final ReadableSignal<String> title, description, annotation;
        final SceneIconButton.Symbol icon;
        final Function<ScenePageContext, SceneNode> content;
        Section(String id, ReadableSignal<String> title, ReadableSignal<String> description,
                SceneIconButton.Symbol icon, ReadableSignal<String> annotation, Function<ScenePageContext, SceneNode> content) {
            this.id = id; this.title = title; this.description = description; this.icon = icon;
            this.annotation = annotation; this.content = content;
        }
        String navigationTitle() {
            String note = annotation.get();
            return title.get() + (note.isEmpty() ? "" : " · " + note);
        }
    }

    private static final class HeaderAction {
        final ReadableSignal<String> label;
        final SceneIconButton.Symbol icon;
        final SceneCommand command;
        HeaderAction(ReadableSignal<String> label, SceneIconButton.Symbol icon, SceneCommand command) {
            this.label = label; this.icon = icon; this.command = command;
        }
    }

    private static final class Action {
        final ReadableSignal<String> label;
        final SceneCommand command;
        final boolean primary;
        Action(ReadableSignal<String> label, SceneCommand command, boolean primary) {
            this.label = label; this.command = command; this.primary = primary;
        }
    }
}
