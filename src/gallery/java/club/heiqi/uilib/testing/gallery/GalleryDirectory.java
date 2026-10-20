package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 分类、入口和覆盖验证消费同一目录；示例只链接正式组件。 */
public final class GalleryDirectory {
    private GalleryDirectory() {}

    public enum Category {
        INPUT("基础输入", "按钮、编辑、开关、选择、菜单与标签导航。", TestGallery.Page.INPUTS),
        LAYOUT(
                "布局与表单",
                "区域组合、字段说明、搜索与动态尺寸。",
                TestGallery.Page.LAYOUTS,
                TestGallery.Page.FORMS,
                TestGallery.Page.DENSITY),
        OVERLAY(
                "弹层与选择",
                "模态确认、内容选择、本地文件与提示。",
                TestGallery.Page.DIALOGS,
                TestGallery.Page.NOTIFICATIONS,
                TestGallery.Page.FILE_PICKER,
                TestGallery.Page.TOOLTIP),
        DATA(
                "数据集合",
                "主从工作区、分页、数据表与资源树。",
                TestGallery.Page.COLLECTIONS,
                TestGallery.Page.NOTICES,
                TestGallery.Page.DATA_TABLE,
                TestGallery.Page.RESOURCE_TREE),
        INTERACTION(
                "拖放与滚动",
                "拾取、目标、排序、视口与弹性动效。",
                TestGallery.Page.DRAGGABLE,
                TestGallery.Page.DROPPABLE,
                TestGallery.Page.SORTABLE,
                TestGallery.Page.SCROLL_AREA,
                TestGallery.Page.MOTION),
        CONTENT(
                "内容与媒体",
                "文字、文档、公式、图像、矢量与媒体播放。",
                TestGallery.Page.DOCUMENTS,
                TestGallery.Page.VECTOR_PRESENTATION,
                TestGallery.Page.MEDIA),
        APPLICATION(
                "综合应用",
                "在完整流程中体验同一套公共组件。",
                TestGallery.Page.MAILBOX,
                TestGallery.Page.SETTINGS,
                TestGallery.Page.PREFERENCES,
                TestGallery.Page.PROJECT_DETAILS),
        TOOLS(
                "验证工具",
                "检查布局、输入归属与核心行为。",
                TestGallery.Page.INSPECTION,
                TestGallery.Page.VERIFICATION);
        public final String title, description;
        public final List<TestGallery.Page> pages;

        Category(String title, String description, TestGallery.Page... pages) {
            this.title = title;
            this.description = description;
            this.pages = Collections.unmodifiableList(Arrays.asList(pages));
        }
    }

    static SceneNode create(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            Consumer<TestGallery.Page> navigate) {
        SceneAppearance style = new SceneAppearance(page, theme);
        SceneNode root =
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .padding(28)
                        .gap(24)
                        .scrollable(true);
        root.add(
                GalleryStyle.header(
                        page, theme, "组件广场", "按类别浏览通用组件，打开示例直接体验。综合应用展示组件在完整流程中的组合。", null));
        for (Category category : Category.values()) {
            SceneNode section =
                    SceneAppearance.column(12)
                            .add(style.label(() -> category.title, 1.4f, false))
                            .add(style.label(() -> category.description, 1, true));
            SceneNode grid = GalleryStyle.grid(page, theme);
            for (TestGallery.Page target : category.pages) {
                SceneNode card = SceneAppearance.column(12).padding(20);
                card.add(
                        SceneIconButton.graphic(page, () -> icon(category), theme)
                                .size(Size.fixed(24), Size.fixed(24)));
                card.add(style.label(() -> target.title, 1.3f, false))
                        .add(style.label(() -> target.purpose, 1, true));
                card.add(
                        style.action(
                                        "打开" + target.title,
                                        null,
                                        () -> navigate.accept(target),
                                        SceneAppearance.Role.OUTLINE)
                                .clickFocus(false));
                grid.add(style.card(card));
            }
            root.add(section.add(grid));
        }
        return root.own(style.scope);
    }

    private static SceneIconButton.Symbol icon(Category category) {
        switch (category) {
            case INPUT:
                return SceneIconButton.Symbol.SLIDERS;
            case OVERLAY:
                return SceneIconButton.Symbol.FOLDER;
            case CONTENT:
                return SceneIconButton.Symbol.MONITOR;
            case APPLICATION:
                return SceneIconButton.Symbol.MAIL;
            default:
                return SceneIconButton.Symbol.SORT;
        }
    }
}
