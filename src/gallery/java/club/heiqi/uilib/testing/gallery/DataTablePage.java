package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.control.SceneDataTable;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 普通任务表组合共享排序命令、列声明及独立备注；没有表格专用输入或布局代码。 */
public final class DataTablePage {
    private static final class Task {
        final int id; final String title; final boolean done;
        Task(int id, String title, boolean done) { this.id = id; this.title = title; this.done = done; }
    }
    private DataTablePage() { }

    public static SceneNode create(ScenePageContext page, ReadableSignal<SceneTheme> theme, Runnable back) {
        Owner owner = page.scope().child();
        List<Task> initial = new ArrayList<>();
        String[] names = {"整理界面素材", "核对设备输入", "更新项目说明", "准备发布清单", "回顾用户反馈"};
        for (int i = 0; i < 1000; i++) initial.add(new Task(i, names[i % names.length] + " · " + (i + 1), i % 7 == 0));
        Signal<List<Task>> source = page.domain().signal(initial);
        Signal<Boolean> ascending = page.domain().signal(true), notesShown = page.domain().signal(true), wider = page.domain().signal(false);
        Signal<String> feedback = page.domain().signal("选择任务，编辑备注或更新完成状态。");
        Map<Integer, SceneEditState> drafts = new HashMap<>();
        Signal<Map<Integer, String>> notes = page.domain().signal(Collections.emptyMap());
        ReadableSignal<List<Task>> sorted = page.domain().computed(owner, () -> {
            List<Task> rows = new ArrayList<>(source.get());
            Comparator<Task> order = Comparator.comparing((Task task) -> task.title).thenComparingInt(task -> task.id);
            rows.sort(ascending.get() ? order : order.reversed()); return rows;
        });
        SceneCommand sort = SceneCommand.of(() -> source.get().size() > 1, () -> ascending.set(!ascending.get()));
        SceneDataTable.Column<Task, String> title = new SceneDataTable.Column<>("title",
                () -> Size.fill(2).bounds(wider.get() ? 320 : 220, 700),
                () -> GalleryStyle.button(page, () -> "任务名称 " + (ascending.get() ? "↑" : "↓"), theme, sort)
                        .size(Size.fill(1), Size.natural()),
                row -> GalleryStyle.button(page, () -> row.get().title, theme,
                        () -> feedback.set("已打开：" + row.get().title)).size(Size.fill(1), Size.natural()));
        SceneDataTable.Column<Task, String> status = new SceneDataTable.Column<>("status", () -> Size.fixed(100),
                () -> GalleryStyle.label(page, () -> "状态", theme, 14, true),
                row -> GalleryStyle.label(page, () -> row.get().done ? "已完成" : "进行中", theme, 14, false));
        SceneDataTable.Column<Task, String> note = new SceneDataTable.Column<>("note", () -> Size.fill(1).bounds(200, 500),
                () -> GalleryStyle.label(page, () -> "我的备注", theme, 14, true), row -> {
                    int id = row.get().id;
                    SceneEditState draft = drafts.computeIfAbsent(id, ignored -> new SceneEditState(page.domain(), notes.get().getOrDefault(id, "")));
                    return SceneTextField.create(page, draft, theme, () -> false, request -> {
                        if (draft.accept(request, request.text)) {
                            Map<Integer, String> next = new HashMap<>(notes.get()); next.put(id, request.text);
                            notes.set(Collections.unmodifiableMap(next));
                            feedback.set("已记录备注：" + row.get().title + " · " + request.text);
                        }
                    }, SceneTextField.SubmitPolicy.BLUR).bind(NodeBinding.semantics(() ->
                            SceneSemantics.builder().name("我的备注：" + row.get().title).build()));
                });
        SceneDataTable.Column<Task, String> action = new SceneDataTable.Column<>("action", () -> Size.fixed(120),
                () -> GalleryStyle.label(page, () -> "操作", theme, 14, true), row -> {
                    SceneCommand toggle = SceneCommand.of(() -> source.get().stream().anyMatch(task -> task.id == row.get().id), () -> {
                        List<Task> next = new ArrayList<>();
                        for (Task task : source.get()) next.add(task.id == row.get().id ? new Task(task.id, task.title, !task.done) : task);
                        source.set(next); feedback.set("已更新：" + row.get().title);
                    });
                    return GalleryStyle.button(page, () -> row.get().done ? "重新打开" : "完成任务", theme, toggle)
                            .size(Size.fill(1), Size.natural());
                });
        ReadableSignal<List<SceneDataTable.Column<Task, String>>> columns = page.domain().computed(owner,
                () -> notesShown.get() ? Arrays.asList(title, status, note, action) : Arrays.asList(title, status, action));
        SceneDataTable<Task, Integer, String> table = SceneDataTable.create(page, sorted, task -> task.id, columns,
                () -> new SceneDataTable.Options().columnGap(theme.get().gap), theme);
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(28).gap(18).own(owner);
        root.add(GalleryStyle.header(page, theme, "团队任务", "按标题排序，按 Tab 编辑备注，离开字段后记录。", back));
        SceneNode tools = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(12);
        tools.add(GalleryStyle.button(page, () -> notesShown.get() ? "隐藏备注列" : "显示备注列", theme, () -> notesShown.set(!notesShown.get())));
        tools.add(GalleryStyle.button(page, () -> wider.get() ? "收窄任务列" : "加宽任务列", theme, () -> wider.set(!wider.get())));
        table.node().bind(NodeBinding.semantics(() -> SceneSemantics.builder().name("团队任务").build()));
        root.add(tools).add(table.node());
        root.add(SceneLabel.create(page, feedback, theme).size(Size.fill(1), Size.natural()));
        return root;
    }
}
