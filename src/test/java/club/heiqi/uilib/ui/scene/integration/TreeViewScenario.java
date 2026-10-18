package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneVirtualList;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTreeView;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 树只投影逻辑层级，焦点、按需行和输入都经过正式场景管线。 */
public final class TreeViewScenario extends TestApplication {
    private static final class Item {
        final int id; final Integer parent;
        Item(int id, Integer parent) { this.id = id; this.parent = parent; }
    }
    public static void main(String[] args) throws Exception { new TreeViewScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("乱序父子来源沿展开投影与主目标左右移动", this::projection);
        scenario("千项展开只创建有限行且折叠结束旧后代", this::bounded);
        scenario("普通无control主根可展开且不替代子项主目标", this::plainRoot);
        scenario("主控件自身编辑与只读字段先于树左右", this::editingRoots);
        scenario("折叠后重开沿业务保留独立编辑草稿", this::draftAcrossCollapse);
        scenario("重复键缺父和循环先在逻辑来源定位", this::invalidStructure);
    }
    private static SceneKeyEvent key(SceneKey key) { return new SceneKeyEvent(key, false, false, false, false, false, null); }
    private static void change(Signal<Set<Integer>> state, SceneTreeView.Expansion<Integer> request) {
        Set<Integer> next = new LinkedHashSet<>(state.get());
        if (request.expanded) next.add(request.key); else next.remove(request.key); state.set(Collections.unmodifiableSet(next));
    }
    private void invalidStructure() {
        List<List<Item>> cases = Arrays.asList(Arrays.asList(new Item(1, null), new Item(1, null)),
                Collections.singletonList(new Item(2, 9)), Arrays.asList(new Item(1, 2), new Item(2, 1)));
        String[] reasons = {"重复", "缺少父项", "循环"};
        for (int i = 0; i < cases.size(); i++) {
            ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
            List<Item> data = cases.get(i); int[] created = {0};
            ScenePageSession session = ScenePageSession.open(domain, host, host, page -> page.mount(SceneTreeView.create(page, () -> data,
                    item -> item.id, item -> item.parent, Collections::emptySet, request -> {}, entry -> {
                        created[0]++; return SceneButton.create(page, () -> "条目", page.theme(), () -> {});
                    }).node())); onClose(session::close);
            try { session.frame(); throw new AssertionError("无效树结构未被定位: " + reasons[i]); }
            catch (IllegalArgumentException failure) { require(failure.getMessage().contains(reasons[i]), "结构错误没有保留定位: " + failure); }
            require(created[0] == 0, "结构校验前创建了昂贵行");
        }
    }

    private void plainRoot() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        SceneTreeView<Item, Integer>[] tree = new SceneTreeView[1]; ScenePageContext[] context = new ScenePageContext[1];
        Signal<Set<Integer>>[] expanded = new Signal[1]; SceneNode[] primary = new SceneNode[1], nested = new SceneNode[1]; int[] clicked = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; expanded[0] = domain.signal(Collections.emptySet());
            tree[0] = SceneTreeView.create(page, () -> Arrays.asList(new Item(0, null), new Item(1, 0)), item -> item.id, item -> item.parent,
                    expanded[0], request -> change(expanded[0], request), entry -> {
                        if (entry.get().key == 0) return primary[0] = new SceneNode().focusable(true).onClick(() -> clicked[0]++).size(Size.fill(1), Size.fixed(25));
                        nested[0] = SceneTextField.create(page,
                                new SceneEditState(domain, "子项草稿"), page.theme(), () -> false, request -> {});
                        return new SceneNode().size(Size.fill(1), Size.natural()).add(nested[0]);
                    }); page.mount(tree[0].node());
        }); onClose(session::close); session.frame();
        session.key(key(SceneKey.RIGHT)); session.frame();
        domain.execute(() -> require(expanded[0].get().contains(0), "无control主根被早退挡住展开接续"));
        session.key(key(SceneKey.RIGHT)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == primary[0], "子项无主目标却焦到内部字段"));
        session.key(key(SceneKey.ENTER)); session.frame(); require(clicked[0] == 1, "普通主根Enter不再沿原动作激活");
        domain.execute(() -> tree[0].rows().focus(1)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == nested[0], "旧集合focus丢失首合法后代语义"));
    }

    private void editingRoots() {
        for (boolean textRoot : new boolean[] {false, true}) {
            ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
            Signal<Set<Integer>>[] expanded = new Signal[1]; Signal<Boolean>[] readOnly = new Signal[1]; int[] moves = {0};
            SceneEditState[] state = new SceneEditState[1];
            ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
                expanded[0] = domain.signal(Collections.emptySet()); readOnly[0] = domain.signal(true);
                state[0] = new SceneEditState(domain, "alpha");
                SceneTreeView<Item, Integer> tree = SceneTreeView.create(page, () -> Arrays.asList(new Item(0, null), new Item(1, 0)),
                        item -> item.id, item -> item.parent, expanded[0], request -> change(expanded[0], request), entry -> {
                            if (textRoot) return SceneTextField.create(page, state[0], page.theme(), readOnly[0], request -> {});
                            return new SceneNode().focusable(true).size(Size.fill(1), Size.fixed(25))
                                    .control(new SceneControl() {
                                        public MeasuredSize measure(SceneNode node,
                                                Constraints limits, SceneTextMeasurer text) {
                                            return new MeasuredSize(100, 25);
                                        }
                                        public ChildPlacements arrange(SceneNode node,
                                                LayoutBox box, SceneChildMeasurer children) {
                                            return new ChildPlacements(Collections.emptyList());
                                        }
                                        public void paint(SceneNode node, SceneGeometryView geometry,
                                                PaintPlanBuilder target) { }
                                        public boolean edit(SceneNode node, SceneEditEvent event) {
                                            if (event.command == SceneEditEvent.Command.LEFT
                                                    || event.command == SceneEditEvent.Command.RIGHT) { moves[0]++; return true; }
                                            return false;
                                        }
                                    });
                        }); page.mount(tree.node());
            }); onClose(session::close); session.frame();
            domain.execute(() -> state[0].select(2, 2));
            session.key(key(SceneKey.RIGHT)); session.frame();
            domain.execute(() -> require(expanded[0].get().isEmpty() && (textRoot ? state[0].snapshot().get().caret == 3 : moves[0] == 1),
                    "树Right抢走主目标原编辑: text=" + textRoot));
            if (textRoot) {
                domain.execute(() -> readOnly[0].set(false));
                SceneCompositionSource source = new SceneCompositionSource();
                session.edit(SceneEditEvent.compositionBegin(source, "拼"));
                session.edit(SceneEditEvent.compositionUpdate(source, "", false));
                session.key(key(SceneKey.RIGHT));
                session.edit(SceneEditEvent.compositionCommit(source, "结果")); session.frame();
                domain.execute(() -> require(expanded[0].get().isEmpty() && state[0].snapshot().get().text.contains("结果"),
                        "树方向抢走有效组字联系"));
            }
        }
    }

    private void draftAcrossCollapse() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<Set<Integer>>[] expanded = new Signal[1]; SceneTreeView<Item, Integer>[] tree = new SceneTreeView[1];
        SceneNode[] field = new SceneNode[1]; SceneEditState[] state = new SceneEditState[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            state[0] = new SceneEditState(domain, "");
            expanded[0] = domain.signal(Collections.singleton(0));
            tree[0] = SceneTreeView.create(page, () -> Arrays.asList(new Item(0, null), new Item(1, 0)), item -> item.id, item -> item.parent,
                    expanded[0], request -> change(expanded[0], request), entry -> entry.get().key == 0
                            ? SceneButton.create(page, () -> "父目录", page.theme(), () -> {})
                            : (field[0] = SceneTextField.create(page, state[0], page.theme(), () -> false, request -> {})));
            page.mount(tree[0].node());
        }); onClose(session::close); session.frame();
        session.key(key(SceneKey.RIGHT));
        session.edit(SceneEditEvent.text("独立重命名")); session.frame();
        SceneNode old = field[0];
        domain.execute(() -> expanded[0].set(Collections.emptySet())); session.frame();
        require(old.destroyed(), "折叠仍保留后代旧挂载");
        domain.execute(() -> expanded[0].set(Collections.singleton(0))); session.frame();
        domain.execute(() -> require(state[0].snapshot().get().text.equals("独立重命名") && field[0] != old, "展开后没有从业务草稿构造新视图"));
    }

    private void projection() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        SceneTreeView<Item, Integer>[] tree = new SceneTreeView[1]; ScenePageContext[] context = new ScenePageContext[1];
        Signal<Set<Integer>>[] expanded = new Signal[1]; Map<Integer, SceneNode> targets = new HashMap<>();
        List<Item> data = Arrays.asList(new Item(2, 1), new Item(1, null), new Item(4, 2), new Item(3, 1), new Item(5, null));
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; expanded[0] = domain.signal(Collections.emptySet());
            tree[0] = SceneTreeView.create(page, () -> data, item -> item.id, item -> item.parent, expanded[0],
                    request -> change(expanded[0], request), entry -> {
                        SceneNode node = SceneButton.create(page, () -> "项目" + entry.get().key, page.theme(), () -> {}).size(Size.fill(1), Size.fixed(25));
                        targets.put(entry.get().key, node); return node;
                    }); page.mount(tree[0].node());
        }); onClose(session::close); session.frame();
        domain.execute(() -> require(tree[0].visibleRows().get().size() == 2 && context[0].runtime().focusRouter().focused().get() == targets.get(1), "折叠根和初始主目标错误"));
        session.key(key(SceneKey.RIGHT)); session.frame();
        domain.execute(() -> require(tree[0].visibleRows().get().size() == 4 && expanded[0].get().contains(1), "主目标Right未发布展开"));
        session.key(key(SceneKey.RIGHT)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == targets.get(2), "展开后的Right没有进入首直接子项"));
        session.key(key(SceneKey.RIGHT)); session.frame();
        domain.execute(() -> require(tree[0].visibleRows().get().get(2).key == 4 && tree[0].visibleRows().get().get(2).depth == 2, "祖先展开后的深度优先顺序错误"));
        session.key(key(SceneKey.LEFT)); session.frame();
        session.key(key(SceneKey.LEFT)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == targets.get(1) && !expanded[0].get().contains(2), "Left没有先折叠再返回父项"));
    }
    private void bounded() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost(); host.height = 140;
        SceneTreeView<Item, Integer>[] tree = new SceneTreeView[1]; ScenePageContext[] context = new ScenePageContext[1];
        Signal<Set<Integer>>[] expanded = new Signal[1]; Map<Integer, SceneNode> targets = new HashMap<>(); int[] created = {0}, ended = {0};
        List<Item> data = new ArrayList<>(); data.add(new Item(0, null)); for (int i = 1; i <= 1000; i++) data.add(new Item(i, 0));
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; expanded[0] = domain.signal(Collections.singleton(0));
            tree[0] = SceneTreeView.create(page, () -> data, item -> item.id, item -> item.parent, expanded[0], request -> change(expanded[0], request), entry -> {
                created[0]++; SceneNode node = SceneButton.create(page, () -> "节点" + entry.get().key, page.theme(), () -> {}).size(Size.fill(1), Size.fixed(25));
                node.own(() -> ended[0]++); targets.put(entry.get().key, node); return node;
            }, () -> new SceneTreeView.Options().rows(new SceneVirtualList.Options().estimatedRowHeight(25).overscan(0)), page.theme());
            page.mount(tree[0].node());
        }); onClose(session::close); session.frame();
        require(created[0] < 15, "展开树提前创建了屏外节点");
        domain.execute(() -> tree[0].focus(500)); session.frame();
        SceneNode old = targets.get(500);
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == old && created[0] - ended[0] < 15, "按键聚焦屏外树项未有限准备"));
        domain.execute(() -> expanded[0].set(Collections.emptySet())); session.frame();
        domain.execute(() -> require(old.destroyed() && context[0].runtime().focusRouter().focused().get() == null && tree[0].visibleRows().get().size() == 1, "外部折叠后代没有结束原焦点挂载"));
    }
}
