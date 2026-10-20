package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 广场局部视觉词汇；正式控件承担输入，卡片只装饰一个按正式引擎布局的内容节点。 */
final class GalleryStyle {
    static final int
            LIGHT_BACKGROUND = club.heiqi.uilib.ui.scene.control.SceneAppearance.LIGHT_CANVAS,
            DARK_BACKGROUND = club.heiqi.uilib.ui.scene.control.SceneAppearance.DARK_CANVAS;

    private GalleryStyle() {}

    static SceneTheme theme(boolean dark) {
        return club.heiqi.uilib.ui.scene.control.SceneAppearance.theme(dark);
    }

    static SceneNode card(ReadableSignal<SceneTheme> theme) {
        return new SceneNode().size(Size.fill(1), Size.natural()).padding(20).gap(12);
    }

    static SceneNode label(
            ScenePageContext context,
            ReadableSignal<String> value,
            ReadableSignal<SceneTheme> theme,
            int em,
            boolean muted) {
        return SceneLabel.create(
                context,
                value,
                () ->
                        theme.get()
                                .withTextStyle(
                                        theme.get()
                                                .textStyle
                                                .withEm(em)
                                                .withFontStyle(
                                                        new club.heiqi.uilib.ui.scene.text
                                                                .FontStyle(
                                                                em >= 18 ? 600 : 400,
                                                                club.heiqi.uilib.ui.scene.text
                                                                        .FontStyle.Posture.NORMAL))
                                                .withColor(
                                                        muted
                                                                ? new club.heiqi.uilib.ui.scene
                                                                                .control
                                                                                .SceneAppearance
                                                                                .Palette(
                                                                                theme.get())
                                                                        .muted
                                                                : theme.get().textStyle.color)));
    }

    static SceneNode button(
            ScenePageContext context,
            ReadableSignal<String> value,
            ReadableSignal<SceneTheme> theme,
            Runnable action) {
        return club.heiqi.uilib.ui.scene.control.SceneAppearance.button(
                context,
                value,
                theme,
                action,
                club.heiqi.uilib.ui.scene.control.SceneAppearance.Role.OUTLINE);
    }

    static SceneNode primary(
            ScenePageContext context,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            Runnable action) {
        return club.heiqi.uilib.ui.scene.control.SceneAppearance.button(
                context,
                text,
                theme,
                action,
                club.heiqi.uilib.ui.scene.control.SceneAppearance.Role.PRIMARY);
    }

    static SceneNode button(
            ScenePageContext context,
            ReadableSignal<String> value,
            ReadableSignal<SceneTheme> theme,
            club.heiqi.uilib.ui.scene.control.SceneCommand command) {
        return club.heiqi.uilib.ui.scene.control.SceneAppearance.button(
                context,
                value,
                theme,
                command,
                club.heiqi.uilib.ui.scene.control.SceneAppearance.Role.OUTLINE);
    }

    static SceneNode primary(
            ScenePageContext context,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            club.heiqi.uilib.ui.scene.control.SceneCommand command) {
        return club.heiqi.uilib.ui.scene.control.SceneAppearance.button(
                context,
                text,
                theme,
                command,
                club.heiqi.uilib.ui.scene.control.SceneAppearance.Role.PRIMARY);
    }

    static SceneNode header(
            ScenePageContext context,
            ReadableSignal<SceneTheme> theme,
            String title,
            String description,
            Runnable back) {
        SceneNode heading = centeredRow().size(Size.fill(1), Size.natural()).gap(16);
        heading.add(
                label(context, () -> title, theme, 28, false).size(Size.fill(1), Size.natural()));
        if (back != null) heading.add(button(context, () -> "返回场景概览", theme, back));
        return new SceneNode()
                .size(Size.fill(1), Size.natural())
                .gap(8)
                .add(heading)
                .add(label(context, () -> description, theme, 15, true));
    }

    static SceneNode frame(
            ScenePageContext page, SceneNode content, ReadableSignal<SceneTheme> theme) {
        club.heiqi.uilib.ui.scene.control.SceneAppearance style =
                new club.heiqi.uilib.ui.scene.control.SceneAppearance(page, theme);
        return style.card(content).padding(1).own(style.scope);
    }

    static SceneNode grid(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        club.heiqi.uilib.ui.reactive.Owner scope = context.scope().child();
        ReadableSignal<SceneGrid.Policy> policy =
                context.domain()
                        .computed(
                                scope,
                                () -> {
                                    SceneTheme current = theme.get();
                                    return new SceneGrid.Policy(
                                            (int) Math.ceil(current.textStyle.logicalEm * 22),
                                            current.gap + 4,
                                            current.gap + 4);
                                });
        return SceneGrid.create(context, policy).own(scope);
    }

    static SceneNode dragGrip(ReadableSignal<SceneTheme> theme) {
        return club.heiqi.uilib.ui.scene.control.SceneDragHandle.create(theme);
    }

    static SceneNode centeredRow() {
        return club.heiqi.uilib.ui.scene.control.SceneAppearance.row(0)
                .size(Size.natural(), Size.natural());
    }
}
