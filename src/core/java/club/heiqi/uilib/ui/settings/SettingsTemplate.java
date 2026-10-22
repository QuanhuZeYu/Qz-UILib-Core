package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
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

    public SettingsTemplate(ReadableSignal<String> title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public SettingsTemplate description(ReadableSignal<String> value) {
        description = Objects.requireNonNull(value, "description"); return this;
    }

    public SettingsTemplate section(String id, ReadableSignal<String> title,
            Function<ScenePageContext, SceneNode> content) {
        Objects.requireNonNull(id, "id");
        for (Section section : sections)
            if (section.id.equals(id)) throw new IllegalArgumentException("重复设置分类：" + id);
        sections.add(new Section(id, Objects.requireNonNull(title, "title"),
                Objects.requireNonNull(content, "content")));
        return this;
    }

    public SettingsTemplate action(ReadableSignal<String> label, SceneCommand command, boolean primary) {
        actions.add(new Action(Objects.requireNonNull(label, "label"),
                Objects.requireNonNull(command, "command"), primary));
        return this;
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
        SceneAppearance ui = new SceneAppearance(context, context.theme());
        SettingsMotion motion = new SettingsMotion(ui.colors);
        ReadableSignal<Float> scale = () -> context.theme().get().textStyle.logicalEm / 18f;
        ReadableSignal<Boolean> compact = () -> context.viewportWidth().get() < Math.round(760 * scale.get());
        ReadableSignal<Boolean> shortWindow = () -> context.viewportHeight().get() < Math.round(560 * scale.get());
        Signal<String> selected = context.domain().signal(sections.isEmpty() ? "" : sections.get(0).id);
        SceneNode content = SceneAppearance.column(16)
                .add(ui.label(title, 1.8f, false))
                .add(ui.label(description, .95f, true)
                        .bind(NodeBinding.value(() -> !description.get().isEmpty() && !shortWindow.get(), SceneNode::visible)));
        content.add(ui.line());
        SceneScrollArea.Area scrolling = SceneScrollArea.create(context, content,
                () -> new SceneScrollArea.Options().autoHide(false), context.theme());
        SceneNode viewport = scrolling.viewport(SceneNode.Axis.COLUMN);
        java.util.function.Consumer<String> navigate = id -> {
            selected.set(id);
            SceneScrollViewport.of(context, viewport).moveToPixels(0);
        };
        SceneNode rail = SceneAppearance.column(8).padding(16)
                .bind(NodeBinding.value(compact, (n, v) -> n.visible(!v)));
        List<SceneSelect.Option<String>> options = new ArrayList<>();
        // 分类声明只构造一次；切换仅结束输入资格，外部草稿及原编辑身份持续保留。
        for (Section section : sections) {
            ReadableSignal<Boolean> active = () -> selected.get().equals(section.id);
            rail.add(ui.action(section.title, ui.label(section.title, 1, false),
                    () -> navigate.accept(section.id), SceneAppearance.Role.ENTRY, active));
            options.add(new SceneSelect.Option<>(section.id, section.title));
            content.add(motion.reveal(SceneAppearance.column(12)
                    .add(ui.label(section.title, 1.25f, false))
                    .add(Objects.requireNonNull(section.content.apply(context), "分类内容")), active));
        }
        SceneNode selector = SceneSelect.create(context, () -> options, selected, navigate,
                sectionLabel, context.theme())
                .size(Size.fill(1), Size.natural())
                .bind(NodeBinding.value(compact, SceneNode::visible));
        SceneNode navigation = SceneAppearance.column(0).add(rail).add(selector)
                .bind(NodeBinding.value(compact,
                        (n, v) -> n.size(Size.fill(1), v ? Size.natural() : Size.fill(1))));
        SceneNode toolbar = SceneAppearance.row(8)
                .add(ui.label(title, 1, true).size(Size.fill(1), Size.natural()))
                .add(ui.action(closeLabel, SceneIconButton.Symbol.CLOSE, () -> close.run(), SceneAppearance.Role.GHOST));
        SceneNode buttons = SceneAppearance.row(8);
        for (Action action : actions)
            buttons.add(ui.action(action.label, null, action.command,
                    action.primary ? SceneAppearance.Role.PRIMARY : SceneAppearance.Role.OUTLINE));
        SceneNode feedback = ui.label(notice, .9f, true)
                .bind(NodeBinding.value(() -> !notice.get().isEmpty(), SceneNode::visible));
        SceneNode footer = SceneAppearance.column(8).add(feedback).add(buttons).clip(true).scrollable(true);
        return SettingsLayout.workspace(scale, compact, shortWindow, toolbar, navigation,
                scrolling.node(), footer, null)
                .bind(NodeBinding.value(ui.colors, (n, p) -> n.background(p.background)))
                .onEscape(() -> { close.run(); return true; })
                .own(ui.scope);
    }

    private static final class Section {
        final String id;
        final ReadableSignal<String> title;
        final Function<ScenePageContext, SceneNode> content;
        Section(String id, ReadableSignal<String> title, Function<ScenePageContext, SceneNode> content) {
            this.id = id; this.title = title; this.content = content;
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
