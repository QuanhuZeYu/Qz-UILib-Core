package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

/** 只负责标签条外观；标题下限与剩余宽度交给共同 Flow，底片直接消费同次排列。 */
final class SceneTabsHeader implements SceneControl {
    private final SceneNode indicator;
    private final ReadableSignal<SceneTabs.Variant> variant;
    private final ReadableSignal<SceneAppearance.Palette> colors;
    private final ReadableSignal<Integer> radius;
    private final ReadableSignal<SceneNode> selected;

    private SceneTabsHeader(SceneNode indicator, ReadableSignal<SceneTabs.Variant> variant,
            ReadableSignal<SceneAppearance.Palette> colors, ReadableSignal<Integer> radius, ReadableSignal<SceneNode> selected) {
        this.indicator = indicator; this.variant = variant; this.colors = colors; this.radius = radius; this.selected = selected;
    }

    static SceneNode create(ScenePageContext page, ReadableSignal<SceneTabs.Variant> variant,
            ReadableSignal<SceneTabs.Options> options, ReadableSignal<SceneTheme> theme,
            ReadableSignal<SceneNode> selected) {
        Owner owner = page.scope().child();
        ReadableSignal<SceneAppearance.Palette> colors = page.domain().computed(owner, () -> new SceneAppearance.Palette(theme.get()));
        ReadableSignal<Integer> radius = page.domain().computed(owner, () -> theme.get().metrics(
                variant.get() == SceneTabs.Variant.SEGMENTED ? SceneControlMetrics.Kind.MODERN_BUTTON : SceneControlMetrics.Kind.TABS).radius);
        ReadableSignal<MotionSpec> motion = () -> variant.get() == SceneTabs.Variant.UNDERLINE
                ? MotionSpec.instant() : options.get().indicatorMotion == null
                        ? theme.get().spatialMotion : options.get().indicatorMotion;
        SceneNode graphic = new SceneNode().size(Size.fill(1), Size.fill(1))
                .control(new Indicator(colors, radius)).bind(SceneMotion.layoutSize(motion));
        // 位置槽不声明尺寸轨迹；宽度在其子图形内变化，避免尺寸参与排版时抵消底片的位移轨迹。
        SceneNode slot = new SceneNode().size(Size.natural(), Size.natural()).control(new IndicatorSlot(graphic)).add(graphic)
                .bind(NodeBinding.semantics(() -> SceneSemantics.builder().excluded(true).build()));
        return new SceneNode().axis(SceneNode.Axis.ROW).size(Size.natural(), Size.natural())
                .control(new SceneTabsHeader(slot, variant, colors, radius, selected)).own(owner)
                .bind(SceneMotion.layoutPositions(motion));
    }

    static SceneNode indicator(SceneNode header) { return ((SceneTabsHeader) header.control()).indicator; }

    @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(variant); }
    @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Arrays.asList(variant, selected); }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(variant, colors, radius); }
    @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
    @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
        return SceneFlowLayout.measure(node, limits, flow(children,
                variant.get() == SceneTabs.Variant.SEGMENTED && limits.availableWidth != Integer.MAX_VALUE));
    }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        ChildPlacements labels = SceneFlowLayout.arrange(node, box, flow(children, variant.get() == SceneTabs.Variant.SEGMENTED));
        LayoutBox target = new LayoutBox(0, 0, 0, 0);
        if (variant.get() == SceneTabs.Variant.SEGMENTED) {
            SceneNode active = selected.get();
            for (ChildPlacements.Entry item : labels.entries()) if (item.child == active) { target = item.relativeBox; break; }
        }
        List<ChildPlacements.Entry> result = new ArrayList<>();
        result.add(new ChildPlacements.Entry(indicator, target));
        result.addAll(labels.entries());
        return new ChildPlacements(result);
    }
    private SceneChildMeasurer flow(SceneChildMeasurer children, boolean stretch) {
        List<SceneNode> labels = new ArrayList<>(children.flowChildren());
        labels.remove(indicator);
        Map<SceneNode, Size> widths = new IdentityHashMap<>();
        if (stretch) for (SceneNode label : labels) widths.put(label, Size.fill(1).bounds(
                children.measureChild(label, new Constraints(Integer.MAX_VALUE, Integer.MAX_VALUE)).width, Integer.MAX_VALUE));
        return new SceneChildMeasurer() {
            public List<SceneNode> flowChildren() { return labels; }
            public Size sizeIntent(SceneNode child, SceneNode.Axis axis) {
                if (stretch && axis == SceneNode.Axis.ROW)
                    return widths.get(child);
                return children.sizeIntent(child, axis);
            }
            public MeasuredSize measureChild(SceneNode child, Constraints limits) {
                // 标题始终以完整自然宽度参与分配；不足的空间归外层原横向视口。
                return children.measureChild(child, new Constraints(Integer.MAX_VALUE, limits.availableHeight));
            }
        };
    }
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
        if (variant.get() != SceneTabs.Variant.SEGMENTED) return;
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry == null) return;
        ScenePaintShapes.rounded(paint, entry.box, entry.clip, colors.get().surface, radius.get());
    }

    private static final class IndicatorSlot implements SceneControl {
        private final SceneNode graphic;
        IndicatorSlot(SceneNode graphic) { this.graphic = graphic; }
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            Size width = children.sizeIntent(graphic, SceneNode.Axis.ROW), height = children.sizeIntent(graphic, SceneNode.Axis.COLUMN);
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(graphic,
                    new LayoutBox(0, 0, width.constrain(width.mode == Size.Mode.FIXED ? width.value : box.width),
                            height.constrain(height.mode == Size.Mode.FIXED ? height.value : box.height)))));
        }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
    }
    private static final class Indicator implements SceneControl {
        private final ReadableSignal<SceneAppearance.Palette> colors;
        private final ReadableSignal<Integer> radius;
        Indicator(ReadableSignal<SceneAppearance.Palette> colors, ReadableSignal<Integer> radius) { this.colors = colors; this.radius = radius; }
        public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(colors, radius); }
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return new ChildPlacements(Collections.emptyList()); }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null || entry.box.width == 0 || entry.box.height == 0) return;
            SceneAppearance.Palette value = colors.get();
            int corner = radius.get();
            ScenePaintShapes.rounded(paint, entry.box, entry.clip, value.border, corner);
            ScenePaintShapes.rounded(paint, new LayoutBox(entry.box.x + 1, entry.box.y + 1,
                    Math.max(0, entry.box.width - 2), Math.max(0, entry.box.height - 2)), entry.clip, value.background, Math.max(0, corner - 1));
        }
    }
}
