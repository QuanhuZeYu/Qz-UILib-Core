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
                        false).bind(style.spacing(8, 0));
        SceneNode header = header(style, field);
        SceneNode content =
                FilePickerStyle.column(0)
                        .size(Size.fill(1), Size.fill(1))
                        .bind(style.spacing(6, 6))
                        .add(listing(style))
                        .add(style.label(selection::feedback, .86f, true)
                                .bind(NodeBinding.value(this::showFeedback, SceneNode::visible)));
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

    private boolean showFeedback() {
        return selection.busy.get()
                || selection.path.snapshot().get().error != null
                || selection.entries.get().isEmpty()
                || selection.selected.get() != null && selection.currentSelection() == null;
    }

    private SceneNode header(FilePickerStyle style, SceneNode field) {
        SceneNode search = search(style).bind(NodeBinding.value(style.controls, (node, value) ->
                node.size(Size.fill(1).bounds(0, Math.round(value.textStyle.logicalEm * 25)), Size.natural())));
        // 标题和当前目录共用层级入口；同批可用宽度决定是否让搜索另起一行。
        SceneNode heading = SceneLayouts.split(breadcrumbs(style), search, style.splitOptions(12, 8));
        return FilePickerStyle.column(0).bind(style.spacing(8, 6))
                .add(heading)
                .add(navigation(style, field));
    }

    private SceneNode search(FilePickerStyle style) {
        SceneSearchField.View search = SceneSearchField.create(
                context, selection.search, theme, selection::query,
                SceneTextField.SubmitPolicy.CONFIRM,
                () -> "按名称搜索", selection::clearQuery);
        search.editor.bind(style.spacing(8, 0));
        SceneNode query = FilePickerStyle.row(0).bind(style.spacing(0, 4))
                .add(search.node)
                .add(style.iconAction(SceneIconButton.Symbol.SEARCH, "搜索",
                        () -> selection.query(selection.search.request(true))));
        SceneNode tools = FilePickerStyle.row(0).bind(style.spacing(0, 4)).size(Size.natural(), Size.natural())
                .add(style.action(() -> selection.order.get().label, null, selection::cycleOrder,
                        FilePickerStyle.Role.GHOST, SceneTextPolicy.ELLIPSIS_SINGLE_LINE))
                .add(style.action(
                                () -> selection.extension.get().isEmpty() ? "所有文件" : options.filterName,
                                null, this::toggleFilter, FilePickerStyle.Role.GHOST,
                                SceneTextPolicy.ELLIPSIS_SINGLE_LINE)
                        .bind(NodeBinding.value(() -> !options.extensions.isEmpty(), SceneNode::visible)));
        return SceneLayouts.split(query, tools, style.splitOptions(8, 4));
    }

    private void toggleFilter() {
        selection.extension.set(selection.extension.get().isEmpty() ? options.extensions : "");
        selection.refresh();
    }

    private SceneNode breadcrumbs(FilePickerStyle style) {
        ReadableSignal<java.util.List<SceneMenu.Item<Path>>> hierarchy = context.domain().computed(style.scope, () -> {
            java.util.ArrayList<SceneMenu.Item<Path>> items = new java.util.ArrayList<>();
            for (Path part : breadcrumbPaths()) {
                items.add(new SceneMenu.Item<>(part, () -> part.toString(),
                        SceneCommand.of(() -> !selection.busy.get(), () -> selection.navigate(part))));
            }
            return items;
        });
        SceneNode location = SceneMenu.create(context,
                () -> "选择文件 · " + directoryLabel(), hierarchy, style.controls);
        return SceneTooltip.attach(context, location,
                () -> SceneTooltip.Content.text(selection.roots.get()
                        ? "选择本地文件：根目录 / 盘符"
                        : "选择本地文件\n当前目录：" + selection.directory.get()),
                () -> new SceneTooltip.Options(), style.controls);
    }

    private String directoryLabel() {
        if (selection.roots.get()) return "根目录 / 盘符";
        Path directory = selection.directory.get();
        return directory.getFileName() == null ? directory.toString() : directory.getFileName().toString();
    }

    private java.util.List<Path> breadcrumbPaths() {
        java.util.ArrayList<Path> chain = new java.util.ArrayList<>();
        // 根范围消费当前 worker 已发布的根事实，不借上次目录伪装当前位置。
        if (selection.roots.get()) {
            for (FilePickerDirectory.Entry entry : selection.entries.get()) chain.add(entry.path);
            return chain;
        }
        for (Path cursor = selection.directory.get(); cursor != null; cursor = cursor.getParent())
            chain.add(cursor);
        java.util.Collections.reverse(chain);
        return chain;
    }

    private SceneNode navigation(FilePickerStyle style, SceneNode field) {
        SceneNode back = style.iconAction(SceneIconButton.Symbol.CHEVRON_LEFT, "后退",
                () -> selection.history(-1))
                .bind(NodeBinding.value(() -> selection.backCount.get() > 0 && !selection.busy.get(), SceneNode::enabled));
        SceneNode forward = style.iconAction(SceneIconButton.Symbol.CHEVRON_RIGHT, "前进",
                () -> selection.history(1))
                .bind(NodeBinding.value(() -> selection.forwardCount.get() > 0 && !selection.busy.get(), SceneNode::enabled));
        SceneNode path = FilePickerStyle.row(0).bind(style.spacing(0, 4))
                .add(back).add(forward).add(style.field(field))
                .add(style.iconAction(SceneIconButton.Symbol.CHEVRON_RIGHT, "前往",
                        () -> selection.go(selection.path.request(true))));
        SceneNode tools = FilePickerStyle.row(0).bind(style.spacing(0, 4)).size(Size.natural(), Size.natural())
                .add(style.action("上一级", null, selection::up, FilePickerStyle.Role.GHOST,
                        SceneTextPolicy.ELLIPSIS_SINGLE_LINE))
                .add(style.iconAction(SceneIconButton.Symbol.MONITOR, "根目录", () -> selection.showRoots(0)))
                .add(style.action("刷新", null, selection::refresh, FilePickerStyle.Role.GHOST,
                        SceneTextPolicy.ELLIPSIS_SINGLE_LINE));
        return SceneLayouts.split(path, tools, style.splitOptions(13, 6));
    }

    private SceneNode listing(FilePickerStyle style) {
        SceneVirtualList<FilePickerDirectory.Entry, Path> listing = SceneVirtualList.create(
                context, selection.entries, entry -> entry.path,
                item -> style.entry(item,
                        () -> item.get().path.equals(selection.currentSelection()),
                        () -> selection.activate(item.get())));
        SceneNode viewport = listing.node().bind(style.spacing(0, 4));
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
        SceneNode previous = style.iconAction(SceneIconButton.Symbol.CHEVRON_LEFT, "上一页", () -> selection.turn(-1))
                .bind(NodeBinding.value(() -> !selection.busy.get() && selection.previous.get(), SceneNode::enabled));
        SceneNode following = style.iconAction(SceneIconButton.Symbol.CHEVRON_RIGHT, "下一页", () -> selection.turn(1))
                .bind(NodeBinding.value(() -> !selection.busy.get() && selection.next.get(), SceneNode::enabled));
        SceneNode pages = ScenePagination.create(context, theme,
                () -> "第 " + (selection.page.get() + 1) + " 页", previous, following);
        SceneNode choose = style.action("选择文件", null, selection::confirm, FilePickerStyle.Role.PRIMARY)
                .bind(NodeBinding.value(() -> !selection.busy.get(), SceneNode::enabled));
        SceneNode actions = FilePickerStyle.row(0).bind(style.spacing(0, 8)).size(Size.natural(), Size.natural())
                .add(style.action("取消", null, selection::cancel, FilePickerStyle.Role.OUTLINE))
                .add(choose);
        // 分页与确认只在实际正文宽度不足时换行，不再按窗口高度强制保留两行。
        return SceneLayouts.split(pages, actions, style.splitOptions(8, 8))
                .bind(style.spacing(8, 0));
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
