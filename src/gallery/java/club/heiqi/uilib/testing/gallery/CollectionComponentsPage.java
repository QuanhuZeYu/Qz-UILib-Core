package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.ArrayList;
import java.util.List;

final class CollectionComponentsPage {
    private CollectionComponentsPage() {}

    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, Runnable back) {
        ComponentExamples e =
                new ComponentExamples(p, theme, "集合与主从", "分页、稳定键虚拟列表和主从区域组合，业务数据与选中值保持独立。", back);
        Signal<Integer> index = p.domain().signal(0);
        Signal<String> selected = p.domain().signal(null);
        Signal<Boolean> split = p.domain().signal(true);
        ReadableSignal<List<String>> items =
                p.domain()
                        .computed(
                                e.style.scope,
                                () -> {
                                    List<String> values = new ArrayList<>();
                                    for (int i = 0; i < 6; i++)
                                        values.add("项目 " + (index.get() * 6 + i + 1));
                                    return values;
                                });
        SceneNode list =
                SceneVirtualList.create(
                                p,
                                items,
                                value -> value,
                                value ->
                                        e.style.action(
                                                () -> value.get(),
                                                null,
                                                () -> selected.set(value.get()),
                                                SceneAppearance.Role.ENTRY))
                        .node();
        SceneNode master =
                SceneAppearance.column(8)
                        .size(Size.fill(1), Size.fill(1))
                        .add(list)
                        .add(
                                ScenePagination.create(
                                        p,
                                        theme,
                                        () -> "第 " + (index.get() + 1) + " 页 / 共 4 页",
                                        SceneCommand.of(
                                                () -> index.get() > 0,
                                                () -> index.set(index.get() - 1)),
                                        SceneCommand.of(
                                                () -> index.get() < 3,
                                                () -> index.set(index.get() + 1))));
        SceneNode detail =
                SceneAppearance.column(12)
                        .padding(16)
                        .size(Size.fill(1), Size.fill(1))
                        .add(
                                e.style.label(
                                        () ->
                                                selected.get() == null
                                                        ? "请选择项目"
                                                        : selected.get() + " 的详情",
                                        1.3f,
                                        false))
                        .add(e.style.label(() -> "所选项目独立于分页保留；单栏时点击返回列表。", 1, true))
                        .add(
                                e.style.action(
                                        "返回列表",
                                        null,
                                        () -> selected.set(null),
                                        SceneAppearance.Role.OUTLINE));
        ReadableSignal<Boolean> divided = () -> split.get() && p.viewportWidth().get() >= 720;
        SceneNode workspace =
                SceneMasterDetail.create(
                                p,
                                master,
                                detail,
                                () ->
                                        new SceneMasterDetail.Policy(
                                                divided.get(),
                                                selected.get() != null,
                                                2,
                                                3,
                                                1,
                                                e.style.colors.get().border))
                        .size(Size.fill(1), Size.fixed(340));
        e.section(
                "主从工作区",
                "宽窗口并列显示，窄窗口切换列表和详情。",
                SceneSwitch.create(p, split, () -> "允许并列显示", theme, split::set),
                workspace);
        e.section(
                "未知总数分页",
                "只需要摘要和可执行状态，无须先统计所有记录。",
                ScenePagination.create(
                        p,
                        theme,
                        () -> "游标页 " + (index.get() + 1),
                        SceneCommand.of(() -> index.get() > 0, () -> index.set(index.get() - 1)),
                        SceneCommand.of(() -> index.get() < 3, () -> index.set(index.get() + 1))));
        return e.root;
    }
}
