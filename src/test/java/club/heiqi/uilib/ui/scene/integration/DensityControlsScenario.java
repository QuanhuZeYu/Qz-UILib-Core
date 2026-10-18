package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextScript;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 密度规格的真实Session反例；字体边界复用现有测试宿主。 */
public final class DensityControlsScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new DensityControlsScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("默认角色往返不累乘，旧明确主题保持精确样式", this::themeValues);
        scenario("密度和尺寸更新正式控件，局部值及编辑联系保持", this::controls);
        scenario("替换标准文字控件的外观后绑定仍更新原工厂默认", this::replacementControls);
    }
    private void themeValues() {
        SceneTheme base = SceneTheme.light();
        SceneTheme compact = base.withDensity(SceneDensity.COMPACT).withControlSize(SceneControlSize.SMALL);
        require(compact.textStyle.logicalEm < base.textStyle.logicalEm && compact.padding < base.padding, "继承角色未改变字号和内距");
        SceneTheme restored = compact.withDensity(SceneDensity.COMFORTABLE).withControlSize(SceneControlSize.REGULAR);
        require(restored.textStyle.equals(base.textStyle) && restored.padding == base.padding && restored.gap == base.gap, "角色往返累计缩放");
        TextStyle local = new TextStyle("明确字族", 24f, base.textStyle.color, java.util.Collections.emptyList(),
                new TextEffects(true, true, true, true, false, 1, .2f), new TextDecoration(1, 2, 3, 4, 2),
                new FontStyle(650, FontStyle.Posture.ITALIC), 2, 1, 0xffaabbcc, 0xff778899, TextScript.NONE.appendScript(true));
        SceneTheme explicit = new SceneTheme(local, base.panel, base.button, base.hover, base.pressed, base.focus,
                base.disabled, base.disabledText, base.tooltip, base.tooltipText, 0, 3);
        SceneTheme resized = explicit.withDensity(SceneDensity.COMPACT).withControlSize(SceneControlSize.LARGE);
        require(resized.textStyle.equals(local) && resized.padding == 0 && resized.gap == 3, "旧构造的作者值被缩放");
        require(base.withTextStyle(local).withDensity(SceneDensity.COMPACT).textStyle.equals(local), "完整局部TextStyle被二次缩放");
        require(new SceneAppearance.Palette(base.withDensity(SceneDensity.COMPACT)).theme(1, base.textStyle.color, 10).padding == 10,
                "Palette公开明确padding参数被当作框架默认缩放");
        require(base.metrics(SceneControlMetrics.Kind.BUTTON).layoutKey().equals(
                SceneTheme.dark().metrics(SceneControlMetrics.Kind.BUTTON).layoutKey()), "颜色进入布局条件");
        require(SceneAppearance.theme(false).textStyle.logicalEm == 15 && SceneAppearance.theme(false).padding == 10,
                "现代主题的原默认改变");
    }
    private static final class Host implements SceneHost, SceneTextMeasurer {
        final MultilineEditingScenario.PlainHost font = new MultilineEditingScenario.PlainHost();
        int layouts;
        PaintPlan last;
        public int width() { return 600; }
        public int height() { return 700; }
        public long metricsRevision() { return 0; }
        public void replay(PaintPlan plan) { last = plan; }
        public TextLayout layout(String text, TextStyle style, int width) { return layout(text, style, width, TextPolicy.NORMAL); }
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            layouts++; return font.layout(text, style, width, policy);
        }
    }
    private void controls() {
        ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
        Signal<SceneDensity>[] density = new Signal[1]; Signal<SceneControlSize>[] size = new Signal[1];
        Signal<TextStyle>[] local = new Signal[1]; Signal<Boolean>[] dark = new Signal[1];
        SceneNode[] button = new SceneNode[1], field = new SceneNode[1], symbol = new SceneNode[1], fixed = new SceneNode[1], modern = new SceneNode[1];
        SceneNode[] bar = new SceneNode[1], automaticHost = new SceneNode[1], prefix = new SceneNode[1], same = new SceneNode[1];
        SceneNode[] areaNode = new SceneNode[1];
        SceneEditState[] draft = new SceneEditState[1]; int[] clicks = {0};
        try (ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            page.motionPolicy(() -> MotionPolicy.OFF);
            density[0] = domain.signal(SceneDensity.COMFORTABLE); size[0] = domain.signal(SceneControlSize.REGULAR);
            dark[0] = domain.signal(false); local[0] = domain.signal(SceneTheme.light().textStyle.withEm(24));
            page.density(density[0]);
            ReadableSignal<SceneTheme> theme = domain.computed(page.scope(), () -> (dark[0].get() ? SceneTheme.dark() : SceneTheme.light())
                    .withDensity(density[0].get()).withControlSize(size[0].get()));
            button[0] = SceneButton.create(page, () -> "密度按钮", theme, () -> clicks[0]++).size(Size.natural(), Size.natural())
                    .bind(NodeBinding.tooltip(() -> SceneTooltip.Content.text("密度按钮说明"), SceneTooltip.Options::new, theme));
            draft[0] = new SceneEditState(domain, "保留草稿");
            field[0] = SceneTextField.create(page, draft[0], theme, () -> false, request -> { });
            symbol[0] = SceneIconButton.create(page, SceneIconButton.Symbol.STAR, () -> "星标", theme, () -> clicks[0]++);
            fixed[0] = SceneButton.create(page, () -> "明确", theme, () -> { }).style(local[0].get()).padding(0).gap(3)
                    .size(Size.fixed(100), Size.fixed(50)).bind(NodeBinding.value(local[0], SceneNode::style));
            modern[0] = SceneAppearance.button(page, () -> "图标动作", theme, () -> { }, SceneAppearance.Role.OUTLINE);
            prefix[0] = new SceneAppearance(page, theme).action("图标动作", SceneIconButton.Symbol.STAR, () -> clicks[0]++, SceneAppearance.Role.OUTLINE)
                    .size(Size.natural(), Size.natural());
            same[0] = SceneButton.create(page, () -> "同值覆盖", theme, () -> { }).style(theme.get().textStyle).padding(8).gap(8);
            SceneNode viewport = new SceneNode().size(Size.fill(1), Size.fixed(80)).scrollable(true)
                    .add(new SceneNode().size(Size.fill(1), Size.fixed(400)));
            bar[0] = SceneScrollbar.create(page, SceneScrollViewport.of(page, viewport, SceneNode.Axis.COLUMN),
                    () -> new SceneScrollbar.Options().dimensions(4, 24, 1).autoHide(false), theme);
            automaticHost[0] = new SceneNode().size(Size.fill(1), Size.fixed(60)).scrollable(true)
                    .add(new SceneNode().size(Size.fill(1), Size.fixed(300)));
            SceneScrollArea.Area area = SceneScrollArea.create(page, new SceneNode().size(Size.fixed(900), Size.fixed(200)),
                    () -> new SceneScrollArea.Options().axes(SceneScrollArea.Axes.BOTH).autoHide(false), theme);
            areaNode[0] = area.node().size(Size.fill(1), Size.fixed(60));
            page.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).gap(5).add(button[0]).add(field[0])
                    .add(symbol[0]).add(fixed[0]).add(modern[0]).add(prefix[0]).add(same[0]).add(new SceneNode().axis(SceneNode.Axis.ROW)
                            .size(Size.fill(1), Size.fixed(80)).add(viewport).add(bar[0])).add(automaticHost[0]).add(areaNode[0]));
        })) {
            session.frame();
            int[] before = new int[6];
            domain.execute(() -> {
                before[0] = box(session, button[0]).height; before[1] = box(session, field[0]).height;
                before[2] = box(session, symbol[0]).width; before[3] = box(session, modern[0]).height;
                before[4] = box(session, prefix[0]).width - box(session, modern[0]).width;
                before[5] = automaticWidth(session, automaticHost[0]);
                require(before[4] > 0 && before[5] == 11, "原图标预算或自动条基准改变");
                require(fixed[0].style().logicalEm == 24 && fixed[0].padding() == 0 && fixed[0].gap() == 3, "首挂覆盖作者声明");
                session.runtime().focusRouter().request(session.runtime().captureMount(field[0]));
            });
            session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL, false, null)); session.frame();
            SceneEditState.Snapshot[] selection = new SceneEditState.Snapshot[1];
            domain.execute(() -> { selection[0] = draft[0].snapshot().get(); density[0].set(SceneDensity.COMPACT); size[0].set(SceneControlSize.SMALL); });
            session.frame();
            domain.execute(() -> {
                require(box(session, button[0]).height < before[0] && box(session, field[0]).height < before[1]
                        && box(session, symbol[0]).width < before[2] && box(session, modern[0]).height < before[3], "正式控件未共同消费密度");
                require(box(session, fixed[0]).width == 100 && box(session, fixed[0]).height == 50
                        && fixed[0].padding() == 0 && fixed[0].gap() == 3 && fixed[0].style().logicalEm == 24, "密度反写明确像素");
                require(box(session, bar[0]).width == 4, "显式条宽被密度改写");
                require(same[0].style().logicalEm == 18 && same[0].padding() == 8 && same[0].gap() == 8, "与默认同值的作者声明丢失");
                require(box(session, prefix[0]).width - box(session, modern[0]).width < before[4], "前缀图标预算没有随有效字体和间距更新");
                require(automaticWidth(session, automaticHost[0]) < before[5], "自动条没有消费页面密度");
                SceneNode xBar = areaNode[0].children().get(1), yBar = areaNode[0].children().get(2);
                require(box(session, xBar).height == box(session, yBar).width && box(session, yBar).width < before[5], "Area外框与条的有效厚度不同步");
                require(session.runtime().focusRouter().focused().get() == field[0]
                        && draft[0].snapshot().get() == selection[0], "切密度破坏焦点或编辑状态");
                local[0].set(local[0].get().withEm(27));
            });
            session.frame();
            domain.execute(() -> { density[0].set(SceneDensity.COMFORTABLE); size[0].set(SceneControlSize.REGULAR); }); session.frame();
            domain.execute(() -> {
                require(box(session, button[0]).height == before[0] && box(session, modern[0]).height == before[3], "控件往返尺寸改变");
                require(fixed[0].style().logicalEm == 27 && fixed[0].padding() == 0, "主题失效覆盖后续局部Signal");
            });
            LayoutBox[] hit = new LayoutBox[1]; domain.execute(() -> hit[0] = box(session, button[0]));
            session.pointer(ScenePointerEvent.move(hit[0].x + 1, hit[0].y + 1)); session.frame();
            int measured = host.layouts;
            domain.execute(() -> dark[0].set(true)); session.frame();
            require(host.layouts == measured, "纯配色造成控件或提示文字重新测量");
            session.pointer(ScenePointerEvent.down(0, hit[0].x + 1, hit[0].y + 1));
            session.pointer(ScenePointerEvent.up(0, hit[0].x + 1, hit[0].y + 1)); session.frame();
            require(clicks[0] == 1, "角色变化后的可见按钮无法沿正式输入激活");
        }
    }
    private void replacementControls() {
        ReactiveDomain domain = ReactiveDomain.direct(); Host host = new Host();
        Signal<SceneDensity>[] density = new Signal[1]; Signal<String>[] text = new Signal[1];
        SceneNode[] nodes = new SceneNode[4]; SceneControl[] surfaces = new SceneControl[4]; int[] clicks = {0};
        try (ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            page.motionPolicy(() -> MotionPolicy.OFF);
            density[0] = domain.signal(SceneDensity.COMFORTABLE); text[0] = domain.signal("替换外观");
            ReadableSignal<SceneTheme> theme = domain.computed(page.scope(),
                    () -> SceneTheme.light().withDensity(density[0].get()));
            nodes[0] = SceneButton.create(page, text[0], theme, () -> clicks[0]++);
            nodes[1] = SceneLabel.create(page, text[0], theme);
            nodes[2] = SceneAppearance.button(page, text[0], theme, () -> clicks[0]++, SceneAppearance.Role.OUTLINE);
            SceneNode select = SceneSelect.create(page, () -> java.util.Collections.singletonList(
                    new SceneSelect.Option<>("a", text[0])), () -> "a", ignored -> {}, theme);
            nodes[3] = select.children().get(0);
            for (int i = 0; i < nodes.length; i++) {
                surfaces[i] = new ReplacementSurface();
                nodes[i].control(surfaces[i]);
            }
            page.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).gap(5)
                    .add(nodes[0]).add(nodes[1]).add(nodes[2]).add(select));
        })) {
            session.frame();
            domain.execute(() -> {
                require(nodes[0].padding() == 8 && nodes[1].padding() == 0 && nodes[2].padding() == 10 && nodes[3].padding() == 8,
                        "替换外观改变原工厂的默认内距");
                density[0].set(SceneDensity.COMPACT); text[0].set("当前文案");
            });
            session.frame();
            domain.execute(() -> {
                for (int i = 0; i < nodes.length; i++) {
                    require(nodes[i].control() == surfaces[i] && nodes[i].text().equals("当前文案")
                            && nodes[i].style().logicalEm < 18, "替换外观后工厂绑定未继续消费当前来源");
                }
                require(nodes[0].padding() < 8 && nodes[1].padding() == 0 && nodes[2].padding() < 10 && nodes[3].padding() < 8,
                        "替换外观后密度没有沿原工厂角色更新");
            });
            LayoutBox[] hit = new LayoutBox[1]; domain.execute(() -> hit[0] = box(session, nodes[0]));
            session.pointer(ScenePointerEvent.down(0, hit[0].x + 1, hit[0].y + 1));
            session.pointer(ScenePointerEvent.up(0, hit[0].x + 1, hit[0].y + 1)); session.frame();
            require(clicks[0] == 1, "替换外观破坏原标准按钮动作");
        }
    }
    private static final class ReplacementSurface implements SceneControl {
        public club.heiqi.uilib.ui.scene.layout.MeasuredSize measure(SceneNode node,
                club.heiqi.uilib.ui.scene.layout.Constraints constraints, SceneTextMeasurer text) {
            TextLayout body = text.layout(node.text(), node.style(), constraints.availableWidth, node.textPolicy());
            return new club.heiqi.uilib.ui.scene.layout.MeasuredSize(body.width, body.height);
        }
        public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode node, LayoutBox box,
                club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children) {
            return new club.heiqi.uilib.ui.scene.layout.ChildPlacements(java.util.Collections.emptyList());
        }
        public void paint(SceneNode node, SceneGeometryView geometry, club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder target) {
            SceneGeometryView.Entry entry = geometry.get(node);
            LayoutBox inner = new LayoutBox(entry.box.x + node.padding(), entry.box.y + node.padding(),
                    Math.max(0, entry.box.width - node.padding() * 2), Math.max(0, entry.box.height - node.padding() * 2));
            target.add(club.heiqi.uilib.ui.scene.paint.PaintCommand.text(inner, entry.clip, entry.text));
        }
    }
    private static int automaticWidth(ScenePageSession page, SceneNode host) {
        for (SceneNode bar : page.runtime().scrollDecorations(host)) {
            LayoutBox box = box(page, bar);
            if (box.height > 0 && box.width > 0 && box.width < box.height) return box.width;
        }
        throw new AssertionError("没有可见的默认纵向条");
    }
    private static LayoutBox box(ScenePageSession page, SceneNode node) {
        SceneGeometryView geometry = page.pipeline().current().geometry;
        return geometry.get(node).box;
    }
}
