package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDataTable;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneVirtualList;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 正式行视图与共同几何检验数据表头体对齐，不复制列分配算法。 */
public final class DataTableScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new DataTableScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("固定表头与有限行共享列宽及横向位置", this::alignment);
        scenario("列宽重排保留节点且定义替换结束旧产物", this::columnLifecycle);
        scenario("总宽不变的列重分配重测行高并保持滚动锚点", this::columnMeasurement);
        scenario("排序与列隐藏保留业务草稿及同批新目标", this::sortingDraft);
        scenario("全单元格暂时隐藏后仍可按来源恢复", this::emptyCells);
        scenario("普通表格字段优先处理光标并在Tab离开时提交", this::editingPriority);
    }

    private void editingPriority() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        ScenePageContext[] context = new ScenePageContext[1]; SceneNode[] field = new SceneNode[1], action = new SceneNode[1];
        club.heiqi.uilib.ui.scene.edit.SceneEditState[] draft = new club.heiqi.uilib.ui.scene.edit.SceneEditState[1];
        Signal<String>[] value = new Signal[1]; Signal<Boolean>[] readOnly = new Signal[1]; int[] submissions = {0}, actions = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; value[0] = domain.signal("alpha"); readOnly[0] = domain.signal(false);
            draft[0] = new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, value[0].get());
            SceneDataTable.Column<Integer, String> text = new SceneDataTable.Column<>("text", () -> Size.fill(1).bounds(120, 500),
                    () -> SceneLabel.create(page, () -> "备注", page.theme()), row -> field[0] =
                    club.heiqi.uilib.ui.scene.control.SceneTextField.create(page, draft[0], page.theme(), readOnly[0], request -> {
                        submissions[0]++;
                        if (draft[0].accept(request, request.text)) value[0].set(request.text);
                    }, club.heiqi.uilib.ui.scene.control.SceneTextField.SubmitPolicy.BLUR));
            SceneDataTable.Column<Integer, String> button = new SceneDataTable.Column<>("action", () -> Size.fixed(100),
                    () -> SceneLabel.create(page, () -> "操作", page.theme()), row -> action[0] =
                    club.heiqi.uilib.ui.scene.control.SceneButton.create(page, () -> "执行", page.theme(), () -> actions[0]++));
            page.mount(SceneDataTable.create(page, () -> java.util.Collections.singletonList(1), row -> row, () -> Arrays.asList(text, button)).node());
        }); onClose(session::close); session.frame();
        domain.execute(() -> draft[0].select(2, 2));
        session.key(new club.heiqi.uilib.ui.scene.input.SceneKeyEvent(club.heiqi.uilib.ui.scene.input.SceneKey.LEFT, false, false, false, false, false, null));
        session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("X")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == field[0]
                && draft[0].snapshot().get().text.equals("aXlpha") && value[0].get().equals("alpha"),
                "表格方向抢走字段光标或编辑过早提交业务值"));
        session.key(new club.heiqi.uilib.ui.scene.input.SceneKeyEvent(club.heiqi.uilib.ui.scene.input.SceneKey.HOME, false, false, false, false, false, null)); session.frame();
        domain.execute(() -> require(draft[0].snapshot().get().caret == 0 && context[0].runtime().focusRouter().focused().get() == field[0], "字段Home改成表格导航"));
        session.key(new club.heiqi.uilib.ui.scene.input.SceneKeyEvent(club.heiqi.uilib.ui.scene.input.SceneKey.TAB, false, false, false, false, false, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == action[0] && submissions[0] == 1
                && value[0].get().equals("aXlpha") && !draft[0].snapshot().get().active, "Tab没有按普通字段失焦提交到业务来源"));
        session.navigation(club.heiqi.uilib.ui.scene.input.SceneNavigationEvent.ACTIVATE); session.frame();
        require(actions[0] == 1, "字段后的普通动作不再可达");
        domain.execute(() -> { readOnly[0].set(true); context[0].runtime().focusRouter().request(context[0].runtime().captureMount(field[0])); draft[0].select(2, 2); });
        session.key(new club.heiqi.uilib.ui.scene.input.SceneKeyEvent(club.heiqi.uilib.ui.scene.input.SceneKey.RIGHT, false, false, false, false, false, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == field[0] && draft[0].snapshot().get().caret == 3
                && value[0].get().equals("aXlpha"), "只读字段的查看方向被表格截获"));
    }

    private void emptyCells() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<Boolean>[] visible = new Signal[1]; SceneDataTable<Integer, Integer, String>[] table = new SceneDataTable[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            visible[0] = domain.signal(true);
            SceneDataTable.Column<Integer, String> column = new SceneDataTable.Column<>("value", () -> Size.fill(1),
                    () -> club.heiqi.uilib.ui.scene.control.SceneButton.create(page, () -> "内容", page.theme(), () -> {}), row ->
                    SceneLabel.create(page, () -> "项目" + row.get(), page.theme()).bind(
                            club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(visible[0], SceneNode::visible)));
            table[0] = SceneDataTable.create(page, () -> Arrays.asList(1, 2, 3, 4), value -> value, () -> java.util.Collections.singletonList(column));
            page.mount(table[0].node());
        }); onClose(session::close); session.frame();
        domain.execute(() -> visible[0].set(false)); session.frame();
        domain.execute(() -> visible[0].set(true)); session.frame();
        domain.execute(() -> require(!table[0].rows().node().children().isEmpty(), "内容隐藏形成全零高度索引后不能恢复正文"));
    }

    private void sortingDraft() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<Boolean>[] reversed = new Signal[1], shown = new Signal[1];
        ScenePageContext[] context = new ScenePageContext[1]; SceneNode[] sortButton = new SceneNode[1];
        Map<Integer, club.heiqi.uilib.ui.scene.edit.SceneEditState> drafts = new HashMap<>(); Map<Integer, SceneNode> fields = new HashMap<>();
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; reversed[0] = domain.signal(false); shown[0] = domain.signal(true);
            club.heiqi.uilib.ui.reactive.ReadableSignal<List<Integer>> sorted = domain.computed(page.scope(),
                    () -> reversed[0].get() ? Arrays.asList(2, 1) : Arrays.asList(1, 2));
            club.heiqi.uilib.ui.scene.control.SceneCommand sort = club.heiqi.uilib.ui.scene.control.SceneCommand.of(
                    () -> true, () -> reversed[0].set(!reversed[0].get()));
            SceneDataTable.Column<Integer, String> title = new SceneDataTable.Column<>("title", () -> Size.fixed(100),
                    () -> sortButton[0] = club.heiqi.uilib.ui.scene.control.SceneButton.create(page, () -> "排序", page.theme(), sort::tryExecute),
                    row -> SceneLabel.create(page, () -> "项目" + row.get(), page.theme()));
            SceneDataTable.Column<Integer, String> note = new SceneDataTable.Column<>("note", () -> Size.fill(1).bounds(100, 500),
                    () -> SceneLabel.create(page, () -> "备注", page.theme()), row -> {
                        int id = row.get(); club.heiqi.uilib.ui.scene.edit.SceneEditState state = drafts.computeIfAbsent(id,
                                key -> new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, ""));
                        SceneNode field = club.heiqi.uilib.ui.scene.control.SceneTextField.create(page, state, page.theme(), () -> false, request -> {});
                        fields.put(id, field); return field;
                    });
            SceneDataTable<Integer, Integer, String> table = SceneDataTable.create(page, sorted, value -> value,
                    () -> shown[0].get() ? Arrays.asList(title, note) : java.util.Collections.singletonList(title));
            page.mount(table.node());
        }); onClose(session::close); session.frame();
        session.navigation(club.heiqi.uilib.ui.scene.input.SceneNavigationEvent.NEXT);
        session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("草稿一")); session.frame();
        domain.execute(() -> require(drafts.get(1).snapshot().get().text.equals("草稿一"), "表头Tab后文字没有进入实际首行字段"));
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(sortButton[0])));
        session.navigation(club.heiqi.uilib.ui.scene.input.SceneNavigationEvent.ACTIVATE);
        session.navigation(club.heiqi.uilib.ui.scene.input.SceneNavigationEvent.NEXT);
        session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("草稿二")); session.frame();
        domain.execute(() -> require(drafts.get(2).snapshot().get().text.equals("草稿二") && drafts.get(1).snapshot().get().text.equals("草稿一"),
                "排序同批导航把独立草稿改绑给旧行"));
        SceneNode old = fields.get(2);
        domain.execute(() -> shown[0].set(false)); session.frame();
        require(old.destroyed(), "隐藏备注列没有结束旧字段挂载");
        domain.execute(() -> shown[0].set(true)); session.frame();
        domain.execute(() -> require(drafts.get(2).snapshot().get().text.equals("草稿二") && fields.get(2) != old,
                "列恢复没有使用业务独立草稿和新呈现"));
    }

    private void columnMeasurement() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost(); host.width = 420;
        Signal<Integer>[] width = new Signal[1]; SceneDataTable<Integer, Integer, String>[] table = new SceneDataTable[1];
        Map<Integer, SceneNode> cells = new HashMap<>();
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            width[0] = domain.signal(80); List<Integer> data = new ArrayList<>(); for (int i = 0; i < 1000; i++) data.add(i);
            SceneDataTable.Column<Integer, String> text = new SceneDataTable.Column<>("text", () -> Size.fixed(width[0].get()),
                    () -> club.heiqi.uilib.ui.scene.control.SceneButton.create(page, () -> "标题", page.theme(), () -> {}), row -> {
                        SceneNode node = SceneLabel.create(page, () -> "保持独立草稿，在有限视图中浏览可变行高内容。项目" + row.get(), page.theme())
                                .size(Size.fill(1), Size.natural()); cells.put(row.get(), node); return node;
                    });
            SceneDataTable.Column<Integer, String> status = new SceneDataTable.Column<>("status", () -> Size.fixed(400 - width[0].get()),
                    () -> SceneLabel.create(page, () -> "状态", page.theme()), row -> SceneLabel.create(page, () -> "待处理", page.theme()));
            table[0] = SceneDataTable.create(page, () -> data, value -> value, () -> Arrays.asList(text, status));
            page.mount(table[0].node());
        }); onClose(session::close); session.frame();
        int[] before = {0}, anchor = {-1};
        domain.execute(() -> { before[0] = session.pipeline().current().geometry.get(cells.get(0)).box.height; table[0].rows().viewport().moveToPixels(600); }); session.frame();
        domain.execute(() -> {
            for (Map.Entry<Integer, SceneNode> entry : cells.entrySet()) {
                club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry placed = session.pipeline().current().geometry.get(entry.getValue());
                if (placed != null && placed.box.y <= placed.clip.y && (long)placed.box.y + placed.box.height > placed.clip.y) { anchor[0] = entry.getKey(); break; }
            }
            width[0].set(240);
        }); session.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry placed = session.pipeline().current().geometry.get(cells.get(anchor[0]));
            require(anchor[0] >= 0 && placed != null && placed.box.y <= placed.clip.y
                    && (long)placed.box.y + placed.box.height > placed.clip.y, "列重分配丢失原首可见业务行");
            require(placed.box.height < before[0], "列总宽不变时没有使用新的行内测量宽度");
        });
    }

    private void columnLifecycle() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        SceneDataTable<Integer, Integer, String>[] table = new SceneDataTable[1];
        Signal<List<SceneDataTable.Column<Integer, String>>>[] columns = new Signal[1];
        Signal<Integer>[] width = new Signal[1]; Signal<Boolean>[] shown = new Signal[1];
        ScenePageContext[] context = new ScenePageContext[1];
        Map<Integer, SceneNode> first = new HashMap<>(), second = new HashMap<>();
        SceneDataTable.Column<Integer, String>[] definitions = new SceneDataTable.Column[2];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; width[0] = domain.signal(120); shown[0] = domain.signal(true);
            definitions[0] = new SceneDataTable.Column<>("first", () -> Size.fixed(width[0].get()),
                    () -> SceneLabel.create(page, () -> "首列", page.theme()), row -> {
                        SceneNode node = SceneLabel.create(page, () -> "第" + row.get() + "项", page.theme()).size(Size.fill(1), Size.fixed(25));
                        node.bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(shown[0], SceneNode::visible)); first.put(row.get(), node); return node;
                    });
            definitions[1] = new SceneDataTable.Column<>("second", () -> Size.fill(1).bounds(100, 500),
                    () -> SceneLabel.create(page, () -> "次列", page.theme()), row -> {
                        SceneNode node = SceneLabel.create(page, () -> "详情" + row.get(), page.theme()).size(Size.fill(1), Size.fixed(25));
                        second.put(row.get(), node); return node;
                    });
            columns[0] = domain.signal(Arrays.asList(definitions[0], definitions[1]));
            List<Integer> data = new ArrayList<>(); for (int i = 0; i < 100; i++) data.add(i);
            table[0] = SceneDataTable.create(page, () -> data, value -> value, columns[0]); page.mount(table[0].node());
        }); onClose(session::close); session.frame();
        SceneNode original = first.get(0), other = second.get(0);
        domain.execute(() -> width[0].set(80)); session.frame();
        require(first.get(0) == original && !original.destroyed(), "改列宽重建了单元格");
        domain.execute(() -> shown[0].set(false)); session.frame();
        domain.execute(() -> require(session.pipeline().current().geometry.get(other).box.x == 80, "隐藏单元格使后续列左移"));
        domain.execute(() -> { shown[0].set(true); columns[0].set(Arrays.asList(definitions[1], definitions[0])); }); session.frame();
        require(first.get(0) == original && second.get(0) == other && !original.destroyed(), "纯重排列结束了原节点");
        domain.execute(() -> require(session.pipeline().current().geometry.get(other).box.x < session.pipeline().current().geometry.get(original).box.x,
                "当前列声明顺序未生效"));
        domain.execute(() -> {
            definitions[0] = new SceneDataTable.Column<>("first", () -> Size.fixed(80),
                    () -> SceneLabel.create(context[0], () -> "新首列", context[0].theme()), row -> {
                        SceneNode node = SceneLabel.create(context[0], () -> "新定义" + row.get(), context[0].theme()).size(Size.fill(1), Size.fixed(25));
                        first.put(row.get(), node); return node;
                    });
            columns[0].set(Arrays.asList(definitions[1], definitions[0]));
        }); session.frame();
        require(original.destroyed() && first.get(0) != original && !other.destroyed(), "同键新列定义没有只替换所属旧产物");
        domain.execute(() -> columns[0].set(java.util.Collections.emptyList())); session.frame();
        domain.execute(() -> require(table[0].rows().node().children().isEmpty(), "空列仍保留无用行树"));
        domain.execute(() -> columns[0].set(Arrays.asList(definitions[0], definitions[1]))); session.frame();
        domain.execute(() -> require(!table[0].rows().node().children().isEmpty(), "空列恢复后零高索引阻止正文准备"));
    }

    private void alignment() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost(); host.width = 420;
        SceneDataTable<Integer, Integer, String>[] table = new SceneDataTable[1]; ScenePageContext[] context = new ScenePageContext[1];
        SceneNode[] headers = new SceneNode[2]; Map<Integer, SceneNode[]> cells = new HashMap<>();
        Signal<List<SceneDataTable.Column<Integer, String>>>[] columns = new Signal[1];
        int[] created = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; List<Integer> data = new ArrayList<>(); for (int i = 0; i < 1000; i++) data.add(i);
            SceneDataTable.Column<Integer, String> name = new SceneDataTable.Column<>("name", () -> Size.fixed(120),
                    () -> headers[0] = club.heiqi.uilib.ui.scene.control.SceneButton.create(page, () -> "名称", page.theme(), () -> {})
                            .size(Size.fill(1), Size.fixed(28)), row -> {
                        created[0]++;
                        SceneNode cell = SceneLabel.create(page, () -> "项目" + row.get(), page.theme()).size(Size.fill(1), Size.fixed(28));
                        cells.computeIfAbsent(row.get(), key -> new SceneNode[2])[0] = cell; return cell;
                    });
            SceneDataTable.Column<Integer, String> description = new SceneDataTable.Column<>("description", () -> Size.fill(1).bounds(180, 600),
                    () -> headers[1] = SceneLabel.create(page, () -> "详情", page.theme()).size(Size.fill(1), Size.fixed(28)), row -> {
                        created[0]++;
                        SceneNode cell = SceneLabel.create(page, () -> "正文" + row.get(), page.theme()).size(Size.fill(1), Size.fixed(row.get() % 3 == 0 ? 42 : 28));
                        cells.computeIfAbsent(row.get(), key -> new SceneNode[2])[1] = cell; return cell;
                    });
            columns[0] = domain.signal(Arrays.asList(name, description));
            table[0] = SceneDataTable.create(page, () -> data, value -> value, columns[0],
                    () -> new SceneDataTable.Options().columnGap(8).rows(new SceneVirtualList.Options().estimatedRowHeight(28).overscan(0)), page.theme());
            page.mount(table[0].node().size(Size.fill(1), Size.fill(1)));
        }); onClose(session::close); session.frame();
        require(created[0] < 50, "千行表格提前创建了全部单元格: " + created[0]);
        domain.execute(() -> {
            for (int i = 0; i < 2; i++) {
                LayoutBox head = session.pipeline().current().geometry.get(headers[i]).box;
                LayoutBox body = session.pipeline().current().geometry.get(cells.get(0)[i]).box;
                require(head.x == body.x && head.width == body.width, "表头正文列不对齐: " + i);
            }
        });
        int[] headerY = {0}; domain.execute(() -> { headerY[0] = session.pipeline().current().geometry.get(headers[0]).box.y; table[0].rows().viewport().moveToPixels(500); }); session.frame();
        domain.execute(() -> require(session.pipeline().current().geometry.get(headers[0]).box.y == headerY[0], "正文纵向滚动带走表头"));
        host.width = 220; session.frame();
        domain.execute(() -> table[0].horizontalViewport().moveToPixels(70)); session.frame();
        domain.execute(() -> {
            LayoutBox head = session.pipeline().current().geometry.get(headers[1]).box;
            SceneNode active = null;
            for (SceneNode[] row : cells.values()) if (row[1].runtime() == context[0].runtime()) { active = row[1]; break; }
            require(active != null && session.pipeline().current().geometry.get(active).box.x == head.x, "横向滚动头体位置不同源");
            require(table[0].horizontalViewport().snapshot(session.pipeline().current().geometry).offset > 0, "窄窗口没有保留最小列宽的横向范围");
        });
        int[] start = new int[3];
        domain.execute(() -> {
            LayoutBox body = session.pipeline().current().geometry.get(table[0].rows().node()).box;
            start[0] = host.width - 5; start[1] = body.y + 12; start[2] = body.y + body.height - 30;
        });
        session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, start[0], start[1]));
        session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(start[0], start[2]));
        session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, start[0], start[2])); session.frame();
        domain.execute(() -> require(table[0].rows().viewport().snapshot(session.pipeline().current().geometry).offset > 500,
                "窄窗口的纵向条被全宽列内容横移到裁剪外"));
    }
}
