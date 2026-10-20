package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import java.util.Objects;
import java.util.function.Consumer;

/** 搜索的输入、清空与焦点组合；外部查询同步及筛选仍由业务决定。 */
public final class SceneSearchField {
    private SceneSearchField() {}

    public static final class View {
        public final SceneNode node, editor;
        public final SceneCommand focus;

        private View(SceneNode node, SceneNode editor, SceneCommand focus) {
            this.node = node;
            this.editor = editor;
            this.focus = focus;
        }
    }

    public static View create(
            ScenePageContext page,
            SceneEditState state,
            ReadableSignal<SceneTheme> theme,
            Consumer<SceneEditState.Submission> submit,
            SceneTextField.SubmitPolicy policy,
            ReadableSignal<String> placeholder,
            Runnable clear) {
        return create(page, state, theme, submit, policy, placeholder, clear, true);
    }

    /** 调用者可为搜索提供应用专属表面，输入与清空仍使用同一正式组合。 */
    public static View content(
            ScenePageContext page,
            SceneEditState state,
            ReadableSignal<SceneTheme> theme,
            Consumer<SceneEditState.Submission> submit,
            SceneTextField.SubmitPolicy policy,
            ReadableSignal<String> placeholder,
            Runnable clear) {
        return create(page, state, theme, submit, policy, placeholder, clear, false);
    }

    private static View create(
            ScenePageContext page,
            SceneEditState state,
            ReadableSignal<SceneTheme> theme,
            Consumer<SceneEditState.Submission> submit,
            SceneTextField.SubmitPolicy policy,
            ReadableSignal<String> placeholder,
            Runnable clear,
            boolean framed) {
        Objects.requireNonNull(page).domain().checkWrite();
        Objects.requireNonNull(clear);
        SceneAppearance style = new SceneAppearance(page, theme);
        SceneNode editor =
                SceneTextField.create(
                        page,
                        state,
                        () -> style.colors.get().editorTheme(),
                        () -> false,
                        submit,
                        policy,
                        placeholder,
                        false);
        SceneNode row = SceneAppearance.row(4).size(Size.fill(1), Size.natural());
        if (framed) {
            SceneAppearance.Surface surface =
                    new SceneAppearance.Surface(
                            style.colors, SceneAppearance.Role.FIELD, editor, () -> false);
            row.control(surface);
            surface.install(row);
        }
        row.add(
                SceneIconButton.graphic(
                                page,
                                () -> SceneIconButton.Symbol.SEARCH,
                                () ->
                                        theme.get()
                                                .withTextStyle(
                                                        theme.get()
                                                                .textStyle
                                                                .withColor(
                                                                        style.colors.get().muted)))
                        .padding(8));
        row.add(editor);
        row.add(
                SceneIconButton.create(
                                page, SceneIconButton.Symbol.CLOSE, () -> "清空搜索", theme, clear)
                        .bind(
                                NodeBinding.value(
                                        () -> !state.text().get().isEmpty(), SceneNode::visible)));
        SceneCommand focus =
                SceneCommand.of(
                        () ->
                                editor.runtime() == page.runtime()
                                        && editor.focusable()
                                        && page.runtime().interactive(editor),
                        () ->
                                page.runtime()
                                        .focusRouter()
                                        .request(page.runtime().captureMount(editor)));
        return new View(row.own(style.scope), editor, focus);
    }
}
