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

/** 通用字段组合；值与校验属于业务，语义、编辑及主题沿正式控件。 */
public final class SettingsFields {
    private final ScenePageContext context;

    public SettingsFields(ScenePageContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public SceneNode row(ReadableSignal<String> label, ReadableSignal<String> description,
            SceneNode editor, ReadableSignal<String> error) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(error, "error");
        return row(new SceneAppearance(context, context.theme()),
                label, description, new SceneField.Content(editor), error, false);
    }

    private SceneNode row(SceneAppearance style, ReadableSignal<String> label, ReadableSignal<String> description,
            SceneField.Content content, ReadableSignal<String> error, boolean inline) {
        return SceneField.create(context, () -> style.colors.get().editorTheme(),
                () -> new SceneField.Labels(label.get(), description.get(), error.get(), inline), content)
                .bind(NodeBinding.value(() -> style.colors.get().source.metrics(SceneControlMetrics.Kind.FIELD).gap,
                        SceneNode::gap))
                .own(style.scope);
    }

    /** 每次输入写入调用者的草稿 Signal；业务命令自行决定是否应用或保存。 */
    public SceneNode text(ReadableSignal<String> label, ReadableSignal<String> description,
            Signal<String> value, ReadableSignal<Boolean> enabled, ReadableSignal<String> error) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(enabled, "enabled");
        SceneAppearance style = new SceneAppearance(context, context.theme());
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
                }, SceneTextField.SubmitPolicy.INPUT)
                .size(Size.fill(1), Size.natural())
                .bind(NodeBinding.value(enabled, SceneNode::enabled));
        return row(style, label, description, new SceneField.Content(style.field(editor), editor, null), error, false)
                .own(owner);
    }

    public SceneNode toggle(ReadableSignal<String> label, ReadableSignal<String> description,
            Signal<Boolean> value, ReadableSignal<Boolean> enabled, ReadableSignal<String> error) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(enabled, "enabled");
        SceneAppearance style = new SceneAppearance(context, context.theme());
        SceneNode editor = SceneSwitch.create(context, value, label, () -> style.colors.get().editorTheme(), value::set, false)
                .bind(NodeBinding.value(enabled, SceneNode::enabled));
        return row(style, label, description, new SceneField.Content(editor), error, true);
    }

    /** 标准选择器与文本共用现代编辑器主题和外表面，仍由正式 Select 发布选择意图。 */
    public <K> SceneNode choice(ReadableSignal<String> label, ReadableSignal<String> description,
            ReadableSignal<List<SceneSelect.Option<K>>> options, Signal<K> value,
            ReadableSignal<Boolean> enabled, ReadableSignal<String> error) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(enabled, "enabled");
        SceneAppearance style = new SceneAppearance(context, context.theme());
        SceneNode editor = SceneSelect.create(context, options, value, value::set, label,
                () -> style.colors.get().editorTheme())
                .size(Size.fill(1), Size.natural())
                .bind(NodeBinding.value(enabled, SceneNode::enabled));
        return row(style, label, description, new SceneField.Content(style.field(editor), editor, null), error, false);
    }
}
