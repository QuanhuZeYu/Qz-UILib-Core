package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import java.util.Objects;

/** 摘要与前后页意图，不要求总数，也不持有分页或请求状态。 */
public final class ScenePagination {
    private ScenePagination() {}

    public static SceneNode create(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<String> summary,
            SceneCommand previous,
            SceneCommand next) {
        Objects.requireNonNull(page).domain().checkWrite();
        Objects.requireNonNull(summary);
        return create(
                page,
                theme,
                summary,
                button(page, theme, SceneIconButton.Symbol.CHEVRON_LEFT, "上一页", previous),
                button(page, theme, SceneIconButton.Symbol.CHEVRON_RIGHT, "下一页", next));
    }

    public static SceneNode create(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<String> summary,
            SceneNode previous,
            SceneNode next) {
        Objects.requireNonNull(page).domain().checkWrite();
        SceneAppearance style = new SceneAppearance(page, theme);
        return SceneAppearance.row(4)
                .size(Size.fill(1), Size.natural())
                .add(style.label(summary, .8f, true).size(Size.fill(1), Size.natural()))
                .add(Objects.requireNonNull(previous))
                .add(Objects.requireNonNull(next))
                .own(style.scope);
    }

    private static SceneNode button(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            SceneIconButton.Symbol icon,
            String name,
            SceneCommand command) {
        return SceneIconButton.create(
                        page, icon, () -> name, theme, Objects.requireNonNull(command))
                .bind(
                        NodeBinding.value(
                                theme,
                                (node, value) -> {
                                    int size =
                                            Math.max(
                                                    1,
                                                    Math.round(
                                                            value.textStyle.logicalEm * 32f / 14f));
                                    node.size(Size.fixed(size), Size.fixed(size));
                                }));
    }
}
