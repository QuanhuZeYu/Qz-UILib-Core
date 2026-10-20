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
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneTextPolicy;
import club.heiqi.uilib.ui.scene.control.SceneDensity;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
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
    @Override protected void runTests() throws Exception {
        scenario("开关拒绝不伪造状态且每次动作即时读取", this::switchIntent);
        scenario("单选组一个Tab入口且箭头跳禁用并允许拒绝", this::tabAndSelection);
        scenario("同组方向repeat继续而合批离组回组永久结束旧段", this::repeatScope);
        scenario("动态重排保留项身份且当前项退出不改业务选择", this::dynamicOptions);
        scenario("首次选择中离组返回和动作失败均结束原续段", this::firstEventExit);
        scenario("续段资格失败仍完成唯一焦点交接并保留原错误", this::qualificationFailure);
        scenario("状态语义读取真实三态与单选来源且业务拒绝不缓存", this::semanticState);
        scenario("标准状态首挂直达当前值且三态和快速反向只更新绘制", this::stateMotion);
        scenario("标准反馈覆盖服从减少动效与关闭且卸载重挂不续旧轨迹", this::motionPolicyAndMount);
        scenario("带名符号共用截断完整提示并随名称字号密度更新", this::symbolText);
        scenario("同色主题的开关资格来自真实enabled且图标共用命令资格", this::enabledAndCommand);
    }
    private void stateMotion() {
        MotionFixture f = new MotionFixture(); onClose(f.session::close);
        PaintPlan initial = f.plan(); int right = f.thumbX(); long check = f.ink(f.check), radio = f.ink(f.radio.node().children().get(0));
        f.advance(1_000_000_000L);
        require(f.plan() == initial, "首挂已经处于目标的标准状态仍启动了入场或永久tick");
        int layouts = f.host.layouts;
        f.domain.execute(() -> { f.tri.set(SceneCheckBox.State.MIXED); f.checked.set(false); f.selected.set("b"); }); f.session.frame();
        require(f.thumbX() == right && f.ink(f.check) == check, "真实状态更新时呈现没有从当前值起步");
        f.advance(50_000_000L);
        int middle = f.thumbX(); long cross = f.ink(f.check);
        require(middle < right && f.ink(f.radio.node().children().get(0)) < radio, "开关或单选标记未消费主题运动");
        f.domain.execute(() -> f.tri.set(SceneCheckBox.State.UNCHECKED)); f.session.frame();
        require(f.ink(f.check) == cross, "三态中途换目标重置了当前可见权重");
        f.advance(50_000_000L);
        long fading = f.ink(f.check); int reverse = f.thumbX();
        require(fading < cross, "撤销三态没有从当前勾/横线继续淡出");
        f.domain.execute(() -> { f.tri.set(SceneCheckBox.State.CHECKED); f.checked.set(true); f.selected.set("a"); }); f.session.frame();
        require(f.ink(f.check) == fading && f.thumbX() == reverse, "快速反向先跳到旧起点再播放");
        f.advance(500_000_000L);
        require(f.thumbX() == right && f.ink(f.check) == check && f.ink(f.radio.node().children().get(0)) == radio,
                "状态运动未精确停止在最新来源");
        require(f.host.layouts == layouts, "选中反馈触发了名称重测");
        PaintPlan settled = f.plan(); f.advance(1_000_000_000L);
        require(f.plan() == settled, "有限状态反馈结束后未恢复静态计划复用");
    }

    private void motionPolicyAndMount() {
        MotionFixture f = new MotionFixture(MotionSpec.tween(200_000_000L, MotionSpec.LINEAR));
        onClose(f.session::close); int right = f.thumbX();
        f.domain.execute(() -> {
            f.theme.set(f.theme.get().withMotion(MotionSpec.instant(), MotionSpec.instant()));
            f.checked.set(false);
        }); f.session.frame(); f.advance(50_000_000L);
        require(f.thumbX() < right, "显式feedbackMotion没有覆盖thumb的主题空间策略");
        int moving = f.thumbX();
        f.domain.execute(() -> f.policy.set(MotionPolicy.REDUCED)); f.session.frame();
        require(f.thumbX() == moving, "切换REDUCED没有从当前呈现值续接");
        f.advance(200_000_000L);
        int left = f.thumbX(); require(left < moving, "REDUCED未以短时单调位移收敛到当前来源");
        f.domain.execute(() -> { f.policy.set(MotionPolicy.OFF); f.checked.set(true); f.tri.set(SceneCheckBox.State.MIXED); }); f.session.frame();
        require(f.thumbX() == right, "OFF仍保留开关空间尾部");
        PaintPlan off = f.plan(); f.advance(500_000_000L); require(f.plan() == off, "OFF保留了等待推进的状态反馈");
        f.domain.execute(() -> { f.policy.set(MotionPolicy.NORMAL); f.checked.set(false); }); f.session.frame(); f.advance(50_000_000L);
        require(f.thumbX() > left && f.thumbX() < right, "卸载反例没有先建立活动轨迹");
        f.domain.execute(() -> f.mount.detach()); f.session.frame(); PaintPlan detached = f.plan();
        f.domain.execute(() -> f.checked.set(true)); f.advance(1_000_000_000L);
        require(f.plan() == detached, "卸载后旧状态通道仍重建PaintPlan");
        f.domain.execute(() -> f.mount = f.context.mount(f.root)); f.session.frame();
        require(f.thumbX() == right, "重挂载继续旧进度而没有读取当前来源");
    }

    private void symbolText() {
        ReactiveDomain domain = ReactiveDomain.direct(); StateHost host = new StateHost();
        String full = "当前完整名称应随字体密度和可用空间更新并在截断后保留完整内容供读取";
        Signal<String>[] name = new Signal[1]; Signal<SceneTheme>[] theme = new Signal[1];
        Signal<Boolean>[] checked = new Signal[1]; Signal<SceneCheckBox.State>[] tri = new Signal[1]; Signal<String>[] selected = new Signal[1];
        SceneNode[] nodes = new SceneNode[3]; SceneRadioGroup<String>[] group = new SceneRadioGroup[1];
        ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            context.motionPolicy(() -> MotionPolicy.OFF);
            name[0] = domain.signal(full); theme[0] = domain.signal(SceneTheme.light()); checked[0] = domain.signal(false);
            tri[0] = domain.signal(SceneCheckBox.State.UNCHECKED); selected[0] = domain.signal("missing");
            nodes[0] = SceneCheckBox.create(context, tri[0], name[0], theme[0], value -> {}, SceneTextPolicy.WRAP)
                    .size(Size.fixed(160), Size.fixed(45)).padding(0);
            nodes[1] = SceneSwitch.create(context, checked[0], name[0], theme[0], value -> {}, SceneTextPolicy.ELLIPSIS_SINGLE_LINE)
                    .size(Size.fixed(160), Size.fixed(45)).padding(0);
            group[0] = SceneRadioGroup.create(context, () -> Collections.singletonList(new SceneRadioGroup.Option<>("only", name[0])),
                    selected[0], value -> {}, theme[0], SceneTextPolicy.ELLIPSIS_SINGLE_LINE);
            context.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).gap(10).add(nodes[0]).add(nodes[1])
                    .add(group[0].node().size(Size.fixed(160), Size.fixed(45))));
        }); onClose(page::close); page.frame();
        domain.execute(() -> nodes[2] = group[0].node().children().get(0));
        for (SceneNode node : nodes) {
            domain.execute(() -> {
                SceneGeometryView.Entry entry = page.pipeline().current().geometry.get(node);
                require(full.equals(entry.text.originalText) && entry.text.lines().stream()
                        .flatMap(line -> line.clusters().stream()).anyMatch(cluster -> cluster.logicalStart == cluster.logicalEnd
                                && cluster.sourceStart == cluster.sourceEnd && cluster.advance > 0), "带名符号没有共用全原文省略投影");
                require(entry.text.lines().stream().allMatch(line -> line.y + line.lineHeight <= entry.box.height), "符号名称绘制了残行");
            });
            LayoutBox box = box(page, node);
            page.pointer(ScenePointerEvent.move(box.x + 2, box.y + 2).at(host.now)); page.frame();
            domain.execute(() -> require(page.pipeline().current().paint.commands().stream().anyMatch(command ->
                    command.kind == PaintCommand.Kind.TEXT && full.equals(rendered(command.text))), "截断名称未经过唯一提示计划保留完整文案"));
        }
        int before = host.layouts;
        domain.execute(() -> { name[0].set("短名"); theme[0].set(theme[0].get().withDensity(SceneDensity.COMPACT)); }); page.frame();
        require(host.layouts > before, "名称/密度变化未走正式测量");
        domain.execute(() -> {
            for (SceneNode node : nodes) require("短名".equals(rendered(page.pipeline().current().geometry.get(node).text)), "动态短名称仍保留旧截断");
            require(page.pipeline().current().paint.commands().stream().noneMatch(command -> command.kind == PaintCommand.Kind.TEXT
                    && full.equals(rendered(command.text))), "名称已不截断仍保留旧提示");
        });
        int settled = host.layouts;
        domain.execute(() -> { checked[0].set(true); tri[0].set(SceneCheckBox.State.CHECKED); selected[0].set("only"); }); page.frame();
        require(host.layouts == settled, "只改选中状态重测了同族名称");
    }

    private void enabledAndCommand() {
        MotionFixture f = new MotionFixture(); onClose(f.session::close);
        f.domain.execute(() -> {
            SceneTheme current = f.theme.get();
            f.theme.set(current.withTextStyle(current.textStyle.withColor(current.disabledText))
                    .withMotion(MotionSpec.instant(), MotionSpec.instant()));
        }); f.session.frame();
        f.domain.execute(() -> require(hasColor(f.session, f.toggle, f.theme.get().focus), "正常文字与禁用文字同色时开关被误画为禁用"));
        f.domain.execute(() -> f.enabled.set(false)); f.session.frame();
        f.domain.execute(() -> require(!hasColor(f.session, f.toggle, f.theme.get().focus), "开关未读取祖先的真实禁用资格"));
        ReactiveDomain domain = ReactiveDomain.direct(); SemanticScenario.Host host = new SemanticScenario.Host();
        Signal<Boolean>[] allowed = new Signal[1]; SceneNode[] icon = new SceneNode[1]; int[] actions = {0};
        ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            context.motionPolicy(() -> MotionPolicy.OFF); allowed[0] = domain.signal(true);
            icon[0] = SceneIconButton.create(context, SceneIconButton.Symbol.STAR, () -> "命令图标", context.theme(),
                    SceneCommand.of(allowed[0], () -> actions[0]++)); context.mount(icon[0]);
        }); onClose(page::close); page.frame();
        page.semantic(SceneSemanticRequest.of(host.snapshot.entries.stream()
                .filter(entry -> "命令图标".equals(entry.values.name)).findFirst().get().id, SceneSemantics.Action.ACTIVATE)); page.frame();
        require(actions[0] == 1, "图标命令未使用原激活入口");
        domain.execute(() -> allowed[0].set(false)); page.frame();
        page.semantic(SceneSemanticRequest.of(host.snapshot.entries.stream()
                .filter(entry -> "命令图标".equals(entry.values.name)).findFirst().get().id, SceneSemantics.Action.ACTIVATE)); page.frame();
        domain.execute(() -> require(!icon[0].enabled() && actions[0] == 1, "图标命令资格和显示enabled发生分叉"));
    }

    /** 只替换现有字体边界和宿主时刻；控制/轨迹/绘制仍消费生产PageSession。 */
    private static final class StateHost implements SceneHost, SceneTextMeasurer {
        final MultilineEditingScenario.PlainHost font = new MultilineEditingScenario.PlainHost();
        long now = 1_000_000_000L; int layouts;
        public long frameNanos() { return now; }
        public int width() { return 400; }
        public int height() { return 400; }
        public long metricsRevision() { return 0; }
        public void replay(PaintPlan plan) { }
        public TextLayout layout(String text, TextStyle style, int width) { return layout(text, style, width, TextPolicy.NORMAL); }
        public TextLayout layout(String text, TextStyle style, int width, TextPolicy policy) {
            layouts++;
            return font.layout(text, style, policy.wrap == TextPolicy.Wrap.NONE ? Integer.MAX_VALUE : width, policy);
        }
    }
    private static final class MotionFixture {
        static final int INK = 0xff123456;
        final ReactiveDomain domain = ReactiveDomain.direct(); final StateHost host = new StateHost();
        final ScenePageSession session; ScenePageContext context; SceneNode root, check, toggle; SceneRadioGroup<String> radio;
        MountHandle mount; Signal<SceneCheckBox.State> tri; Signal<Boolean> checked, enabled; Signal<String> selected;
        Signal<SceneTheme> theme; Signal<MotionPolicy> policy;
        MotionFixture() { this(null); }
        MotionFixture(MotionSpec feedback) {
            // 局部覆盖在挂载前声明；动态验收只更新正式theme/policy/资格来源。
            session = ScenePageSession.open(domain, host, host, page -> {
                context = page; tri = domain.signal(SceneCheckBox.State.CHECKED); checked = domain.signal(true); selected = domain.signal("a");
                MotionSpec linear = MotionSpec.tween(200_000_000L, MotionSpec.LINEAR);
                theme = domain.signal(SceneTheme.light().withTextStyle(new TextStyle("fixture", 24, INK)).withMotion(linear, linear));
                policy = domain.signal(MotionPolicy.NORMAL); page.motionPolicy(policy); enabled = domain.signal(true);
                check = SceneCheckBox.create(page, tri, () -> "复选", theme, value -> {}, false).size(Size.fixed(100), Size.fixed(40)).padding(0);
                toggle = SceneSwitch.create(page, checked, () -> "开关", theme, value -> {}, false)
                        .size(Size.fixed(100), Size.fixed(40)).padding(0).feedbackMotion(feedback);
                radio = SceneRadioGroup.create(page, () -> Arrays.asList(new SceneRadioGroup.Option<>("a", () -> "A"), new SceneRadioGroup.Option<>("b", () -> "B")),
                        selected, value -> {}, theme);
                root = new SceneNode().size(Size.fill(1), Size.fill(1)).gap(10).add(check).add(toggle).add(radio.node())
                        .bind(NodeBinding.value(enabled, SceneNode::enabled));
                mount = page.mount(root);
            }); session.frame();
        }
        void advance(long elapsed) { host.now += elapsed; session.frame(); }
        PaintPlan plan() { PaintPlan[] result = new PaintPlan[1]; domain.execute(() -> result[0] = session.pipeline().current().paint); return result[0]; }
        long ink(SceneNode node) {
            long[] result = {0}; domain.execute(() -> {
                LayoutBox box = session.pipeline().current().geometry.get(node).box;
                for (PaintCommand command : session.pipeline().current().paint.commands()) if (command.kind == PaintCommand.Kind.RECT
                        && (command.color & 0xffffff) == (INK & 0xffffff)) {
                    LayoutBox visible = command.box.intersect(box); if (command.clip != null) visible = visible.intersect(command.clip);
                    result[0] += (long) visible.width * visible.height * (command.color >>> 24);
                }
            }); return result[0];
        }
        int thumbX() {
            int[] result = {Integer.MAX_VALUE}; domain.execute(() -> {
                LayoutBox box = session.pipeline().current().geometry.get(toggle).box;
                for (PaintCommand command : session.pipeline().current().paint.commands()) if (command.kind == PaintCommand.Kind.RECT
                        && (command.color & 0xffffff) == (INK & 0xffffff) && (command.color >>> 24) != 0) {
                    LayoutBox visible = command.box.intersect(box); if (command.clip != null) visible = visible.intersect(command.clip);
                    if (visible.width > 0 && visible.height > 0) result[0] = Math.min(result[0], visible.x);
                }
                if (result[0] == Integer.MAX_VALUE) throw new AssertionError("正式绘制计划缺少开关thumb");
            }); return result[0];
        }
    }
    private static LayoutBox box(ScenePageSession session, SceneNode node) {
        LayoutBox[] result = new LayoutBox[1]; session.domain().execute(() -> result[0] = session.pipeline().current().geometry.get(node).box); return result[0];
    }
    private static boolean hasColor(ScenePageSession session, SceneNode node, int color) {
        LayoutBox box = session.pipeline().current().geometry.get(node).box;
        return session.pipeline().current().paint.commands().stream().anyMatch(command -> command.kind == PaintCommand.Kind.RECT
                && command.color == color && command.box.intersect(box).width > 0 && command.box.intersect(box).height > 0);
    }
    private static String rendered(TextLayout text) {
        StringBuilder result = new StringBuilder(); for (TextLayout.Line line : text.lines()) result.append(line.text); return result.toString();
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
