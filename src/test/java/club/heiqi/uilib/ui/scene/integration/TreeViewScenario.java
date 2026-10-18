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
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
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
        scenario("普通内容由树提供主条目并复用已有可聚焦根", this::plainRoot);
        scenario("主控件自身编辑与只读字段先于树左右", this::editingRoots);
        scenario("折叠后重开沿业务保留独立编辑草稿", this::draftAcrossCollapse);
        scenario("重复键缺父和循环先在逻辑来源定位", this::invalidStructure);
        scenario("参考树主项方向首末与屏外名称跳转", this::referenceNavigation);
        scenario("复杂条目的Tab进入内部编辑后离树", this::complexItemTab);
        scenario("逻辑禁用键跳过首末方向与名称且即时结束焦点", this::disabledItems);
        scenario("树语义保留逻辑层级并定向展开主项和字段", this::semanticTree);
    }
    private static SceneKeyEvent key(SceneKey key) { return new SceneKeyEvent(key, false, false, false, false, false, null); }
    private static void change(Signal<Set<Integer>> state, SceneTreeView.Expansion<Integer> request) {
        Set<Integer> next = new LinkedHashSet<>(state.get());
        if (request.expanded) next.add(request.key); else next.remove(request.key); state.set(Collections.unmodifiableSet(next));
    }
    private void semanticTree() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        host.width = 500; host.height = 160;
        ScenePageContext[] context = new ScenePageContext[1]; Signal<Set<Integer>>[] expanded = new Signal[1], disabled = new Signal[1];
        SceneEditState[] draft = new SceneEditState[1]; Map<Integer, SceneNode> primary = new HashMap<>(); int[] created = {0}, activated = {-1};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; expanded[0] = domain.signal(Collections.emptySet()); disabled[0] = domain.signal(Collections.emptySet());
            draft[0] = new SceneEditState(domain, "原名称"); List<Item> source = new ArrayList<>(); source.add(new Item(0, null));
            for (int i = 1; i <= 1000; i++) source.add(new Item(i, 0)); source.add(new Item(1001, null));
            SceneTreeView<Item, Integer> tree = SceneTreeView.create(page, () -> source, item -> item.id, item -> item.parent,
                    expanded[0], request -> change(expanded[0], request), disabled[0], item -> item.id == 0 ? "完整项目目录" : "资源 " + item.id,
                    entry -> {
                        created[0]++; int id = entry.get().key;
                        SceneNode content = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.fixed(32))
                                .focusable(true).onClick(() -> activated[0] = id);
                        content.add(club.heiqi.uilib.ui.scene.control.SceneLabel.create(page, () -> "资源 " + entry.get().key, page.theme()));
                        if (id == 1) content.add(SceneTextField.create(page, draft[0], page.theme(), () -> false, request -> {})
                                .size(Size.fill(1), Size.fixed(28)).bind(NodeBinding.semantics(() -> SceneSemantics.builder().name("资源重命名").build())));
                        primary.put(id, content); return content;
                    }, () -> new SceneTreeView.Options().rows(new SceneVirtualList.Options().estimatedRowHeight(32).overscan(0)), page.theme());
            page.mount(tree.node().bind(NodeBinding.semantics(() -> SceneSemantics.builder().name("资源浏览器").build())));
        }); onClose(session::close); session.frame();
        SceneSemanticSnapshot initial = semanticSnapshot(domain, session);
        SceneSemanticSnapshot.Entry root = semanticEntry(initial, SceneSemantics.Role.TREE_ITEM, "完整项目目录");
        require(root.values.level == 1 && root.values.index == 1 && root.values.count == 2 && Boolean.FALSE.equals(root.values.expanded), "折叠根的层级或同级总数错误");
        require(root.focused && root.actions.contains(SceneSemantics.Action.EXPAND), "TreeItem没有代表实际主焦点或展开动作");
        java.util.concurrent.CompletableFuture<SceneSemanticRequest.Result> opening = session.semantic(SceneSemanticRequest.of(root.id, SceneSemantics.Action.EXPAND)); session.frame();
        domain.execute(() -> require(opening.getNow(null) == SceneSemanticRequest.Result.APPLIED && expanded[0].get().contains(0), "语义展开没有沿业务展开意图"));
        SceneSemanticSnapshot opened = semanticSnapshot(domain, session);
        SceneSemanticSnapshot.Entry child = semanticEntry(opened, SceneSemantics.Role.TREE_ITEM, "资源 1");
        require(child.values.level == 2 && child.values.index == 1 && child.values.count == 1000 && child.values.expanded == null, "叶子的同级位置或branch事实错误");
        require(semanticEntry(opened, SceneSemantics.Role.TREE, "资源浏览器").values.count == 1002 && created[0] < 40,
                "语义为了全量树条目创建了视图或把挂载数当逻辑总数");
        java.util.concurrent.CompletableFuture<SceneSemanticRequest.Result> focusing = session.semantic(SceneSemanticRequest.of(child.id, SceneSemantics.Action.FOCUS)); session.frame();
        domain.execute(() -> require(focusing.getNow(null) == SceneSemanticRequest.Result.APPLIED
                && context[0].runtime().focusRouter().focused().get() == primary.get(1), "TreeItem语义没有聚焦原实际主项"));
        session.semantic(SceneSemanticRequest.of(child.id, SceneSemantics.Action.ACTIVATE)); session.frame();
        require(activated[0] == 1, "TreeItem激活没有复用当前业务动作");
        SceneSemanticSnapshot.Entry field = semanticEntry(semanticSnapshot(domain, session), SceneSemantics.Role.TEXT_FIELD, "资源重命名");
        session.semantic(SceneSemanticRequest.text(field.id, "语义新名称")); session.frame();
        domain.execute(() -> require(draft[0].snapshot().get().text.equals("语义新名称"), "字段子目标被树展开动作夺走"));
        domain.execute(() -> disabled[0].set(Collections.singleton(1)));
        java.util.concurrent.CompletableFuture<SceneSemanticRequest.Result> blocked = session.semantic(SceneSemanticRequest.of(child.id, SceneSemantics.Action.FOCUS)); session.frame();
        SceneSemanticSnapshot.Entry disabledChild = semanticEntry(semanticSnapshot(domain, session), SceneSemantics.Role.TREE_ITEM, "资源 1");
        require(blocked.getNow(null) == SceneSemanticRequest.Result.REJECTED && !disabledChild.enabled && disabledChild.actions.isEmpty(), "禁用树项仍提供动作或丢失阅读事实");
        session.semantic(SceneSemanticRequest.of(root.id, SceneSemantics.Action.COLLAPSE)); session.frame();
        require(semanticSnapshot(domain, session).get(child.id) == null && Boolean.FALSE.equals(root.values.expanded), "折叠未移除旧后代或修改了旧快照");
        domain.execute(() -> disabled[0].set(Collections.emptySet()));
        session.semantic(SceneSemanticRequest.of(root.id, SceneSemantics.Action.EXPAND)); session.frame();
        SceneSemanticSnapshot.Entry returned = semanticEntry(semanticSnapshot(domain, session), SceneSemantics.Role.TREE_ITEM, "资源 1");
        require(!returned.id.equals(child.id), "再次展开复活了旧语义挂载ID");
        java.util.concurrent.CompletableFuture<SceneSemanticRequest.Result> stale = session.semantic(SceneSemanticRequest.of(child.id, SceneSemantics.Action.ACTIVATE)); session.frame();
        require(stale.getNow(null) == SceneSemanticRequest.Result.REJECTED, "旧TreeItem ID转投新主项");
    }
    private static SceneSemanticSnapshot semanticSnapshot(ReactiveDomain domain, ScenePageSession session) {
        SceneSemanticSnapshot[] result = new SceneSemanticSnapshot[1]; domain.execute(() -> result[0] = session.pipeline().current().semantics); return result[0];
    }
    private static SceneSemanticSnapshot.Entry semanticEntry(SceneSemanticSnapshot snapshot, SceneSemantics.Role role, String name) {
        for (SceneSemanticSnapshot.Entry entry : snapshot.entries) if (entry.values.role == role && name.equals(entry.values.name)) return entry;
        throw new AssertionError("缺少语义: " + role + " / " + name);
    }

    private void disabledItems() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        ScenePageContext[] context = new ScenePageContext[1]; SceneTreeView<Item, Integer>[] tree = new SceneTreeView[1];
        Signal<Set<Integer>>[] disabled = new Signal[1]; Map<Integer, SceneNode> content = new HashMap<>(); int[] changes = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; disabled[0] = domain.signal(new LinkedHashSet<>(Arrays.asList(0, 2, 4)));
            List<Item> items = Arrays.asList(new Item(0, null), new Item(1, null), new Item(2, null), new Item(3, null), new Item(4, null));
            java.util.function.Function<Item, String> name = item -> item.id == 1 ? "Bravo" : "Alpha " + item.id;
            tree[0] = SceneTreeView.create(page, () -> items, item -> item.id, item -> item.parent, Collections::emptySet,
                    request -> changes[0]++, disabled[0], name, entry -> {
                        SceneNode label = club.heiqi.uilib.ui.scene.control.SceneLabel.create(page, () -> name.apply(entry.get().value), page.theme());
                        content.put(entry.get().key, label); return label;
                    }); page.mount(tree[0].node());
        }); onClose(session::close); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == content.get(1).parent(), "初始入口没有跳过禁用首项"));
        session.key(key(SceneKey.DOWN)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == content.get(3).parent(), "Down没有跳过禁用逻辑项"));
        session.key(key(SceneKey.END)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == content.get(3).parent(), "End进入禁用末项"));
        session.key(key(SceneKey.HOME)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == content.get(1).parent(), "Home进入禁用首项"));
        session.edit(SceneEditEvent.text("a")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == content.get(3).parent(), "字符匹配进入禁用名称"));
        domain.execute(() -> disabled[0].set(new LinkedHashSet<>(Arrays.asList(0, 2, 3, 4)))); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == null && !tree[0].focus(3), "动态禁用当前树项没有结束原焦点"));
        domain.execute(() -> disabled[0].set(Collections.emptySet())); session.frame();
        domain.execute(() -> { require(context[0].runtime().focusRouter().focused().get() == null, "重新启用擅自恢复旧焦点"); tree[0].focus(1); }); session.frame();
        session.key(key(SceneKey.HOME)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == content.get(0).parent(), "启用来源变化后旧主项不可再达"));
        require(changes[0] == 0, "禁用变化或焦点移动擅自写展开状态");
    }

    private void referenceNavigation() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        host.width = 320; host.height = 100;
        SceneTreeView<Item, Integer>[] tree = new SceneTreeView[1]; ScenePageContext[] context = new ScenePageContext[1];
        Map<Integer, SceneNode> labels = new HashMap<>(); int[] created = {0}, expansions = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; List<Item> items = new ArrayList<>();
            for (int i = 0; i < 120; i++) items.add(new Item(i, null));
            java.util.function.Function<Item, String> name = item -> item.id == 0 ? "Alpha" : item.id == 90 ? "Beta"
                    : item.id == 91 ? "Bravo" : item.id == 92 ? "Éclair" : "Node " + item.id;
            tree[0] = SceneTreeView.create(page, () -> items, item -> item.id, item -> item.parent, Collections::emptySet,
                    request -> expansions[0]++, name, entry -> {
                        created[0]++; SceneNode label = club.heiqi.uilib.ui.scene.control.SceneLabel.create(page,
                                () -> name.apply(entry.get().value), page.theme()).size(Size.fill(1), Size.fixed(24));
                        labels.put(entry.get().key, label); return label;
                    }, () -> new SceneTreeView.Options().rows(new SceneVirtualList.Options().estimatedRowHeight(24).overscan(0)), page.theme());
            page.mount(tree[0].node());
        }); onClose(session::close); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == labels.get(0).parent(), "纯标签工厂缺少可聚焦TreeItem"));
        session.key(key(SceneKey.DOWN));
        session.key(new SceneKeyEvent(SceneKey.DOWN, false, false, false, false, true, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == labels.get(2).parent(), "按住Down没有沿可见DFS主条目继续"));
        session.key(key(SceneKey.END)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == labels.get(119).parent(), "End没有准备最后主项"));
        require(created[0] < 40, "End导航为屏外条目全量构造视图");
        session.key(key(SceneKey.DOWN)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == labels.get(119).parent(), "树Down在末尾循环"));
        session.key(key(SceneKey.HOME)); session.frame();
        session.edit(SceneEditEvent.text("b")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == labels.get(90).parent(), "字符没有按屏外业务名称准备主项"));
        session.edit(SceneEditEvent.text("r")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == labels.get(91).parent(), "连续字符没有组成名称前缀"));
        session.key(key(SceneKey.HOME)); session.frame();
        session.edit(SceneEditEvent.text("b")); session.frame(); session.edit(SceneEditEvent.text("b")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == labels.get(91).parent(), "重复首字母没有循环同前缀项"));
        session.key(key(SceneKey.HOME)); session.frame(); session.edit(SceneEditEvent.text("e")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == labels.get(92).parent(), "名称跳转未复用不区分重音的前缀算法"));
        require(expansions[0] == 0, "普通主项导航隐含更改展开或选择");
    }

    private void complexItemTab() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        host.width = 500; host.height = 240;
        ScenePageContext[] context = new ScenePageContext[1]; SceneTreeView<Item, Integer>[] tree = new SceneTreeView[1];
        SceneNode[] before = new SceneNode[1], after = new SceneNode[1]; Map<Integer, SceneNode[]> parts = new HashMap<>();
        Map<Integer, SceneEditState> drafts = new HashMap<>(); int[] actions = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page;
            tree[0] = SceneTreeView.create(page, () -> Arrays.asList(new Item(0, null), new Item(1, null)), item -> item.id,
                    item -> item.parent, Collections::emptySet, request -> {}, item -> "Node " + item.id, entry -> {
                        int id = entry.get().key; SceneEditState draft = new SceneEditState(domain, "alpha"); drafts.put(id, draft);
                        SceneNode field = SceneTextField.create(page, draft, page.theme(), () -> false, request -> {});
                        SceneNode action = SceneButton.create(page, () -> "打开", page.theme(), () -> actions[0]++);
                        SceneNode item = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural())
                                .focusable(true).onClick(() -> actions[0]++).add(field).add(action);
                        parts.put(id, new SceneNode[] {item, field, action}); return item;
                    });
            before[0] = SceneButton.create(page, () -> "之前", page.theme(), () -> {});
            after[0] = SceneButton.create(page, () -> "之后", page.theme(), () -> {});
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(before[0]).add(tree[0].node()).add(after[0]));
        }); onClose(session::close); session.frame();
        session.key(key(SceneKey.TAB)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == parts.get(0)[0], "Tab没有从树的单入口进入主项"));
        session.key(key(SceneKey.DOWN)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == parts.get(1)[0], "Down误入当前行内部动作"));
        session.key(key(SceneKey.TAB)); session.frame();
        domain.execute(() -> { require(context[0].runtime().focusRouter().focused().get() == parts.get(1)[1], "Tab没有进入当前条目字段"); drafts.get(1).select(2, 2); });
        session.key(key(SceneKey.LEFT)); session.edit(SceneEditEvent.text("X")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == parts.get(1)[1]
                && drafts.get(1).snapshot().get().text.equals("aXlpha"), "树字符或方向导航抢走内部编辑输入"));
        session.key(key(SceneKey.TAB)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == parts.get(1)[2], "字段后内部动作不可达"));
        session.key(key(SceneKey.TAB)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == after[0], "当前条目结束后Tab没有离树"));
        require(actions[0] == 0, "导航意外激活主项或内部动作");
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
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == nested[0].parent().parent()
                && nested[0].parent().parent().focusable(), "纯内容子项没有自己的TreeItem或错误聚焦内部字段"));
        session.key(key(SceneKey.LEFT)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == primary[0], "子TreeItem不能返回原合法主根"));
        session.key(key(SceneKey.ENTER)); session.frame(); require(clicked[0] == 1, "普通主根Enter不再沿原动作激活");
        domain.execute(() -> tree[0].rows().focus(1)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == nested[0].parent().parent(), "集合首合法焦点不是新增TreeItem"));
        session.key(key(SceneKey.TAB)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == nested[0], "TreeItem的Tab没有进入其内部字段"));
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
