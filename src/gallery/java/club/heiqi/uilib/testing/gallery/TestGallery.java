package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.settings.SettingsPage;

import java.util.Collections;
import java.util.function.Consumer;

/** 仅收录有真实参考的应用页面；页面内容复用正式组件，入口不保留旧示例分派。 */
public final class TestGallery implements ScenePage {
    public enum Page {
        HOME("场景概览", "按类别浏览通用组件与综合应用"),
        INPUTS("基础输入", "按钮、文本编辑、开关、滑杆、选择、菜单与标签"),
        LAYOUTS("布局组合", "换行、居中、叠层、具名区域与响应网格"),
        FORMS("表单与搜索", "标签、说明、错误、附属动作与搜索框"),
        DIALOGS("弹窗与选择", "确认、输入、嵌套模态与内容选择"),
        FILE_PICKER("本地文件选择", "目录浏览、路径导航、搜索与文件核验"),
        COLLECTIONS("集合与主从", "稳定键虚拟列表、主从区域与已知或未知总数分页"),
        NOTICES("状态反馈", "说明、成功、警告、错误与空态"),
        NOTIFICATIONS("浮动通知", "Toast 轻提示、持续通知、堆叠与附属动作"),
        DOCUMENTS("文字与文档", "标签、富文本、链接、文档与数学"),
        MAILBOX("收件箱", "查找、阅读和整理团队邮件"),
        SETTINGS("配置", "管理字体、外观与当前宿主配置"),
        DRAGGABLE("拖拽", "拾取、移动与释放普通内容"),
        DROPPABLE("接收区", "选择有效目标并放下内容"),
        SORTABLE("排序", "重排列表、网格与跨容器内容"),
        SCROLL_AREA("滚动区", "浏览纵横内容、嵌套视口与动态滚动条"),
        MOTION("动效", "状态、展开、重排与手势释放的自然接续"),
        MEDIA("媒体播放", "播放视频与音频、暂停和跳转"),
        TOOLTIP("Tooltip", "悬停与键盘提示、方向避让和完整说明"),
        DATA_TABLE("任务数据表", "排序、编辑备注与调整数据列"),
        RESOURCE_TREE("项目资源", "展开目录、浏览资源与编辑名称"),
        PREFERENCES("通知偏好", "开关与单选条件组成的偏好表单"),
        PROJECT_DETAILS("项目详情", "标签切换、独立草稿与保存反馈"),
        VECTOR_PRESENTATION("路径与形状", "环形进度、扇区动作、旋转与形状编辑视口"),
        DENSITY("尺寸与密度", "紧凑与舒适排布、局部尺寸和同一份编辑内容"),
        INSPECTION("场景检查", "查看当前布局、裁剪、输入归属与准备原因"),
        VERIFICATION("核心验证", "运行核心检查并查看实际结果");
        public final String title, purpose;

        Page(String title, String purpose) {
            this.title = title;
            this.purpose = purpose;
        }

        String reference() {
            switch (this) {
                case DATA_TABLE:
                    return "参考：桌面任务工作区";
                case RESOURCE_TREE:
                    return "参考：项目与资源浏览器";
                case PREFERENCES:
                    return "参考：项目通知与偏好表单";
                case PROJECT_DETAILS:
                    return "参考：项目详情与标签导航";
                case VECTOR_PRESENTATION:
                    return "参考：React Konva / 形状、裁剪与变换";
                case DENSITY:
                    return "参考：MUI Density / React Spectrum";
                case INSPECTION:
                    return "参考：React DevTools / MDN Box Model";
                case VERIFICATION:
                    return "UILib / 核心验证";
                case MEDIA:
                    return "参考：HTML video / 媒体控制";
                case TOOLTIP:
                    return "参考：shadcn Base / Tooltip";
                case NOTIFICATIONS:
                    return "参考：shadcn Toast / Sonner";
                case MAILBOX:
                    return "参考：Gmail";
                case DRAGGABLE:
                    return "参考：dnd-kit / Draggable";
                case DROPPABLE:
                    return "参考：dnd-kit / Droppable";
                case SORTABLE:
                    return "参考：dnd-kit / Sortable";
                case SCROLL_AREA:
                    return "参考：SimpleBar / Examples";
                case MOTION:
                    return "参考：shadcn / 声明式动效";
                default:
                    return "参考：Shadcn Admin / Settings";
            }
        }
    }

    private final GalleryEnvironment environment;
    private final Page initial;
    private final club.heiqi.uilib.ui.scene.localization.LocalizationSource language;
    private Signal<Page> selected;
    private SettingsPage settingsPage;
    private MotionPage motionPage;

    public MotionPage motionPage() {
        return motionPage;
    }

    public TestGallery(GalleryEnvironment environment) {
        this(environment, Page.HOME);
    }

    public TestGallery(GalleryEnvironment environment, Page initial) {
        this(environment, initial, null);
    }

    /** 标准组合文案借用显式来源；资料准备、选择及重载归装配者。 */
    public TestGallery(GalleryEnvironment environment, Page initial,
            club.heiqi.uilib.ui.scene.localization.LocalizationSource language) {
        this.environment = environment;
        this.initial = initial;
        this.language = language;
    }

    @Override
    public void build(ScenePageContext context) {
        GalleryCompositeLabels labels = language == null ? GalleryCompositeLabels.defaults()
                : GalleryCompositeLabels.from(context.localization(language));
        selected = context.domain().signal(initial);
        settingsPage = new SettingsPage(environment.settings(), () -> selected.set(Page.HOME));
        context.own(
                () -> {
                    selected = null;
                    settingsPage = null;
                    motionPage = null;
                });
        Signal<ThemeMode> preview = context.domain().signal(null);
        ReadableSignal<ThemeMode> global = context.themeMode();
        context.domain()
                .effect(
                        context.scope(),
                        () -> {
                            global.get();
                            preview.set(null);
                        });
        ReadableSignal<ThemeMode> mode =
                context.domain()
                        .computed(
                                context.scope(),
                                () -> preview.get() == null ? global.get() : preview.get());
        ReadableSignal<Boolean> dark =
                context.domain().computed(context.scope(), () -> mode.get().isDark());
        Signal<club.heiqi.uilib.ui.scene.control.SceneDensity> density =
                context.domain().signal(club.heiqi.uilib.ui.scene.control.SceneDensity.COMFORTABLE);
        context.density(density);
        ReadableSignal<SceneTheme> theme =
                context.domain()
                        .computed(
                                context.scope(),
                                () -> GalleryStyle.theme(dark.get()).withDensity(density.get()));
        MediaPage media = new MediaPage(context, environment, theme, labels);
        motionPage =
                new MotionPage(
                        context,
                        theme,
                        () -> preview.set(dark.get() ? ThemeMode.LIGHT : ThemeMode.DARK));
        context.motionPolicy(
                () ->
                        selected.get() == Page.MOTION
                                ? motionPage.policy().get()
                                : club.heiqi.uilib.ui.scene.motion.MotionPolicy.NORMAL);
        Consumer<Page> navigate =
                target -> {
                    if (selected.get() == Page.MEDIA && target != Page.MEDIA) media.stop();
                    selected.set(target);
                };
        SceneNode shell =
                new SceneNode()
                        .size(Size.fill(1), Size.fill(1))
                        .bind(
                                NodeBinding.value(
                                        dark,
                                        (n, value) ->
                                                n.background(
                                                        value
                                                                ? GalleryStyle.DARK_BACKGROUND
                                                                : GalleryStyle.LIGHT_BACKGROUND)));
        SceneNode header =
                GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).padding(24).gap(16);
        header.bind(NodeBinding.value(() -> selected.get() == Page.HOME, SceneNode::visible));
        header.add(
                GalleryStyle.label(context, () -> "QZ / 场景工作台", theme, 20, false)
                        .size(Size.fill(1), Size.natural()));
        header.add(
                GalleryStyle.button(context, () -> "场景概览", theme, () -> navigate.accept(Page.HOME))
                        .clickFocus(false));
        header.add(
                GalleryStyle.button(
                        context,
                        () -> dark.get() ? "预览浅色" : "预览深色",
                        theme,
                        () -> preview.set(dark.get() ? ThemeMode.LIGHT : ThemeMode.DARK)));
        shell.add(header);
        SceneNode deck = club.heiqi.uilib.ui.scene.layout.SceneLayouts.overlay();
        for (Page id : Page.values()) {
            SceneNode page;
            switch (id) {
                case HOME:
                    page = GalleryDirectory.create(context, theme, navigate);
                    break;
                case INPUTS:
                    page =
                            InputComponentsPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case LAYOUTS:
                    page =
                            LayoutComponentsPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case FORMS:
                    page =
                            FormComponentsPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME), labels);
                    break;
                case DIALOGS:
                    page =
                            DialogComponentsPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME), labels);
                    break;
                case FILE_PICKER:
                    page =
                            FilePickerComponentsPage.create(
                                    context, theme, environment, () -> navigate.accept(Page.HOME), labels);
                    break;
                case COLLECTIONS:
                    page =
                            CollectionComponentsPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME), labels);
                    break;
                case NOTICES:
                    page =
                            NoticeComponentsPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case NOTIFICATIONS:
                    page = NotificationComponentsPage.create(
                            context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case DOCUMENTS:
                    page =
                            DocumentComponentsPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case MEDIA:
                    page = media.create(() -> navigate.accept(Page.HOME));
                    break;
                case SETTINGS:
                    page = settingsPage.create(context);
                    break;
                case DRAGGABLE:
                    page = DraggablePage.create(context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case DROPPABLE:
                    page = DroppablePage.create(context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case SORTABLE:
                    page = SortablePage.create(context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case SCROLL_AREA:
                    page = ScrollAreaPage.create(context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case MOTION:
                    page = motionPage.create(() -> navigate.accept(Page.HOME));
                    break;
                case TOOLTIP:
                    page = TooltipPage.create(context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case DATA_TABLE:
                    page = DataTablePage.create(context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case RESOURCE_TREE:
                    page =
                            ResourceTreePage.create(
                                    context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case PREFERENCES:
                    page = PreferencesPage.create(context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case PROJECT_DETAILS:
                    page =
                            ProjectDetailsPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case VECTOR_PRESENTATION:
                    page =
                            VectorPresentationPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case DENSITY:
                    page =
                            DensityPage.create(
                                    context, theme, density, () -> navigate.accept(Page.HOME));
                    break;
                case INSPECTION:
                    page = InspectionPage.create(context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case VERIFICATION:
                    page =
                            VerificationPage.create(
                                    context, theme, () -> navigate.accept(Page.HOME));
                    break;
                case MAILBOX:
                    page =
                            club.heiqi.uilib.testing.gallery.mail.MailboxPage.create(
                                    context,
                                    theme,
                                    mode,
                                    () -> navigate.accept(Page.HOME),
                                    () ->
                                            preview.set(
                                                    dark.get() ? ThemeMode.LIGHT : ThemeMode.DARK), labels);
                    break;
                default:
                    throw new AssertionError(id);
            }
            context.own(page::destroy);
            SceneNode layer = club.heiqi.uilib.ui.scene.layout.SceneLayouts.overlay();
            layer.bind(
                    NodeBinding.children(
                            () ->
                                    selected.get() == id
                                            ? Collections.singletonList(page)
                                            : Collections.emptyList()));
            deck.add(layer);
        }
        shell.add(deck);
        context.mountOwned(shell);
    }

    /** 原生宿主请求按当前页面转交；确认完成动作由窗口提供，不混入目录导航。 */
    public void requestClose(Runnable complete) {
        environment
                .settings()
                .domain()
                .execute(
                        () -> {
                            if (selected.get() == Page.SETTINGS)
                                settingsPage.requestClose(complete);
                            else complete.run();
                        });
    }

    public void requestReload(Runnable complete) {
        environment
                .settings()
                .domain()
                .execute(
                        () -> {
                            if (selected.get() == Page.SETTINGS) settingsPage.requestReload();
                            else complete.run();
                        });
    }
}
