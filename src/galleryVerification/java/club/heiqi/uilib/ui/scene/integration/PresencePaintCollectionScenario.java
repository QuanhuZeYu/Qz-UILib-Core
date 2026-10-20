package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.PresenceOptions;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 真实页面的嵌套退场候选；计数只证明采集工作量，不推算帧时间。 */
public final class PresencePaintCollectionScenario {
    public static void main(String[] args) {
        if (args.length == 0) verify();
        else deadlineInput(Boolean.parseBoolean(args[0]));
    }

    static void verify() {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        host.width = 240; host.height = 240;
        Signal<List<Integer>>[] rows = new Signal[1];
        Signal<Integer>[] color = new Signal[1];
        List<CountingControl> controls = new ArrayList<>();
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            rows[0] = domain.signal(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
            color[0] = domain.signal(0xff13579b);
            PresenceOptions exit = new PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR));
            context.mountOwned(new SceneNode().bind(NodeBinding.repeat(rows[0], value -> value, value -> {
                CountingControl outer = new CountingControl(color[0]), inner = new CountingControl(color[0]);
                controls.add(outer); controls.add(inner);
                SceneNode child = new SceneNode().control(inner).size(Size.fixed(40), Size.fixed(10))
                        .bind(SceneMotion.presence(domain.signal(true), () -> exit));
                return new SceneNode().control(outer).size(Size.fixed(80), Size.fixed(20)).add(child);
            }, () -> exit)));
        })) {
            page.frame();
            once(controls, "首次主绘与嵌套候选");
            controls.forEach(CountingControl::reset);
            domain.execute(() -> color[0].set(0xff2468ac));
            // 输入准备先捕获退场片段，随后主绘必须消费同批片段，不能再调控件。
            page.pointer(ScenePointerEvent.move(-1, -1));
            page.frame();
            once(controls, "输入捕获后主绘");
            controls.forEach(CountingControl::reset);
            domain.execute(() -> rows[0].set(Collections.emptyList()));
            page.frame();
            for (CountingControl control : controls)
                require(control.paint == 0 && control.prepare == 0, "退场访问了已结束挂载的控件");
            domain.execute(() -> require(page.pipeline().current().paint.commands().stream()
                    .anyMatch(command -> (command.color & 0xffffff) == 0x2468ac), "退场未保留最新被动画面"));
            System.out.println("presenceCollection controls=" + controls.size() + " prepare/paint/after/overlay=1 per preparation; exit livePaint=0");
        }
        deadlineInput(false);
        deadlineInput(true);
    }

    private static void deadlineInput(boolean presence) {
        ReactiveDomain domain = ReactiveDomain.direct();
        SoftwareSceneScenario.SoftwareHost host = new SoftwareSceneScenario.SoftwareHost();
        DeadlineControl control = new DeadlineControl();
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            SceneNode node = new SceneNode().control(control).size(Size.fixed(40), Size.fixed(10));
            if (presence) node.bind(SceneMotion.presence(domain.signal(true),
                    () -> new PresenceOptions(MotionSpec.tween(1_000_000_000L, MotionSpec.LINEAR))));
            context.mountOwned(node);
        })) {
            page.frame();
            require(host.last.commands().stream().anyMatch(command -> command.color == 0xff13579b), "期限探针未绘制初始颜色");
            // 首批控件已报告到期；页外MOVE不改声明，却先准备退场片段。
            // 候选控件改报未来期限，也不能使尚未合成的整页画面被当成已更新。
            page.pointer(ScenePointerEvent.move(-1, -1));
            page.frame();
            require(host.last.commands().stream().anyMatch(command -> command.color == 0xff2468ac),
                    "输入捕获吞掉未兑现的整页绘制期限 presence=" + presence + " preparations=" + control.prepare);
            require(control.prepare == 2 && control.paint == 2, "期限更新没有复用本批局部片段");
            page.frame();
            require(control.prepare == 2 && control.paint == 2, "整页计划合成后仍保留待绘标记");
            System.out.println("presenceDeadline presence=" + presence + " inputBeforeFrame=true preparations=2 latestColor=true settledNextFrame=0");
        }
    }

    private static void once(List<CountingControl> controls, String phase) {
        for (CountingControl control : controls)
            require(control.prepare == 1 && control.paint == 1 && control.after == 1 && control.overlay == 1,
                    phase + ": prepare=" + control.prepare + " paint=" + control.paint
                            + " after=" + control.after + " overlay=" + control.overlay);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static final class DeadlineControl implements SceneControl {
        int prepare, paint;
        public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) { return new MeasuredSize(40, 10); }
        public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            return new ChildPlacements(Collections.emptyList());
        }
        public void preparePaint(SceneNode node, SceneGeometryView geometry, SceneTextMeasurer text, long revision) { prepare++; }
        public long nextPaintNanos(SceneNode node) { return prepare == 1 ? Long.MIN_VALUE : Long.MAX_VALUE - 1; }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            paint++;
            SceneGeometryView.Entry entry = geometry.get(node);
            target.add(PaintCommand.rect(entry.box, entry.clip, prepare == 1 ? 0xff13579b : 0xff2468ac));
        }
    }
    private static final class CountingControl implements SceneControl {
        final Signal<Integer> color;
        int prepare, paint, after, overlay;
        CountingControl(Signal<Integer> color) { this.color = color; }
        void reset() { prepare = paint = after = overlay = 0; }
        public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Collections.singletonList(color); }
        public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) { return new MeasuredSize(40, 10); }
        public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            List<ChildPlacements.Entry> entries = new ArrayList<>();
            for (SceneNode child : node.children()) entries.add(new ChildPlacements.Entry(child, new LayoutBox(0, 0, 40, 10)));
            return new ChildPlacements(entries);
        }
        public void preparePaint(SceneNode node, SceneGeometryView geometry, SceneTextMeasurer text, long revision) { prepare++; }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            paint++;
            SceneGeometryView.Entry entry = geometry.get(node);
            require(SceneGeometryView.sameBox(entry.box, entry.layoutBox), "控件未消费原始局部外盒");
            target.add(PaintCommand.rect(entry.box, entry.clip, color.get()));
        }
        public void paintAfterChildren(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) { after++; }
        public void paintOverlay(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target, SceneTextMeasurer text) { overlay++; }
    }
}
