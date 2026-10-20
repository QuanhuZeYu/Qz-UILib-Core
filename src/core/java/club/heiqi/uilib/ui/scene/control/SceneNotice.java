package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;

import java.util.Objects;

/** 统一反馈内容；出现、持续时长与业务恢复动作由声明者选择。 */
public final class SceneNotice {
    private SceneNotice() {}

    public enum Kind {
        INFO,
        SUCCESS,
        WARNING,
        ERROR,
        EMPTY
    }

    public static final class Message {
        public final Kind kind;
        public final String title, text;

        public Message(Kind kind, String title, String text) {
            this.kind = Objects.requireNonNull(kind);
            this.title = Objects.requireNonNull(title);
            this.text = Objects.requireNonNull(text);
        }
    }

    public static SceneNode create(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Message> message) {
        return create(page, theme, message, null);
    }

    public static SceneNode create(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Message> message,
            SceneNode action) {
        Objects.requireNonNull(page).domain().checkWrite();
        Objects.requireNonNull(message);
        SceneAppearance style = new SceneAppearance(page, theme);
        SceneNode title =
                style.label(() -> message.get() == null ? "" : message.get().title, 1, false)
                        .bind(
                                NodeBinding.value(
                                        () ->
                                                message.get() != null
                                                        && !message.get().title.isEmpty(),
                                        SceneNode::visible));
        SceneNode text =
                style.label(() -> message.get() == null ? "" : message.get().text, .86f, true)
                        .bind(
                                NodeBinding.value(
                                        () ->
                                                message.get() != null
                                                                && message.get().kind == Kind.ERROR
                                                        ? style.colors.get().error
                                                        : style.colors.get().muted,
                                        (node, color) ->
                                                node.style(
                                                        theme.get()
                                                                .textStyle
                                                                .withEm(
                                                                        theme.get()
                                                                                        .textStyle
                                                                                        .logicalEm
                                                                                * .86f)
                                                                .withColor(color))));
        SceneNode root =
                SceneAppearance.column(5)
                        .add(title)
                        .add(text)
                        .bind(NodeBinding.value(() -> message.get() != null, SceneNode::visible))
                        .bind(
                                NodeBinding.semantics(
                                        () ->
                                                SceneSemantics.builder(SceneSemantics.Role.GROUP)
                                                        .name(
                                                                message.get() == null
                                                                        ? ""
                                                                        : message.get().title)
                                                        .build()))
                        .own(style.scope);
        if (action != null) root.add(action);
        return root;
    }
}
