package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneAnimatedContent;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.EntranceOptions;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 与业务 HUD 共用内容切换；广场只发布选择、方向和显隐。 */
public final class ContentMotionCard {
    private final ScenePageContext page;
    private final ReadableSignal<SceneTheme> theme;
    private final Signal<Integer> selected, direction;
    private final Signal<Boolean> vertical, shown;
    private SceneNode content;

    public ContentMotionCard(ScenePageContext page, ReadableSignal<SceneTheme> theme) {
        this.page = page; this.theme = theme;
        selected = page.domain().signal(0); direction = page.domain().signal(1);
        vertical = page.domain().signal(false); shown = page.domain().signal(true);
    }
    public SceneNode create() {
        SceneNode body = SceneAppearance.column(12).padding(16);
        body.add(GalleryStyle.label(page, () -> "方向与内容切换", theme, 20, false));
        body.add(GalleryStyle.label(page, () -> "切换时新内容滑入，旧画面淡出；快速反向保持最新选择。", theme, 14, true));
        SceneNode actions = SceneLayouts.wrap(8);
        actions.add(GalleryStyle.button(page, () -> "上一个内容", theme, () -> change(-1)));
        actions.add(GalleryStyle.button(page, () -> "下一个内容", theme, () -> change(1)));
        actions.add(GalleryStyle.button(page, () -> "横纵切换", theme, () -> vertical.set(!vertical.get())));
        actions.add(GalleryStyle.button(page, () -> "收起内容", theme, () -> shown.set(!shown.get())));
        body.add(actions);
        content = SceneAnimatedContent.create(page, () -> shown.get() ? selected.get() : null, value -> value,
                value -> GalleryStyle.label(page, () -> new String[] {"内容 · 晨光", "内容 · 远行", "内容 · 星夜"}[value.get()], theme, 28, false),
                () -> new EntranceOptions(theme.get().spatialMotion, theme.get().feedbackMotion,
                        vertical.get() ? 0 : direction.get() * theme.get().textStyle.logicalEm * 1.8f,
                        vertical.get() ? direction.get() * theme.get().textStyle.logicalEm * 1.3f : 0));
        SceneNode reading = SceneLayouts.centeredColumn(() -> 4);
        reading.add(content);
        reading.add(GalleryStyle.label(page, () -> "自然宽度 · 同一中线", theme, 12, false).size(Size.natural(), Size.natural()));
        body.add(new SceneNode().size(Size.fill(1), Size.fixed(100)).add(reading));
        return GalleryStyle.frame(page, body, theme);
    }
    private void change(int step) {
        page.domain().batch(() -> { direction.set(step); selected.set(Math.floorMod(selected.get() + step, 3)); shown.set(true); });
    }
    public SceneNode content() { return content; }
    public ReadableSignal<Integer> selected() { return selected; }
}
