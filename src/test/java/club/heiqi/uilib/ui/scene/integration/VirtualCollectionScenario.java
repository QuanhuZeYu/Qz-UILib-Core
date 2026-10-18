package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneVirtualList;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 真实绑定/布局/输入消费长集合；宿主仅替代文字度量及Plan回放边界。 */
public final class VirtualCollectionScenario extends TestApplication {
    private static final class Host implements club.heiqi.uilib.ui.scene.host.SceneHost,
            club.heiqi.uilib.ui.scene.text.SceneTextMeasurer {
        int width = 200, height = 100, measures;
        long metrics;
        final MultilineEditingScenario.PlainHost font = new MultilineEditingScenario.PlainHost();
        public int width() { return width; }
        public int height() { return height; }
        public long metricsRevision() { return metrics; }
        public void replay(club.heiqi.uilib.ui.scene.paint.PaintPlan plan) { }
        public club.heiqi.uilib.ui.scene.text.TextLayout layout(String text,
                club.heiqi.uilib.ui.scene.text.TextStyle style, int width) {
            return layout(text, style, width, club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL);
        }
        public club.heiqi.uilib.ui.scene.text.TextLayout layout(String text,
                club.heiqi.uilib.ui.scene.text.TextStyle style, int width, club.heiqi.uilib.ui.scene.text.TextPolicy policy) {
            measures++; return font.layout(text, style, width, policy);
        }
    }

    private static final class Item {
        final int id, height;
        final String caption;
        Item(int id, int height) { this(id, height, "A" + id); }
        Item(int id, int height, String caption) { this.id = id; this.height = height; this.caption = caption; }
    }

    private final class Page {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final Host host = new Host();
        final Map<Integer, SceneNode[]> nodes = new HashMap<>();
        final Map<Integer, club.heiqi.uilib.ui.scene.edit.SceneEditState> drafts = new HashMap<>();
        int submissions;
        ScenePageContext context;
        Signal<List<Item>> items;
        Signal<List<SceneNode>> layers;
        Signal<Boolean> enabled;
        SceneVirtualList<Item, Integer> list;
        SceneNode before, after;
        ScenePageSession session;
        int created, mounted, released, clicks = -1;

        Page(int count, int firstFocusable, boolean header) { this(count, firstFocusable, header, false); }
        Page(int count, int firstFocusable, boolean header, boolean editors) { this(count, firstFocusable, header, editors, false); }
        Page(int count, int firstFocusable, boolean header, boolean editors, boolean smallAction) {
            host.width = 200; host.height = 100;
            session = ScenePageSession.open(domain, host, host, page -> {
                context = page;
                List<Item> values = new ArrayList<>();
                for (int i = 0; i < count; i++) values.add(new Item(i, smallAction ? 60 : i % 4 == 0 ? 40 : 20));
                items = domain.signal(values);
                list = SceneVirtualList.create(page, items, item -> item.id, value -> {
                    created++;
                    Item current = value.get();
                    SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), current.height < 0 ? Size.natural() : Size.fixed(current.height));
                    row.bind(NodeBinding.value(value, (node, next) -> node.size(Size.fill(1), next.height < 0 ? Size.natural() : Size.fixed(next.height))));
                    row.bind((runtime, node, owner) -> { mounted++; return new club.heiqi.uilib.ui.scene.runtime.BindingActivation() {
                        public void refresh() { }
                        public void close() { released++; }
                    }; });
                    SceneNode a = new SceneNode().size(Size.fill(1), Size.fill(1)).focusable(current.id >= firstFocusable)
                            .clickFocus(true).onClick(() -> clicks = value.get().id);
                    if (editors) {
                        club.heiqi.uilib.ui.scene.edit.SceneEditState draft = drafts.computeIfAbsent(current.id,
                                ignored -> new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, ""));
                        a = club.heiqi.uilib.ui.scene.control.SceneTextField.create(page, draft, page.theme(), () -> false,
                                submission -> submissions++, club.heiqi.uilib.ui.scene.control.SceneTextField.SubmitPolicy.BLUR)
                                .size(Size.fill(1), Size.fill(1));
                    } else a.bind(NodeBinding.value(value, (node, next) -> node.text(next.caption)));
                    SceneNode b = new SceneNode().size(Size.fixed(30), Size.fill(1)).text("B")
                            .focusable(current.id >= firstFocusable).clickFocus(true).onClick(() -> clicks = -value.get().id - 2);
                    if (smallAction) { a.size(Size.fill(1), Size.fixed(10)); b.focusable(false); }
                    row.add(a).add(b); nodes.put(current.id, new SceneNode[] {row, a, b});
                    return row;
                }, () -> new SceneVirtualList.Options().estimatedRowHeight(20).overscan(0));
                enabled = domain.signal(true); list.node().bind(NodeBinding.value(enabled, SceneNode::enabled));
                before = new SceneNode().size(Size.fill(1), Size.fixed(10)).text("before").focusable(header);
                after = new SceneNode().size(Size.fill(1), Size.fixed(10)).text("after").focusable(true);
                layers = domain.signal(java.util.Collections.emptyList());
                SceneNode layerHost = new SceneNode().size(Size.fixed(0), Size.fixed(0)).bind(NodeBinding.ownedChildren(layers));
                page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(before).add(list.node()).add(after).add(layerHost));
            });
            onClose(session::close);
            session.frame();
        }

        @SuppressWarnings("unchecked") <V> V read(java.util.function.Supplier<V> value) {
            Object[] result = new Object[1]; domain.execute(() -> result[0] = value.get()); return (V)result[0];
        }
        SceneGeometryView.Entry entry(SceneNode node) { return read(() -> session.pipeline().current().geometry.get(node)); }
        SceneNode focus() { return read(() -> context.runtime().focusRouter().focused().get()); }
        void next() { session.navigation(SceneNavigationEvent.NEXT); session.frame(); }
        void previous() { session.navigation(SceneNavigationEvent.PREVIOUS); session.frame(); }
        void scroll(int offset) { domain.execute(() -> context.runtime().scrollToPixels(list.node(), SceneNode.Axis.COLUMN, offset)); session.frame(); }
        void click(SceneNode node) {
            LayoutBox box = entry(node).box;
            int x = box.x + box.width / 2, y = box.y + box.height / 2;
            session.pointer(ScenePointerEvent.down(0, x, y)); session.pointer(ScenePointerEvent.up(0, x, y)); session.frame();
        }
    }

    public static void main(String[] args) throws Exception { new VirtualCollectionScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("有限行、真实变高、范围与静止复用", this::bounded);
        scenario("Tab跨屏行内顺序及前后控件", this::navigation);
        scenario("初始选择按完整逻辑顺序且不显露", this::initialFocus);
        scenario("真实删除吞旧手势尾部", this::removedPress);
        scenario("锚点行缩短先限制行内距离", this::shorterAnchor);
        scenario("独立草稿离屏保留与同批导航后编辑", this::editing);
        scenario("离屏编辑被模态覆盖后恢复原联系", this::coveredFocus);
        scenario("离屏按压保持正式几何但不激活邻行", this::pressedOffscreen);
        scenario("行声明失败释放已取得但未挂载资源", this::factoryFailure);
        scenario("宽度资源换代保持真实首可见键及禁用呈现", this::resizeAndDisabled);
        scenario("资源换代的范围限制不能覆盖逻辑锚定", this::metricsClamp);
        scenario("已接受请求随隐藏范围结束而失效", () -> endedRequest(false));
        scenario("模态属性结束但保留内容时旧请求失效", () -> endedRequest(true));
        scenario("折叠整行不贡献尺寸和间距", this::hiddenRows);
        scenario("独立资格投影全隐藏与恢复保持有限行", this::membershipProjection);
        scenario("显式跳末尾在估算校正后显露最后一行", this::jumpToEnd);
        scenario("末行隐藏也结束末端意图且追加不追尾", this::hiddenEnd);
        scenario("方向跨屏按相邻逻辑行和原横向位置到达", this::directionalRows);
        scenario("空方向复合入口继续页脚而不截断", this::emptyDirectional);
        scenario("方向跨行后同批文字进入实际新草稿", this::directionalEditing);
        scenario("行内小动作显露后结束请求且不追随用户滚动", this::smallActionFocus);
        scenario("无合法焦点的显式行请求结束有限准备", this::noFocusableRequest);
    }

    private void directionalRows() {
        Page page = new Page(1000, 0, true);
        page.session.navigation(SceneNavigationEvent.DOWN); page.session.frame();
        require(page.focus() == page.nodes.get(0)[1], "外部向下没有进入当前可见首行");
        page.session.navigation(SceneNavigationEvent.RIGHT); page.session.frame();
        require(page.focus() == page.nodes.get(0)[2], "同行Right错误换行或离开集合");
        for (int i = 1; i <= 14; i++) {
            page.session.navigation(SceneNavigationEvent.DOWN); page.session.frame();
            require(page.focus() == page.nodes.get(i)[2], "Down跨行未保留原横向位置: " + i);
            require(page.mounted - page.released < 12, "方向准备保留了累计行树");
        }
        SceneGeometryView.Entry placed = page.entry(page.focus());
        require(placed.box.y >= placed.clip.y && (long)placed.box.y + placed.box.height <= (long)placed.clip.y + placed.clip.height,
                "屏外方向目标未在同批显露");
        page.session.navigation(SceneNavigationEvent.UP); page.session.frame();
        require(page.focus() == page.nodes.get(13)[2], "向上没有返回相邻逻辑行");
        page.domain.execute(() -> page.list.focus(999)); page.session.frame();
        SceneGeometryView.Entry last = page.entry(page.focus());
        require(last.box.y >= last.clip.y && (long)last.box.y + last.box.height <= (long)last.clip.y + last.clip.height,
                "显式末行准备在邻行校正后仍落于视口外");
        page.session.navigation(SceneNavigationEvent.DOWN); page.session.frame();

        require(page.focus() == page.after, "逻辑末行边界没有衔接同范围页脚");
        page.session.navigation(SceneNavigationEvent.DOWN); page.session.frame();
        require(page.focus() == page.after, "方向页脚边缘不应循环");
    }

    private void smallActionFocus() {
        Page page = new Page(1000, 0, true, false, true);
        page.domain.execute(() -> page.list.focus(20)); page.session.frame();
        SceneGeometryView.Entry target = page.entry(page.nodes.get(20)[1]);
        require(page.focus() == target.node && target.box.y >= target.clip.y
                && (long)target.box.y + target.box.height <= (long)target.clip.y + target.clip.height, "行首小按钮未显露");
        page.scroll(0);
        require(page.entry(page.list.node()).scrollOffset == 0, "focus请求已显露小按钮后仍用整行条件追随用户滚动");
    }

    private void noFocusableRequest() {
        Page page = new Page(1000, 2000, true);
        page.domain.execute(() -> page.list.focus(20)); page.session.frame();
        require(page.focus() == page.before, "无合法目标的focus请求创建了备用焦点");
        require(page.nodes.get(20)[0].runtime() == null, "没有可聚焦项的行被未结束请求永久保留");
    }

    private void directionalEditing() {
        Page page = new Page(1000, 0, true, true);
        page.session.navigation(SceneNavigationEvent.DOWN);
        page.session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("首行")); page.session.frame();
        require(page.read(() -> page.drafts.get(0).snapshot().get().text).equals("首行"), "方向后文字未进入首行");
        page.session.navigation(SceneNavigationEvent.DOWN);
        page.session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("次行")); page.session.frame();
        require(page.focus() == page.nodes.get(1)[1] && page.read(() -> page.drafts.get(1).snapshot().get().text).equals("次行"),
                "跨行方向后的同批文字进入旧行");
        require(page.read(() -> page.drafts.get(0).snapshot().get().text).equals("首行"), "跨行改变了旧草稿");
    }

    private void emptyDirectional() {
        Page page = new Page(0, 0, true);
        page.session.navigation(SceneNavigationEvent.DOWN); page.session.frame();
        require(page.focus() == page.after, "空集合END截断了页脚几何候选");
        Page noTargets = new Page(8, 20, true);
        noTargets.session.navigation(SceneNavigationEvent.DOWN); noTargets.session.frame();
        require(noTargets.focus() == noTargets.after, "全部无合法目标的集合截断页脚");
        require(noTargets.mounted - noTargets.released < 12, "无目标方向搜索保留了无用行树");
    }

    private void bounded() {
        Page page = new Page(1000, 0, true);
        require(page.created < 12 && page.mounted - page.released < 12, "首次进入构造了屏外行: " + page.created);
        require(page.entry(page.list.node()).scrollMaximum > 19000, "未实例化尾部没有进入正式滚动范围");
        require(page.entry(page.nodes.get(0)[0]).box.height == 40, "实际行高被估算替代");
        SceneNode old = page.nodes.get(0)[0];
        int created = page.created, measures = page.host.measures;
        page.session.frame();
        require(page.created == created && page.host.measures == measures, "静止帧重建或重测");
        page.scroll(600);
        require(old.destroyed(), "普通离屏行视图未结束");
        require(page.list.node().children().size() < 12, "滚动将全量行留在场景树");
        page.domain.execute(() -> require(page.list.reveal(80), "正式键显露被拒绝")); page.session.frame();
        SceneNode action = page.nodes.get(80)[1];
        require(page.entry(action).box.y >= 10 && page.entry(action).box.y < 90, "目标未显露");
        page.click(action); require(page.clicks == 80, "命中未使用当前行业务身份: " + page.clicks);
        page.scroll(0);
        require(page.nodes.get(0)[0] != old && page.entry(page.nodes.get(0)[0]).box.height == 40, "返回行未按当前数据重建");
    }

    private void navigation() {
        Page page = new Page(12, 0, true);
        require(page.focus() == page.before, "页面首目标");
        for (int key = 0; key < 12; key++) {
            page.next(); require(page.focus() == page.nodes.get(key)[1], "Tab遗漏第一行内动作: " + key);
            page.next(); require(page.focus() == page.nodes.get(key)[2], "Tab遗漏第二行内动作: " + key);
            require(page.entry(page.focus()) != null, "导航目标缺少正式几何");
        }
        page.next(); require(page.focus() == page.after, "集合尾部没有衔接页脚");
        page.previous(); require(page.focus() == page.nodes.get(11)[2], "反向没有按需准备末行");
        for (int key = 11; key >= 0; key--) {
            page.previous(); require(page.focus() == page.nodes.get(key)[1], "反向行内第一动作: " + key);
            page.previous(); require(page.focus() == (key == 0 ? page.before : page.nodes.get(key - 1)[2]), "反向跨行: " + key);
        }
    }

    private void initialFocus() {
        Page page = new Page(20, 9, false);
        require(page.focus() == page.nodes.get(9)[1], "初始选择跳过屏外合法目标到页脚");
        require(page.entry(page.list.node()).scrollOffset == 0, "初始选择擅自显露");
        page.next(); require(page.focus() == page.nodes.get(9)[2], "主动导航行内顺序");
        require(page.entry(page.list.node()).scrollOffset > 0, "主动导航没有显露屏外目标");
    }

    private void shorterAnchor() {
        Page page = new Page(8, 0, true);
        page.domain.execute(() -> {
            List<Item> values = new ArrayList<>();
            for (int i = 0; i < 8; i++) values.add(new Item(i, i == 0 ? 200 : 100));
            page.items.set(values);
        }); page.session.frame();
        page.scroll(150);
        require(page.entry(page.nodes.get(0)[0]).box.y == -140, "前置锚点行与行内距离不符");
        page.domain.execute(() -> {
            List<Item> values = new ArrayList<>(page.items.get()); values.set(0, new Item(0, 40)); page.items.set(values);
        }); page.session.frame();
        require(page.entry(page.list.node()).scrollOffset == 39, "锚点缩短后未先限制行内距离: "
                + page.entry(page.list.node()).scrollOffset);
    }

    private void editing() {
        Page page = new Page(100, 0, true, true);
        page.domain.execute(() -> require(page.list.focus(0), "不能请求首行编辑")); page.session.frame();
        SceneNode field = page.nodes.get(0)[1];
        page.session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("草稿")); page.session.frame();
        page.scroll(600);
        require(!field.destroyed() && page.focus() == field, "离屏结束了有效编辑联系");
        require(page.submissions == 0, "用户滚动产生失焦提交");
        require(page.entry(page.list.node()).scrollOffset == 600, "保留编辑行擅自追焦");
        page.domain.execute(() -> page.context.runtime().focusRouter().request(page.context.runtime().captureMount(page.after)));
        page.session.frame();
        require(field.destroyed(), "编辑联系结束后离屏视图未释放");
        require(page.read(() -> page.drafts.get(0).text().get()).equals("草稿"), "视图释放清除了独立草稿");
        page.domain.execute(() -> require(page.list.focus(0), "不能返回独立草稿")); page.session.frame();
        require(page.nodes.get(0)[1] != field && page.read(() -> page.drafts.get(0).text().get()).equals("草稿"), "新视图未消费独立草稿");
        page.next(); // 行0第二个动作
        page.session.navigation(SceneNavigationEvent.NEXT);
        page.session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("新目标"));
        page.session.frame();
        require(page.focus() == page.nodes.get(1)[1] && page.read(() -> page.drafts.get(1).text().get()).equals("新目标"),
                "同批导航后的文本未进入实际目标");
        require(page.read(() -> page.drafts.get(0).text().get()).equals("草稿"), "同批文本写入旧行");
    }

    private void coveredFocus() {
        Page page = new Page(100, 0, true, true);
        page.domain.execute(() -> page.list.focus(0)); page.session.frame();
        SceneNode field = page.nodes.get(0)[1]; page.scroll(600);
        SceneNode[] dialog = new SceneNode[1];
        page.domain.execute(() -> {
            dialog[0] = new SceneNode().modal(true).focusable(true).text("dialog").size(Size.fixed(80), Size.fixed(30));
            page.layers.set(java.util.Collections.singletonList(dialog[0]));
        }); page.session.frame();
        require(page.focus() == dialog[0], "模态没有取得唯一焦点");
        page.domain.execute(() -> require(!page.list.focus(80) && !page.list.reveal(80), "后台显露/焦点请求被接受"));
        require(!field.destroyed(), "合法保存的离屏焦点在覆盖时被销毁");
        page.domain.execute(() -> page.layers.set(java.util.Collections.emptyList())); page.session.frame();
        require(page.focus() == field && page.entry(page.list.node()).scrollOffset == 600,
                "返回没有恢复原有效编辑联系或擅自追焦");
    }

    private void pressedOffscreen() {
        Page page = new Page(100, 0, true);
        SceneNode action = page.nodes.get(0)[1];
        page.session.pointer(ScenePointerEvent.down(0, 20, 20)); page.session.frame();
        page.scroll(600);
        require(!action.destroyed() && page.entry(action) != null
                && page.read(() -> page.context.runtime().pointerRouter().pressedTarget().get()) == action,
                "离屏按压丢失原owner或共同几何");
        page.session.pointer(ScenePointerEvent.up(0, 20, 20)); page.session.frame();
        require(page.clicks == -1 && action.destroyed(), "UP激活邻行或结束手势后没有释放离屏行");
    }

    private void factoryFailure() {
        ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
        int[] acquired = {0}, closed = {0};
        IllegalStateException expected = new IllegalStateException("row declaration failure");
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            List<Integer> keys = java.util.Arrays.asList(0, 1, 2, 3, 4);
            SceneNode list = SceneVirtualList.create(page, () -> keys, key -> key, item -> {
                if (item.get() == 3) throw expected;
                acquired[0]++;
                return new SceneNode().size(Size.fill(1), Size.fixed(20)).own(() -> closed[0]++);
            }, () -> new SceneVirtualList.Options().estimatedRowHeight(20)).node();
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).focusable(true).add(list));
        });
        try { session.frame(); throw new AssertionError("行构造失败被掩盖"); }
        catch (IllegalStateException failure) { require(failure == expected, "原行构造异常被替换"); }
        finally { session.close(); }
        require(acquired[0] == closed[0], "未挂载行资源遗漏释放: acquired=" + acquired[0] + " closed=" + closed[0]);
    }

    private void resizeAndDisabled() {
        Page page = new Page(100, 0, true);
        String caption = "可变内容行 测试动态宽度与真实换行 Material review ";
        page.domain.execute(() -> {
            List<Item> values = new ArrayList<>();
            for (int i = 0; i < 100; i++) values.add(new Item(i, -1, caption + i));
            page.items.set(values);
        }); page.session.frame(); page.scroll(210);
        SceneNode[] anchor = {null}; int[] within = {0};
        page.domain.execute(() -> {
            SceneGeometryView geometry = page.session.pipeline().current().geometry;
            SceneGeometryView.Entry view = geometry.get(page.list.node());
            for (SceneNode row : page.list.node().children()) {
                LayoutBox box = geometry.get(row).box;
                if ((long)box.y + box.height > view.box.y && box.y < (long)view.box.y + view.box.height) {
                    anchor[0] = row; within[0] = view.box.y - box.y; break;
                }
            }
        });
        require(anchor[0] != null, "缺少真实可见锚点");
        page.host.width = 120; page.host.metrics++; page.session.frame();
        LayoutBox resized = page.entry(anchor[0]).box;
        int expected = page.entry(page.list.node()).box.y - Math.min(within[0], Math.max(0, resized.height - 1));
        require(resized.y == expected, "宽度/资源换代改变首可见键或行内距离: " + resized.y + " expected=" + expected);
        page.scroll(0);
        page.domain.execute(() -> {
            page.enabled.set(false);
            List<Item> values = new ArrayList<>(page.items.get());
            values.set(0, new Item(0, 70)); values.set(1, new Item(1, 20)); page.items.set(values);
        }); page.session.frame();
        LayoutBox first = page.entry(page.nodes.get(0)[0]).box, second = page.entry(page.nodes.get(1)[0]).box;
        require(first.height == 70 && second.y == first.y + first.height, "禁用停止了内容行高更新");
    }

    private void metricsClamp() {
        Page page = new Page(20, 0, true);
        page.domain.execute(() -> {
            List<Item> values = new ArrayList<>();
            for (int i = 0; i < 20; i++) values.add(new Item(i, i == 0 ? 200 : 20));
            page.items.set(values);
        }); page.session.frame(); page.scroll(450);
        require(page.entry(page.nodes.get(13)[0]).box.y == 0, "前置锚点13/行内10");
        page.host.metrics++; page.session.frame();
        require(page.entry(page.list.node()).scrollOffset == 270, "估算范围限制被当成用户滚动: "
                + page.entry(page.list.node()).scrollOffset);
        require(page.entry(page.nodes.get(13)[0]).box.y == 0, "资源换代没有保留逻辑锚点");
    }

    private void hiddenRows() {
        ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
        Signal<Boolean>[] visibleChild = new Signal[1]; SceneNode[] shown = new SceneNode[2];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            visibleChild[0] = domain.signal(false);
            SceneNode list = SceneVirtualList.create(page, () -> java.util.Arrays.asList(0, 1, 2), key -> key,
                    item -> {
                        if (item.get() == 0) return new SceneNode().size(Size.fill(1), Size.fixed(20)).visible(false);
                        SceneNode row = new SceneNode().size(Size.fill(1), Size.natural());
                        if (item.get() == 1) row.add(new SceneNode().size(Size.fill(1), Size.fixed(20))
                                .bind(NodeBinding.value(visibleChild[0], SceneNode::visible)));
                        row.add(new SceneNode().size(Size.fill(1), Size.fixed(20)));
                        shown[item.get() - 1] = row; return row;
                    }).node().gap(7);
            page.mount(list);
        }); onClose(session::close); session.frame();
        domain.execute(() -> {
            SceneGeometryView geometry = session.pipeline().current().geometry;
            require(geometry.get(shown[0]).box.y == 0 && geometry.get(shown[1]).box.y == 27, "折叠整行仍贡献高度或间距");
        });
        domain.execute(() -> visibleChild[0].set(true)); session.frame();
        domain.execute(() -> require(session.pipeline().current().geometry.get(shown[1]).box.y == 47, "行内子树恢复未按普通流重测"));
    }

    private void membershipProjection() {
        ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
        Signal<Boolean>[] participating = new Signal[1]; int[] created = {0}, released = {0};
        SceneNode[] collection = new SceneNode[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            participating[0] = domain.signal(false);
            List<Integer> all = new ArrayList<>(); for (int i = 0; i < 1000; i++) all.add(i);
            club.heiqi.uilib.ui.reactive.ReadableSignal<List<Integer>> displayed = domain.computed(page.scope(),
                    () -> participating[0].get() ? all : java.util.Collections.emptyList());
            collection[0] = SceneVirtualList.create(page, displayed, key -> key, item -> {
                created[0]++;
                return new SceneNode().size(Size.fill(1), Size.fixed(20)).own(() -> released[0]++);
            }).node();
            page.mount(new SceneNode().focusable(true).size(Size.fill(1), Size.fill(1)).add(collection[0]));
        }); onClose(session::close); session.frame();
        require(created[0] == 0, "全不参与仍构造行树");
        domain.execute(() -> participating[0].set(true)); session.frame();
        require(created[0] > 0 && created[0] < 50, "资格恢复没有通过数据投影有限准备");
        SceneNode first = collection[0].children().get(0);
        domain.execute(() -> participating[0].set(false)); session.frame();
        require(collection[0].children().isEmpty() && released[0] == created[0], "投影退出没有结束所属视图");
        domain.execute(() -> participating[0].set(true)); session.frame();
        require(!collection[0].children().isEmpty() && collection[0].children().get(0) != first, "投影恢复复活旧行身份");
    }

    private void endedRequest(boolean keepContent) {
        ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
        Signal<Boolean>[] open = new Signal[1]; SceneNode[] initial = new SceneNode[1];
        SceneVirtualList<Integer, Integer>[] collection = new SceneVirtualList[1];
        ScenePageContext[] context = new ScenePageContext[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; open[0] = domain.signal(true);
            List<Integer> keys = new ArrayList<>(); for (int i = 0; i < 100; i++) keys.add(i);
            collection[0] = SceneVirtualList.create(page, () -> keys, key -> key,
                    item -> new SceneNode().focusable(true).size(Size.fill(1), Size.fixed(20)));
            initial[0] = new SceneNode().focusable(true).text("initial").size(Size.fill(1), Size.fixed(20));
            SceneNode modal = new SceneNode().modal(true).initialFocus(initial[0]).size(Size.fixed(200), Size.fixed(100))
                    .bind(keepContent ? NodeBinding.value(open[0], SceneNode::modal)
                            : NodeBinding.value(open[0], SceneNode::visible)).add(initial[0]).add(collection[0].node());
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(modal));
        }); onClose(session::close); session.frame();
        domain.execute(() -> {
            require(collection[0].focus(80), "前置屏外请求未接受"); open[0].set(false);
        }); session.frame();
        if (keepContent) domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == initial[0],
                "旧请求在modal属性结束后覆盖返回焦点"));
        domain.execute(() -> open[0].set(true)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == initial[0],
                "旧请求跨范围结束复活并覆盖新initialFocus"));
    }

    private void jumpToEnd() {
        Page page = new Page(1000, 0, true);
        page.domain.execute(() -> page.list.viewport().moveToPixels(Integer.MAX_VALUE)); page.session.frame();
        SceneNode last = page.nodes.get(999)[0];
        require(last != null && page.entry(last) != null, "跳末尾没有准备最后一行: created=" + page.created
                + " keys=" + page.nodes.keySet() + " offset=" + page.entry(page.list.node()).scrollOffset
                + " maximum=" + page.entry(page.list.node()).scrollMaximum);
        SceneGeometryView.Entry view = page.entry(page.list.node()); LayoutBox box = page.entry(last).box;
        require((long)box.y + box.height == (long)view.box.y + view.box.height,
                "行高校正后末行未贴到视口末端: rowBottom=" + ((long)box.y + box.height)
                        + " viewportBottom=" + ((long)view.box.y + view.box.height));
        page.scroll(0); require(page.entry(page.list.node()).scrollOffset == 0, "末尾后反向不能立即生效");
        for (boolean empty : new boolean[] {true, false}) {
            page.domain.execute(() -> {
                page.list.viewport().moveToPixels(Integer.MAX_VALUE);
                page.items.set(empty ? java.util.Collections.emptyList() : java.util.Collections.singletonList(new Item(999, 20)));
            }); page.session.frame();
            List<Item> next = new ArrayList<>(); for (int i = 0; i < 10; i++) next.add(new Item(i, i % 4 == 0 ? 40 : 20));
            page.domain.execute(() -> page.items.set(next)); page.session.frame();
            require(page.entry(page.list.node()).scrollOffset == 0, "已结束末尾意图追随了后续追加: empty=" + empty);
        }
    }

    private void hiddenEnd() {
        ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
        Signal<List<Integer>>[] source = new Signal[1]; SceneVirtualList<Integer, Integer>[] list = new SceneVirtualList[1];
        ScenePageContext[] context = new ScenePageContext[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; List<Integer> keys = new ArrayList<>(); for (int i = 0; i < 100; i++) keys.add(i);
            source[0] = domain.signal(keys);
            list[0] = SceneVirtualList.create(page, source[0], key -> key,
                    item -> new SceneNode().size(Size.fill(1), Size.fixed(20)).visible(item.get() != 99));
            page.mount(list[0].node().gap(7));
        }); onClose(session::close); session.frame();
        domain.execute(() -> list[0].viewport().moveToPixels(Integer.MAX_VALUE)); session.frame();
        int[] previous = {0}; domain.execute(() -> previous[0] = context[0].runtime().scrollOffset(list[0].node()));
        domain.execute(() -> { List<Integer> next = new ArrayList<>(source[0].get()); next.add(100); source[0].set(next); }); session.frame();
        domain.execute(() -> require(context[0].runtime().scrollOffset(list[0].node()) == previous[0], "隐藏末行留下追尾意图"));
    }

    private void removedPress() {
        Page page = new Page(20, 0, true);
        SceneNode old = page.nodes.get(0)[1];
        LayoutBox box = page.entry(old).box;
        int x = box.x + 2, y = box.y + 2;
        page.session.pointer(ScenePointerEvent.down(0, x, y)); page.session.frame();
        page.domain.execute(() -> {
            List<Item> changed = new ArrayList<>(page.items.get()); changed.remove(0); page.items.set(changed);
        }); page.session.frame();
        page.session.pointer(ScenePointerEvent.up(0, x, y)); page.session.frame();
        require(page.clicks == -1 && old.destroyed(), "旧UP激活了新条目");
        page.click(page.nodes.get(1)[1]); require(page.clicks == 1, "新完整输入未恢复");
    }
}
