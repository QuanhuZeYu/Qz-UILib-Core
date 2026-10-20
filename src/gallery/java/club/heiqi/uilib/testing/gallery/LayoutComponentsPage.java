package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

final class LayoutComponentsPage {
    private LayoutComponentsPage() {}

    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, Runnable back) {
        ComponentExamples e =
                new ComponentExamples(p, theme, "布局组合", "缩小窗口观察换行、居中、叠层与固定首尾区域。", back);
        Signal<Integer> counter = p.domain().signal(0);
        SceneNode actions = SceneLayouts.wrap(8);
        for (String name : new String[] {"新建项目", "导入资源", "查看最近项目", "管理工作区", "更多操作"})
            actions.add(
                    e.style.action(
                            name,
                            null,
                            () -> counter.set(counter.get() + 1),
                            SceneAppearance.Role.OUTLINE));
        e.section(
                "内容换行",
                "动作按自然宽度排列，剩余空间不足时换到下一行。",
                actions,
                e.style.label(() -> "操作次数：" + counter.get(), 1, true));
        SceneNode center =
                SceneLayouts.center(
                                e.style
                                        .card(e.style.label(() -> "始终居中", 1, false).padding(14))
                                        .size(Size.fixed(180), Size.fixed(64)))
                        .size(Size.fill(1), Size.fixed(160));
        e.section(
                "居中与叠层",
                "居中使用当次可用空间，叠层用于组合背景与正文。",
                SceneLayouts.overlay()
                        .size(Size.fill(1), Size.fixed(160))
                        .bind(
                                club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(
                                        theme, (node, value) -> node.background(value.panel)))
                        .add(center));
        SceneNode header = e.style.label(() -> "固定标题", 1.1f, false).padding(12);
        SceneNode body = SceneAppearance.column(8).padding(12);
        for (int i = 0; i < 12; i++) {
            final int index = i + 1;
            body.add(e.style.label(() -> "可滚动正文 " + index, 1, true));
        }
        SceneNode scroll =
                SceneScrollArea.create(p, body, () -> new SceneScrollArea.Options(), theme)
                        .node()
                        .size(Size.fill(1), Size.fill(1));
        SceneNode footer =
                e.style
                        .action(
                                "固定页脚动作",
                                null,
                                () -> counter.set(counter.get() + 1),
                                SceneAppearance.Role.OUTLINE)
                        .padding(12);
        e.section(
                "标题、正文与页脚",
                "标题和页脚按内容分配，正文使用剩余空间独立滚动。",
                SceneLayouts.frame(header, scroll, footer).size(Size.fill(1), Size.fixed(240)));
        SceneNode grid =
                SceneGrid.create(
                        p,
                        () ->
                                new SceneGrid.Policy(
                                        Math.round(theme.get().textStyle.logicalEm * 14), 12, 12));
        for (int i = 0; i < 4; i++) {
            final int index = i + 1;
            grid.add(e.style.card(e.style.label(() -> "响应网格 " + index, 1, false).padding(16)));
        }
        e.section("响应网格", "列数由容器宽度与最小列宽决定。", grid);
        return e.root;
    }
}
