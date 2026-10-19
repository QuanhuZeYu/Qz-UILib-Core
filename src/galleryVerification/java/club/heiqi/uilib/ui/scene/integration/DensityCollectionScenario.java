package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import java.util.*;

/** 复合控件与有限集合的真实密度换代；只记录实际行测量，不复制高度索引或输入路由。 */
public final class DensityCollectionScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("打开Select切密度保留弹层与实际命中且Tabs保留草稿", this::popupAndTabs);
        scenario("显式估算不变的Virtual换代清除旧区段高度并保留锚点", () -> collection(false));
        scenario("表格仅内距变化重测有限行且固定列和选区保持", () -> collection(true));
        scenario("树角色缩进与标记槽变化而明确像素和行工厂主题保持", this::treeMetrics);
    }

    private static SceneNode find(SceneNode root, String text) {
        if (text.equals(root.text())) return root;
        for (SceneNode child : root.children()) { SceneNode found = find(child, text); if (found != null) return found; }
        return null;
    }
    private static String describe(SceneNode node) { return node == null ? "null" : node.id() + " / " + node.text(); }
    private static SceneSemanticSnapshot.Entry named(SceneSemanticSnapshot snapshot, SceneSemantics.Role role, String name) {
        for (SceneSemanticSnapshot.Entry entry : snapshot.entries) if (entry.values.role == role && name.equals(entry.values.name)) return entry;
        throw new AssertionError("缺少语义: " + role + " / " + name);
    }
    private void popupAndTabs() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        host.width = 520; host.height = 430;
        Signal<SceneDensity>[] density = new Signal[1]; Signal<String>[] choice = new Signal[1];
        ScenePageContext[] context = new ScenePageContext[1]; SceneEditState[] draft = new SceneEditState[1];
        SceneNode[] field = new SceneNode[1], select = new SceneNode[1]; SceneTabs<String>[] tabs = new SceneTabs[1]; int[] created = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; density[0] = domain.signal(SceneDensity.COMFORTABLE); page.density(density[0]);
            draft[0] = new SceneEditState(domain, "保留的项目草稿"); choice[0] = domain.signal("50");
            tabs[0] = SceneTabs.create(page, () -> Arrays.asList(new SceneTabs.Tab<>("edit", () -> "编辑", () -> {
                created[0]++; field[0] = SceneTextField.create(page, draft[0], page.theme(), () -> false, request -> {}); return field[0];
            }), new SceneTabs.Tab<>("other", () -> "概览", () -> SceneLabel.create(page, () -> "项目概览", page.theme()))), () -> "edit", ignored -> {});
            tabs[0].node().gap(0);
            List<SceneSelect.Option<String>> options = new ArrayList<>();
            for (int i = 0; i < 120; i++) { final String key = String.valueOf(i); options.add(new SceneSelect.Option<>(key, () -> "选项" + key)); }
            select[0] = SceneSelect.create(page, () -> options, choice[0], choice[0]::set).size(Size.fixed(180), Size.natural());
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(select[0]).add(tabs[0].node()));
        }); onClose(session::close); session.frame();
        SceneNode original = field[0]; SceneNode[] popup = new SceneNode[1], option = new SceneNode[1]; int[] oldHeight = new int[1];
        domain.execute(() -> { draft[0].select(1, 4); context[0].runtime().focusRouter().request(context[0].runtime().captureMount(select[0].children().get(0))); });
        session.navigation(club.heiqi.uilib.ui.scene.input.SceneNavigationEvent.ACTIVATE); session.frame();
        domain.execute(() -> {
            SceneGeometryView geometry = session.pipeline().current().geometry; popup[0] = geometry.activeModal();
            require(popup[0] != null, "原激活没有打开选择器");
            option[0] = find(popup[0], "选项50");
            require(option[0] != null && context[0].runtime().focusRouter().focused().get() == option[0],
                    "原打开没有聚焦实际选项50: focused=" + describe(context[0].runtime().focusRouter().focused().get()));
            oldHeight[0] = geometry.get(option[0]).box.height; density[0].set(SceneDensity.COMPACT);
        }); session.frame();
        int[] point = new int[2];
        domain.execute(() -> {
            SceneGeometryView geometry = session.pipeline().current().geometry;
            require(geometry.activeModal() == popup[0], "密度变化重建或关闭原弹层: before=" + describe(popup[0])
                    + ", after=" + describe(geometry.activeModal()));
            SceneNode current = find(popup[0], "选项50");
            require(current == option[0], "密度变化替换原业务选项: before=" + describe(option[0]) + ", after=" + describe(current));
            int actualHeight = geometry.get(current).box.height;
            require(actualHeight < oldHeight[0], "实际选项仍使用旧行高: before=" + oldHeight[0] + ", after=" + actualHeight
                    + ", padding=" + current.padding() + ", em=" + current.style().logicalEm);
            require(context[0].runtime().focusRouter().focused().get() == current, "密度变化丢失实际选项焦点: expected="
                    + describe(current) + ", actual=" + describe(context[0].runtime().focusRouter().focused().get()));
            require("50".equals(choice[0].get()), "密度变化改写业务选择: actual=" + choice[0].get());
            require(field[0] == original && created[0] == 1 && draft[0].snapshot().get().anchor == 1 && draft[0].snapshot().get().caret == 4
                    && tabs[0].node().gap() == 0 && geometry.get(select[0]).box.width == 180,
                    "密度变化重建活动面板、覆盖明确尺寸或破坏草稿选区");
            require(Boolean.TRUE.equals(named(session.pipeline().current().semantics, SceneSemantics.Role.OPTION, "选项50").values.selected),
                    "密度变化后选中事实没有读取原业务来源");
        });
        session.edit(SceneEditEvent.command(SceneEditEvent.Command.DOWN, false, null)); session.frame();
        domain.execute(() -> {
            SceneGeometryView geometry = session.pipeline().current().geometry;
            SceneGeometryView.Entry entry = geometry.get(find(geometry.activeModal(), "选项51"));
            require(!geometry.visibleRegion(entry.node).empty(), "原方向导航没有显露下一真实选项");
            point[0] = entry.box.x + 4; point[1] = entry.box.y + entry.box.height / 2;
        });
        session.pointer(ScenePointerEvent.down(0, point[0], point[1])); session.pointer(ScenePointerEvent.up(0, point[0], point[1])); session.frame();
        domain.execute(() -> require("51".equals(choice[0].get()) && session.pipeline().current().geometry.activeModal() == null,
                "密度更新后的当前几何不能命中并确认实际选项"));
        domain.execute(() -> density[0].set(SceneDensity.COMFORTABLE)); session.frame();
        domain.execute(() -> require(field[0] == original && "保留的项目草稿".equals(draft[0].snapshot().get().text) && created[0] == 1,
                "密度往返替换了同键面板或独立草稿"));
    }

    private final class CollectionPage {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        final List<Integer> source = new ArrayList<>();
        final Map<Integer, SceneNode> rowNodes = new HashMap<>(), fields = new HashMap<>();
        final Map<Integer, SceneEditState> drafts = new HashMap<>();
        final Map<Integer, Map<String, Integer>> facts = new HashMap<>();
        ScenePageContext page; Signal<SceneDensity> density; Signal<Boolean> dark; ReadableSignal<SceneTheme> theme;
        SceneVirtualList<Integer, Integer> rows; SceneDataTable<Integer, Integer, String> table; ScenePageSession session;
        int created;
        CollectionPage(boolean tabular) {
            host.width = 420; host.height = 180; for (int i = 0; i < 600; i++) source.add(i);
            session = ScenePageSession.open(domain, host, host, context -> {
                page = context; density = domain.signal(SceneDensity.COMFORTABLE); dark = domain.signal(false); page.density(density);
                // 明确完整字体：此用法切密度只改变内距，估算值也始终不变。
                theme = () -> {
                    SceneTheme base = dark.get() ? SceneTheme.dark() : SceneTheme.light();
                    return base.withTextStyle(base.textStyle.withEm(18)).withDensity(density.get());
                };
                if (tabular) {
                    SceneDataTable.Column<Integer, String> title = new SceneDataTable.Column<>("title", () -> Size.fixed(120),
                            () -> SceneLabel.create(page, () -> "项目", theme), value -> cell(value.get(), "title", SceneControlMetrics.Kind.TABLE));
                    SceneDataTable.Column<Integer, String> note = new SceneDataTable.Column<>("note", () -> Size.fill(1).bounds(180, Integer.MAX_VALUE),
                            () -> SceneLabel.create(page, () -> "备注", theme), value -> cell(value.get(), "note", SceneControlMetrics.Kind.TABLE));
                    table = SceneDataTable.create(page, () -> source, value -> value, () -> Arrays.asList(title, note),
                            () -> new SceneDataTable.Options().columnGap(7).rows(new SceneVirtualList.Options().estimatedRowHeight(60).overscan(0)), theme);
                    rows = table.rows(); page.mount(table.node());
                } else {
                    rows = SceneVirtualList.create(page, () -> source, value -> value,
                            value -> cell(value.get(), "main", SceneControlMetrics.Kind.VIRTUAL),
                            () -> new SceneVirtualList.Options().estimatedRowHeight(60).overscan(0));
                    page.mount(rows.node());
                }
            }); session.frame();
        }
        SceneNode cell(int id, String column, SceneControlMetrics.Kind kind) {
            created++;
            SceneNode cell = new SceneNode().size(Size.fill(1), Size.natural()).control(new MeasuredFlow(id, column));
            cell.bind(NodeBinding.value(() -> theme.get().metrics(kind).padding, SceneNode::defaultPadding));
            if (!column.equals("note")) cell.add(SceneLabel.create(page, () -> id % 3 == 0 ? "项目" + id + "\n附加信息" : "项目" + id, theme));
            if (!column.equals("title")) {
                SceneEditState draft = drafts.computeIfAbsent(id, ignored -> new SceneEditState(domain, "备注 " + id));
                SceneNode field = SceneTextField.create(page, draft, theme, () -> false, request -> {});
                fields.put(id, field); cell.add(field);
            }
            if (!column.equals("note")) rowNodes.put(id, cell);
            return cell;
        }
        final class MeasuredFlow implements SceneControl {
            final int id; final String column;
            MeasuredFlow(int id, String column) { this.id = id; this.column = column; }
            public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
            public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
                MeasuredSize result = SceneFlowLayout.measure(node, body, children);
                facts.computeIfAbsent(id, ignored -> new HashMap<>()).put(column, result.height + node.padding() * 2);
                return result;
            }
            public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(node, box, children); }
            public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) { }
        }
        SceneScrollViewport.Snapshot range() { return rows.viewport().snapshot(session.pipeline().current().geometry); }
        void anchor(int id) {
            domain.execute(() -> require(rows.reveal(id), "无法准备锚点行")); session.frame();
            domain.execute(() -> {
                SceneGeometryView geometry = session.pipeline().current().geometry;
                SceneNode row = table == null ? rowNodes.get(id) : rowNodes.get(id).parent().parent();
                rows.viewport().moveToPixels(range().offset + geometry.get(row).box.y - range().box.y + 3);
            }); session.frame();
        }
        int rowY(int id) {
            SceneNode row = table == null ? rowNodes.get(id) : rowNodes.get(id).parent().parent();
            return session.pipeline().current().geometry.get(row).box.y - range().box.y;
        }
        void extent() {
            long expected = (long) source.size() * 60;
            for (Map<String, Integer> cell : facts.values()) expected += Collections.max(cell.values()) - 60;
            SceneScrollViewport.Snapshot view = range();
            long actual = (long) view.maximum + view.extent;
            require(actual == expected, "集合沿用了其它密度的屏外高度: content=" + actual + ", expected=" + expected
                    + ", viewport=" + view.extent + ", maximum=" + view.maximum + ", measuredRows=" + facts.size());
        }
    }
    private void collection(boolean tabular) {
        CollectionPage f = new CollectionPage(tabular); onClose(f.session::close);
        f.anchor(20); f.anchor(450); f.anchor(150);
        f.domain.execute(() -> require(f.rows.focus(150), "无法聚焦中段实际字段")); f.session.frame();
        f.session.edit(SceneEditEvent.text("未保存")); f.session.frame();
        SceneNode[] original = new SceneNode[1]; String[] text = new String[1]; int[] y = new int[1], height = new int[1];
        f.domain.execute(() -> {
            f.drafts.get(150).select(1, 3); original[0] = f.fields.get(150); text[0] = f.drafts.get(150).snapshot().get().text;
            y[0] = f.rowY(150); height[0] = f.session.pipeline().current().geometry.get(original[0]).box.height;
            f.extent(); f.facts.clear(); f.density.set(SceneDensity.COMPACT);
        }); f.session.frame();
        f.domain.execute(() -> {
            f.extent(); require(f.rowY(150) == y[0] && f.fields.get(150) == original[0]
                    && f.session.pipeline().current().geometry.get(original[0]).box.height < height[0]
                    && text[0].equals(f.drafts.get(150).snapshot().get().text)
                    && f.drafts.get(150).snapshot().get().anchor == 1 && f.drafts.get(150).snapshot().get().caret == 3,
                    "实际密度换代没有保持锚点、字段挂载或独立草稿选区");
            if (tabular) require(f.session.pipeline().current().geometry.get(f.rowNodes.get(150).parent()).box.width == 120,
                    "密度改变了明确fixed列宽");
        });
        f.domain.execute(() -> require(f.rows.reveal(599), "末行不可达")); f.session.frame();
        f.domain.execute(() -> { f.extent(); require(f.session.pipeline().current().geometry.get(f.rowNodes.get(599)) != null, "末行没有有限准备"); });
        f.anchor(150); int[] oldExtent = new int[1], oldCreated = new int[1];
        f.domain.execute(() -> { f.extent(); oldExtent[0] = f.range().maximum + f.range().extent; oldCreated[0] = f.created; f.dark.set(true); }); f.session.frame();
        f.domain.execute(() -> require(f.range().maximum + f.range().extent == oldExtent[0] && f.created == oldCreated[0],
                "纯颜色变化使屏外高度失效或创建新行"));
        f.domain.execute(() -> { f.facts.clear(); f.density.set(SceneDensity.COMFORTABLE); }); f.session.frame();
        f.domain.execute(() -> {
            f.extent(); require(text[0].equals(f.drafts.get(150).snapshot().get().text) && f.fields.get(150) == original[0]
                    && f.drafts.get(150).snapshot().get().anchor == 1 && f.drafts.get(150).snapshot().get().caret == 3,
                    "往返密度或访问末行破坏原字段和选区");
            require(f.created < 160 && f.rows.node().children().size() < 40, "密度变化提前构造了全量行");
        });
    }

    private void treeMetrics() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<SceneDensity>[] density = new Signal[1]; Signal<SceneTreeView.Options>[] options = new Signal[1];
        SceneTreeView<Integer, Integer>[] tree = new SceneTreeView[1]; Map<Integer, SceneNode> content = new HashMap<>(); int[] created = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            density[0] = domain.signal(SceneDensity.COMFORTABLE); page.density(density[0]); options[0] = domain.signal(new SceneTreeView.Options());
            SceneTheme exact = SceneTheme.light().withTextStyle(new TextStyle("default", 24, 0xff20252b));
            tree[0] = SceneTreeView.create(page, () -> Arrays.asList(0, 1), id -> id, id -> id == 0 ? null : 0,
                    () -> Collections.singleton(0), request -> {}, entry -> {
                        created[0]++; SceneNode label = SceneLabel.create(page, () -> "层级" + entry.get().key, () -> exact);
                        content.put(entry.get().key, label); return label;
                    }, options[0], page.theme()); page.mount(tree[0].node());
        }); onClose(session::close); session.frame();
        int[] oldIndent = new int[1], oldSlot = new int[1];
        domain.execute(() -> {
            SceneGeometryView geometry = session.pipeline().current().geometry;
            oldIndent[0] = geometry.get(content.get(1)).box.x - geometry.get(content.get(0)).box.x;
            oldSlot[0] = geometry.get(content.get(0).parent().children().get(1)).box.width;
            density[0].set(SceneDensity.COMPACT);
        }); session.frame();
        domain.execute(() -> {
            SceneGeometryView geometry = session.pipeline().current().geometry;
            int indent = geometry.get(content.get(1)).box.x - geometry.get(content.get(0)).box.x;
            require(indent < oldIndent[0] && geometry.get(content.get(0).parent().children().get(1)).box.width < oldSlot[0]
                    && created[0] == 2 && content.get(1).style().logicalEm == 24,
                    "树默认缩进未随角色变化、重建行或覆盖业务捕获的精确主题");
            options[0].set(new SceneTreeView.Options().indentPixels(33));
        }); session.frame();
        domain.execute(() -> density[0].set(SceneDensity.COMFORTABLE)); session.frame();
        domain.execute(() -> {
            SceneGeometryView geometry = session.pipeline().current().geometry;
            require(geometry.get(content.get(1)).box.x - geometry.get(content.get(0)).box.x == 33 && created[0] == 2,
                    "树明确缩进像素被角色缩放或换代重建了业务行");
        });
    }
}
