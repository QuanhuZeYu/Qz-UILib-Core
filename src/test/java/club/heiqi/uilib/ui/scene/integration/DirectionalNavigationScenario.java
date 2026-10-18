package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 正式网格与场景管线验证空间方向，Tab仍只表达逻辑顺序。 */
public final class DirectionalNavigationScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new DirectionalNavigationScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("二维方向按当前几何选择且边缘不循环", this::grid);
        scenario("中性方向和键盘均尊重编辑与组字联系", this::editingPriority);
        scenario("嵌套集合耗尽后准备下一组屏外合法项", this::nestedCollections);
    }

    private void nestedCollections() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        ScenePageContext[] context = new ScenePageContext[1];
        java.util.Map<String, SceneNode> items = new java.util.HashMap<>();
        SceneNode[] footer = new SceneNode[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page;
            club.heiqi.uilib.ui.scene.control.SceneVirtualList<Integer, Integer> groups = club.heiqi.uilib.ui.scene.control.SceneVirtualList.create(
                    page, () -> java.util.Arrays.asList(0, 1), value -> value, group -> {
                        int id = group.get(); java.util.List<Integer> values = new java.util.ArrayList<>();
                        for (int i = 0; i < (id == 0 ? 3 : 20); i++) values.add(i);
                        club.heiqi.uilib.ui.scene.control.SceneVirtualList<Integer, Integer> children = club.heiqi.uilib.ui.scene.control.SceneVirtualList.create(
                                page, () -> values, value -> value, item -> {
                                    int index = item.get();
                                    SceneNode button = SceneButton.create(page, () -> id + ":" + index, page.theme(), () -> {})
                                            .size(Size.fill(1), Size.fixed(20)).focusable(id == 0 || index >= 8);
                                    items.put(id + ":" + index, button); return button;
                                }, () -> new club.heiqi.uilib.ui.scene.control.SceneVirtualList.Options().estimatedRowHeight(20).overscan(0));
                        return children.node().size(Size.fill(1), Size.fixed(80));
                    }, () -> new club.heiqi.uilib.ui.scene.control.SceneVirtualList.Options().estimatedRowHeight(80).overscan(0));
            footer[0] = SceneButton.create(page, () -> "页脚", page.theme(), () -> {}).size(Size.fill(1), Size.fixed(30));
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(groups.node().size(Size.fill(1), Size.fixed(160))).add(footer[0]));
        }); onClose(session::close); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == items.get("0:0"),
                "嵌套集合初始逻辑选择在子行准备前误判为空"));
        for (int i = 0; i < 3; i++) { session.navigation(SceneNavigationEvent.DOWN); session.frame(); }
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == items.get("1:8"),
                "外层把下一内集合未创建的合法项当成空集合跳到页脚"));
    }

    private void editingPriority() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        club.heiqi.uilib.ui.scene.edit.SceneEditState[] state = new club.heiqi.uilib.ui.scene.edit.SceneEditState[1];
        SceneNode[] editor = new SceneNode[1]; ScenePageContext[] context = new ScenePageContext[1];
        club.heiqi.uilib.ui.reactive.Signal<Boolean>[] readOnly = new club.heiqi.uilib.ui.reactive.Signal[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; readOnly[0] = domain.signal(false);
            state[0] = new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, "alpha\nbeta", club.heiqi.uilib.ui.scene.edit.SceneEditState.Mode.MULTI_LINE);
            editor[0] = club.heiqi.uilib.ui.scene.control.SceneTextArea.create(page, state[0], page.theme(), readOnly[0], request -> {})
                    .size(Size.fill(1), Size.fixed(100));
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).initialFocus(editor[0])
                    .add(editor[0]).add(SceneButton.create(page, () -> "后方动作", page.theme(), () -> {})));
        }); onClose(session::close); session.frame();
        domain.execute(() -> state[0].select(state[0].snapshot().get().text.length(), state[0].snapshot().get().text.length()));
        session.navigation(SceneNavigationEvent.UP); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == editor[0] && state[0].snapshot().get().caret < 5,
                "设备方向没有进入多行光标移动"));
        domain.execute(() -> readOnly[0].set(true));
        session.key(new SceneKeyEvent(SceneKey.DOWN, false, false, false, false, false, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == editor[0] && state[0].snapshot().get().caret > 5,
                "只读方向查看跳成焦点导航"));
        club.heiqi.uilib.ui.scene.input.SceneCompositionSource source = new club.heiqi.uilib.ui.scene.input.SceneCompositionSource();
        domain.execute(() -> readOnly[0].set(false));
        session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.compositionBegin(source, "拼"));
        session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.compositionUpdate(source, "", false));
        session.navigation(SceneNavigationEvent.DOWN);
        session.key(new SceneKeyEvent(SceneKey.DOWN, false, false, false, false, false, null));
        session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.compositionCommit(source, "结果")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == editor[0]
                && state[0].snapshot().get().text.contains("结果"), "方向抢走了UPDATE(false)的有效组字联系"));
    }

    private void grid() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        SceneNode[] buttons = new SceneNode[4]; ScenePageContext[] context = new ScenePageContext[1]; int[] actions = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page;
            SceneNode grid = SceneGrid.create(page, () -> new SceneGrid.Policy(150, 10, 10));
            for (int i = 0; i < buttons.length; i++) {
                final int index = i;
                buttons[i] = SceneButton.create(page, () -> "项目" + index, page.theme(), () -> actions[0]++)
                        .size(Size.fill(1), Size.fixed(30));
                grid.add(buttons[i]);
            }
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).initialFocus(buttons[0]).add(grid));
        }); onClose(session::close); session.frame();
        session.navigation(SceneNavigationEvent.DOWN); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == buttons[2], "Down未沿同列进入下一行"));
        session.navigation(SceneNavigationEvent.RIGHT); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == buttons[3], "Right未沿同行移动"));
        session.navigation(SceneNavigationEvent.DOWN); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == buttons[3], "方向边缘发生循环或清焦"));
        session.navigation(SceneNavigationEvent.UP); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == buttons[1], "Up未沿同列返回"));
        host.width = 180; session.frame();
        session.key(new SceneKeyEvent(SceneKey.DOWN, false, false, false, false, false, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == buttons[2], "窗口变窄后仍用旧二维邻接"));
        session.navigation(SceneNavigationEvent.NEXT); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == buttons[3], "方向接入改变了Tab逻辑顺序"));
        require(actions[0] == 0, "移动焦点时激活了业务动作");
    }
}
