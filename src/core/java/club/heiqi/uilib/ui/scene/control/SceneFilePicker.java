package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 可复用的本地文件选择组合控件，行为归 specs/content/2026-10-07-uilib-theme-spec.md。
 * 作为可见来源节点的直属孩子声明，正式所属层占用当前视口并隔离后台输入。 一次挂载对应一次选择；确认或取消先结束本次工作并隐藏，再通知调用方。 调用方通常用 ownedChildren
 * 移除已结束控件；卸载不补发取消或提交。
 */
public final class SceneFilePicker {
    public static final class Options {
        public final String filterName, extensions;

        public Options() {
            this("", "");
        }

        private Options(String name, String extensions) {
            this.filterName = Objects.requireNonNull(name);
            this.extensions = Objects.requireNonNull(extensions);
        }

        public Options filter(String name, String extensions) {
            return new Options(name, extensions);
        }
    }

    private final Options options;
    private final ScenePageContext context;
    private final ReadableSignal<SceneTheme> theme;
    private final FilePickerSelection selection;

    private SceneFilePicker(
            ScenePageContext context,
            ReadableSignal<SceneTheme> theme,
            FilePickerSelection selection,
            Options options) {
        this.options = options;
        this.context = context;
        this.theme = theme;
        this.selection = selection;
    }

    public static SceneNode create(
            ScenePageContext context,
            Path initialDirectory,
            Consumer<Path> onSelected,
            Runnable onCancelled) {
        return create(context, initialDirectory, context.theme(), onSelected, onCancelled);
    }

    public static SceneNode create(
            ScenePageContext context,
            Path initialDirectory,
            ReadableSignal<SceneTheme> theme,
            Consumer<Path> onSelected,
            Runnable onCancelled) {
        return create(context, initialDirectory, theme, new Options(), onSelected, onCancelled);
    }

    public static SceneNode create(
            ScenePageContext context,
            Path initialDirectory,
            Options options,
            Consumer<Path> onSelected,
            Runnable onCancelled) {
        return create(context, initialDirectory, context.theme(), options, onSelected, onCancelled);
    }

    public static SceneNode create(
            ScenePageContext context,
            Path initialDirectory,
            ReadableSignal<SceneTheme> theme,
            Options options,
            Consumer<Path> onSelected,
            Runnable onCancelled) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(initialDirectory, "initialDirectory");
        Objects.requireNonNull(theme, "theme");
        FilePickerSelection selection =
                new FilePickerSelection(
                        context.domain(),
                        initialDirectory,
                        Objects.requireNonNull(onSelected, "onSelected"),
                        Objects.requireNonNull(onCancelled, "onCancelled"));
        return new SceneFilePicker(context, theme, selection, Objects.requireNonNull(options))
                .createNode();
    }

    private SceneNode createNode() {
        FilePickerStyle style = new FilePickerStyle(context, theme);
        SceneNode field =
                SceneTextField.create(
                        context,
                        selection.path,
                        () -> style.colors.get().editorTheme(),
                        () -> false,
                        selection::go,
                        SceneTextField.SubmitPolicy.CONFIRM,
                        () -> "输入目录或文件路径",
                        false);
        SceneNode header = header(style, field);
        SceneNode content =
                FilePickerStyle.column(10)
                        .size(Size.fill(1), Size.fill(1))
                        .padding(12)
                        .add(style.label(() -> "名称", .86f, true)
                                .bind(NodeBinding.value(this::fullChrome, SceneNode::visible)))
                        .add(listing(style))
                        .add(style.label(selection::feedback, .86f, true));
        content.control(new Panel());
        SceneNode modal =
                SceneDialog.create(
                                context,
                                theme,
                                selection.showing,
                                () ->
                                        new SceneDialog.Options(
                                                "选择本地文件",
                                                Math.round(theme.get().textStyle.logicalEm * 58),
                                                Math.round(theme.get().textStyle.logicalEm * 46)),
                                new SceneDialog.Content(
                                        FilePickerStyle.column(0).add(header).add(style.line()),
                                        content,
                                        FilePickerStyle.column(0)
                                                .add(style.line())
                                                .add(footer(style)),
                                        field,
                                        selection::cancel))
                        .own(style.scope);
        modal.bind(
                (runtime, node, owner) -> {
                    selection.attach();
                    return new BindingActivation() {
                        @Override
                        public void refresh() {}

                        @Override
                        public void close() {
                            selection.detach();
                        }
                    };
                });
        // 保留文件选择器可附着于普通流的所属层合同，弹窗外框仍复用正式组合。
        return SceneLayer.create(
                        context,
                        () -> new SceneLayer.Policy(0, SceneLayer.Participation.INTERACTIVE))
                .size(Size.fill(1), Size.fill(1))
                // 退场快照仍可呈现，但关闭的所属层立即退出命中，允许后台继续滚动。
                .bind(NodeBinding.value(selection.showing, SceneNode::enabled))
                .add(modal);
    }

    // 矮窗先保留路径、搜索、列表与确认；临界空间随当前字号同步变化。
    private boolean fullChrome() {
        return context.viewportHeight().get() >= Math.round(theme.get().textStyle.logicalEm * 32);
    }

    private SceneNode header(FilePickerStyle style, SceneNode field) {
        SceneNode heading =
                FilePickerStyle.row(14)
                        .add(style.icon(SceneIconButton.Symbol.FOLDER))
                        .add(
                                FilePickerStyle.column(5)
                                        .add(style.title("选择本地文件"))
                                        .add(
                                                style.label(() -> "浏览文件夹，选择需要打开的文件。", .9f, true)
                                                        .bind(
                                                                NodeBinding.value(
                                                                        () ->
                                                                                context.viewportHeight()
                                                                                                .get()
                                                                                        >= 800,
                                                                        SceneNode::visible))));
        heading.bind(NodeBinding.value(this::fullChrome, SceneNode::visible));
        SceneNode header =
                FilePickerStyle.column(8)
                        .padding(12)
                        .add(heading)
                        .add(navigation(style))
                        .add(
                                FilePickerStyle.row(10)
                                        .add(style.field(field))
                                        .add(
                                                style.action(
                                                        "前往",
                                                        null,
                                                        () ->
                                                                selection.go(
                                                                        selection.path.request(
                                                                                true)),
                                                        FilePickerStyle.Role.OUTLINE)));
        return header.add(search(style)).add(breadcrumbs(style));
    }

    private SceneNode search(FilePickerStyle style) {
        SceneSearchField.View search =
                SceneSearchField.create(
                        context,
                        selection.search,
                        theme,
                        selection::query,
                        SceneTextField.SubmitPolicy.CONFIRM,
                        () -> "输入名称，按 Enter 搜索",
                        selection::clearQuery);
        SceneNode query = FilePickerStyle.row(8)
                .add(search.node)
                .add(style.action(
                        "搜索", SceneIconButton.Symbol.SEARCH,
                        () -> selection.query(selection.search.request(true)),
                        FilePickerStyle.Role.OUTLINE));
        SceneNode tools = FilePickerStyle.row(8)
                .add(
                        style.action(
                                () -> selection.order.get().label,
                                null,
                                selection::cycleOrder,
                                FilePickerStyle.Role.OUTLINE))
                .add(
                        style.action(
                                        () ->
                                                selection.extension.get().isEmpty()
                                                        ? "所有文件"
                                                        : options.filterName,
                                        null,
                                        this::toggleFilter,
                                        FilePickerStyle.Role.GHOST)
                                .bind(
                                        NodeBinding.value(
                                                () -> !options.extensions.isEmpty(),
                                                SceneNode::visible)));
        return SceneLayouts.split(query, tools,
                () -> new SceneLayouts.SplitOptions(
                        Math.round(theme.get().textStyle.logicalEm * 16), 8));
    }

    private void toggleFilter() {
        selection.extension.set(selection.extension.get().isEmpty() ? options.extensions : "");
        selection.refresh();
    }

    private SceneNode breadcrumbs(FilePickerStyle style) {
        SceneNode crumbs = FilePickerStyle.row(3).scrollable(true);
        crumbs.bind(
                NodeBinding.repeat(
                        this::breadcrumbPaths,
                        part -> part,
                        item ->
                                SceneTooltip.attach(
                                        context,
                                        style.action(
                                                        () -> breadcrumbLabel(item.get()),
                                                        null,
                                                        () -> selection.navigate(item.get()),
                                                        FilePickerStyle.Role.GHOST,
                                                        SceneTextPolicy.ELLIPSIS_SINGLE_LINE)
                                                .size(Size.fill(1), Size.natural()),
                                        () -> SceneTooltip.Content.text(item.get().toString()),
                                        () -> new SceneTooltip.Options(),
                                        theme)));
        return crumbs.bind(
                NodeBinding.value(() -> context.viewportHeight().get() >= 700 && !selection.roots.get(), SceneNode::visible));
    }

    private java.util.List<Path> breadcrumbPaths() {
        java.util.ArrayList<Path> chain = new java.util.ArrayList<>();
        if (selection.roots.get()) return chain;
        for (Path cursor = selection.directory.get(); cursor != null; cursor = cursor.getParent())
            chain.add(cursor);
        java.util.Collections.reverse(chain);
        if (chain.size() > 3) chain.subList(1, chain.size() - 2).clear();
        return chain;
    }

    private String breadcrumbLabel(Path part) {
        Path directory = selection.directory.get();
        String prefix =
                part.equals(directory.getParent()) && directory.getNameCount() > 2 ? "… / " : "";
        return prefix
                + (part.getFileName() == null ? part.toString() : part.getFileName().toString());
    }

    private SceneNode navigation(FilePickerStyle style) {
        SceneNode back =
                style.action("后退", null, () -> selection.history(-1), FilePickerStyle.Role.GHOST)
                        .bind(
                                NodeBinding.value(
                                        () ->
                                                selection.backCount.get() > 0
                                                        && !selection.busy.get(),
                                        SceneNode::enabled));
        SceneNode forward =
                style.action("前进", null, () -> selection.history(1), FilePickerStyle.Role.GHOST)
                        .bind(
                                NodeBinding.value(
                                        () ->
                                                selection.forwardCount.get() > 0
                                                        && !selection.busy.get(),
                                        SceneNode::enabled));
        SceneNode actions = SceneLayouts.wrap(8)
                .add(back)
                .add(forward)
                .add(
                        style.action(
                                "上一级",
                                SceneIconButton.Symbol.CHEVRON_LEFT,
                                selection::up,
                                FilePickerStyle.Role.OUTLINE))
                .add(
                        style.action(
                                "根目录",
                                SceneIconButton.Symbol.MONITOR,
                                () -> selection.showRoots(0),
                                FilePickerStyle.Role.GHOST));
        SceneNode refresh = style.action("刷新", null, selection::refresh, FilePickerStyle.Role.GHOST);
        return SceneLayouts.split(actions, refresh,
                () -> new SceneLayouts.SplitOptions(
                        Math.round(theme.get().textStyle.logicalEm * 12), 8));
    }

    private SceneNode listing(FilePickerStyle style) {
        SceneVirtualList<FilePickerDirectory.Entry, Path> listing = SceneVirtualList.create(
                context, selection.entries, entry -> entry.path,
                item -> style.entry(item,
                        () -> item.get().path.equals(selection.currentSelection()),
                        () -> selection.activate(item.get())));
        SceneNode viewport = listing.node().gap(4);
        viewport.bind(NodeBinding.value(selection.revealTarget, (node, target) -> {
            if (target != null) listing.reveal(target);
        }));
        viewport.bind(
                NodeBinding.value(
                        () -> {
                            selection.revision.get();
                            return selection.scroll.get();
                        },
                        SceneNode::scrollOffset));
        viewport.bind(
                (runtime, node, owner) -> {
                    selection.scrollPosition = () -> runtime.scrollOffset(node);
                    return new BindingActivation() {
                        public void refresh() {}

                        public void close() {
                            selection.scrollPosition = () -> 0;
                        }
                    };
                });
        SceneNode scrollbar = SceneScrollbar.create(context, listing.viewport(),
                () -> new SceneScrollbar.Options().dimensions(7, 24, 1)
                        .colors(0, style.colors.get().border, style.colors.get().muted), theme);
        scrollbar.size(Size.natural(), Size.fill(1));
        // 页预算约束文件事实；共享有限集合再按视口准备行，并承接定位与键盘显露。
        return FilePickerStyle.row(0)
                .size(Size.fill(1), Size.fill(1))
                .control(new FilePickerListNavigation(selection, listing))
                .add(viewport).add(scrollbar);
    }

    private SceneNode footer(FilePickerStyle style) {
        SceneNode previous =
                style.action("上一页", null, () -> selection.turn(-1), FilePickerStyle.Role.GHOST);
        previous.bind(
                NodeBinding.value(
                        () -> !selection.busy.get() && selection.previous.get(),
                        SceneNode::enabled));
        SceneNode following =
                style.action("下一页", null, () -> selection.turn(1), FilePickerStyle.Role.GHOST);
        following.bind(
                NodeBinding.value(
                        () -> !selection.busy.get() && selection.next.get(), SceneNode::enabled));
        SceneNode pages =
                ScenePagination.create(
                        context,
                        theme,
                        () -> "第 " + (selection.page.get() + 1) + " 页",
                        previous,
                        following);
        SceneNode choose =
                style.action("选择文件", null, selection::confirm, FilePickerStyle.Role.PRIMARY);
        choose.bind(NodeBinding.value(() -> !selection.busy.get(), SceneNode::enabled));
        SceneNode actions =
                FilePickerStyle.row(10)
                        .add(
                                style.ellipsis(selection::targetLabel, .86f, true)
                                        .size(Size.fill(1), Size.natural())
                                        .bind(NodeBinding.value(this::fullChrome, SceneNode::visible)))
                        .add(
                                style.action(
                                        "取消",
                                        null,
                                        selection::cancel,
                                        FilePickerStyle.Role.OUTLINE))
                        .add(choose);
        // 紧凑高度把分页与确认并排，完整模式保持两行及待打开目标提示。
        return SceneLayouts.split(pages, actions,
                () -> new SceneLayouts.SplitOptions(
                        fullChrome() ? context.viewportWidth().get()
                                : Math.round(theme.get().textStyle.logicalEm * 16), 8))
                .padding(12);
    }

    /** 正式输入帧领取目录结果；表面装饰和普通流继续走同一扩展合同。 */
    private final class Panel extends FilePickerStyle.Flow {
        @Override
        public long nextInputNanos(SceneNode node) {
            return selection.waiting() ? 0 : Long.MAX_VALUE;
        }

        @Override
        public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {
            selection.receiveCompletion();
        }
    }
}
