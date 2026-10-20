package club.heiqi.uilib.ui.settings;

import java.util.*;
import java.nio.file.*;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;

/** 字体字段编辑器；添加子视图和所属文件选择器只在此拥有，不发布配置。 */
final class SettingsFontDialog {
    private enum View {
        NONE,
        LIST,
        ADD
    }

    private final SettingsUi ui;
    private final Signal<View> view;
    private final Signal<Boolean> fileSource, picking;
    private final Signal<String> selected;
    private final SceneEditState path;
    private final ReadableSignal<Map<Object, Integer>> ordinals;
    private SceneNode addTrigger, selector, pathField;

    SettingsFontDialog(SettingsUi ui) {
        this.ui = ui;
        view = ui.context.domain().signal(View.NONE);
        // 编号与行动作共享一遍来源索引；重复声明仍以独立身份定位，不让全量行产生平方扫描。
        ordinals =
                ui.context
                        .domain()
                        .computed(
                                ui.context.scope(),
                                () -> {
                                    Map<Object, Integer> values = new IdentityHashMap<>();
                                    int index = 0;
                                    for (SettingsModel.Source source : ui.model.sources().get())
                                        values.put(source.identity(), ++index);
                                    return values;
                                });
        fileSource = ui.context.domain().signal(ui.configuration.builtinCandidates().isEmpty());
        picking = ui.context.domain().signal(false);
        List<String> builtins = ui.configuration.builtinCandidates();
        selected = ui.context.domain().signal(builtins.isEmpty() ? "" : builtins.get(0));
        path = new SceneEditState(ui.context.domain(), "");
    }

    void open() {
        view.set(View.LIST);
    }

    void end() {
        picking.set(false);
        view.set(View.NONE);
    }

    boolean closeRequested() {
        if (view.get() == View.NONE) return false;
        if (picking.get()) {
            picking.set(false);
            return true;
        }
        view.set(view.get() == View.ADD ? View.LIST : View.NONE);
        return true;
    }

    SceneNode build() {
        // 两个互斥的视图范围沿同一外框位置替换，不叠加第二个添加模态；
        // 范围进入/返回由正式初始焦点和恢复规则处理，路径编辑节点持续保留。
        return ui.stack(0)
                .size(Size.fill(1), Size.fill(1))
                .control(new SettingsLayout.Layers())
                .add(listView())
                .add(addView());
    }

    private void startAdd() {
        path.reset("");
        fileSource.set(ui.configuration.builtinCandidates().isEmpty());
        view.set(View.ADD);
    }

    private SceneNode listView() {
        addTrigger = ui.button("+ 添加来源", this::startAdd, SceneAppearance.Role.PRIMARY);
        SceneNode header =
                ui.inset(
                        ui.stack(8)
                                .add(
                                        SettingsLayout.split(
                                                ui,
                                                ui.heading("管理字体来源", 20),
                                                ui.actions(
                                                        ui.button(
                                                                "关闭",
                                                                this::end,
                                                                SceneAppearance.Role.GHOST))))
                                .add(
                                        ui.text("按从上到下的顺序回退；拖动把手调整。更改保留在草稿中。", 13, true)
                                                .bind(
                                                        NodeBinding.value(
                                                                () ->
                                                                        !ui.shortWindow.get()
                                                                                && !ui.model
                                                                                        .sources()
                                                                                        .get()
                                                                                        .isEmpty(),
                                                                SceneNode::visible)))
                                .add(
                                        SettingsLayout.split(
                                                ui,
                                                ui.text(ui::sourceCounts, 12, true)
                                                        .bind(
                                                                NodeBinding.value(
                                                                        ui.shortWindow,
                                                                        (n, v) -> n.visible(!v))),
                                                ui.actions(
                                                        ui.button(
                                                                "恢复默认",
                                                                ui.model::resetFonts,
                                                                SceneAppearance.Role.GHOST),
                                                        addTrigger)))
                                .add(ui.line()),
                        16);
        SceneNode list =
                SceneSortable.create(
                                ui.context,
                                () ->
                                        Collections.singletonList(
                                                new SceneSortable.Group<>(
                                                        "fonts", ui.model.sources().get())),
                                SettingsModel.Source::identity,
                                key -> {
                                    SceneNode rows = ui.inset(ui.scroll(ui.stack(8)), 16, 12);
                                    return new SceneSortable.Container(
                                            rows, rows, SceneSortable.Layout.VERTICAL);
                                },
                                this::row,
                                source -> row(source).content,
                                move -> ui.model.moveBefore(move.key, move.beforeKey),
                                () ->
                                        new SceneSortable.Options<SettingsModel.Source, String>()
                                                .reorderMotion(ui.motion.spatial.get())
                                                .dropMotion(ui.motion.spatial.get()))
                        .size(Size.fill(1), Size.fill(1));
        SceneNode empty =
                ui.inset(
                        ui.stack(12)
                                .add(ui.heading("还没有字体来源", 16))
                                .add(ui.text("空列表是合法配置。添加来源或恢复默认后，应用即可生效。", 13, true)),
                        20);
        list.bind(NodeBinding.value(ui.model.sources(), (n, items) -> n.visible(!items.isEmpty())));
        empty.bind(NodeBinding.value(ui.model.sources(), (n, items) -> n.visible(items.isEmpty())));
        SceneNode body = ui.stack(0).size(Size.fill(1), Size.fill(1)).add(list).add(empty);
        SceneNode footer =
                ui.inset(
                        ui.stack(8)
                                .add(ui.line())
                                .add(ui.errorText(ui.model.fontError()))
                                .add(
                                        SettingsLayout.split(
                                                ui,
                                                ui.text("更改仍在草稿中，返回设置后应用。", 12, true),
                                                ui.actions(
                                                        ui.button(
                                                                "完成字体编辑",
                                                                this::end,
                                                                SceneAppearance.Role.OUTLINE)))),
                        16);
        return SettingsDialogFrame.create(
                ui,
                "管理字体来源",
                936,
                720,
                header,
                body,
                footer,
                () -> view.get() == View.LIST,
                addTrigger,
                this::closeRequested);
    }

    private SceneSortable.Row row(ReadableSignal<SettingsModel.Source> source) {
        SceneNode handle = SceneDragHandle.create(() -> ui.colors.get().source);
        SceneNode name =
                ui.text(
                        () -> {
                            Integer position = ordinals.get().get(source.get().identity());
                            return (position == null
                                            ? ""
                                            : String.format(Locale.ROOT, "%02d", position) + "  ")
                                    + ui.sourceName(source.get().declaration())
                                    + (source.get().declaration().startsWith("builtin:")
                                            ? " · 内置"
                                            : " · 文件");
                        },
                        14,
                        false);
        SceneNode info = ui.stack(5).add(name).add(ui.path(() -> source.get().declaration(), 12));
        SceneNode up = move(source, -1, "↑", "上移"), down = move(source, 1, "↓", "下移");
        SceneNode remove =
                ui.action(
                        () -> "移除",
                        null,
                        () -> ui.model.remove(source.get()),
                        SceneAppearance.Role.GHOST);
        SceneNode details =
                SettingsLayout.split(ui, info, ui.actions(up, down, remove))
                        .size(Size.fill(1), Size.natural());
        SceneNode content =
                ui.inset(ui.stack(12).axis(SceneNode.Axis.ROW).add(handle).add(details), 12);
        return new SceneSortable.Row(ui.card(content), handle);
    }

    private SceneNode move(
            ReadableSignal<SettingsModel.Source> source, int offset, String glyph, String name) {
        SceneCommand command =
                SceneCommand.of(
                        () -> {
                            // 重载可先刷新旧行再协调移除；不在当前索引的来源已没有移动资格。
                            Integer position = ordinals.get().get(source.get().identity());
                            return position != null
                                    && (offset < 0
                                            ? position > 1
                                            : position < ui.model.sources().get().size());
                        },
                        () -> ui.model.move(source.get(), offset));
        return ui.button(glyph, command, SceneAppearance.Role.GHOST)
                .bind(
                        NodeBinding.semantics(
                                () ->
                                        SceneSemantics.builder()
                                                .name(
                                                        name
                                                                + " "
                                                                + ui.sourceName(
                                                                        source.get().declaration()))
                                                .build()));
    }

    private SceneNode addView() {
        SceneNode back = ui.button("返回字体来源", () -> view.set(View.LIST), SceneAppearance.Role.GHOST);
        SceneNode header =
                ui.inset(
                        ui.stack(8, 4)
                                .add(
                                        ui.actions(
                                                back,
                                                ui.button(
                                                        "关闭",
                                                        this::end,
                                                        SceneAppearance.Role.GHOST)))
                                .add(ui.heading("添加字体来源", 20))
                                .add(
                                        ui.text("先选择来源，再添加到列表末尾。", 13, true)
                                                .bind(
                                                        NodeBinding.value(
                                                                ui.shortWindow,
                                                                (n, v) -> n.visible(!v))))
                                .add(ui.line()),
                        16,
                        8);
        SceneNode typeLabel = ui.heading("来源类型", 14);
        SceneRadioGroup<Boolean> types =
                SceneRadioGroup.create(
                        ui.context,
                        () ->
                                Arrays.asList(
                                        new SceneRadioGroup.Option<>(
                                                false,
                                                () -> "内置来源",
                                                () ->
                                                        !ui.configuration
                                                                .builtinCandidates()
                                                                .isEmpty()),
                                        new SceneRadioGroup.Option<>(true, () -> "本地字体文件")),
                        fileSource,
                        fileSource::set,
                        () -> ui.colors.get().source);
        types.node()
                .bind(
                        NodeBinding.semantics(
                                () -> SceneSemantics.builder().labelledBy(typeLabel).build()));
        selector =
                SceneSelect.create(
                        ui.context,
                        () -> {
                            List<SceneSelect.Option<String>> values = new ArrayList<>();
                            for (String item : ui.configuration.builtinCandidates())
                                values.add(
                                        new SceneSelect.Option<>(item, () -> ui.sourceName(item)));
                            return values;
                        },
                        selected,
                        selected::set,
                        () -> "选择内置字体",
                        () -> ui.colors.get().source);
        SceneNode builtinLabel = ui.heading("内置字体", 14);
        selector.bind(
                NodeBinding.semantics(
                        () -> SceneSemantics.builder().labelledBy(builtinLabel).build()));
        SceneNode builtin =
                ui.stack(10)
                        .add(builtinLabel)
                        .add(selector)
                        .add(ui.path(selected, 12))
                        .bind(NodeBinding.value(fileSource, (n, value) -> n.visible(!value)));
        SceneNode fileLabel = ui.heading("字体文件路径", 14),
                help = ui.text("支持 ttf、otf、ttc、woff、woff2；应用前会检查字体来源。", 12, true);
        pathField =
                SceneTextField.create(
                                ui.context,
                                path,
                                () ->
                                        ui.colors
                                                .get()
                                                .source
                                                .withTextStyle(
                                                        SettingsTypography.pathStyle(
                                                                ui.px(14), ui.colors.get().text)),
                                () -> false,
                                this::addFile,
                                SceneTextField.SubmitPolicy.CONFIRM,
                                () -> "C:/Fonts/Example.ttf",
                                false)
                        .bind(
                                NodeBinding.semantics(
                                        () ->
                                                SceneSemantics.builder()
                                                        .labelledBy(fileLabel)
                                                        .describedBy(help)
                                                        .build()));
        SceneNode browse =
                ui.button("浏览字体文件", () -> picking.set(true), SceneAppearance.Role.OUTLINE);
        SceneNode file =
                ui.stack(10)
                        .add(SettingsLayout.split(ui, fileLabel, ui.actions(browse)))
                        .add(ui.field(pathField))
                        .add(help)
                        .bind(NodeBinding.value(fileSource, SceneNode::visible));
        SceneNode content =
                ui.inset(
                        ui.stack(16, 8)
                                .add(typeLabel)
                                .add(types.node())
                                .add(builtin)
                                .add(file)
                                .add(
                                        ui.errorText(
                                                () ->
                                                        path.snapshot().get().error == null
                                                                ? ui.model.fontError().get()
                                                                : path.snapshot().get().error)),
                        20,
                        8);
        SceneCommand add =
                SceneCommand.of(
                        () ->
                                fileSource.get()
                                        ? !path.snapshot().get().text.trim().isEmpty()
                                        : !selected.get().isEmpty(),
                        () -> {
                            if (fileSource.get()) addFile(path.request(true));
                            else {
                                ui.model.add(selected.get());
                                if (ui.model.fontError().get().isEmpty()) view.set(View.LIST);
                            }
                        });
        SceneNode footer =
                ui.inset(
                        ui.stack(8)
                                .add(ui.line())
                                .add(
                                        SettingsLayout.split(
                                                ui,
                                                ui.text("添加到草稿末尾，应用后生效。", 12, true)
                                                        .bind(
                                                                NodeBinding.value(
                                                                        ui.shortWindow,
                                                                        (n, v) -> n.visible(!v))),
                                                ui.actions(
                                                        ui.button(
                                                                "取消",
                                                                () -> view.set(View.LIST),
                                                                SceneAppearance.Role.GHOST),
                                                        ui.button(
                                                                "添加到末尾",
                                                                add,
                                                                SceneAppearance.Role.PRIMARY)))),
                        16,
                        8);
        SceneNode pickerOwner =
                ui.stack(0)
                        .size(Size.fill(1), Size.fill(1))
                        .bind(
                                NodeBinding.repeat(
                                        () ->
                                                picking.get()
                                                        ? Collections.singletonList(true)
                                                        : Collections.emptyList(),
                                        value -> value,
                                        item ->
                                                SceneFilePicker.create(
                                                        ui.context,
                                                        directory(),
                                                        () -> ui.colors.get().source,
                                                        new SceneFilePicker.Options()
                                                                .filter(
                                                                        "字体文件",
                                                                        "ttf,otf,ttc,woff,woff2"),
                                                        chosen -> {
                                                            path.reset(chosen.toString());
                                                            picking.set(false);
                                                        },
                                                        () -> picking.set(false))));
        return SettingsDialogFrame.create(
                ui,
                "添加字体来源",
                936,
                720,
                header,
                ui.scrollArea(content).node(),
                footer,
                () -> view.get() == View.ADD,
                ui.configuration.builtinCandidates().isEmpty() ? pathField : selector,
                this::closeRequested,
                pickerOwner);
    }

    private Path directory() {
        String raw = path.snapshot().get().text.trim();
        try {
            if (!raw.isEmpty()) {
                Path candidate = Paths.get(raw).toAbsolutePath().normalize();
                if (candidate.getParent() != null) return candidate.getParent();
            }
        } catch (IllegalArgumentException ignored) {
            /* 非法手输路径由提交显示字段错误，浏览仍从宿主目录开始。 */
        }
        return Paths.get(System.getProperty("user.home", "."));
    }

    private void addFile(SceneEditState.Submission submission) {
        String value = submission.text.trim();
        if (path.snapshot().get().composing) {
            path.reject(submission, "请先完成正在输入的文字。");
            return;
        }
        if (value.isEmpty()) {
            path.reject(submission, "请输入字体文件路径。");
            return;
        }
        ui.model.add("file:" + value);
        if (!ui.model.fontError().get().isEmpty()) {
            path.reject(submission, ui.model.fontError().get());
            return;
        }
        path.accept(submission, value);
        view.set(View.LIST);
    }
}
