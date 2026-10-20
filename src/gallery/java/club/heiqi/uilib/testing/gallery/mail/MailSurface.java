package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 案例的复合表面沿正式流布局，ROW 内仅补明确的垂直居中；text 是完整动作名称，子内容负责绘制。 */
class MailSurface implements SceneControl {
    private final ReadableSignal<SceneTheme> theme;
    private final ReadableSignal<Integer> color;
    private final float radius;
    private final boolean feedback;
    private Signal<Integer> presentedColor;
    MailSurface(ReadableSignal<SceneTheme> theme, ReadableSignal<Integer> color, float radius) {
        this(theme,color,radius,true);
    }
    MailSurface(ReadableSignal<SceneTheme> theme, ReadableSignal<Integer> color, float radius, boolean feedback) {
        this.theme = theme; this.color = color; this.radius = radius; this.feedback=feedback;
    }
    /** 与正式控件一样在声明期安装；挂载拥有观察及轨迹，绘制只读取发布后的颜色。 */
    void install(SceneNode node) {
        if (!feedback) return;
        node.bind((runtime, self, owner) -> {
            presentedColor = runtime.domain().signal(color.get());
            return SceneMotion.color(() -> {
                runtime.interactionRevision().get();
                runtime.pointerRouter().hoverTarget().get();
                runtime.pointerRouter().pressedTarget().get();
                runtime.keyboardPressedTarget().get();
                return targetColor(self);
            }, () -> self.feedbackMotion() == null ? fillMotion(self) : self.feedbackMotion(),
                    "mail.surface.fill", (n, value) -> {
                        presentedColor.set(value);
                        runtime.invalidatePaint();
                    }).activate(runtime, self, owner);
        });
    }
    protected int targetColor(SceneNode node) {
        if (node.clickAction() != null && node.runtime().interactive(node)) {
            if (node.runtime().pressed(node)) return theme.get().pressed;
            if (node.runtime().pointerRouter().hoverTarget().get() == node) return theme.get().hover;
        }
        return color.get();
    }
    protected MotionSpec fillMotion(SceneNode node) {
        return node.clickAction() == null || node.runtime().pressed(node) ? MotionSpec.instant() : MailMotion.STANDARD;
    }
    protected final int presentedColor() { return presentedColor == null ? color.get() : presentedColor.get(); }
    @Override public TextLayout layoutText(SceneNode n, SceneTextMeasurer text, int width) {
        return text.layout("", n.style(), width);
    }
    @Override public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text) {
        return new MeasuredSize(0, 0);
    }
    @Override public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text, SceneChildMeasurer children) {
        return SceneFlowLayout.measure(n, c, children);
    }
    @Override public ChildPlacements arrange(SceneNode n, LayoutBox box, SceneChildMeasurer children) {
        ChildPlacements flow = SceneFlowLayout.arrange(n, box, children);
        if (n.axis() != SceneNode.Axis.ROW) return flow;
        List<ChildPlacements.Entry> entries = new ArrayList<>();
        for (ChildPlacements.Entry entry : flow.entries()) {
            LayoutBox b = entry.relativeBox;
            entries.add(new ChildPlacements.Entry(entry.child, new LayoutBox(b.x,
                    n.padding() + Math.max(0, (box.height - n.padding() * 2 - b.height) / 2), b.width, b.height)));
        }
        return new ChildPlacements(entries);
    }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode n) { return Arrays.asList(theme, this::presentedColor); }
    @Override public void paint(SceneNode n, SceneGeometryView g, PaintPlanBuilder target) {
        SceneGeometryView.Entry e = g.get(n); if (e == null) return;
        SceneTheme t = theme.get(); int background = presentedColor();
        if ((background >>> 24) != 0) paintBackground(n,e,target,background,
                Math.max(0, Math.round(t.textStyle.logicalEm * radius)));
    }
    protected void paintBackground(SceneNode node,SceneGeometryView.Entry entry,PaintPlanBuilder target,int color,int radius) {
        ScenePaintShapes.rounded(target,entry.box,entry.clip,color,radius);
    }
    @Override public void paintAfterChildren(SceneNode n, SceneGeometryView g, PaintPlanBuilder target) {
        if (!feedback || !n.runtime().focusRouter().focusVisible(n)) return;
        SceneGeometryView.Entry e = g.get(n); if (e == null) return;
        int color = theme.get().focus;
        target.add(PaintCommand.rect(new LayoutBox(e.box.x, e.box.y, e.box.width, 2), e.clip, color));
        target.add(PaintCommand.rect(new LayoutBox(e.box.x, e.box.y + e.box.height - 2, e.box.width, 2), e.clip, color));
    }
}
