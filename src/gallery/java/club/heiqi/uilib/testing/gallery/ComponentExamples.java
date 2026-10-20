package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 示例只提供内容与业务状态，表面和排列直接调用公共组件。 */
final class ComponentExamples {
    final ScenePageContext page;
    final ReadableSignal<SceneTheme> theme;
    final SceneAppearance style;
    final SceneNode root;

    ComponentExamples(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            String title,
            String description,
            Runnable back) {
        this.page = page;
        this.theme = theme;
        style = new SceneAppearance(page, theme);
        root =
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .padding(28)
                        .gap(20)
                        .scrollable(true)
                        .own(style.scope);
        root.add(GalleryStyle.header(page, theme, title, description, back));
    }

    SceneNode section(String title, String description, SceneNode... content) {
        SceneNode body =
                SceneAppearance.column(12)
                        .padding(20)
                        .add(style.label(() -> title, 1.25f, false))
                        .add(style.label(() -> description, .9f, true));
        for (SceneNode node : content) body.add(node);
        SceneNode card = style.card(body);
        root.add(card);
        return card;
    }
}
