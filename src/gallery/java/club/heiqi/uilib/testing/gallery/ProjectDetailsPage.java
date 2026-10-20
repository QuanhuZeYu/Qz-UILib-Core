package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.control.SceneMenu;
import club.heiqi.uilib.ui.scene.control.SceneSelect;
import club.heiqi.uilib.ui.scene.control.SceneTabs;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
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
import java.util.Collections;
import java.util.List;

/** 当前详情面板按需建立，名称与多行备注草稿跨标签切换独立保留。 */
public final class ProjectDetailsPage {
    private static final class Activity {
        final int id;
        final String text;

        Activity(int id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    private ProjectDetailsPage() {}

    public static SceneNode create(
            ScenePageContext page, ReadableSignal<SceneTheme> theme, Runnable back) {
        Signal<String> active = page.domain().signal("overview"),
                projectName = page.domain().signal("界面资源整理"),
                savedNotes = page.domain().signal("核对各个界面资源，整理本周交付清单。"),
                feedback = page.domain().signal("查看项目概览，或切到备注编辑。");
        Signal<Boolean> historyAvailable = page.domain().signal(true);
        Signal<String> assignee = page.domain().signal("lin");
        Signal<List<Activity>> events =
                page.domain().signal(Collections.singletonList(new Activity(0, "已创建项目")));
        SceneEditState name = new SceneEditState(page.domain(), projectName.get());
        SceneEditState notes =
                new SceneEditState(page.domain(), savedNotes.get(), SceneEditState.Mode.MULTI_LINE);
        java.util.function.BiConsumer<SceneEditState.Submission, SceneEditState.Submission> apply =
                (nameRequest, noteRequest) -> {
                    String nextName = nameRequest.text.trim();
                    if (nextName.isEmpty()) {
                        name.reject(nameRequest, "项目名称不能为空");
                        notes.reject(noteRequest, "先填写项目名称");
                        feedback.set("请输入项目名称。");
                        return;
                    }
                    name.accept(nameRequest, nextName);
                    notes.accept(noteRequest, noteRequest.text);
                    projectName.set(nextName);
                    savedNotes.set(noteRequest.text);
                    List<Activity> next = new ArrayList<>(events.get());
                    next.add(new Activity(next.size(), "已保存：" + nextName));
                    events.set(Collections.unmodifiableList(next));
                    feedback.set("项目名称和备注已保存。");
                };
        SceneCommand saveCommand =
                SceneCommand.of(
                        () -> !name.snapshot().get().text.trim().isEmpty(),
                        () -> apply.accept(name.request(true), notes.request(true)));
        SceneTabs.Tab<String> overview =
                new SceneTabs.Tab<>(
                        "overview",
                        () -> "项目概览",
                        () -> {
                            SceneNode body =
                                    GalleryStyle.card(theme).size(Size.fill(1), Size.fill(1));
                            body.add(GalleryStyle.label(page, projectName, theme, 22, false));
                            body.add(GalleryStyle.label(page, savedNotes, theme, 16, true));
                            body.add(
                                    GalleryStyle.label(
                                            page, () -> "项目处于进行中。备注会在保存后反映到此处。", theme, 14, true));
                            body.add(
                                    GalleryStyle.button(
                                            page,
                                            () -> "编辑名称和备注",
                                            theme,
                                            () -> active.set("notes")));
                            return body;
                        });
        SceneTabs.Tab<String> edit =
                new SceneTabs.Tab<>(
                        "notes",
                        () -> "名称与备注",
                        () -> {
                            SceneNode body =
                                    GalleryStyle.card(theme)
                                            .size(Size.fill(1), Size.fill(1))
                                            .gap(12);
                            SceneNode nameLabel =
                                    GalleryStyle.label(page, () -> "项目名称", theme, 14, true);
                            body.add(nameLabel);
                            body.add(
                                    SceneTextField.create(
                                                    page,
                                                    name,
                                                    theme,
                                                    () -> false,
                                                    request ->
                                                            apply.accept(
                                                                    request, notes.request(true)))
                                            .bind(
                                                    NodeBinding.semantics(
                                                            () ->
                                                                    SceneSemantics.builder()
                                                                            .labelledBy(nameLabel)
                                                                            .build())));
                            SceneNode notesLabel =
                                    GalleryStyle.label(page, () -> "项目备注", theme, 14, true);
                            body.add(notesLabel);
                            body.add(
                                    SceneTextArea.create(
                                                    page,
                                                    notes,
                                                    theme,
                                                    () -> false,
                                                    request ->
                                                            apply.accept(
                                                                    name.request(true), request))
                                            .size(Size.fill(1), Size.fill(1).bounds(100, 420))
                                            .bind(
                                                    NodeBinding.semantics(
                                                            () ->
                                                                    SceneSemantics.builder()
                                                                            .labelledBy(notesLabel)
                                                                            .build())));
                            body.add(GalleryStyle.primary(page, () -> "保存项目", theme, saveCommand));
                            return body;
                        });
        SceneTabs.Tab<String> history =
                new SceneTabs.Tab<>(
                        "history",
                        () -> "活动记录",
                        historyAvailable,
                        () -> {
                            SceneNode body =
                                    GalleryStyle.card(theme)
                                            .size(Size.fill(1), Size.fill(1))
                                            .scrollable(true);
                            body.add(GalleryStyle.label(page, () -> "项目活动", theme, 20, false));
                            SceneNode timeline =
                                    new SceneNode().size(Size.fill(1), Size.natural()).gap(12);
                            timeline.bind(
                                    NodeBinding.repeat(
                                            events,
                                            value -> value.id,
                                            event ->
                                                    GalleryStyle.label(
                                                            page,
                                                            () -> event.get().text,
                                                            theme,
                                                            15,
                                                            false)));
                            return body.add(timeline);
                        });
        Signal<List<SceneTabs.Tab<String>>> tabs =
                page.domain().signal(Arrays.asList(overview, edit, history));
        SceneTabs<String> details =
                SceneTabs.create(
                        page,
                        tabs,
                        active,
                        next -> {
                            active.set(next);
                            feedback.set(
                                    "已切换到"
                                            + (next.equals("overview")
                                                    ? "概览。"
                                                    : next.equals("notes") ? "名称与备注。" : "活动记录。"));
                        },
                        theme);
        SceneCommand resetCommand =
                SceneCommand.of(
                        () ->
                                !name.snapshot().get().text.equals(projectName.get())
                                        || !notes.snapshot().get().text.equals(savedNotes.get()),
                        () -> {
                            name.cancel();
                            notes.cancel();
                            feedback.set("名称和备注已恢复到上次保存。");
                        });
        SceneNode assignment =
                GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(12);
        SceneNode assigneeLabel = GalleryStyle.label(page, () -> "负责人", theme, 14, true);
        assignment.add(assigneeLabel);
        assignment.add(
                SceneSelect.create(
                                page,
                                () ->
                                        Arrays.asList(
                                                new SceneSelect.Option<>("lin", () -> "林岚"),
                                                new SceneSelect.Option<>("zhou", () -> "周宁"),
                                                new SceneSelect.Option<>(
                                                        "guest", () -> "访客（不可分配）", () -> false)),
                                assignee,
                                next -> {
                                    assignee.set(next);
                                    feedback.set("负责人已改为" + (next.equals("lin") ? "林岚。" : "周宁。"));
                                },
                                theme)
                        .size(Size.fixed(180), Size.natural())
                        .bind(
                                NodeBinding.semantics(
                                        () ->
                                                SceneSemantics.builder()
                                                        .labelledBy(assigneeLabel)
                                                        .build())));
        assignment.add(
                SceneMenu.create(
                                page,
                                () -> "更多操作",
                                () ->
                                        Arrays.asList(
                                                new SceneMenu.Item<>(
                                                        "save", () -> "保存项目", saveCommand),
                                                new SceneMenu.Item<>(
                                                        "reset", () -> "撤销未保存编辑", resetCommand)),
                                theme)
                        .size(Size.fixed(160), Size.natural()));
        SceneNode tools = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(12);
        tools.add(
                GalleryStyle.button(
                        page,
                        () -> historyAvailable.get() ? "暂停活动入口" : "启用活动入口",
                        theme,
                        () -> historyAvailable.set(!historyAvailable.get())));
        tools.add(
                GalleryStyle.button(
                        page,
                        () -> "反转标签顺序",
                        theme,
                        () -> {
                            List<SceneTabs.Tab<String>> next = new ArrayList<>(tabs.get());
                            Collections.reverse(next);
                            tabs.set(next);
                        }));
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(28).gap(18);
        root.add(GalleryStyle.header(page, theme, "项目详情", "切换内容、编辑备注并保存。草稿在标签之间保留。", back));
        return root.add(assignment)
                .add(tools)
                .add(
                        GalleryStyle.frame(page, details.node(), theme)
                                .size(Size.fill(1), Size.fill(1)))
                .add(GalleryStyle.label(page, feedback, theme, 14, true));
    }
}
