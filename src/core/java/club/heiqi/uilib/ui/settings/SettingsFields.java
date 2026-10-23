package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 通用字段组合；值与校验属于业务，语义、编辑及主题沿正式控件。 */
public final class SettingsFields {
    private final ScenePageContext context;
    private final ReadableSignal<SceneTheme> theme;

    public SettingsFields(ScenePageContext context) {
        this(context, Objects.requireNonNull(context, "context").theme());
    }

    /** 局部预览传完整主题，不改变页面或宿主的主题权威。 */
    public SettingsFields(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        this.context = Objects.requireNonNull(context, "context");
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    public SceneNode row(ReadableSignal<String> label, ReadableSignal<String> description,
            SceneNode editor, ReadableSignal<String> error) {
        return row(label, description, new SceneField.Content(editor), error, false);
    }

    /** 复杂编辑保留正式控件、主焦点及尾部动作，标签和错误仍沿同一字段组合。 */
    public SceneNode row(ReadableSignal<String> label, ReadableSignal<String> description,
            SceneField.Content content, ReadableSignal<String> error, boolean inline) {
        return row(new SceneAppearance(context, theme), label, description, content, error, inline);
    }

    private SceneNode row(SceneAppearance style, ReadableSignal<String> label, ReadableSignal<String> description,
            SceneField.Content content, ReadableSignal<String> error, boolean inline) {
        Objects.requireNonNull(label, "label"); Objects.requireNonNull(description, "description");
        Objects.requireNonNull(content, "content"); Objects.requireNonNull(error, "error");
        return SceneField.create(context, () -> style.colors.get().source,
                () -> new SceneField.Labels(label.get(), description.get(), error.get(), inline), content)
                .bind(NodeBinding.value(() -> style.colors.get().source.metrics(SceneControlMetrics.Kind.FIELD).gap,
                        SceneNode::gap))
                .own(style.scope);
    }

    /** 每次输入写入调用者的草稿 Signal；业务命令自行决定是否应用或保存。 */
    public SceneNode text(ReadableSignal<String> label, ReadableSignal<String> description,
            Signal<String> value, ReadableSignal<Boolean> enabled, ReadableSignal<String> error) {
        Objects.requireNonNull(value, "value"); Objects.requireNonNull(enabled, "enabled");
        SceneAppearance style = new SceneAppearance(context, theme);
        Owner owner = style.scope.child();
        ReadableSignal<SceneTheme> editorTheme = () -> style.colors.get().editorTheme();
        SceneEditState state = new SceneEditState(context.domain(), value.get());
        context.domain().effect(owner, () -> {
            // 与 SceneEditState 单行入口使用相同规范化，避免 reset 的 Snapshot 反复使本 Effect 失效。
            String next = Objects.requireNonNull(value.get(), "字段草稿").replace("\r", "").replace("\n", "");
            // 此 Signal 本身就是业务草稿；显式放弃/恢复须结束旧输入联系，同值输入保留选区与历史。
            if (!next.equals(state.text().get())) state.reset(next);
        });
        SceneNode editor = SceneTextField.create(context, state, editorTheme, () -> false,
                submission -> {
                    if (state.accept(submission, submission.text)) value.set(submission.text);
                }, SceneTextField.SubmitPolicy.INPUT, () -> "", false)
                .size(Size.fill(1), Size.natural())
                .bind(NodeBinding.value(enabled, SceneNode::enabled));
        return row(style, label, description, new SceneField.Content(style.field(editor), editor, null), error, false)
                .own(owner);
    }

    public SceneNode toggle(ReadableSignal<String> label, ReadableSignal<String> description,
            Signal<Boolean> value, ReadableSignal<Boolean> enabled, ReadableSignal<String> error) {
        Objects.requireNonNull(value, "value");
        return toggle(label, description, value, value::set, enabled, error);
    }

    /** 业务可拒绝意图；显示值始终重读原来源，不在字段内部另存草稿。 */
    public SceneNode toggle(ReadableSignal<String> label, ReadableSignal<String> description,
            ReadableSignal<Boolean> value, Consumer<Boolean> change,
            ReadableSignal<Boolean> enabled, ReadableSignal<String> error) {
        Objects.requireNonNull(value, "value"); Objects.requireNonNull(change, "change");
        Objects.requireNonNull(enabled, "enabled");
        SceneAppearance style = new SceneAppearance(context, theme);
        SceneNode editor = SceneSwitch.create(context, value, label, () -> switchTheme(style.colors.get(), value.get()), change, false)
                .bind(NodeBinding.value(enabled, SceneNode::enabled));
        return row(style, label, description, new SceneField.Content(editor), error, true);
    }

    private static SceneTheme switchTheme(SceneAppearance.Palette palette, boolean checked) {
        // Switch 自己绘制轨道；不使用为外层 field 去框的透明编辑主题。
        int thumb = checked ? palette.onPrimary : palette.dark ? palette.text : palette.background;
        return new SceneTheme(palette.textStyle.withColor(thumb), palette.background, 0, 0, 0,
                palette.primary, palette.border, palette.muted, palette.primary, palette.onPrimary,
                palette.source.padding, palette.source.gap)
                .withMotion(palette.source.feedbackMotion, palette.source.spatialMotion)
                .withDensity(palette.source.density).withControlSize(palette.source.controlSize);
    }

    public <K> SceneNode choice(ReadableSignal<String> label, ReadableSignal<String> description,
            ReadableSignal<List<SceneSelect.Option<K>>> options, Signal<K> value,
            ReadableSignal<Boolean> enabled, ReadableSignal<String> error) {
        Objects.requireNonNull(value, "value");
        return choice(label, description, options, value, value::set, enabled, error);
    }

    /** 标准选择器传完整主题；内部触发去框不改变所属弹层表面。 */
    public <K> SceneNode choice(ReadableSignal<String> label, ReadableSignal<String> description,
            ReadableSignal<List<SceneSelect.Option<K>>> options, ReadableSignal<K> value, Consumer<K> change,
            ReadableSignal<Boolean> enabled, ReadableSignal<String> error) {
        Objects.requireNonNull(value, "value"); Objects.requireNonNull(change, "change");
        Objects.requireNonNull(enabled, "enabled");
        SceneAppearance style = new SceneAppearance(context, theme);
        SceneNode editor = SceneSelect.create(context, options, value, change, label,
                () -> style.colors.get().source, false)
                .size(Size.fill(1), Size.natural())
                .bind(NodeBinding.value(enabled, SceneNode::enabled));
        return row(style, label, description, new SceneField.Content(style.field(editor), editor, null), error, false);
    }
}
