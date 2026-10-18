package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.input.SceneEditContinuation;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneCheckBox;
import club.heiqi.uilib.ui.scene.control.SceneRadioGroup;
import club.heiqi.uilib.ui.scene.control.SceneSwitch;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 状态来自业务；通过正式键盘管线验证单停靠、同组续段和离组不可复活。 */
public final class StateControlsScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new StateControlsScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("开关拒绝不伪造状态且每次动作即时读取", this::switchIntent);
        scenario("单选组一个Tab入口且箭头跳禁用并允许拒绝", this::tabAndSelection);
        scenario("同组方向repeat继续而合批离组回组永久结束旧段", this::repeatScope);
        scenario("动态重排保留项身份且当前项退出不改业务选择", this::dynamicOptions);
        scenario("首次选择中离组返回和动作失败均结束原续段", this::firstEventExit);
        scenario("续段资格失败仍完成唯一焦点交接并保留原错误", this::qualificationFailure);
        scenario("状态语义读取真实三态与单选来源且业务拒绝不缓存", this::semanticState);
    }
    private static SceneKeyEvent key(SceneKey key, boolean repeat) { return new SceneKeyEvent(key, false, false, false, false, repeat, null); }

    private void semanticState() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost text = new MultilineEditingScenario.PlainHost();
        SceneSemanticSnapshot[] snapshot = {SceneSemanticSnapshot.EMPTY};
        SceneHost host = new SceneHost() {
            public int width() { return 400; }
            public int height() { return 300; }
            public long metricsRevision() { return text.metricsRevision(); }
            public void replay(PaintPlan plan) { }
            public void semanticsChanged(SceneSemanticSnapshot next) { snapshot[0] = next; }
        };
        Signal<SceneCheckBox.State>[] check = new Signal[1]; Signal<Boolean>[] checked = new Signal[1];
        Signal<String>[] selected = new Signal[1];
        List<Boolean> checkRequests = new ArrayList<>(), switchRequests = new ArrayList<>(); List<String> radioRequests = new ArrayList<>();
        ScenePageSession session = ScenePageSession.open(domain, host, text, page -> {
            page.runtime().motionPolicy(() -> club.heiqi.uilib.ui.scene.motion.MotionPolicy.OFF);
            check[0] = domain.signal(SceneCheckBox.State.MIXED); checked[0] = domain.signal(false); selected[0] = domain.signal("a");
            SceneNode checkbox = SceneCheckBox.create(page, check[0], () -> "全部项目", page.theme(), checkRequests::add);
            SceneNode toggle = SceneSwitch.create(page, checked[0], () -> "自动保存", page.theme(), switchRequests::add);
            SceneRadioGroup<String> radio = SceneRadioGroup.create(page, () -> Arrays.asList(
                    new SceneRadioGroup.Option<>("a", () -> "标准"), new SceneRadioGroup.Option<>("b", () -> "紧凑")),
                    selected[0], radioRequests::add);
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(checkbox).add(toggle).add(radio.node()));
        }); onClose(session::close); session.frame();
        SceneSemanticSnapshot.Entry checkbox = stateEntry(snapshot[0], SceneSemantics.Role.CHECKBOX, "全部项目");
        SceneSemanticSnapshot.Entry toggle = stateEntry(snapshot[0], SceneSemantics.Role.SWITCH, "自动保存");
        SceneSemanticSnapshot.Entry standard = stateEntry(snapshot[0], SceneSemantics.Role.RADIO, "标准");
        SceneSemanticSnapshot.Entry compact = stateEntry(snapshot[0], SceneSemantics.Role.RADIO, "紧凑");
        require(Boolean.TRUE.equals(checkbox.values.mixed) && Boolean.FALSE.equals(checkbox.values.checked)
                && Boolean.FALSE.equals(toggle.values.checked) && Boolean.TRUE.equals(standard.values.checked)
                && Boolean.FALSE.equals(compact.values.selected), "初始语义没有区分三态、开关与当前单选来源");
        CompletableFuture<SceneSemanticRequest.Result> first = session.semantic(SceneSemanticRequest.of(checkbox.id, SceneSemantics.Action.ACTIVATE));
        CompletableFuture<SceneSemanticRequest.Result> second = session.semantic(SceneSemanticRequest.of(toggle.id, SceneSemantics.Action.ACTIVATE));
        CompletableFuture<SceneSemanticRequest.Result> third = session.semantic(SceneSemanticRequest.of(compact.id, SceneSemantics.Action.SELECT));
        session.frame();
        require(first.join() == SceneSemanticRequest.Result.APPLIED && second.join() == SceneSemanticRequest.Result.APPLIED
                && third.join() == SceneSemanticRequest.Result.APPLIED && checkRequests.equals(Collections.singletonList(true))
                && switchRequests.equals(Collections.singletonList(true)) && radioRequests.equals(Collections.singletonList("b")),
                "语义ACTIVATE/SELECT没有经过原业务改变意图");
        domain.execute(() -> require(check[0].get() == SceneCheckBox.State.MIXED && !checked[0].get() && "a".equals(selected[0].get()),
                "业务拒绝后语义动作写入了控件私有选中状态"));
        require(Boolean.TRUE.equals(snapshot[0].get(checkbox.id).values.mixed) && Boolean.FALSE.equals(snapshot[0].get(toggle.id).values.checked)
                && Boolean.FALSE.equals(snapshot[0].get(compact.id).values.selected), "拒绝后快照呈现了虚假接受状态");
        domain.execute(() -> { check[0].set(SceneCheckBox.State.CHECKED); checked[0].set(true); selected[0].set("b"); }); session.frame();
        require(Boolean.TRUE.equals(snapshot[0].get(checkbox.id).values.checked) && Boolean.FALSE.equals(snapshot[0].get(checkbox.id).values.mixed)
                && Boolean.TRUE.equals(snapshot[0].get(toggle.id).values.checked) && Boolean.TRUE.equals(snapshot[0].get(compact.id).values.selected),
                "外部真实状态没有更新原挂载语义");
        session.semantic(SceneSemanticRequest.of(checkbox.id, SceneSemantics.Action.ACTIVATE));
        session.semantic(SceneSemanticRequest.of(toggle.id, SceneSemantics.Action.ACTIVATE));
        session.semantic(SceneSemanticRequest.of(standard.id, SceneSemantics.Action.SELECT)); session.frame();
        require(checkRequests.equals(Arrays.asList(true, false)) && switchRequests.equals(Arrays.asList(true, false))
                && radioRequests.equals(Arrays.asList("b", "a")), "第二次语义动作没有即时读取外部更新后的状态");
        require(Boolean.TRUE.equals(snapshot[0].get(toggle.id).values.checked) && Boolean.TRUE.equals(snapshot[0].get(compact.id).values.selected),
                "第二次业务拒绝被缓存成新的选中来源");
    }
    private static SceneSemanticSnapshot.Entry stateEntry(SceneSemanticSnapshot snapshot, SceneSemantics.Role role, String name) {
        for (SceneSemanticSnapshot.Entry entry : snapshot.entries)
            if (entry.values.role == role && name.equals(entry.values.name)) return entry;
        throw new AssertionError("缺少状态语义 " + role + " / " + name);
    }

    private void qualificationFailure() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        ScenePageContext[] context = new ScenePageContext[1]; SceneNode[] nodes = new SceneNode[3];
        SceneEditState[] text = new SceneEditState[1];
        RuntimeException expected = new RuntimeException("续段资格失败"); int[] lost = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; text[0] = new SceneEditState(domain, "");
            nodes[1] = SceneTextField.create(page, text[0], page.theme(), () -> false, request -> {});
            nodes[2] = SceneButton.create(page, () -> "之后", page.theme(), () -> {});
            nodes[0] = new SceneNode().size(Size.fill(1), Size.fixed(30)).focusable(true).control(new SceneControl() {
                public MeasuredSize measure(SceneNode node, Constraints limits,
                        SceneTextMeasurer measurer) { return new MeasuredSize(100, 30); }
                public ChildPlacements arrange(SceneNode node, LayoutBox box,
                        SceneChildMeasurer children) {
                    return new ChildPlacements(Collections.emptyList());
                }
                public void paint(SceneNode node, SceneGeometryView geometry,
                        PaintPlanBuilder paint) { }
                public void focusChanged(SceneNode node, boolean focused, boolean eligible) { if (!focused) lost[0]++; }
                public boolean edit(SceneNode node, SceneEditEvent event) {
                    if (event.command != SceneEditEvent.Command.RIGHT) return false;
                    page.runtime().focusRouter().request(page.runtime().captureMount(nodes[1])); return true;
                }
                public SceneEditContinuation editContinuation(SceneNode node, SceneNode focused,
                        SceneEditEvent event) {
                    return new SceneEditContinuation() {
                        public boolean accepts(SceneNode actual) { if (actual == nodes[1]) throw expected; return actual == nodes[0]; }
                        public boolean repeat(SceneEditEvent next) { throw new AssertionError("失败续段被复活"); }
                    };
                }
            });
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(nodes[0]).add(nodes[1]).add(nodes[2]));
        }); onClose(session::close); session.frame(); session.key(key(SceneKey.RIGHT, false));
        try { session.frame(); throw new AssertionError("资格失败没有传播"); }
        catch (RuntimeException failure) { require(failure == expected, "资格错误没有保留原异常"); }
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == nodes[1] && lost[0] == 1,
                "资格错误打断内部owner与公开焦点及失焦通知交接"));
        session.key(key(SceneKey.RIGHT, true));
        session.edit(SceneEditEvent.text("继续")); session.frame();
        domain.execute(() -> require(text[0].snapshot().get().text.equals("继续"), "资格失败后的文字仍投旧公开焦点"));
        session.navigation(SceneNavigationEvent.NEXT); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == nodes[2], "资格失败后Tab没有从同一实际owner继续"));
    }

    private void dynamicOptions() {
        Fixture fixture = new Fixture(); onClose(fixture.session::close); fixture.session.frame();
        List<SceneNode> original = new ArrayList<>();
        fixture.domain.execute(() -> { original.addAll(fixture.items()); fixture.selected.set("c"); });
        fixture.session.navigation(SceneNavigationEvent.NEXT); fixture.session.frame();
        fixture.domain.execute(() -> { List<SceneRadioGroup.Option<String>> next = new ArrayList<>(fixture.options.get()); Collections.reverse(next); fixture.options.set(next); }); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == original.get(2) && fixture.items().get(0) == original.get(3), "重排替换了原焦点项或没有按当前来源排序"));
        fixture.session.key(key(SceneKey.HOME, false)); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == original.get(3), "Home没有读取重排后的可用顺序"));
        fixture.session.key(key(SceneKey.END, false)); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == original.get(0), "End没有跳过不可用项"));
        fixture.domain.execute(() -> fixture.options.set(Collections.singletonList(new SceneRadioGroup.Option<>("c", () -> "当前不可用", () -> false)))); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == null && fixture.selected.get().equals("c"), "项删除或禁用意外改写了选择或补选焦点"));
        fixture.session.navigation(SceneNavigationEvent.NEXT); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.before, "没有可用项的组仍截住Tab入口"));
        fixture.domain.execute(() -> fixture.options.set(Collections.emptyList())); fixture.session.frame();
        fixture.session.navigation(SceneNavigationEvent.NEXT); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.after && fixture.selected.get().equals("c"), "空组无法离开或制造了默认选择"));
    }

    private void firstEventExit() {
        Fixture fixture = new Fixture(); onClose(fixture.session::close); fixture.session.frame();
        fixture.session.navigation(SceneNavigationEvent.NEXT); fixture.session.frame();
        fixture.domain.execute(() -> fixture.change = chosen -> fixture.domain.batch(() -> {
            fixture.requests.add(chosen);
            fixture.context.runtime().focusRouter().request(fixture.context.runtime().captureMount(fixture.after));
            fixture.context.runtime().focusRouter().request(fixture.context.runtime().captureMount(fixture.items().get(0)));
        }));
        fixture.session.key(key(SceneKey.RIGHT, false)); fixture.session.frame();
        fixture.session.key(key(SceneKey.RIGHT, true)); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(0) && fixture.requests.equals(Collections.singletonList("c")),
                "fresh动作内离组返回发生在许可登记前，旧repeat被复活"));
        RuntimeException expected = new RuntimeException("选择保存失败");
        fixture.domain.execute(() -> fixture.change = chosen -> { throw expected; });
        int[] fallback = {0}; fixture.session.key(key(SceneKey.RIGHT, false), () -> fallback[0]++);
        try { fixture.session.frame(); throw new AssertionError("动作失败被当成未消费"); }
        catch (RuntimeException failure) { require(failure == expected, "选择错误没有保留原异常"); }
        fixture.domain.execute(() -> fixture.change = fixture.requests::add);
        fixture.session.key(key(SceneKey.RIGHT, true)); fixture.session.frame();
        require(fixture.requests.equals(Collections.singletonList("c")) && fallback[0] == 0, "失败后旧续段或宿主动作仍被执行");
    }

    private void switchIntent() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<Boolean>[] checked = new Signal[1], enabled = new Signal[1]; List<Boolean> requests = new ArrayList<>(); SceneNode[] toggle = new SceneNode[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            checked[0] = domain.signal(false); enabled[0] = domain.signal(true);
            toggle[0] = SceneSwitch.create(page, checked[0], () -> "自动保存", page.theme(), requests::add)
                    .size(Size.fixed(180), Size.fixed(40)).bind(NodeBinding.value(enabled[0], SceneNode::enabled));
            page.mount(toggle[0]);
        }); onClose(session::close); session.frame();
        session.key(key(SceneKey.SPACE, false)); session.key(key(SceneKey.SPACE, false).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        session.key(key(SceneKey.ENTER, false)); session.frame();
        require(requests.equals(Arrays.asList(true, true)), "拒绝后开关保存了自己的伪状态");
        domain.execute(() -> checked[0].set(true));
        session.key(key(SceneKey.SPACE, false)); session.key(key(SceneKey.SPACE, false).withPhase(SceneKeyEvent.Phase.UP)); session.frame(); require(requests.equals(Arrays.asList(true, true, false)), "开关没重读外部新状态");
        domain.execute(() -> enabled[0].set(false)); session.key(key(SceneKey.SPACE, false)); session.key(key(SceneKey.SPACE, false).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        require(requests.size() == 3, "禁用开关仍发布改变");
        domain.execute(() -> require(session.pipeline().current().geometry.get(toggle[0]).box.width == 180
                && session.pipeline().current().geometry.get(toggle[0]).box.height == 40, "自然尺寸覆盖了显式Size"));
    }

    private void tabAndSelection() {
        Fixture fixture = new Fixture(); onClose(fixture.session::close); fixture.session.frame();
        fixture.session.navigation(SceneNavigationEvent.NEXT); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(0), "未选中组没有首可用Tab入口"));
        fixture.session.key(key(SceneKey.RIGHT, false)); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(2) && fixture.requests.equals(Collections.singletonList("c"))
                && fixture.selected.get().equals("missing"), "箭头未跳禁用或伪造选择状态"));
        fixture.session.key(key(SceneKey.RIGHT, false)); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(3), "业务拒绝后没有从实际焦点继续移动"));
        fixture.session.navigation(SceneNavigationEvent.NEXT); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.after, "Tab没有从组内直接离组"));
        fixture.session.navigation(SceneNavigationEvent.PREVIOUS); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(3), "无有效选择时反向进入应到末个可用项"));
        fixture.domain.execute(() -> fixture.selected.set("c")); fixture.session.navigation(SceneNavigationEvent.PREVIOUS); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.before, "反向Tab没有直接离组"));
        fixture.session.navigation(SceneNavigationEvent.NEXT); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(2), "有效业务选择未作为组入口"));
    }

    private void repeatScope() {
        Fixture fixture = new Fixture(); onClose(fixture.session::close); fixture.session.frame();
        fixture.session.navigation(SceneNavigationEvent.NEXT); fixture.session.frame();
        fixture.session.key(key(SceneKey.RIGHT, false)); fixture.session.frame();
        fixture.session.key(key(SceneKey.RIGHT, true)); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(3) && fixture.requests.equals(Arrays.asList("c", "d")),
                "同组repeat仍被原字段A挂载检查挡住"));
        fixture.domain.execute(() -> fixture.domain.batch(() -> {
            fixture.context.runtime().focusRouter().request(fixture.context.runtime().captureMount(fixture.after));
            fixture.context.runtime().focusRouter().request(fixture.context.runtime().captureMount(fixture.items().get(0)));
        }));
        fixture.session.key(key(SceneKey.RIGHT, true)); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(0) && fixture.requests.equals(Arrays.asList("c", "d")),
                "合批离组返回让旧repeat复活"));
        fixture.session.key(key(SceneKey.RIGHT, false)); fixture.session.frame();
        fixture.domain.execute(() -> require(fixture.focused() == fixture.items().get(2), "旧段终止后fresh不能重新取得许可"));
    }

    private static final class Fixture {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        final List<String> requests = new ArrayList<>();
        final ScenePageSession session;
        ScenePageContext context; SceneRadioGroup<String> group; Signal<String> selected; SceneNode before, after;
        Signal<List<SceneRadioGroup.Option<String>>> options;
        java.util.function.Consumer<String> change = requests::add;
        Fixture() {
            session = ScenePageSession.open(domain, host, host, page -> {
                context = page; selected = domain.signal("missing");
                options = domain.signal(Arrays.asList(new SceneRadioGroup.Option<>("a", () -> "标准"),
                        new SceneRadioGroup.Option<>("b", () -> "暂不可用", () -> false), new SceneRadioGroup.Option<>("c", () -> "紧凑"),
                        new SceneRadioGroup.Option<>("d", () -> "宽松")));
                group = SceneRadioGroup.create(page, options, selected, chosen -> change.accept(chosen));
                before = SceneButton.create(page, () -> "之前", page.theme(), () -> {});
                after = SceneButton.create(page, () -> "之后", page.theme(), () -> {});
                page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(before).add(group.node()).add(after));
            });
        }
        SceneNode focused() { return context.runtime().focusRouter().focused().get(); }
        List<SceneNode> items() { return group.node().children(); }
    }
}
