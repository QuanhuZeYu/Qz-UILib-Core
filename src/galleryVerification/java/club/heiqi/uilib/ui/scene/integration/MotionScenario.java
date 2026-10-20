package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneSortable;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 解析求解互证与真实挂载生命周期，不依赖墙钟 sleep。 */
public final class MotionScenario extends TestApplication {
    protected void runTests() throws Exception {
        scenario("解析弹簧同时间、不同分帧及三种阻尼", this::spring);
        scenario("有限反向缩时与显式惯性终点", this::finite);
        scenario("声明目标、动态策略、重复准备及重挂", this::binding);
        scenario("减少模式保留短平稳位移而关闭立即落定", this::reducedSpatial);
        scenario("减少模式约束短自定义反馈曲线单调", this::reducedFeedback);
        scenario("显式弹性边缘有阻力且放下提交合法坐标", this::elasticDrag);
        scenario("正式颜色与透明度通道", this::properties);
        scenario("标准控件默认过渡、局部覆盖与纯绘制失效", this::standardFeedback);
        scenario("自然尺寸真实分配和位置重排途中命中", this::layout);
        scenario("显式退场立即结束输入及显隐反向", this::presence);
        scenario("拥有集合退场预算、同键新身份与页面释放", this::ownedExits);
        scenario("途中减少动效同步已删除条目的平稳退场", this::reducedOwnedExit);
        scenario("拥有集合退场跟随策略来源而不复活身份", this::ownedExitStrategy);
        scenario("已有结构退场按当前预算与期限释放", this::ownedExitLimits);
        scenario("所属容器移动后退场消费当前祖先而非旧归属", this::movedExit);
        scenario("退场片段跟随父视口回弹的当前空间", this::presenceBounce);
        scenario("弹性排序完全落定后往返滚动不复活旧反馈", () -> sortableScroll(true));
        scenario("弹性排序落下中滚动父容器立即同步且不重启位移", () -> sortableScroll(false));
        scenario("排序同批真实采集速度与输入门内持续呈现", this::sortableSampling);
        scenario("排序取消与卸载释放呈现且不提交", this::sortableCancellation);
        scenario("排序UP后曲线失败清空所属呈现且保留原异常", this::sortableFailure);
        scenario("拖拽释放保留位置和速度且单次意图", this::dragRelease);
        scenario("停手释放不甩出且碰壁后立即回槽", this::dragReleaseFeedback);
        scenario("碰壁按实际时刻推进且预览沿视口回槽", this::boundedRelease);
        scenario("滚动祖先优先、合法偏移与显式惯性", this::scrollMotion);
        scenario("滚动减少模式保留单调回弹而关闭立即落定", this::reducedScroll);
        scenario("退场滚动与窗口扩张消费当前裁剪", this::presenceClip);
        scenario("主绘与嵌套退场候选只采集一次局部片段", PresencePaintCollectionScenario::verify);
        scenario("撤销回弹立即重排与旧滚动样本隔离", this::scrollCancellation);
        scenario("回槽途中键盘接管与取消", this::keyboardTakeover);
        scenario("自身裁剪外盒与退场错误清理", this::presenceOwnClip);
        scenario("无效目标结束轨迹后可卸载重挂", this::failedMount);
        scenario("退场所属层继续逃离来源局部裁剪", this::presenceLayer);
        scenario("纯位置重排复用度量且内容与视口变更失效", this::measureReuse);
        scenario("排列信号512种约束只保留当前测量工作集", this::measureWorkingSet);
        scenario("父测量命中保留子事实且纯绘制读取已定位约束", this::positionedMeasureReuse);
    }
    private void spring() {
        for (double damping : new double[] {.65, 1, 1.7}) {
            MotionSpec spec = MotionSpec.spring(.3, damping);
            MotionValue one = new MotionValue(), many = new MotionValue();
            one.snap(0, 0); many.snap(0, 0);
            one.retarget(100, spec, 0); many.retarget(100, spec, 0);
            one.advance(173_000_000L);
            for (long now = 1_000_000L; now <= 173_000_000L; now += 1_000_000L) many.advance(now);
            near(one.value(), many.value(), 1e-10, "分帧改变位置");
            near(one.velocity(), many.velocity(), 1e-10, "分帧改变速度");
            double x = one.value(), v = one.velocity();
            one.retarget(-30, MotionSpec.spring(.24, .8), 173_000_000L);
            near(one.value(), x, 1e-12, "反向位置跳变"); near(one.velocity(), v, 1e-12, "反向速度清零");
            one.advance(60_000_000_000L);
            require(one.value() == -30 && one.velocity() == 0 && !one.active(), "长间隔未精确停止");
        }
        // 临界阻尼的独立闭式：x(t)=1-(1+omega*t)*exp(-omega*t)。
        MotionValue critical = new MotionValue(); MotionSpec spec = MotionSpec.spring(.3, 1);
        critical.snap(0, 0); critical.retarget(1, spec, 0); critical.advance(100_000_000L);
        double t = .1, omega = 2 * Math.PI / .3;
        near(critical.value(), 1 - (1 + omega * t) * Math.exp(-omega * t), 1e-12, "临界解错误");
    }
    private void finite() {
        MotionValue value = new MotionValue(); MotionSpec linear = MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR);
        value.snap(0, 0); value.retarget(1, linear, 0); value.advance(400_000_000L);
        near(value.value(), .4, 1e-12, "有限目标");
        value.retarget(0, linear, 400_000_000L); value.advance(600_000_000L);
        near(value.value(), .2, 1e-12, "反向未缩时"); value.advance(800_000_000L);
        require(value.value() == 0 && !value.active(), "反向未精确落点");
        for (boolean middle : new boolean[] {true, false}) {
            MotionValue completed = new MotionValue(); completed.snap(0, 0); completed.retarget(1, linear, 0);
            if (middle) completed.advance(400_000_000L);
            completed.advance(1_000_000_000L); completed.retarget(0, linear, 1_000_000_000L); completed.advance(1_200_000_000L);
            near(completed.value(), .8, 1e-12, "完成后反向未恢复完整时长 middle=" + middle);
        }
        value.decay(20, 300, MotionSpec.decay(6), 1_000_000_000L);
        near(value.target(), 70, 1e-12, "惯性终点"); value.advance(1_100_000_000L);
        near(value.value(), 70 - 50 * Math.exp(-.6), 1e-10, "惯性经过时间");
    }
    private void binding() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            SceneRuntime runtime = new SceneRuntime(domain);
            Signal<Float> target = domain.signal(0f); Signal<MotionPolicy> policy = domain.signal(MotionPolicy.NORMAL);
            runtime.motionPolicy(policy);
            float[] shown = {0}; int[] calls = {0};
            SceneNode node = new SceneNode().bind(NodeBinding.motion(target, () -> MotionSpec.spring(.3, .8),
                    (n, value) -> { shown[0] = value; calls[0]++; }));
            MountHandle mount = runtime.attach(null, node); runtime.preparePresentation(1_000_000_000L);
            target.set(100f); runtime.preparePresentation(1_000_000_000L); runtime.preparePresentation(1_050_000_000L);
            require(shown[0] > 0 && shown[0] < 100, "挂载声明没有推进弹簧");
            float current = shown[0]; int count = calls[0]; runtime.preparePresentation(1_050_000_000L);
            require(shown[0] == current && calls[0] == count, "重复准备推进或重发映射");
            policy.set(MotionPolicy.OFF); runtime.preparePresentation(1_050_000_000L);
            require(shown[0] == 100, "关闭未落到目标"); mount.detach();
            target.set(30f); runtime.preparePresentation(2_000_000_000L);
            require(shown[0] == 100, "卸载后仍更新");
            runtime.attach(null, node); runtime.preparePresentation(2_000_000_000L);
            require(shown[0] == 30, "重挂未直接消费当前目标"); runtime.close(); node.destroy();
        });
    }
    private void reducedSpatial() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            SceneRuntime runtime = new SceneRuntime(domain); Signal<MotionPolicy> policy = domain.signal(MotionPolicy.REDUCED);
            Signal<Float> target = domain.signal(0f); runtime.motionPolicy(policy);
            SceneNode node = new SceneNode().bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.translateX(target, () -> MotionSpec.spring(.42, .68)));
            runtime.attach(null, node); runtime.preparePresentation(0); target.set(100f); runtime.preparePresentation(0);
            float previous = node.translationX();
            for (long t = 20; t <= 160; t += 20) {
                runtime.preparePresentation(t * 1_000_000L); float next = node.translationX();
                require(next >= previous && next <= 100, "减少模式出现往返或超调");
                if (t == 80) require(next > 0 && next < 100, "减少模式仍等于立即关闭");
                previous = next;
            }
            require(node.translationX() == 100, "减少模式未及时落目标");
            policy.set(MotionPolicy.OFF); target.set(0f); runtime.preparePresentation(160_000_000L);
            require(node.translationX() == 0, "关闭未立即落定"); runtime.close(); node.destroy();
        });
    }
    private void reducedFeedback() {
        MotionSpec requested = MotionSpec.tween(100_000_000L, t -> t + .15 * Math.sin(4 * Math.PI * t));
        require(MotionPolicy.NORMAL.resolve(requested, false) == requested, "正常模式覆盖了局部曲线");
        MotionValue value = new MotionValue(); value.snap(0, 0);
        value.retarget(1, MotionPolicy.REDUCED.resolve(requested, false), 0);
        double previous = 0;
        for (long now = 12_500_000L; now <= 100_000_000L; now += 12_500_000L) {
            value.advance(now);
            require(value.value() >= previous && value.value() <= 1, "减少模式保留了往返曲线：" + previous + "→" + value.value());
            previous = value.value();
        }
        require(previous == 1 && !value.active(), "短反馈没有保留有限结束");
    }
    private void elasticDrag() {
        club.heiqi.uilib.ui.scene.motion.ElasticOptions elastic = new club.heiqi.uilib.ui.scene.motion.ElasticOptions(32, MotionSpec.spring(.42, .68));
        double a = elastic.displacement(40, MotionPolicy.NORMAL), b = elastic.displacement(80, MotionPolicy.NORMAL), c = elastic.displacement(120, MotionPolicy.NORMAL);
        require(a > 0 && b > a && c > b && c - b < b - a && b - a < a, "越界阻力没有逐渐增大");
        require(elastic.displacement(120, MotionPolicy.REDUCED) > 0 && elastic.displacement(120, MotionPolicy.REDUCED) < c, "减少模式形变没有缩小");
        require(elastic.displacement(120, MotionPolicy.OFF) == 0, "关闭仍有形变");
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 300; host.height = 180;
        SceneNode[] content = {null}; int[] drops = {0}, submitted = {-1}; long[] released = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            content[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            SceneNode drag = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content[0], club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                    new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().bounds(club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds.PARENT).elastic(elastic).autoScroll(false)
                            .onDrop(value -> { drops[0]++; submitted[0] = value.deltaX; released[0] = context.runtime().inputNanos(); }));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(80)).add(drag));
        }));
        page.frame(); long input = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(input));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(175, 5).at(input + 50_000_000L)); page.frame();
        int[] stretched = {0}; domain.execute(() -> { stretched[0] = page.pipeline().current().geometry.get(content[0]).box.x; require(stretched[0] > 40 && stretched[0] < 72, "拖中没有有限弹性越界"); });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 175, 5).at(input + 150_000_000L)); page.frame();
        domain.execute(() -> {
            require(drops[0] == 1 && submitted[0] == 40, "弹性视觉位移污染合法业务落点");
            page.runtime().preparePresentation(released[0] + 50_000_000L);
            int middle = page.pipeline().current().geometry.get(content[0]).box.x;
            require(middle > 0 && middle < stretched[0], "松手未从拉伸位置自然回弹");
            page.runtime().preparePresentation(released[0] + 2_000_000_000L);
            require(page.pipeline().current().geometry.get(content[0]).box.x == 0 && drops[0] == 1, "回弹未停止或重复提交");
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(175, 5)); page.frame();
        int[] beforeGrab = {0}; domain.execute(() -> beforeGrab[0] = page.pipeline().current().geometry.get(content[0]).box.x);
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 175, 5)); page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, beforeGrab[0] + 5, 5)); page.frame();
        domain.execute(() -> near(page.pipeline().current().geometry.get(content[0]).box.x, beforeGrab[0], 1, "软边缘再次按下重复压缩可见位移"));
    }
    private void properties() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            SceneRuntime runtime = new SceneRuntime(domain);
            Signal<Integer> color = domain.signal(0x00ff0000);
            MotionSpec linear = MotionSpec.tween(100_000_000L, MotionSpec.LINEAR);
            SceneNode node = new SceneNode().bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.background(color, () -> linear));
            runtime.attach(null, node); runtime.preparePresentation(0);
            color.set(0xff0000ff); runtime.preparePresentation(0); runtime.preparePresentation(50_000_000L);
            require(node.background() == 0x800000ff, "透明端点污染预乘颜色: " + Integer.toHexString(node.background()));
            runtime.preparePresentation(100_000_000L);
            require(node.background() == 0xff0000ff, "颜色未精确落端点");
            runtime.close(); node.destroy();
        });
    }
    private void standardFeedback() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] button = {null}; Signal<MotionSpec>[] local = new Signal[1];
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            local[0] = domain.signal(null);
            button[0] = club.heiqi.uilib.ui.scene.control.SceneButton.create(context, () -> "", context.theme(), () -> {})
                    .size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(40))
                    .bind(NodeBinding.value(local[0], SceneNode::feedbackMotion));
            context.mountOwned(button[0]);
        }));
        page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(5, 5)); page.frame();
        domain.execute(() -> {
            long start = page.runtime().inputNanos();
            club.heiqi.uilib.ui.scene.layout.SceneGeometry before = page.pipeline().current().geometry;
            page.runtime().preparePresentation(start + 70_000_000L);
            club.heiqi.uilib.ui.scene.runtime.ScenePresentation middle = page.pipeline().current();
            int color = middle.paint.commands().get(0).color;
            club.heiqi.uilib.ui.scene.control.SceneTheme theme = club.heiqi.uilib.ui.scene.control.SceneTheme.light();
            require(color != theme.button && color != theme.hover, "默认未产生中间颜色");
            require(middle.geometry == before, "仅颜色运动重建了几何");
            page.runtime().preparePresentation(start + 140_000_000L);
            require(page.pipeline().current().paint.commands().get(0).color == theme.hover, "默认未精确落到悬停色");
            local[0].set(MotionSpec.instant());
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5)); page.frame();
        domain.execute(() -> require(page.pipeline().current().paint.commands().get(0).color
                == club.heiqi.uilib.ui.scene.control.SceneTheme.light().pressed, "局部即时覆盖未生效"));
    }
    private void measureReuse() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<Integer>[] content = new Signal[1], position = new Signal[1]; int[] measured = {0}; SceneNode[] item = {null};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            content[0] = domain.signal(20); position[0] = domain.signal(0);
            item[0] = new SceneNode().control(new club.heiqi.uilib.ui.scene.control.SceneControl() {
                public java.util.List<club.heiqi.uilib.ui.reactive.ReadableSignal<?>> measureDependencies(SceneNode node) { return java.util.Collections.singletonList(content[0]); }
                public club.heiqi.uilib.ui.scene.layout.MeasuredSize measure(SceneNode n, club.heiqi.uilib.ui.scene.layout.Constraints c, club.heiqi.uilib.ui.scene.text.SceneTextMeasurer t) { measured[0]++; return new club.heiqi.uilib.ui.scene.layout.MeasuredSize(80, content[0].get()); }
                public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode n, club.heiqi.uilib.ui.scene.layout.LayoutBox b, club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer c) { return new club.heiqi.uilib.ui.scene.layout.ChildPlacements(java.util.Collections.emptyList()); }
                public void paint(SceneNode n, club.heiqi.uilib.ui.scene.layout.SceneGeometryView g, club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder p) {}
            }).bind(NodeBinding.value(position[0], (node, value) -> node.translation(0, value)));
            context.mountOwned(item[0]);
        }));
        page.frame(); int initial = measured[0];
        for (int i = 1; i <= 8; i++) { final int next = i; domain.execute(() -> position[0].set(next)); page.frame(); }
        require(measured[0] == initial, "纯位置变化重复测量内容：" + initial + "→" + measured[0]);
        domain.execute(() -> content[0].set(45)); page.frame();
        domain.execute(() -> require(page.pipeline().current().geometry.get(item[0]).layoutBox.height == 45, "内容变化未结束缓存"));
        require(measured[0] > initial, "测量依赖改变仍使用旧度量");
        int beforeResize = measured[0]; host.width = 360; page.frame();
        require(measured[0] > beforeResize, "视口改变没有重新测量");
    }
    private void measureWorkingSet() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = host.height = 1000;
        Signal<Integer>[] width = new Signal[1], position = new Signal[1];
        int[] parentMeasures = {0}, childMeasures = {0}; SceneNode[] child = new SceneNode[1];
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            width[0] = domain.signal(1); position[0] = domain.signal(0);
            child[0] = new SceneNode().control(measuredLeaf(childMeasures));
            context.mountOwned(new SceneNode().size(Size.fixed(1000), Size.fixed(1000)).add(child[0]).control(new SceneControl() {
                public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Arrays.asList(width[0], position[0]); }
                public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
                    parentMeasures[0]++; return new MeasuredSize(1000, 1000);
                }
                public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
                    MeasuredSize measured = children.measureChild(child[0], new Constraints(width[0].get(), 20));
                    return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(child[0],
                            new LayoutBox(0, position[0].get(), measured.width, measured.height))));
                }
                public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
            }));
        }));
        int maximum = 0;
        for (int value = 1; value <= 512; value++) {
            final int next = value; domain.execute(() -> width[0].set(next)); page.frame();
            domain.execute(() -> require(page.pipeline().current().geometry.get(child[0]).layoutBox.width == next,
                    "排列没有消费当前子约束：" + next));
            maximum = Math.max(maximum, measurementCount(page, child[0]));
        }
        require(maximum == 1, "测量缓存累积了排列历史约束：" + maximum);
        int before = childMeasures[0]; domain.execute(() -> position[0].set(1)); page.frame();
        require(parentMeasures[0] == 1 && childMeasures[0] == before, "稳定约束的跨批内容度量未被复用");
        Object dependencies = field(field(page.pipeline(), "layout"), "measureDependencies");
        page.close();
        require(((Map<?, ?>) field(dependencies, "stamps")).isEmpty(), "页面结束仍持有测量约束");
    }
    private void positionedMeasureReuse() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = host.height = 1000;
        Signal<Integer>[] position = new Signal[1], color = new Signal[1];
        SceneNode[] parent = new SceneNode[1], measuredOnly = new SceneNode[1], inline = new SceneNode[1];
        int[] parentMeasures = {0}, childMeasures = {0}, arrangements = {0};
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            position[0] = domain.signal(0); color[0] = domain.signal(0xffffffff);
            measuredOnly[0] = new SceneNode().size(Size.fixed(37), Size.fixed(20)).control(measuredLeaf(childMeasures));
            inline[0] = new SceneNode().size(Size.fixed(41), Size.fixed(20)).control(measuredLeaf(childMeasures));
            parent[0] = new SceneNode().size(Size.fixed(1000), Size.fixed(1000)).add(measuredOnly[0]).add(inline[0])
                    .bind(NodeBinding.value(position[0], (node, value) -> node.translation(0, value)))
                    .bind(NodeBinding.value(color[0], (node, value) -> node.style(node.style().withColor(value))))
                    .control(new SceneControl() {
                public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) { return new MeasuredSize(1000, 1000); }
                public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text, SceneChildMeasurer children) {
                    parentMeasures[0]++;
                    children.measureChild(measuredOnly[0], new Constraints(37, 20));
                    children.measureChild(inline[0], new Constraints(41, 20));
                    return new MeasuredSize(1000, 1000);
                }
                public TextLayout layoutText(SceneNode node, SceneTextMeasurer text, int width, SceneChildMeasurer children) {
                    MeasuredSize embedded = children.measureChild(inline[0], new Constraints(41, 20));
                    return text.layout(embedded.width + ":" + embedded.height, node.style(), width);
                }
                public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
                    arrangements[0]++;
                    return new ChildPlacements(Arrays.asList(new ChildPlacements.Entry(measuredOnly[0], new LayoutBox(0, 0, 37, 20)),
                            new ChildPlacements.Entry(inline[0], new LayoutBox(37, 0, 41, 20))));
                }
                public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
            });
            context.mountOwned(parent[0]);
        }));
        page.frame();
        for (int value = 1; value <= 8; value++) { final int next = value; domain.execute(() -> position[0].set(next)); page.frame(); }
        require(parentMeasures[0] == 1 && childMeasures[0] == 2, "父命中后重排重复测量子内容");
        require(measurementCount(page, measuredOnly[0]) == 1, "父命中省略的有效子度量被当作历史清除");
        int before = arrangements[0]; domain.execute(() -> color[0].set(0xff123456)); page.frame();
        domain.execute(() -> require(page.pipeline().current().geometry.get(parent[0]).text.style.color == 0xff123456,
                "纯绘制投影没有读取当前样式与已定位子约束"));
        require(parentMeasures[0] == 1 && childMeasures[0] == 2 && arrangements[0] == before,
                "纯绘制投影触发了测量或重新排列");
    }
    private static SceneControl measuredLeaf(int[] measures) {
        return new SceneControl() {
            public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) {
                measures[0]++; return new MeasuredSize(constraints.availableWidth, 20);
            }
            public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
                return new ChildPlacements(Collections.emptyList());
            }
            public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
        };
    }
    // 反射只观察合法生产调用形成的容量与释放，不注入缓存，也不改变求解路径。
    private static int measurementCount(ScenePageSession page, SceneNode node) throws Exception {
        Object layout = field(page.pipeline(), "layout"), dependencies = field(layout, "measureDependencies");
        Object stamp = ((Map<?, ?>) field(dependencies, "stamps")).get(node);
        return stamp == null ? 0 : ((Map<?, ?>) field(stamp, "measurements")).size();
    }
    private static Object field(Object owner, String name) throws Exception {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private void layout() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] pane = {null}, neighbor = {null}, a = {null}, b = {null};
        Signal<Integer>[] height = new Signal[1]; Signal<java.util.List<SceneNode>>[] order = new Signal[1];
        int[] clicks = {0}; MotionSpec spring = MotionSpec.spring(.4, 1);
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            height[0] = domain.signal(40);
            pane[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.natural())
                    .clip(true).bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.layoutSize(() -> spring))
                    .add(new SceneNode().bind(NodeBinding.value(height[0], (node, h) -> node.size(
                            club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(h)))));
            neighbor[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(20));
            a[0] = new SceneNode().background(0xff112233).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(30)).onClick(() -> clicks[0]++);
            b[0] = new SceneNode().background(0xff445566).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(40), club.heiqi.uilib.ui.scene.layout.Size.fixed(30));
            order[0] = domain.signal(java.util.Arrays.asList(a[0], b[0]));
            SceneNode list = new SceneNode().bind(NodeBinding.children(order[0])).bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.layoutPositions(() -> spring));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.natural())
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.layoutPositions(() -> spring)).add(pane[0]).add(neighbor[0]).add(list));
        }));
        page.frame();
        domain.execute(() -> { height[0].set(140); order[0].set(java.util.Arrays.asList(b[0], a[0])); });
        page.frame();
        int[] clickY = {0};
        domain.execute(() -> {
            long start = page.runtime().inputNanos();
            page.runtime().preparePresentation(start + 80_000_000L);
            club.heiqi.uilib.ui.scene.layout.SceneGeometry geometry = page.pipeline().current().geometry;
            int h = geometry.get(pane[0]).box.height;
            require(h > 40 && h < 140, "自然尺寸没有中间分配: " + h);
            require(geometry.get(neighbor[0]).box.y == geometry.get(pane[0]).box.y + h,
                    "邻项未随当前实际空间排列");
            int localA = geometry.get(a[0]).box.y - geometry.get(a[0].parent()).box.y;
            require(localA > 0 && localA < 30, "重排未从当前局部位置续接: " + localA);
            clickY[0] = geometry.get(a[0]).box.y + 5;
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.syntheticActivation(90, clickY[0])); page.frame();
        require(clicks[0] == 1, "可见中间位置未通过正式路由命中");
        domain.execute(() -> {
            page.runtime().preparePresentation(page.runtime().inputNanos() + 2_000_000_000L);
            club.heiqi.uilib.ui.scene.runtime.ScenePresentation settled = page.pipeline().current();
            require(settled.geometry.get(pane[0]).box.height == 140, "尺寸未落自然目标");
            require(page.pipeline().current() == settled, "静止没有复用呈现");
        });
    }
    private void presence() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<Boolean>[] visible = new Signal[1]; SceneNode[] node = {null}; int[] clicks = {0};
        club.heiqi.uilib.ui.scene.motion.PresenceOptions options = new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(200_000_000L, MotionSpec.LINEAR));
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            visible[0] = domain.signal(true);
            node[0] = new SceneNode().focusable(true).modal(true).background(0xff123456)
                    .size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                    .onClick(() -> clicks[0]++).bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.presence(visible[0], () -> options));
            context.mountOwned(new SceneNode().add(node[0]));
        }));
        page.frame(); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5)); page.frame();
        domain.execute(() -> visible[0].set(false)); page.frame();
        domain.execute(() -> {
            require(page.pipeline().current().geometry.activeModal() == null && page.runtime().focusRouter().focused().get() == null,
                    "退出仍持有模态或焦点");
            require(page.pipeline().current().geometry.get(node[0]) == null, "退场仍发布输入几何");
            long now = page.runtime().inputNanos(); page.runtime().preparePresentation(now + 80_000_000L);
            club.heiqi.uilib.ui.scene.paint.PaintPlan plan = page.pipeline().current().paint;
            require(plan.commands().stream().anyMatch(c -> c.color == 0x99123456), "退场未保留当前被动画面");
            visible[0].set(true); page.runtime().preparePresentation(now + 80_000_000L);
            near(page.runtime().presenceOpacity(node[0]), .6, 1e-6, "反向未沿当前透明度");
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 5, 5)); page.frame();
        require(clicks[0] == 0, "显隐反向复活了旧按压");
    }
    private void ownedExits() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<java.util.List<String>>[] items = new Signal[1]; SceneNode[] container = {null};
        java.util.List<SceneNode> built = new java.util.ArrayList<>(); int[] released = {0};
        club.heiqi.uilib.ui.scene.motion.PresenceOptions options = new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(500_000_000L, MotionSpec.LINEAR)).budget(1);
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            items[0] = domain.signal(java.util.Arrays.asList("a", "b"));
            container[0] = new SceneNode().bind(NodeBinding.repeat(items[0], value -> value, value -> {
                SceneNode child = new SceneNode().background(0xff224466).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(30)).own(() -> released[0]++);
                built.add(child); return child;
            }, () -> options));
            context.mountOwned(container[0]);
        }));
        page.frame(); domain.execute(() -> items[0].set(java.util.Collections.singletonList("b"))); page.frame();
        domain.execute(() -> {
            require(built.get(0).runtime() == null && built.get(0).retired() && !built.get(0).destroyed(), "退出身份或保留资源错误");
            items[0].set(java.util.Arrays.asList("a", "b"));
        }); page.frame();
        require(built.size() == 3 && !built.get(0).id().equals(built.get(2).id()), "同键返回复活原身份");
        domain.execute(() -> items[0].set(java.util.Collections.singletonList("b"))); page.frame();
        require(built.get(0).destroyed() && released[0] == 1, "同键较旧代未逐出");
        domain.execute(() -> items[0].set(java.util.Collections.emptyList())); page.frame();
        require(built.get(2).destroyed() && released[0] == 2, "退场预算未逐出最早记录");
        page.close(); require(released[0] == 3, "页面结束没有释放最后保留资源");
    }
    private void reducedOwnedExit() {
        for (boolean prepared : new boolean[] {false, true}) {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<java.util.List<String>>[] items = new Signal[1]; Signal<MotionPolicy>[] policy = new Signal[1];
        SceneNode[] row = {null}; int[] released = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            items[0] = domain.signal(java.util.Collections.singletonList("条目")); policy[0] = domain.signal(MotionPolicy.NORMAL); context.motionPolicy(policy[0]);
            context.mountOwned(new SceneNode().bind(NodeBinding.repeat(items[0], item -> item, item -> {
                row[0] = new SceneNode().background(0xff182736).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(40)).own(() -> released[0]++);
                return row[0];
            }, () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.spring(.58, .52)))));
        }));
        page.frame(); domain.execute(() -> items[0].set(java.util.Collections.emptyList())); page.frame();
        domain.execute(() -> {
            long now = page.runtime().inputNanos();
            if (prepared) { page.runtime().preparePresentation(now + 80_000_000L); page.pipeline().current(); }
            policy[0].set(MotionPolicy.REDUCED); page.runtime().preparePresentation(now + 80_000_000L);
            MotionValue reference = new MotionValue(); reference.snap(1, 0); reference.retarget(0, MotionSpec.spring(.58, .52), 0); reference.advance(80_000_000L);
            int expected = Math.round((float) reference.value() * 255);
            int current = page.pipeline().current().paint.commands().stream().filter(command -> (command.color & 0xffffff) == 0x182736)
                    .mapToInt(command -> command.color >>> 24).max().orElse(0);
            require(Math.abs(current - expected) <= 1, "减少策略回溯到上帧启动: current=" + current + " expected=" + expected);
            int previous = 255;
            for (long time = 80; time <= 700; time += 20) {
                page.runtime().preparePresentation(now + time * 1_000_000L);
                int alpha = page.pipeline().current().paint.commands().stream().filter(command -> (command.color & 0xffffff) == 0x182736)
                        .mapToInt(command -> command.color >>> 24).max().orElse(0);
                require(alpha <= previous, "减少动效后退场回闪: " + previous + "→" + alpha);
                previous = alpha;
                if (time == 200) require(row[0].destroyed() && released[0] == 1, "已删除条目仍运行原弹簧退出");
            }
        });
        }
    }
    private void ownedExitStrategy() {
        for (boolean repeat : new boolean[] {true, false}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
            Signal<Boolean>[] present = new Signal[1];
            Signal<club.heiqi.uilib.ui.scene.motion.PresenceOptions>[] options = new Signal[1];
            SceneNode[] row = {null}; int[] released = {0};
            club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
                present[0] = domain.signal(true);
                options[0] = domain.signal(new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR)));
                java.util.function.Supplier<SceneNode> build = () -> {
                    SceneNode child = new SceneNode().background(0xff13579b).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(30)).own(() -> released[0]++);
                    row[0] = child; return child;
                };
                NodeBinding children = repeat ? NodeBinding.repeat(
                        () -> present[0].get() ? java.util.Collections.singletonList("a") : java.util.Collections.emptyList(),
                        key -> key, item -> build.get(), options[0]) : NodeBinding.ownedChildren(
                        () -> present[0].get() ? java.util.Collections.singletonList(row[0]) : java.util.Collections.emptyList(), options[0]);
                if (!repeat) build.get();
                context.mountOwned(new SceneNode().bind(children));
            }));
            page.frame(); domain.execute(() -> present[0].set(false)); page.frame();
            domain.execute(() -> {
                SceneRuntime runtime = page.runtime(); long now = runtime.inputNanos();
                runtime.preparePresentation(now + 80_000_000L); page.pipeline().current();
                require(row[0].retired() && !row[0].destroyed() && row[0].runtime() == null, "未进入被动退场");
                options[0].set(new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.instant()));
                runtime.preparePresentation(now + 80_000_000L);
                require(row[0].destroyed() && released[0] == 1, "已有退场忽略策略来源：repeat=" + repeat);
                require(page.pipeline().current().paint.commands().stream().noneMatch(c -> (c.color & 0xffffff) == 0x13579b), "即时策略仍保留旧退场片段");
            });
            page.close(); require(released[0] == 1, "页面关闭重复释放退出资源");
        }
    }
    private void ownedExitLimits() {
        for (boolean repeat : new boolean[] {true, false}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.height = 200;
            Signal<Boolean>[] present = new Signal[1];
            Signal<club.heiqi.uilib.ui.scene.motion.PresenceOptions>[] options = new Signal[1];
            java.util.List<SceneNode> rows = new java.util.ArrayList<>(); int[] released = {0};
            club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
                present[0] = domain.signal(true);
                options[0] = domain.signal(new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR)).budget(3));
                java.util.function.Supplier<SceneNode> build = () -> {
                    SceneNode child = new SceneNode().background(0xff2468ac).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(30)).own(() -> released[0]++);
                    rows.add(child); return child;
                };
                NodeBinding children;
                if (repeat) children = NodeBinding.repeat(() -> present[0].get() ? java.util.Arrays.asList("a", "b", "c") : java.util.Collections.emptyList(), key -> key, item -> build.get(), options[0]);
                else {
                    build.get(); build.get(); build.get();
                    children = NodeBinding.ownedChildren(() -> present[0].get() ? rows : java.util.Collections.emptyList(), options[0]);
                }
                context.mountOwned(new SceneNode().bind(children));
            }));
            page.frame(); domain.execute(() -> present[0].set(false)); page.frame();
            domain.execute(() -> {
                SceneRuntime runtime = page.runtime(); long now = runtime.inputNanos();
                runtime.preparePresentation(now + 80_000_000L); page.pipeline().current();
                require(released[0] == 0, "初始预算未保留退场资源");
                options[0].set(options[0].get().budget(1)); runtime.preparePresentation(now + 80_000_000L);
                require(rows.get(0).destroyed() && rows.get(1).destroyed() && !rows.get(2).destroyed() && released[0] == 2,
                        "已有退场未按新预算逐出最早记录：repeat=" + repeat + "，released=" + released[0]);
                options[0].set(options[0].get().maximumExit(50_000_000L)); runtime.preparePresentation(now + 80_000_000L);
                require(rows.get(2).destroyed() && released[0] == 3, "缩短退出期限没有按最初退出时刻清理");
            });
            page.close(); require(released[0] == 3, "预算变更后重复释放退出资源");
        }
    }
    private void movedExit() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.height = 200;
        Signal<Boolean>[] present = new Signal[1], keepFormer = new Signal[1], keepCurrent = new Signal[1];
        Signal<SceneNode>[] destination = new Signal[1];
        SceneNode[] former = {null}, current = {null}, container = {null}, row = {null}; int[] released = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            keepCurrent[0] = domain.signal(true);
            former[0] = new SceneNode().opacity(.8f); current[0] = new SceneNode().opacity(.2f);
            destination[0] = domain.signal(former[0]); present[0] = domain.signal(true); keepFormer[0] = domain.signal(true);
            container[0] = new SceneNode().bind(NodeBinding.move(destination[0])).bind(NodeBinding.repeat(
                    () -> present[0].get() ? java.util.Collections.singletonList("a") : java.util.Collections.emptyList(), key -> key, item -> {
                        row[0] = new SceneNode().background(0xff3579bd).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(30)).own(() -> released[0]++);
                        return row[0];
                    }, () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR))));
            former[0].add(container[0]);
            context.mountOwned(new SceneNode().bind(NodeBinding.ownedChildren(() -> {
                java.util.List<SceneNode> children = new java.util.ArrayList<>();
                if (keepFormer[0].get()) children.add(former[0]);
                if (keepCurrent[0].get()) children.add(current[0]);
                return children;
            })));
        }));
        page.frame(); domain.execute(() -> present[0].set(false)); page.frame();
        domain.execute(() -> {
            destination[0].set(current[0]);
            page.runtime().preparePresentation(page.runtime().inputNanos());
            int alpha = page.pipeline().current().paint.commands().stream().filter(c -> (c.color & 0xffffff) == 0x3579bd).mapToInt(c -> c.color >>> 24).max().orElse(0);
            require(alpha > 0 && alpha <= Math.round(.2f * 255), "移动后退场仍消费原祖先透明度：alpha=" + alpha);
            keepFormer[0].set(false); page.pipeline().current();
            require(former[0].destroyed() && !container[0].destroyed() && container[0].parent() == current[0], "未完成正式同页转移");
            require(!row[0].destroyed() && released[0] == 0, "结束旧祖先错误释放已迁移作用域的退场");
            keepCurrent[0].set(false); page.runtime().preparePresentation(page.runtime().inputNanos() + 20_000_000L); page.pipeline().current();
            require(row[0].destroyed() && released[0] == 1, "当前祖先结束没有释放退场资源");
        });
    }
    private void dragRelease() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] content = {null}, drag = {null}; int[] dropped = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            content[0] = new SceneNode().background(0xff887766).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            drag[0] = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content[0],
                    club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                    new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().autoScroll(false)
                            .releaseMotion(MotionSpec.spring(.35, .8)).onDrop(value -> dropped[0]++));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(300), club.heiqi.uilib.ui.scene.layout.Size.fixed(200)).add(drag[0]));
        }));
        page.frame(); long input = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(input));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(55, 5).at(input + 50_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 55, 5).at(input + 50_000_000L)); page.frame();
        domain.execute(() -> {
            int position = page.pipeline().current().geometry.get(content[0]).box.x;
            require(position == 50 && dropped[0] == 1, "释放跳回原槽或意图重复: " + position);
            long now = page.runtime().inputNanos(); page.runtime().preparePresentation(now + 10_000_000L);
            require(page.pipeline().current().geometry.get(content[0]).box.x > 50, "正式采样速度未续接");
            page.runtime().preparePresentation(now + 2_000_000_000L);
            require(page.pipeline().current().geometry.get(content[0]).box.x == 0 && dropped[0] == 1, "回槽端点或动作次数错误");
        });
    }
    private void dragReleaseFeedback() {
        for (boolean paused : new boolean[] {true, false}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
            SceneNode[] content = {null};
            club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
                content[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
                SceneNode drag = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content[0],
                        club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                        new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().autoScroll(false)
                                .bounds(club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds.PARENT).releaseMotion(MotionSpec.spring(.58, .52)));
                context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(180), club.heiqi.uilib.ui.scene.layout.Size.fixed(100)).add(drag));
            }));
            page.frame(); long input = System.nanoTime();
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(input));
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(75, 5).at(input + 5_000_000L));
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 75, 5).at(input + (paused ? 80_000_000L : 5_000_000L))); page.frame();
            domain.execute(() -> {
                long time = page.runtime().inputNanos();
                page.runtime().preparePresentation(time + 10_000_000L);
                int first = page.pipeline().current().geometry.get(content[0]).box.x;
                page.runtime().preparePresentation(time + 40_000_000L);
                int next = page.pipeline().current().geometry.get(content[0]).box.x;
                if (paused) require(first <= 70 && next < first, "停手松开仍按旧速度向外甩出: " + first + "/" + next);
                else require(next < first, "撞到右边界后仍贴边等待内部越界轨迹: " + first + "/" + next);
                page.runtime().preparePresentation(time + 2_000_000_000L);
                require(page.pipeline().current().geometry.get(content[0]).box.x == 0, "边界投影没有回到合法槽位");
            });
        }
    }
    private void boundedRelease() {
        MotionSpec released = MotionSpec.spring(.58, .52);
        MotionValue constrained = new MotionValue(), reference = new MotionValue();
        constrained.release(70, 0, 0, released, 0); constrained.constrain(0, 120, 0); constrained.advance(80_000_000L);
        reference.release(constrained.value(), constrained.velocity(), 0, released, 80_000_000L);
        constrained.unconstrain(); constrained.advance(300_000_000L); reference.advance(300_000_000L);
        require(constrained.value() < 0, "撤销边界后仍被旧范围截断");
        near(constrained.value(), reference.value(), .00001, "撤销约束重置了当前轨迹");
        near(constrained.velocity(), reference.velocity(), .0001, "撤销约束丢失了速度");
        for (double damping : new double[] {.52, 1, 1.8}) {
            MotionSpec spring = MotionSpec.spring(.58, damping);
            MotionValue one = new MotionValue(), many = new MotionValue();
            one.release(70, 14000, 0, spring, 0); many.release(70, 14000, 0, spring, 0);
            one.constrain(0, 120, 0); many.constrain(0, 120, 0);
            one.advance(100_000_000L);
            for (long now = 10_000_000L; now <= 100_000_000L; now += 10_000_000L) many.advance(now);
            near(one.value(), many.value(), .00001, "碰壁分帧改变位置 damping=" + damping);
            near(one.velocity(), many.velocity(), .0001, "碰壁分帧改变速度 damping=" + damping);
            require(one.value() < 120, "大帧仍停在边界未推进剩余时间");
            one.advance(1_000_000_000L); many.advance(1_000_000_000L);
            near(one.value(), many.value(), .00001, "跨第二侧边界后不一致");
        }
        for (club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds bounds : new club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds[] {
                club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds.NONE, club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds.PARENT}) for (boolean paused : new boolean[] {false, true}) {
            int one = overlayRelease(bounds, paused, false), many = overlayRelease(bounds, paused, true);
            near(one, many, 1, "预览PageSession单帧与连续排列改变相同经过时间的回槽位置");
        }
    }
    private int overlayRelease(club.heiqi.uilib.ui.scene.control.SceneDraggable.Bounds bounds, boolean paused, boolean partitioned) {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 180; host.height = 120;
        SceneNode[] preview = {null}; long[] releasedAt = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            preview[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            SceneNode content = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            SceneNode drag = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content, club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                    new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().autoScroll(false).bounds(bounds).overlay(preview[0]).releaseMotion(MotionSpec.spring(.58, .52))
                            .onDrop(ignored -> releasedAt[0] = context.runtime().inputNanos()));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(300), club.heiqi.uilib.ui.scene.layout.Size.fixed(100)).add(drag));
        }));
        page.frame(); long input = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(input));
        int pointer = paused ? 175 : 75;
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(pointer, 5).at(input + 5_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, pointer, 5).at(input + (paused ? 80_000_000L : 5_000_000L))); page.frame();
        int[] result = {0};
        domain.execute(() -> {
            int first = -1;
            for (long time = partitioned ? 10 : 100; time <= 100; time += 10) {
                page.runtime().preparePresentation(releasedAt[0] + time * 1_000_000L);
                int current = page.pipeline().current().geometry.get(preview[0]).box.x;
                if (time == 10) first = current;
                if (time == 40) {
                    require(current < first && first <= 120, "预览视口内部轨迹仍越界: " + first + "/" + current);
                    if (paused) {
                        MotionValue expected = new MotionValue(); expected.release(120, 0, 0, MotionSpec.spring(.58, .52), 0); expected.advance(40_000_000L);
                        near(current, Math.round(expected.value()), 1, "预览UP与MOVE坐标混用产生假速度");
                    }
                }
                result[0] = current;
            }
        });
        return result[0];
    }
    private void scrollMotion() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] inner = {null}, outer = {null}, content = {null};
        club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions options = new club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions(MotionSpec.spring(.3, .8)).inertia(MotionSpec.decay(6));
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            content[0] = new SceneNode().background(0xff112244).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(1000));
            inner[0] = new SceneNode().scrollable(true).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.scroll(() -> options)).add(content[0]);
            outer[0] = new SceneNode().scrollable(true).scrollOffset(40)
                    .size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(120))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.scroll(() -> options)).add(inner[0])
                    .add(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(400)));
            context.mountOwned(outer[0]);
        }));
        page.frame(); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(10, 5, 1)); page.frame();
        domain.execute(() -> {
            require(page.runtime().scrollOffset(outer[0]) == 20 && page.runtime().scrollOffset(inner[0]) == 0,
                    "内层边界未交祖先");
            require(page.runtime().scrollPresentationOffset(inner[0]) == 0, "内层回弹抢走祖先输入");
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(10, 5, 1)); page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(10, 5, 1)); page.frame();
        domain.execute(() -> require(page.runtime().scrollOffset(outer[0]) == 0 && page.runtime().scrollPresentationOffset(outer[0]) > 0, "边界暂态未保持合法偏移"));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(10, 5, -1)); page.frame();
        domain.execute(() -> require(page.runtime().scrollOffset(outer[0]) == 20 && page.runtime().scrollPresentationOffset(outer[0]) == 0, "反向积存越界距离"));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(90, 100)); page.frame();
        long input = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(90, 100, -1, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.UPDATE, input));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(90, 100, -1, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.UPDATE, input + 50_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(90, 100, 0, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.END, input + 50_000_000L)); page.frame();
        domain.execute(() -> {
            int before = page.runtime().scrollOffset(outer[0]); long now = page.runtime().inputNanos();
            page.runtime().preparePresentation(now + 100_000_000L); page.pipeline().current();
            require(page.runtime().scrollOffset(outer[0]) > before, "显式结束未合成所属视口惯性");
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(90, 100, 1)); page.frame();
        domain.execute(() -> {
            int stopped = page.runtime().scrollOffset(outer[0]); page.runtime().preparePresentation(page.runtime().inputNanos() + 200_000_000L); page.pipeline().current();
            require(page.runtime().scrollOffset(outer[0]) == stopped, "新输入没有接管旧惯性");
        });
    }
    private void reducedScroll() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        host.width = 200; host.height = 200;
        SceneNode[] viewport = {null}, content = {null};
        Signal<MotionPolicy>[] policy = new Signal[1];
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            policy[0] = domain.signal(MotionPolicy.NORMAL); context.motionPolicy(policy[0]);
            content[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(400));
            viewport[0] = new SceneNode().scrollable(true).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(100))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.scroll(() -> new club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions(MotionSpec.spring(.6, 1))))
                    .add(content[0]);
            context.mountOwned(viewport[0]);
        }));
        page.frame(); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(5, 5, 1)); page.frame();
        long[] time = {0}; int[] normal = {0};
        domain.execute(() -> {
            SceneRuntime runtime = page.runtime(); time[0] = runtime.inputNanos();
            normal[0] = runtime.scrollPresentationOffset(viewport[0]);
            require(normal[0] > 0, "未建立正常滚动回弹");
            policy[0].set(MotionPolicy.REDUCED); runtime.preparePresentation(time[0]);
            int previous = runtime.scrollPresentationOffset(viewport[0]);
            require(previous > 0, "切换减少模式立即清空回弹：正常=" + normal[0] + "，减少=" + previous);
            for (long elapsed = 20; elapsed <= 160; elapsed += 20) {
                runtime.preparePresentation(time[0] + elapsed * 1_000_000L);
                int current = runtime.scrollPresentationOffset(viewport[0]);
                require(current >= 0 && current <= previous, "减少模式滚动回弹反向或超调");
                require(runtime.scrollOffset(viewport[0]) == 0, "暂态回弹污染有效滚动值");
                require(page.pipeline().current().geometry.get(content[0]).box.y == current, "回弹几何没有消费当前偏移");
                if (elapsed == 80) require(current > 0 && current < normal[0], "减少模式没有可见中间位置");
                previous = current;
            }
            require(previous == 0, "减少模式回弹没有及时落定");
        });
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(5, 5, 1).at(time[0] + 200_000_000L)); page.frame();
        domain.execute(() -> {
            SceneRuntime runtime = page.runtime(); long now = runtime.inputNanos();
            int reduced = runtime.scrollPresentationOffset(viewport[0]);
            require(reduced > 0 && reduced < normal[0], "减少模式新输入未保留小幅回弹：正常=" + normal[0] + "，减少=" + reduced + "，有效=" + runtime.scrollOffset(viewport[0]));
            runtime.preparePresentation(now + 40_000_000L); page.pipeline().current();
            policy[0].set(MotionPolicy.OFF); runtime.preparePresentation(now + 40_000_000L);
            require(runtime.scrollPresentationOffset(viewport[0]) == 0, "关闭没有立即清空回弹");
            require(page.pipeline().current().geometry.get(content[0]).box.y == 0, "关闭保留了旧回弹几何");
            runtime.preparePresentation(now + 300_000_000L);
            Object settled = page.pipeline().current();
            runtime.preparePresentation(now + 400_000_000L);
            require(page.pipeline().current() == settled, "停止后仍破坏静态复用");
        });
    }
    private void presenceBounce() {
        for (boolean captureDuringBounce : new boolean[] {true, false}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.height = 200;
            Signal<Boolean>[] present = new Signal[1]; SceneNode[] viewport = {null}, sibling = {null};
            club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
                present[0] = domain.signal(true);
                SceneNode row = new SceneNode().background(0xff468ace).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(30))
                        .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.presence(present[0], () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR))));
                sibling[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(30));
                viewport[0] = new SceneNode().scrollable(true).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(100))
                        .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.scroll(() -> new club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions(MotionSpec.spring(.3, 1))))
                        .add(row).add(sibling[0]).add(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(400)));
                context.mountOwned(viewport[0]);
            }));
            page.frame();
            if (captureDuringBounce) { page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(5, 5, 1)); page.frame(); }
            domain.execute(() -> present[0].set(false)); page.frame();
            if (!captureDuringBounce) { page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(5, 5, 1)); page.frame(); }
            domain.execute(() -> {
                SceneRuntime runtime = page.runtime(); long now = runtime.inputNanos();
                require(runtime.scrollPresentationOffset(viewport[0]) > 0, "没有建立父视口回弹");
                for (long elapsed : new long[] {0, 80_000_000L, 400_000_000L}) {
                    runtime.preparePresentation(now + elapsed);
                    club.heiqi.uilib.ui.scene.paint.PaintPlan plan = page.pipeline().current().paint;
                    int ghostY = plan.commands().stream().filter(c -> (c.color & 0xffffff) == 0x468ace).mapToInt(c -> c.box.y).min().orElse(Integer.MIN_VALUE);
                    int siblingY = page.pipeline().current().geometry.get(sibling[0]).box.y;
                    require(ghostY == siblingY, "退场没有消费父回弹基准：capture=" + captureDuringBounce + "，elapsed=" + elapsed + "，ghost=" + ghostY + "，sibling=" + siblingY);
                }
            });
        }
    }
    private void presenceClip() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] viewport = {null}; Signal<Boolean>[] present = new Signal[1]; Signal<Integer>[] height = new Signal[1];
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            present[0] = domain.signal(true); height[0] = domain.signal(100);
            viewport[0] = new SceneNode().scrollable(true).bind(NodeBinding.value(height[0], (node, h) -> node.size(
                    club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(h))))
                    .add(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(80)))
                    .add(new SceneNode().background(0xff1239ab).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                            .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.presence(present[0], () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR)))))
                    .add(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(300)));
            context.mountOwned(viewport[0]);
        }));
        page.frame(); domain.execute(() -> present[0].set(false)); page.frame();
        domain.execute(() -> page.runtime().scrollByPixels(viewport[0], 10)); page.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.paint.PaintCommand ghost = page.pipeline().current().paint.commands().stream()
                    .filter(command -> (command.color & 0xffffff) == 0x1239ab).findFirst().get();
            require(ghost.box.y == 70 && ghost.clip.contains(5, 99), "滚动平移了祖先旧裁剪");
            height[0].set(140);
        }); page.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.paint.PaintCommand ghost = page.pipeline().current().paint.commands().stream()
                    .filter(command -> (command.color & 0xffffff) == 0x1239ab).findFirst().get();
            require(ghost.clip.contains(5, 129), "窗口扩大仍受旧祖先裁剪限制");
        });
    }
    private void scrollCancellation() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] viewport = {null}, content = {null}; Signal<club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions>[] options = new Signal[1];
        club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions motion = new club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions(MotionSpec.spring(.3, .8)).inertia(MotionSpec.decay(6));
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            options[0] = domain.signal(motion);
            content[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(1000));
            viewport[0] = new SceneNode().scrollable(true).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(100))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.scroll(options[0])).add(content[0]); context.mountOwned(viewport[0]);
        }));
        page.frame(); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(5, 5, 1)); page.frame();
        domain.execute(() -> {
            require(page.pipeline().current().geometry.get(content[0]).box.y > 0, "未建立回弹反例");
            options[0].set(null);
            require(page.pipeline().current().geometry.get(content[0]).box.y == 0, "撤销回弹复用了旧偏移几何");
            options[0].set(motion);
        }); page.frame();
        long now = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(5, 5, -1, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.UPDATE, now)); page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5)); page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 5, 5)); page.frame();
        domain.execute(() -> page.runtime().scrollToPixels(viewport[0], club.heiqi.uilib.ui.scene.node.SceneNode.Axis.COLUMN, 200)); page.frame();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(5, 5, -1, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.UPDATE, now + 60_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scrollGesture(5, 5, 0, club.heiqi.uilib.ui.scene.input.ScenePointerEvent.ScrollPhase.END, now + 60_000_000L)); page.frame();
        domain.execute(() -> {
            int offset = page.runtime().scrollOffset(viewport[0]); require(offset == 220, "新段未直接滚动");
            page.runtime().preparePresentation(page.runtime().inputNanos() + 100_000_000L); page.pipeline().current();
            require(page.runtime().scrollOffset(viewport[0]) == offset, "旧段采样污染新段单个UPDATE");
        });
    }
    private void keyboardTakeover() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        SceneNode[] content = {null}; int[] drops = {0};
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            content[0] = new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(60), club.heiqi.uilib.ui.scene.layout.Size.fixed(40));
            SceneNode drag = club.heiqi.uilib.ui.scene.control.SceneDraggable.create(context, content[0], club.heiqi.uilib.ui.scene.control.SceneDraggable.state(domain),
                    new club.heiqi.uilib.ui.scene.control.SceneDraggable.Options().autoScroll(false).releaseMotion(MotionSpec.spring(.6, 1)).onDrop(value -> drops[0]++));
            context.mountOwned(new SceneNode().size(club.heiqi.uilib.ui.scene.layout.Size.fixed(300), club.heiqi.uilib.ui.scene.layout.Size.fixed(200)).add(drag));
        }));
        page.frame(); long now = System.nanoTime();
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, 5).at(now));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(55, 5).at(now + 50_000_000L));
        page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 55, 5).at(now + 50_000_000L)); page.frame();
        int[] released = {0}; domain.execute(() -> released[0] = page.pipeline().current().geometry.get(content[0]).box.x);
        require(released[0] != 0, "未建立回槽中的键盘接管反例");
        page.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.SPACE, false, null));
        // 沿原输入队列结算，但保持同一呈现时刻；托管 runner 的调度间隔会让回槽正常前进。
        page.inspect().join();
        int[] grabbed = {0}; domain.execute(() -> {
            grabbed[0] = page.pipeline().current().geometry.get(content[0]).box.x;
            require(grabbed[0] == released[0], "键盘拾取没有保持当前呈现位置: " + released[0] + " -> " + grabbed[0]);
            System.out.println("MOTION_KEYBOARD_TAKEOVER before=" + released[0] + " after=" + grabbed[0]);
        });
        page.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.RIGHT, false, null)); page.frame();
        domain.execute(() -> require(page.pipeline().current().geometry.get(content[0]).box.x > grabbed[0], "键盘位移仍被旧回槽覆盖"));
        page.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.ESCAPE, false, null)); page.frame();
        domain.execute(() -> {
            page.runtime().preparePresentation(page.runtime().inputNanos() + 300_000_000L);
            require(page.pipeline().current().geometry.get(content[0]).box.x == 0 && drops[0] == 1, "取消复活旧释放或重复业务意图");
        });
    }
    private void sortableSampling() {
        for (long capture : new long[] {10_000_000L, 50_000_000L, 100_000_000L}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
            host.width = 320; host.height = 320; host.frameTime = 1_000_000_000L; host.frameStep = 0;
            SceneNode[] board = {null}, preview = {null}; Map<String, SceneNode> rows = new java.util.LinkedHashMap<>();
            int[] moves = {0}; MotionSpec spring = MotionSpec.spring(.4, .75);
            ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
                board[0] = SceneSortable.create(context,
                        () -> Collections.singletonList(new SceneSortable.Group<>("group", Arrays.asList("a", "b", "c", "d"))), value -> value,
                        key -> { SceneNode items = new SceneNode().size(Size.fixed(240), Size.natural());
                            return new SceneSortable.Container(items, items, SceneSortable.Layout.VERTICAL); },
                        value -> { SceneNode content = new SceneNode().size(Size.fill(1), Size.fixed(40));
                            rows.put(value.get(), content); return new SceneSortable.Row(content); },
                        value -> preview[0] = new SceneNode().background(0xff876543), move -> moves[0]++,
                        () -> new SceneSortable.Options<String, String>().autoScroll(false)
                                .reorderMotion(MotionSpec.tween(100_000_000L, MotionSpec.LINEAR)).dropMotion(spring));
                context.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).add(board[0]));
            }));
            page.frame(); long down = host.frameTime;
            // 三条输入在同一批结算；最后一组先移动再停手，UP 必须覆盖整个静止窗口。
            boolean paused = capture == 100_000_000L;
            page.pointer(ScenePointerEvent.down(0, 20, 20).at(down));
            page.pointer(ScenePointerEvent.move(20, 100).at(down + (paused ? 10_000_000L : capture)));
            page.pointer(ScenePointerEvent.up(0, 20, 100).at(down + capture));
            host.frameTime += 200_000_000L; page.frame();
            domain.execute(() -> {
                SceneGeometryView initial = page.pipeline().current().geometry;
                LayoutBox from = initial.get(preview[0]).box, target = initial.get(rows.get("a")).box;
                require(moves[0] == 1, "同批排序未发布唯一 Move");
                long now = page.runtime().inputNanos(), inputTick = inputSampleTime(page.runtime());
                double speed = paused ? 0 : 80 * 1_000_000_000.0 / capture;
                MotionValue reference = new MotionValue(); reference.release(from.y, speed, target.y, spring, now);
                host.frameTime += 2_000_000L; reference.advance(host.frameTime); page.frame();
                club.heiqi.uilib.ui.scene.runtime.ScenePresentation current = page.pipeline().current();
                require(inputSampleTime(page.runtime()) == inputTick, "门内帧推进了主动输入");
                int actual = current.geometry.get(preview[0]).box.y, expected = (int) Math.round(reference.value());
                require(actual == expected, "排序释放未使用真实采集间隔：capture=" + capture + " actual=" + actual + " expected=" + expected);
                page.runtime().preparePresentation(host.frameTime);
                require(page.pipeline().current() == current, "同刻 prepare 重复推进排序");
                host.frameTime += 3_000_000_000L; page.frame();
                require(!sortableActive(board[0]) && preview[0].destroyed(), "排序落定未释放活跃呈现");
                require(sortableResourcesReleased(board[0]), "排序落定仍持有调度或几何观察");
                club.heiqi.uilib.ui.scene.runtime.ScenePresentation stopped = page.pipeline().current();
                host.frameTime += 1_000_000L; page.frame();
                require(page.pipeline().current() == stopped && moves[0] == 1, "排序静止未复用呈现或重复提交");
            });
            page.close();
        }
    }
    private void sortableFailure() throws Exception {
        AssertionError failures = null;
        for (String entry : new String[] {"frame", "geometry", "policy"})
                for (boolean explicit : new boolean[] {false, true}) for (boolean cleanupFails : new boolean[] {false, true}) {
            try { sortableFailure(entry, explicit, cleanupFails); }
            catch (Throwable failure) {
                if (failures == null) failures = new AssertionError("排序曲线失败清理未满足合同", failure);
                else failures.addSuppressed(failure);
            }
        }
        if (failures != null) throw failures;
    }
    private void sortableFailure(String entry, boolean explicit, boolean cleanupFails) throws Exception {
        String mode = entry + "/" + (explicit ? "Tween" : "Transition") + "/cleanup=" + cleanupFails;
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        host.width = 320; host.height = 320; host.frameTime = 1_000_000_000L; host.frameStep = 0;
        RuntimeException primary = new IllegalStateException("排序曲线失败/" + mode);
        Throwable[] cleanup = {new AssertionError("偏移b释放失败"), new IllegalStateException("偏移c释放失败"),
                new AssertionError("预览释放失败"), new IllegalStateException("忙碌标记释放失败")};
        boolean[] armed = {false}, primaryRaised = {false}; int[] moves = {0}, calls = {0};
        Signal<List<String>>[] order = new Signal[1]; Signal<Integer>[] inset = new Signal[1];
        Signal<SceneSortable.Options<String, String>>[] configured = new Signal[1];
        SceneNode[] board = {null}, preview = {null}; Object[] motion = {null}, model = {null}; club.heiqi.uilib.ui.reactive.Owner[] observerOwner = {null};
        java.util.function.DoubleUnaryOperator easing = progress -> {
            if (armed[0] && "frame".equals(entry) && progress > 0) {
                primaryRaised[0] = true; calls[0]++; throw primary;
            }
            return progress;
        };
        SceneSortable.Options<String, String> base = new SceneSortable.Options<String, String>().autoScroll(false)
                .reorderMotion(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR));
        SceneSortable.Options<String, String> options = explicit
                ? base.dropMotion(MotionSpec.tween(250_000_000L, easing))
                : base.dropAnimation(new SceneSortable.Transition(250, easing));
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            order[0] = domain.signal(Arrays.asList("a", "b", "c", "d"));
            inset[0] = domain.signal(0); configured[0] = domain.signal(options);
            board[0] = SceneSortable.create(context,
                    () -> Collections.singletonList(new SceneSortable.Group<>("group", order[0].get())), value -> value,
                    key -> { SceneNode items = new SceneNode().size(Size.fixed(240), Size.natural())
                            .bind(NodeBinding.value(inset[0], SceneNode::padding));
                        return new SceneSortable.Container(items, items, SceneSortable.Layout.VERTICAL); },
                    value -> new SceneSortable.Row(new SceneNode().size(Size.fill(1), Size.fixed(40))),
                    value -> preview[0] = new SceneNode().background(0xff876543), move -> {
                        moves[0]++; order[0].set(Arrays.asList("b", "c", "a", "d"));
                    }, configured[0]);
            context.mountOwned(new SceneNode().add(board[0]));
        }));
        try {
            page.frame(); page.pointer(ScenePointerEvent.down(0, 20, 20).at(host.frameTime));
            page.pointer(ScenePointerEvent.move(20, 100).at(host.frameTime + 20_000_000L));
            host.frameTime += 20_000_000L; page.frame();
            host.frameTime += 20_000_000L; page.frame();
            page.pointer(ScenePointerEvent.up(0, 20, 100).at(host.frameTime)); page.frame();
            domain.execute(() -> {
                motion[0] = sortableMotion(board[0]);
                try { model[0] = sortableField(board[0].control(), "model"); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                require(moves[0] == 1 && sortableActive(motion[0]), "未建立UP后的活动排序：" + mode);
                observerOwner[0] = domain.owner();
                // Flights 的身份容器不承诺行顺序；第一条真实通知才是首错，清理故障须在它之后启用。
                if (!"frame".equals(entry)) for (String key : new String[] {"b", "c"})
                    domain.observe(observerOwner[0], sortableRowOffset(model[0], key)::get, () -> {
                        if (armed[0] && !primaryRaised[0]) {
                            primaryRaised[0] = true; calls[0]++;
                            System.out.println("SORTABLE_FAILURE_PRIMARY mode=" + mode + " row=" + key);
                            throw primary;
                        }
                    }).refresh();
                if (cleanupFails) {
                    observeSortableCleanup(domain, observerOwner[0], sortableRowOffset(model[0], "b"),
                            () -> sortableOffsetY(model[0], "b") == 0, primaryRaised, cleanup[0]);
                    observeSortableCleanup(domain, observerOwner[0], sortableRowOffset(model[0], "c"),
                            () -> sortableOffsetY(model[0], "c") == 0, primaryRaised, cleanup[1]);
                    try {
                        ReadableSignal<?> shown = (ReadableSignal<?>) sortableField(motion[0], "preview");
                        ReadableSignal<?> busy = (ReadableSignal<?>) sortableField(motion[0], "busy");
                        observeSortableCleanup(domain, observerOwner[0], shown, () -> shown.get() == null, primaryRaised, cleanup[2]);
                        observeSortableCleanup(domain, observerOwner[0], busy, () -> Boolean.FALSE.equals(busy.get()), primaryRaised, cleanup[3]);
                    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                }
                require(sortableOffsetY(model[0], "b") != 0 && sortableOffsetY(model[0], "c") != 0,
                        "没有触达多条活动让位：" + mode);
                armed[0] = true;
            });
            Throwable caught = null;
            try {
                if ("frame".equals(entry)) host.frameTime += 2_000_000L;
                else domain.execute(() -> {
                    if ("geometry".equals(entry)) inset[0].set(10);
                    else configured[0].set(options.reorderMotion(MotionSpec.instant()).dropMotion(MotionSpec.instant()));
                });
                page.frame();
            } catch (RuntimeException | Error failure) { caught = failure; }
            require(caught == primary, "没有保留UP后曲线原异常：" + mode + " caught=" + caught);
            domain.execute(() -> {
                boolean active = sortableActive(motion[0]), released = sortableResourcesReleased(motion[0]);
                System.out.println("SORTABLE_FAILURE mode=" + mode + " calls=" + calls[0]
                        + " active=" + active + " resourcesReleased=" + released + " suppressed=" + primary.getSuppressed().length);
                require(!active && released, "UP后曲线失败残留排序轨迹/预览/调度/观察：" + mode);
                require(sortableOffsetY(model[0], "b") == 0 && sortableOffsetY(model[0], "c") == 0,
                        "一条让位清理失败阻断另一条：" + mode);
                if (cleanupFails) for (Throwable next : cleanup)
                    require(hasSuppressed(primary, next), "清理异常未附到原异常：" + mode + " cleanup=" + next);
            });
            host.frameTime += 2_000_000L; page.frame();
            domain.execute(() -> {
                require(calls[0] == 1 && moves[0] == 1, "失败轨迹重试或重复提交：" + mode);
                require(preview[0].destroyed(), "失败后的预览节点未释放：" + mode);
            });
        } finally {
            armed[0] = false;
            domain.execute(() -> { if (observerOwner[0] != null) observerOwner[0].close(); });
            page.close();
        }
    }
    private static void observeSortableCleanup(ReactiveDomain domain, club.heiqi.uilib.ui.reactive.Owner owner,
            ReadableSignal<?> signal, java.util.function.BooleanSupplier cleared, boolean[] primaryRaised, Throwable failure) {
        club.heiqi.uilib.ui.reactive.Observation[] observation = {null};
        observation[0] = domain.observe(owner, signal::get, () -> {
            observation[0].refresh();
            if (primaryRaised[0] && cleared.getAsBoolean()) {
                if (failure instanceof Error) throw (Error) failure;
                throw (RuntimeException) failure;
            }
        });
        observation[0].refresh();
    }
    private static ReadableSignal<?> sortableRowOffset(Object model, String key) {
        try {
            Object row = ((Map<?, ?>) sortableField(model, "rows")).get(key);
            return (ReadableSignal<?>) sortableField(row, "offset");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static int sortableOffsetY(Object model, String key) {
        try { return (Integer) sortableField(sortableRowOffset(model, key).get(), "y"); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static boolean hasSuppressed(Throwable primary, Throwable expected) {
        for (Throwable next : primary.getSuppressed()) if (next == expected || hasSuppressed(next, expected)) return true;
        return false;
    }
    private void sortableCancellation() {
        for (boolean unmount : new boolean[] {false, true}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
            host.width = 320; host.height = 320; host.frameTime = 1_000_000_000L; host.frameStep = 0;
            Signal<Boolean>[] present = new Signal[1]; SceneNode[] board = {null}, neighbor = {null}; int[] moves = {0};
            Object[] observedMotion = {null};
            ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
                present[0] = domain.signal(true);
                board[0] = SceneSortable.create(context,
                        () -> Collections.singletonList(new SceneSortable.Group<>("group", Arrays.asList("a", "b", "c"))), value -> value,
                        key -> { SceneNode items = new SceneNode().size(Size.fixed(240), Size.natural());
                            return new SceneSortable.Container(items, items, SceneSortable.Layout.VERTICAL); },
                        value -> { SceneNode content = new SceneNode().size(Size.fill(1), Size.fixed(40));
                            if ("b".equals(value.get())) neighbor[0] = content; return new SceneSortable.Row(content); },
                        value -> new SceneNode().background(0xff876543), move -> moves[0]++,
                        () -> new SceneSortable.Options<String, String>().autoScroll(false)
                                .reorderMotion(MotionSpec.tween(80_000_000L, MotionSpec.LINEAR)).dropMotion(MotionSpec.spring(.36, .68)));
                context.mountOwned(new SceneNode().bind(NodeBinding.ownedChildren(
                        () -> present[0].get() ? Collections.singletonList(board[0]) : Collections.emptyList())));
            }));
            page.frame(); page.pointer(ScenePointerEvent.down(0, 20, 20).at(host.frameTime));
            page.pointer(ScenePointerEvent.move(20, 100).at(host.frameTime + 20_000_000L));
            host.frameTime += 20_000_000L; page.frame();
            domain.execute(() -> {
                // ownedChildren 移除后会清空 board.control；先保留同一生产轨迹，销毁后仍核验其清理。
                observedMotion[0] = sortableMotion(board[0]);
                require(sortableActive(observedMotion[0]), "没有触达活动排序");
                int before = page.pipeline().current().geometry.get(neighbor[0]).box.y;
                long tick = inputSampleTime(page.runtime()); host.frameTime += 2_000_000L; page.frame();
                int after = page.pipeline().current().geometry.get(neighbor[0]).box.y;
                require(after < before, "输入门内让位没有继续呈现：" + before + "->" + after);
                require(inputSampleTime(page.runtime()) == tick, "让位推进了主动输入帧");
            });
            if (unmount) domain.execute(() -> present[0].set(false));
            else page.pointer(ScenePointerEvent.cancel().at(host.frameTime));
            page.frame(); page.pointer(ScenePointerEvent.up(0, 20, 100).at(host.frameTime)); page.frame();
            host.frameTime += 3_000_000_000L; page.frame();
            domain.execute(() -> {
                if (unmount) require(board[0].destroyed() && board[0].control() == null
                        && page.pipeline().current().geometry.get(board[0]) == null, "拥有移除未结束节点及当前几何");
                require(moves[0] == 0 && !sortableActive(observedMotion[0]), "取消/卸载提交了 Move 或保留运动");
                require(sortableResourcesReleased(observedMotion[0]), "取消/卸载没有对称释放呈现资源");
            });
            page.close();
        }
    }
    private static long inputSampleTime(SceneRuntime runtime) {
        try { return (Long) sortableField(runtime, "inputNanos"); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object sortableMotion(SceneNode board) {
        try { return sortableField(sortableField(board.control(), "model"), "motion"); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static boolean sortableResourcesReleased(SceneNode board) { return sortableResourcesReleased(sortableMotion(board)); }
    private static boolean sortableResourcesReleased(Object motion) {
        try {
            return sortableField(motion, "scheduled") == null && sortableField(motion, "geometryObservation") == null;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void sortableScroll(boolean settled) {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        host.width = 320; host.height = 320; host.frameTime = 1_000_000_000L; host.frameStep = 0;
        Signal<List<String>>[] order = new Signal[1]; SceneNode[] viewport = new SceneNode[1], board = new SceneNode[1], preview = new SceneNode[1];
        Map<String, SceneNode> rows = new java.util.LinkedHashMap<>(); int[] moves = {0};
        SceneSortable.Options<String, String> options = new SceneSortable.Options<String, String>().autoScroll(false)
                .reorderMotion(MotionSpec.spring(.32, .72)).dropMotion(MotionSpec.spring(.36, .68));
        ScenePageSession page = own(ScenePageSession.open(domain, host, host, context -> {
            order[0] = domain.signal(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h"));
            board[0] = SceneSortable.create(context, () -> Collections.singletonList(new SceneSortable.Group<>("group", order[0].get())), value -> value,
                    key -> { SceneNode items = new SceneNode().size(Size.fixed(240), Size.natural());
                        return new SceneSortable.Container(items, items, SceneSortable.Layout.VERTICAL); },
                    value -> { SceneNode content = new SceneNode().size(Size.fill(1), Size.fixed(40)).background(0xff345678);
                        rows.put(value.get(), content); return new SceneSortable.Row(content); },
                    value -> preview[0] = new SceneNode().background(0xff876543), move -> {
                        moves[0]++; List<String> next = new java.util.ArrayList<>(order[0].get()); next.remove(move.key);
                        next.add(move.beforeKey == null ? next.size() : next.indexOf(move.beforeKey), move.key); order[0].set(next);
                    }, () -> options);
            viewport[0] = new SceneNode().size(Size.fixed(240), Size.fixed(200)).scrollable(true).scrollStep(24).add(board[0]);
            context.mountOwned(new SceneNode().size(Size.fill(1), Size.fill(1)).add(viewport[0]));
        }));
        page.frame();
        page.pointer(ScenePointerEvent.down(0, 20, 20).at(host.frameTime));
        host.frameTime += 50_000_000L; page.pointer(ScenePointerEvent.move(20, 145).at(host.frameTime)); page.frame();
        host.frameTime += 100_000_000L; page.pointer(ScenePointerEvent.up(0, 20, 145).at(host.frameTime)); page.frame();
        domain.execute(() -> require(moves[0] == 1 && order[0].get().indexOf("a") > 0, "排序未沿正式 Move 更新权威顺序"));
        host.frameTime += settled ? 3_000_000_000L : 30_000_000L; page.frame();
        domain.execute(() -> require(sortableActive(board[0]) != settled,
                "未触达指定的落下中或完全落定阶段"));
        // 保持同一宿主时刻，独立检验父滚动的即时平移，排除弹簧自身经过时间的位移。
        for (int direction : new int[] {-1, 1}) {
            domain.execute(() -> {
                SceneGeometryView before = page.pipeline().current().geometry;
                page.pointer(ScenePointerEvent.scroll(220, 180, direction).at(host.frameTime)); page.frame();
                SceneGeometryView after = page.pipeline().current().geometry;
                int shift = before.get(viewport[0]).scrollOffset - after.get(viewport[0]).scrollOffset;
                require(shift == direction * 24, "父视口未收到原滚轮输入：" + shift);
                for (Map.Entry<String, SceneNode> row : rows.entrySet()) {
                    LayoutBox was = before.get(row.getValue()).box, now = after.get(row.getValue()).box;
                    require(now.x == was.x && now.y - was.y == shift,
                            "真实行未同步随父滚动：" + row.getKey() + " before=" + was.y + " after=" + now.y + " scroll=" + shift);
                }
                if (!settled) {
                    LayoutBox was = before.get(preview[0]).box, now = after.get(preview[0]).box;
                    require(now.x == was.x && now.y - was.y == shift, "旧落下反馈将父滚动变成新的弹性位移：" + was.y + "->" + now.y + " scroll=" + shift);
                } else require(after.get(preview[0]) == null && preview[0].destroyed(), "完全落定后滚动复活旧 ghost");
                require(sortableActive(board[0]) != settled, "滚动改变了原排序运动生命周期");
            });
        }
        host.frameTime += 3_000_000_000L; page.frame();
        domain.execute(() -> {
            require(!sortableActive(board[0]), "落下运动未释放");
            require(page.pipeline().current().geometry.get(preview[0]) == null && preview[0].destroyed(), "旧反馈未释放");
        });
        for (int direction : new int[] {-1, 1}) {
            page.pointer(ScenePointerEvent.scroll(220, 180, direction).at(host.frameTime)); page.frame();
            domain.execute(() -> {
                SceneGeometryView stopped = page.pipeline().current().geometry;
                for (int frame = 0; frame < 12; frame++) {
                    host.frameTime += 1_000_000_000L / 300; page.frame();
                    SceneGeometryView current = page.pipeline().current().geometry;
                    for (SceneNode row : rows.values()) require(current.get(row).box.y == stopped.get(row).box.y, "300Hz 静止帧出现额外条目位移动效");
                }
                require(!sortableActive(board[0]) && moves[0] == 1, "旧排序反馈或业务意图被滚动复活");
            });
        }
    }
    /** 仅测试观测：区分肉眼落定与 elevation/亚像素仍活跃，不给生产增加探针。 */
    private static boolean sortableActive(SceneNode board) { return sortableActive(sortableMotion(board)); }
    private static boolean sortableActive(Object motion) {
        try {
            Object landing = sortableField(motion, "landing"), flights = sortableField(motion, "flights");
            boolean preview = ((ReadableSignal<?>) sortableField(motion, "preview")).get() != null;
            boolean busy = (Boolean) ((ReadableSignal<?>) sortableField(motion, "busy")).get();
            boolean active = false;
            if (landing != null) { java.lang.reflect.Method method = landing.getClass().getDeclaredMethod("active"); method.setAccessible(true); active = (Boolean) method.invoke(landing); }
            int count = ((Map<?, ?>) sortableField(flights, "flights")).size(); boolean pending = sortableField(flights, "pending") != null;
            return preview || active || busy || count != 0 || pending;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object sortableField(Object owner, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private void failedMount() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            SceneRuntime runtime = new SceneRuntime(domain); Signal<Float> target = domain.signal(Float.NaN);
            MotionSpec spec = MotionSpec.spring(.3, 1); float[] shown = {0};
            SceneNode node = new SceneNode().bind(NodeBinding.motion(target, () -> spec, true, "test.position", (n, value) -> shown[0] = value));
            MountHandle mount = runtime.attach(null, node);
            boolean failed = false;
            try { runtime.preparePresentation(0); } catch (IllegalArgumentException expected) { failed = true; }
            require(failed, "非法目标未按呈现错误通道暴露");
            mount.detach();
            target.set(20f); runtime.attach(null, node); runtime.preparePresentation(0);
            require(shown[0] == 20, "失败通道仍占用后续合法挂载"); runtime.close(); node.destroy();
        });
    }
    private void presenceOwnClip() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<java.util.List<String>>[] rows = new Signal[1]; java.util.List<SceneNode> built = new java.util.ArrayList<>(); int[] released = {0};
        IllegalStateException error = new IllegalStateException("退场资源失败");
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            rows[0] = domain.signal(java.util.Arrays.asList("a", "b", "c"));
            context.mountOwned(new SceneNode().bind(NodeBinding.repeat(rows[0], value -> value, value -> {
                String key = value.get(); SceneNode child = new SceneNode().clip(true).padding(10).background(0xff765432)
                        .size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                        .own(() -> { released[0]++; if (!key.equals("c")) throw error; });
                built.add(child); return child;
            }, () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR)))));
        }));
        page.frame(); domain.execute(() -> rows[0].set(java.util.Collections.emptyList())); page.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.paint.PaintCommand ghost = page.pipeline().current().paint.commands().stream()
                    .filter(command -> (command.color & 0xffffff) == 0x765432).findFirst().get();
            require(ghost.clip.x == 0 && ghost.clip.y == 0 && ghost.clip.width == 100 && ghost.clip.height == 60,
                    "自身clip被错误缩到padding正文盒");
        });
        try { page.close(); } catch (IllegalStateException failure) { require(failure == error, "释放错误被替换"); }
        require(released[0] == 3 && built.stream().allMatch(SceneNode::destroyed), "一个退场资源失败阻止其他资源释放");
    }
    private void presenceLayer() {
        ReactiveDomain domain = ReactiveDomain.direct(); SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost(); host.width = 400; host.height = 300;
        Signal<Boolean>[] shown = new Signal[1];
        club.heiqi.uilib.ui.scene.host.ScenePageSession page = own(club.heiqi.uilib.ui.scene.host.ScenePageSession.open(domain, host, host, context -> {
            shown[0] = domain.signal(true);
            SceneNode floating = club.heiqi.uilib.ui.scene.control.SceneLayer.create(context,
                    () -> new club.heiqi.uilib.ui.scene.control.SceneLayer.Policy(1, club.heiqi.uilib.ui.scene.control.SceneLayer.Participation.PASSIVE));
            floating.background(0xff987654).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(20));
            context.mountOwned(new SceneNode().clip(true).size(club.heiqi.uilib.ui.scene.layout.Size.fixed(100), club.heiqi.uilib.ui.scene.layout.Size.fixed(60))
                    .bind(club.heiqi.uilib.ui.scene.motion.SceneMotion.presence(shown[0], () -> new club.heiqi.uilib.ui.scene.motion.PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR)))).add(floating));
        }));
        page.frame(); domain.execute(() -> shown[0].set(false)); page.frame();
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.paint.PaintCommand layer = page.pipeline().current().paint.commands().stream()
                    .filter(command -> (command.color & 0xffffff) == 0x987654).findFirst().get();
            require(layer.box.y == 61 && layer.clip.contains(5, 70), "退场所属层被来源根clip截断");
            require(page.pipeline().current().geometry.activeModal() == null, "被动退场重新发布层输入范围");
        });
    }
    private static void near(double actual, double expected, double tolerance, String message) {
        require(Math.abs(actual - expected) <= tolerance, message + ": actual=" + actual + " expected=" + expected);
    }
}
