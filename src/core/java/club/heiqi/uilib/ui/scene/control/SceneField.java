package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;

import java.util.Objects;

/** 字段只组合呈现与语义关系；编辑和校验权威由调用者持有。 */
public final class SceneField {
    private SceneField() {}

    public static final class Labels {
        public final String name, description, error;
        public final boolean inline;

        public Labels(String name, String description, String error) {
            this(name, description, error, false);
        }

        public Labels(String name, String description, String error, boolean inline) {
            this.name = Objects.requireNonNull(name);
            this.description = Objects.requireNonNull(description);
            this.error = Objects.requireNonNull(error);
            this.inline = inline;
        }
    }

    public static final class Content {
        public final SceneNode view, editor, actions;

        public Content(SceneNode editor) {
            this(editor, editor, null);
        }

        public Content(SceneNode view, SceneNode editor, SceneNode actions) {
            this.view = Objects.requireNonNull(view);
            this.editor = Objects.requireNonNull(editor);
            this.actions = actions;
        }
    }

    public static SceneNode create(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Labels> labels,
            SceneNode editor) {
        return create(page, theme, labels, new Content(editor));
    }

    public static SceneNode create(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Labels> labels,
            Content content) {
        Objects.requireNonNull(page).domain().checkWrite();
        Objects.requireNonNull(labels);
        Objects.requireNonNull(content);
        SceneAppearance style = new SceneAppearance(page, theme);
        SceneNode name = style.label(() -> labels.get().name, 1, false);
        SceneNode help =
                style.label(() -> labels.get().description, .86f, true)
                        .bind(
                                NodeBinding.value(
                                        () -> !labels.get().description.isEmpty(),
                                        SceneNode::visible));
        SceneNode error =
                style.label(() -> labels.get().error, .86f, false)
                        .bind(
                                NodeBinding.value(
                                        style.colors,
                                        (node, palette) ->
                                                node.style(
                                                        palette.source
                                                                .textStyle
                                                                .withEm(
                                                                        palette.source
                                                                                        .textStyle
                                                                                        .logicalEm
                                                                                * .86f)
                                                                .withColor(palette.error))))
                        .bind(
                                NodeBinding.value(
                                        () -> !labels.get().error.isEmpty(), SceneNode::visible));
        content.editor.bind(
                NodeBinding.semantics(
                        () ->
                                SceneSemantics.builder()
                                        .labelledBy(name)
                                        .describedBy(help, error)
                                        .build()));
        SceneNode value = content.view;
        if (content.actions != null)
            value =
                    SceneLayouts.split(
                            content.view,
                            content.actions,
                            () ->
                                    new SceneLayouts.SplitOptions(
                                            Math.round(theme.get().textStyle.logicalEm * 12),
                                            Math.round(theme.get().textStyle.logicalEm * .6f)));
        SceneNode split =
                SceneLayouts.split(
                        SceneAppearance.column(6).add(name).add(help),
                        value,
                        () ->
                                new SceneLayouts.SplitOptions(
                                        labels.get().inline
                                                ? Math.round(theme.get().textStyle.logicalEm * 15)
                                                : Integer.MAX_VALUE,
                                        Math.round(theme.get().textStyle.logicalEm * .6f)));
        return SceneAppearance.column(6)
                .size(Size.fill(1), Size.natural())
                .add(split)
                .add(error)
                .own(style.scope);
    }
}
