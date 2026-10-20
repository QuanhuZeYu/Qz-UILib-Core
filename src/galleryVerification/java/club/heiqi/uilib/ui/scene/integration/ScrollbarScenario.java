package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneScrollViewport;
import club.heiqi.uilib.ui.scene.control.SceneScrollResult;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 正式视口与 PaintPlan 的反例：普通呈现不占用 300 Hz 的主动输入周期。 */
public final class ScrollbarScenario extends TestApplication {
    private static final long INPUT_INTERVAL = (1_000_000_000L + 299) / 300;
    private static final int THUMB = 0xff123456;

    @Override protected void runTests() throws Exception {
        if (arguments().length > 0 && "body-wheel".equals(arguments()[0])) {
            boolean automatic = Boolean.parseBoolean(arguments()[1]);
            boolean consumeOnly = Boolean.parseBoolean(arguments()[2]);
            boolean horizontal = Boolean.parseBoolean(arguments()[3]);
            scenario("正文滚轮接管/" + automatic + "/" + consumeOnly + "/" + horizontal,
                    () -> bodyWheel(automatic, consumeOnly, horizontal));
            return;
        }
        for (boolean automatic : new boolean[] {false, true})
            for (boolean consumeOnly : new boolean[] {false, true})
                for (boolean horizontal : new boolean[] {false, true})
                    scenario("正文滚轮接管/默认条=" + automatic + "/只消费=" + consumeOnly + "/横轴=" + horizontal,
                            () -> bodyWheel(automatic, consumeOnly, horizontal));
        for (boolean automatic : new boolean[] {false, true}) {
            for (boolean horizontal : new boolean[] {false, true})
                scenario("范围投影不取消分页/默认条=" + automatic + "/横轴=" + horizontal,
                        () -> rangeProjection(automatic, horizontal));
            scenario("隐藏恢复后正文仍接管/默认条=" + automatic, () -> hideAndResume(automatic));
        }
        scenario("接管观察的注销重入与原异常保留", this::takeoverObservers);
        scenario("视口接管回调失败仍取消旧滚动回弹", () -> oldMotionCancel(false));
        scenario("全局接管回调失败仍取消旧滚动回弹", () -> oldMotionCancel(true));
        scenario("分页在输入门内推进而连续手势仍按300Hz结算", this::pageCadence);
        scenario("淡出在输入门内推进且减少关闭即时接管", this::fadePolicy);
        scenario("隐藏等待保留期限、重新交互与卸载不复活旧分页", this::lifecycle);
    }

    private void bodyWheel(boolean automatic, boolean consumeOnly, boolean horizontal) {
        try (Fixture f = new Fixture(1_000, 200, automatic, horizontal)) {
            f.clickTrack();
            f.at(f.host.frameTime + 40_000_000L);
            int before = f.offset();
            require(before > 0 && before < 100, "未触达需要被接管的分页中间态：" + before);
            f.domain.execute(() -> f.consumeOnly.set(consumeOnly));
            // 正文点位与条分离，真正经过WheelRouting，而非Scrollbar.scrollResult。
            f.page.pointer(ScenePointerEvent.scroll(20, 40, -2).at(f.host.frameTime));
            f.page.frame();
            int taken = f.offset();
            require(consumeOnly ? taken == before && f.consumed > 0 : taken > before,
                    "正文滚轮没有经过预期消费路径：" + before + "→" + taken);
            f.at(f.host.frameTime + 20_000_000L);
            int after = f.offset();
            System.out.println("SCROLLBAR_BODY_WHEEL automatic=" + automatic + " consumeOnly=" + consumeOnly
                    + " horizontal=" + horizontal + " before=" + before + " taken=" + taken + " after=" + after);
            require(after == taken, "旧分页覆盖正文新输入：" + taken + "→" + after);
            f.at(f.host.frameTime + 500_000_000L);
            require(f.offset() == taken, "正文接管后仍有旧分页尾部");
        }
    }

    private void rangeProjection(boolean automatic, boolean horizontal) {
        try (Fixture f = new Fixture(1_000, 200, automatic, horizontal)) {
            f.clickTrack();
            f.at(f.host.frameTime + 40_000_000L);
            int before = f.offset();
            require(before > 0 && before < 80, "未触达范围投影的分页中间态");
            f.domain.execute(() -> f.contentPixels.set(180));
            f.page.frame();
            f.at(f.host.frameTime + 40_000_000L);
            int projected = f.offset();
            require(projected > before && projected <= 80, "范围投影被误判为新输入或发生自取消");
            f.at(f.host.frameTime + 200_000_000L);
            require(f.offset() == 80, "范围收缩后没有落到原所有者的合法目标");
            System.out.println("SCROLLBAR_RANGE automatic=" + automatic + " horizontal=" + horizontal
                    + " before=" + before + " projected=" + projected + " final=" + f.offset());
        }
    }

    private void hideAndResume(boolean automatic) {
        try (Fixture f = new Fixture(1_000, 200, automatic, false)) {
            f.clickTrack();
            f.at(f.host.frameTime + 40_000_000L);
            f.domain.execute(() -> f.shown.set(false));
            f.page.frame();
            int stopped = f.offset();
            f.at(f.host.frameTime + 200_000_000L);
            require(f.offset() == stopped, "隐藏视口后旧分页仍推进");
            f.domain.execute(() -> f.shown.set(true));
            f.page.frame();
            f.findAutomaticBar(automatic);
            f.clickTrack();
            f.at(f.host.frameTime + 40_000_000L);
            require(f.offset() > stopped, "恢复后未重新建立分页");
            f.page.pointer(ScenePointerEvent.scroll(20, 40, -2).at(f.host.frameTime));
            f.page.frame();
            int taken = f.offset();
            f.at(f.host.frameTime + 200_000_000L);
            require(f.offset() == taken, "隐藏恢复后正文丢失接管观察");
        }
    }

    private void oldMotionCancel(boolean global) {
        try (Fixture f = new Fixture(1_000, 200)) {
            f.domain.execute(() -> f.scrollOptions.set(new ScrollMotionOptions(MotionSpec.spring(.3, .8))));
            f.page.pointer(ScenePointerEvent.scroll(20, 40, 1).at(f.host.frameTime));
            f.page.frame();
            f.domain.execute(() -> require(f.runtime.scrollPresentationOffset(f.viewport) > 0, "未建立旧滚动协调者的边界回弹"));
            RuntimeException expected = new IllegalStateException("取消观察失败");
            AutoCloseable[] registration = {null};
            f.domain.execute(() -> registration[0] = f.runtime.observeScrollInput(
                    new SceneScrollViewport(f.runtime, f.viewport, SceneNode.Axis.COLUMN), () -> { throw expected; }));
            f.page.pointer(global ? ScenePointerEvent.down(0, 220, 140).at(f.host.frameTime)
                    : ScenePointerEvent.scroll(20, 40, -1).at(f.host.frameTime));
            Throwable caught = null;
            try { f.page.frame(); } catch (RuntimeException failure) { caught = failure; }
            require(caught == expected, "取消回调失败被替换或吞掉");
            f.domain.execute(() -> {
                require(f.runtime.scrollPresentationOffset(f.viewport) == 0, "新通知失败跳过旧滚动资源取消");
                closeRegistration(registration[0]);
            });
        }
    }

    private void takeoverObservers() throws Exception {
        try (Fixture f = new Fixture(1_000, 200)) {
            SceneScrollViewport viewport = new SceneScrollViewport(f.runtime, f.viewport, SceneNode.Axis.COLUMN);
            List<String> trace = new ArrayList<>();
            AutoCloseable[] registrations = new AutoCloseable[5];
            RuntimeException first = new IllegalStateException("接管首个失败"), second = new IllegalArgumentException("接管后续失败");
            f.domain.execute(() -> {
                // 从条映射回真正视口，正文输入与轴端口使用同一接管身份。
                registrations[0] = f.runtime.observeScrollInput(new SceneScrollViewport(f.runtime, f.bar, SceneNode.Axis.COLUMN), () -> {
                    trace.add("first"); closeRegistration(registrations[1]);
                    if (registrations[2] == null) registrations[2] = f.runtime.observeScrollInput(viewport, () -> trace.add("late"));
                });
                registrations[1] = f.runtime.observeScrollInput(viewport, () -> trace.add("removed"));
            });
            f.page.pointer(ScenePointerEvent.scroll(20, 40, -1).at(f.host.frameTime));
            f.page.frame();
            require(trace.equals(Collections.singletonList("first")), "注销/新增观察没有遵守同批快照：" + trace);
            f.domain.execute(() -> {
                registrations[3] = f.runtime.observeScrollInput(viewport, () -> { trace.add("failure1"); throw first; });
                registrations[4] = f.runtime.observeScrollInput(viewport, () -> { trace.add("failure2"); throw second; });
            });
            f.page.pointer(ScenePointerEvent.scroll(20, 40, -1).at(f.host.frameTime));
            Throwable caught = null;
            try { f.page.frame(); } catch (RuntimeException failure) { caught = failure; }
            require(caught == first && Arrays.asList(first.getSuppressed()).contains(second), "接管回调异常被吞掉或替换：" + caught);
            require(trace.equals(Arrays.asList("first", "first", "late", "failure1", "failure2")),
                    "单个观察失败妨碍后续清理：" + trace);
            f.domain.execute(() -> { for (AutoCloseable registration : registrations) closeRegistration(registration); });
            int completed = trace.size();
            f.page.pointer(ScenePointerEvent.scroll(20, 40, -1).at(f.host.frameTime));
            f.page.frame();
            require(trace.size() == completed, "注销后仍有迟到的接管回调");
            System.out.println("SCROLLBAR_OBSERVERS order=" + trace + " primaryPreserved=" + (caught == first));
        }
    }
    private static void closeRegistration(AutoCloseable registration) {
        if (registration == null) return;
        try { registration.close(); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private void pageCadence() {
        try (Fixture f = new Fixture(1_000, 200)) {
            long start = f.host.frameTime;
            int ticks = f.ticks.size();
            f.clickTrack();
            require(f.offset() == 0, "正常分页没有从当前权威位置开始");
            f.at(start + 2_000_000L);
            int middle = f.offset();
            require(middle > 0 && middle < 100 && f.ticks.size() == ticks,
                    "输入门内的普通分页没有推进，或额外推进主动输入：" + middle);
            require(f.nextInput() == Long.MAX_VALUE, "滚动条将普通呈现登记成主动输入");

            // 同时触达真正的捕获手势，排除仅仅放宽整条管线门限的实现。
            f.page.pointer(ScenePointerEvent.down(0, 125, 5).at(f.host.frameTime));
            f.page.frame();
            f.page.pointer(ScenePointerEvent.move(130, 5).at(f.host.frameTime));
            f.at(start + INPUT_INTERVAL - 1);
            require(f.moves.isEmpty() && f.ticks.size() == ticks, "采样门内提前递交连续MOVE");
            f.at(start + INPUT_INTERVAL);
            require(f.moves.equals(Collections.singletonList(130)) && f.ticks.size() == ticks + 1,
                    "正式周期边界没有按序递交手势");
            f.page.pointer(ScenePointerEvent.up(0, 130, 5).at(f.host.frameTime));
            f.page.frame();

            require(f.offset() == middle, "新按下没有沿全局滚动取消入口接管旧分页");
            // 新分页单独验证策略续接，避免把已被真实DOWN接管的旧轨迹当作仍存活。
            int destination = f.offset() + 100;
            f.clickTrack();
            f.setPolicy(MotionPolicy.REDUCED);
            int continued = f.offset();
            f.at(f.host.frameTime + 80_000_000L);
            require(f.offset() > continued && f.offset() < destination, "减少动效未平稳续接分页");
            f.setPolicy(MotionPolicy.OFF);
            require(f.offset() == destination, "OFF没有同批落到分页的合法目标");
            System.out.println("SCROLLBAR_PAGE gateNanos=" + INPUT_INTERVAL + " middle=" + middle
                    + " inputTicks=" + f.ticks.size() + " orderedMoves=" + f.moves + " offOffset=" + f.offset());
        }
    }

    private void fadePolicy() {
        try (Fixture f = new Fixture(0, 2)) {
            long start = f.host.frameTime;
            int ticks = f.ticks.size();
            require(f.alpha() == 255, "初始活动没有显示正式thumb");
            f.at(start + 1_000_000L);
            int middle = f.alpha();
            require(middle > 0 && middle < 255 && f.ticks.size() == ticks,
                    "输入门内alpha未渐变，或触发了额外主动输入：" + middle);
            f.setPolicy(MotionPolicy.REDUCED);
            f.at(f.host.frameTime + 500_000L);
            require(f.alpha() > 0 && f.alpha() < middle, "减少模式没有续接短淡出");
            f.setPolicy(MotionPolicy.OFF);
            require(f.alpha() == 0 && f.ticks.size() == ticks, "OFF淡出未在输入门内立即落定");
            PaintPlan settled = f.host.last;
            f.page.frame();
            require(f.host.last == settled, "同一时刻重复准备重启已结束的淡出");
            System.out.println("SCROLLBAR_FADE gateNanos=" + INPUT_INTERVAL + " middleAlpha=" + middle
                    + " inputTicks=" + ticks + " offAlpha=" + f.alpha());
        }
    }

    private void lifecycle() {
        try (Fixture f = new Fixture(10, 2)) {
            long start = f.host.frameTime;
            f.at(start + 2_000_000L);
            f.setPolicy(MotionPolicy.OFF);
            require(f.alpha() == 255, "OFF误删了尚未结束的交互隐藏等待");
            f.at(start + 10_000_000L);
            require(f.alpha() == 0, "OFF在隐藏期限到达后仍保留淡出");
            f.setPolicy(MotionPolicy.NORMAL);
            f.clickTrack();
            f.at(f.host.frameTime + 20_000_000L);
            int moving = f.offset();
            require(moving > 0 && moving < 100, "未触达待取消的分页中间状态");
            LayoutBox box = f.barBox();
            f.page.pointer(ScenePointerEvent.scroll(box.x + box.width / 2, box.y + 60, 1).at(f.host.frameTime));
            f.page.frame();
            int interrupted = f.offset();
            f.at(f.host.frameTime + 200_000_000L);
            require(f.offset() == interrupted, "新的滚轮输入未结束旧分页");

            f.clickTrack();
            f.at(f.host.frameTime + 20_000_000L);
            f.domain.execute(() -> f.attached.set(false));
            f.page.frame();
            int detached = f.offset();
            f.at(f.host.frameTime + 500_000_000L);
            require(f.offset() == detached, "卸载后旧分页仍修改原视口");
            f.domain.execute(() -> f.attached.set(true));
            f.page.frame();
            f.at(f.host.frameTime + 500_000_000L);
            require(f.offset() == detached && f.alpha() == 0, "重新挂载复活旧轨迹或未结束有限淡出");
            PaintPlan settled = f.host.last;
            f.at(f.host.frameTime + 500_000_000L);
            require(f.host.last == settled, "挂载恢复静止后仍重复失效绘制");
            f.clickTrack();
            f.at(f.host.frameTime + 40_000_000L);
            f.page.pointer(ScenePointerEvent.scroll(20, 40, -2).at(f.host.frameTime));
            f.page.frame();
            int taken = f.offset();
            f.at(f.host.frameTime + 200_000_000L);
            require(f.offset() == taken, "卸载重挂后未重新登记正文输入接管");
            System.out.println("SCROLLBAR_LIFECYCLE interrupted=" + interrupted + " detached=" + detached
                    + " restored=" + detached + " taken=" + taken + " settledAlpha=" + f.alpha());
        }
    }

    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        final List<Long> ticks = new ArrayList<>();
        final List<Integer> moves = new ArrayList<>();
        final ScenePageSession page;
        Signal<MotionPolicy> policy;
        Signal<Boolean> attached, consumeOnly, shown;
        Signal<Integer> contentPixels;
        Signal<ScrollMotionOptions> scrollOptions;
        int consumed;
        final boolean horizontal;
        SceneRuntime runtime;
        SceneNode viewport, bar, content;

        Fixture(long hideDelay, long fade) { this(hideDelay, fade, false, false); }
        Fixture(long hideDelay, long fade, boolean automatic, boolean horizontal) {
            this.horizontal = horizontal;
            host.width = 240; host.height = 160; host.frameTime = 1_000_000_000L; host.frameStep = 0;
            page = ScenePageSession.open(domain, host, host, context -> {
                runtime = context.runtime();
                policy = domain.signal(MotionPolicy.NORMAL); attached = domain.signal(true); consumeOnly = domain.signal(false);
                shown = domain.signal(true); contentPixels = domain.signal(5_000); scrollOptions = domain.signal(null);
                context.motionPolicy(policy);
                SceneNode.Axis scrollAxis = horizontal ? SceneNode.Axis.ROW : SceneNode.Axis.COLUMN;
                content = new SceneNode().bind(NodeBinding.value(contentPixels, (node, pixels) ->
                        node.size(horizontal ? Size.fixed(pixels) : Size.fill(1), horizontal ? Size.fill(1) : Size.fixed(pixels))));
                viewport = new SceneNode().axis(scrollAxis).size(Size.fixed(100), Size.fixed(100)).scrollable(true).scrollStep(20).add(content).bind(NodeBinding.value(shown, SceneNode::visible)).bind(SceneMotion.scroll(scrollOptions));
                viewport.control(new SceneControl() {
                    public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
                    public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
                        return SceneFlowLayout.measure(node, limits, children);
                    }
                    public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
                        return SceneFlowLayout.arrange(node, box, children);
                    }
                    public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
                    public boolean scrollableContent() { return true; }
                    public SceneScrollResult scrollResult(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
                        if (!consumeOnly.get()) return SceneScrollResult.IGNORED;
                        consumed++; return SceneScrollResult.CONSUMED;
                    }
                });
                if (!automatic) bar = SceneScrollbar.create(context, new SceneScrollViewport(runtime, viewport, scrollAxis),
                        () -> new SceneScrollbar.Options().dimensions(20, 25, 2).timing(hideDelay, fade).colors(0, THUMB, THUMB), context.theme())
                        .size(Size.fixed(horizontal ? 100 : 20), Size.fixed(horizontal ? 20 : 100));
                SceneNode probe = new SceneNode().size(Size.fixed(20), Size.fixed(20)).control(new SceneControl() {
                    public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(20, 20); }
                    public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return new ChildPlacements(Collections.emptyList()); }
                    public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
                    public boolean capturesPointer() { return true; }
                    public long nextInputNanos(SceneNode node) { return 0; }
                    public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) { ticks.add(now); }
                    public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
                        if (event.kind == ScenePointerEvent.Kind.MOVE) moves.add(event.x);
                    }
                }).onClick(() -> {});
                SceneNode root = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.fill(1));
                root.bind(NodeBinding.children(() -> !automatic && attached.get() ? Arrays.asList(viewport, bar, probe) : Arrays.asList(viewport, probe)));
                context.mountOwned(root);
            });
            page.frame();
            findAutomaticBar(automatic);
            domain.execute(() -> runtime.focusRouter().clear());
            page.frame();
        }
        void findAutomaticBar(boolean automatic) {
            if (automatic) domain.execute(() -> {
                for (SceneNode candidate : runtime.scrollDecorations(viewport)) {
                    LayoutBox box = page.pipeline().current().geometry.get(candidate).box;
                    if (horizontal ? box.width > box.height : box.height > box.width) bar = candidate;
                }
            });
        }
        void at(long now) { host.frameTime = now; page.frame(); }
        void setPolicy(MotionPolicy value) { domain.execute(() -> policy.set(value)); at(host.frameTime + 1); }
        int offset() {
            int[] result = {0}; domain.execute(() -> result[0] = runtime.scrollOffset(viewport)); return result[0];
        }
        long nextInput() {
            long[] result = {0}; domain.execute(() -> result[0] = bar.control().nextInputNanos(bar)); return result[0];
        }
        LayoutBox barBox() {
            LayoutBox[] result = {null};
            domain.execute(() -> result[0] = page.pipeline().current().geometry.get(bar).box); return result[0];
        }
        void clickTrack() {
            LayoutBox box = barBox();
            page.pointer(ScenePointerEvent.down(0, horizontal ? box.x + box.width - 5 : box.x + box.width / 2,
                    horizontal ? box.y + box.height / 2 : box.y + box.height - 5).at(host.frameTime));
            page.pointer(ScenePointerEvent.up(0, 220, 140).at(host.frameTime));
            page.frame();
        }
        int alpha() {
            int result = 0;
            for (PaintCommand command : host.last.commands())
                if ((command.color & 0x00ffffff) == (THUMB & 0x00ffffff)) result = Math.max(result, command.color >>> 24);
            return result;
        }
        public void close() { page.close(); }
    }
}
