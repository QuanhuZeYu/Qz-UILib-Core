package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.geometry.SceneShape;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 两个正式矢量共享一个动作根；共同几何分别命中填充与描边，不为命中合并网格。 */
final class SceneVectorAction implements SceneControl {
    private final ReadableSignal<SceneAppearance.Palette> colors;
    private final ReadableSignal<SceneVector.Model> model;
    private final ReadableSignal<Boolean> selected;
    private final ReadableSignal<SceneShape> shape;
    private final SceneControlMotion fillMotion = new SceneControlMotion(), strokeMotion = new SceneControlMotion();
    private SceneNode fill, stroke;

    private SceneVectorAction(ReadableSignal<SceneAppearance.Palette> colors, ReadableSignal<SceneVector.Model> model,
            ReadableSignal<Boolean> selected, ReadableSignal<SceneShape> shape) {
        this.colors = colors; this.model = model; this.selected = selected; this.shape = shape;
    }
    static SceneNode create(ScenePageContext page, ReadableSignal<String> name, ReadableSignal<SceneVector.Model> source,
            ReadableSignal<SceneAppearance.Palette> colors, Runnable action, ReadableSignal<Boolean> selected) {
        page.domain().checkWrite();
        Objects.requireNonNull(name, "动作名称"); Objects.requireNonNull(source, "矢量声明");
        Objects.requireNonNull(action, "动作"); Objects.requireNonNull(selected, "选中来源");
        Owner owner = page.scope().child();
        ReadableSignal<SceneVector.Model> model = page.domain().computed(owner, () -> Objects.requireNonNull(source.get(), "矢量声明值"));
        ReadableSignal<SceneShape> shape = page.domain().computed(owner, () -> {
            SceneVector.Model value = model.get();
            // 根保留实际主轮廓；描边外半仍由同批 stroke 子几何交给同一动作身份。
            return SceneShape.vector(value.path, value.viewX, value.viewY, value.viewWidth, value.viewHeight,
                    value.fill != null, value.fill == null ? value.stroke : null);
        });
        SceneVectorAction control = new SceneVectorAction(colors, model, selected, shape);
        SceneNode node = new SceneNode().size(Size.natural(), Size.natural()).control(control).own(owner)
                .focusable(true).clickFocus(true).onClick(action)
                .bind(NodeBinding.value(name, SceneNode::text))
                .bind(NodeBinding.semantics(() -> SceneSemantics.builder(SceneSemantics.Role.BUTTON)
                        .name(name.get()).selected(selected.get()).actions(SceneSemantics.Action.FOCUS, SceneSemantics.Action.ACTIVATE).build()));
        control.fillMotion.install(node, () -> colors.get().source.feedbackMotion, control::fillColor, "vector.fill");
        control.strokeMotion.install(node, () -> colors.get().source.feedbackMotion, control::strokeColor, "vector.stroke");
        ReadableSignal<SceneVector.Model> fill = page.domain().computed(owner, () -> control.part(true));
        ReadableSignal<SceneVector.Model> stroke = page.domain().computed(owner, () -> control.part(false));
        // 动画色不能装回 Model：Computed 会先传播失效，几何值相等也无法阻止测量依赖标脏。
        ReadableSignal<Integer> fillColor = () -> control.fillMotion.color(control.fillColor(node));
        ReadableSignal<Integer> strokeColor = () -> control.strokeMotion.color(control.strokeColor(node));
        control.fill = decoration(SceneVector.create(page, fill, fillColor, strokeColor));
        control.stroke = decoration(SceneVector.create(page, stroke, fillColor, strokeColor));
        return node.add(control.fill).add(control.stroke);
    }
    private static SceneNode decoration(SceneNode vector) {
        return vector.size(Size.fill(1), Size.fill(1))
                .bind(NodeBinding.semantics(() -> SceneSemantics.builder().excluded(true).build()));
    }
    private SceneVector.Model part(boolean filled) {
        SceneVector.Model value = model.get();
        return new SceneVector.Model(value.path, value.viewX, value.viewY, value.viewWidth, value.viewHeight,
                filled ? value.fill : null,
                filled ? null : value.strokeColor,
                filled ? null : value.stroke);
    }
    private boolean focused(SceneNode node) { return node.runtime() != null && node.runtime().focusRouter().focusVisible(node); }
    private boolean pressed(SceneNode node) { return node.runtime() != null && node.runtime().pressed(node); }
    private boolean hovered(SceneNode node) { return node.runtime() != null && node.runtime().pointerRouter().hoverTarget().get() == node; }
    private int fillColor(SceneNode node) {
        SceneAppearance.Palette value = colors.get();
        if (!SceneControlMotion.enabled(node)) return value.surface;
        if (selected.get() || pressed(node) || focused(node)) return value.selected;
        if (hovered(node)) return value.hover;
        Integer normal = model.get().fill;
        return normal == null ? 0 : normal;
    }
    private int strokeColor(SceneNode node) {
        SceneAppearance.Palette value = colors.get();
        if (!SceneControlMotion.enabled(node)) return value.border;
        if (focused(node) || hovered(node)) return value.muted;
        if (selected.get() || pressed(node)) return value.accent;
        Integer normal = model.get().strokeColor;
        return normal == null ? 0 : normal;
    }
    @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Collections.singletonList(shape); }
    @Override public SceneShape hitShape(SceneNode node) { return shape.get(); }
    @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
        return geometry.contains(fill, x, y) || geometry.contains(stroke, x, y) ? node : null;
    }
    @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
    @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
        return children.measureChild(fill, limits);
    }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        return new ChildPlacements(Arrays.asList(place(fill, box, children), place(stroke, box, children)));
    }
    private static ChildPlacements.Entry place(SceneNode child, LayoutBox box, SceneChildMeasurer children) {
        Size width = children.sizeIntent(child, SceneNode.Axis.ROW), height = children.sizeIntent(child, SceneNode.Axis.COLUMN);
        return new ChildPlacements.Entry(child, new LayoutBox(0, 0,
                width.constrain(width.mode == Size.Mode.FIXED ? width.value : box.width),
                height.constrain(height.mode == Size.Mode.FIXED ? height.value : box.height)));
    }
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
}
