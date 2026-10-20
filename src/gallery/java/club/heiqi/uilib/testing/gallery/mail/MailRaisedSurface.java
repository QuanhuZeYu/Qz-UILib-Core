package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Arrays;
import java.util.List;

/** 搜索聚焦与主动作悬停只声明背景和阴影目标，不改变父布局或命中。 */
class MailRaisedSurface extends MailSurface {
    private final ReadableSignal<SceneTheme> theme;
    private final ReadableSignal<Integer> color;
    private final ReadableSignal<Boolean> raised;
    private final float radius;
    private final MotionSpec backgroundMotion, shadowMotion;
    private Signal<Float> shadow;

    MailRaisedSurface(ReadableSignal<SceneTheme> theme, ReadableSignal<Integer> color, float radius,
            ReadableSignal<Boolean> raised, MotionSpec backgroundMotion, MotionSpec shadowMotion) {
        super(theme, color, radius);
        this.theme = theme; this.color = color; this.radius = radius; this.raised = raised;
        this.backgroundMotion = backgroundMotion; this.shadowMotion = shadowMotion;
    }
    @Override void install(SceneNode node) {
        super.install(node);
        node.bind((runtime, self, owner) -> {
            shadow = runtime.domain().signal(0f);
            return NodeBinding.motion(() -> {
                runtime.interactionRevision().get();
                SceneNode hover = runtime.pointerRouter().hoverTarget().get();
                return (raised == null ? runtime.interactive(self) && hover == self : raised.get()) ? 1f : 0f;
            }, () -> self.feedbackMotion() == null ? shadowMotion : self.feedbackMotion(),
                    false, "mail.surface.shadow", (n, value) -> {
                shadow.set(value);
                runtime.invalidatePaint();
            }).activate(runtime, self, owner);
        });
    }
    @Override protected int targetColor(SceneNode node) {
        return node.clickAction() != null && node.runtime().interactive(node) && node.runtime().pressed(node)
                ? theme.get().pressed : color.get();
    }
    @Override protected MotionSpec fillMotion(SceneNode node) {
        return node.runtime().pressed(node) ? MotionSpec.instant() : backgroundMotion;
    }
    private float shadow() { return shadow == null ? 0 : shadow.get(); }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) {
        return Arrays.asList(theme, this::presentedColor, this::shadow);
    }
    @Override public void paint(SceneNode n, SceneGeometryView geometry, PaintPlanBuilder target) {
        SceneGeometryView.Entry e = geometry.get(n); if (e == null) return;
        int opacity = Math.round(Math.max(0, Math.min(1, shadow())) * 255);
        int r = Math.round(theme.get().textStyle.logicalEm * radius);
        if (opacity > 0) for (int spread = 3; spread > 0; spread--) {
            int alpha = opacity * (spread == 1 ? 18 : spread == 2 ? 9 : 4) / 255;
            ScenePaintShapes.rounded(target, new LayoutBox(e.box.x - spread, e.box.y + 1 - spread,
                    e.box.width + spread * 2, e.box.height + spread * 2), e.clip, (alpha << 24) | 0x3c4043, r + spread);
        }
        ScenePaintShapes.rounded(target, e.box, e.clip, presentedColor(), r);
    }
}
