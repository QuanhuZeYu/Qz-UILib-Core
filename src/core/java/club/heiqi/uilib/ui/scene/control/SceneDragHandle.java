package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/** 标准拖动把手：图形和留白共同占位，输入所有权仍归声明它的拖动或排序控件。 */
public final class SceneDragHandle extends SceneSymbolControl {
    private ReadableSignal<Integer> minimum;
    private SceneDragHandle(ReadableSignal<SceneTheme> theme) {
        super(theme, false); minimum = () -> theme.get().spacing(44);
    }

    public static SceneNode create(ReadableSignal<SceneTheme> theme) {
        SceneDragHandle control = new SceneDragHandle(theme);
        return new SceneNode().control(control).size(Size.natural(), Size.natural())
                .bind((runtime, node, owner) -> {
                    // 按实际最小尺寸变化发布，过滤主题纯配色失效。
                    club.heiqi.uilib.ui.reactive.Signal<Integer> minimum = runtime.domain().signal(theme.get().spacing(44));
                    control.minimum = minimum;
                    return NodeBinding.value(theme, (SceneNode self, SceneTheme value) -> {
                        SceneControlMetrics metrics = value.metrics(SceneControlMetrics.Kind.SYMBOL);
                        self.defaultStyle(metrics.textStyle).defaultPadding(metrics.padding);
                        minimum.set(value.spacing(44));
                    }).activate(runtime, node, owner);
                });
    }

    @Override public java.util.List<ReadableSignal<?>> measureDependencies(SceneNode node) {
        return java.util.Collections.singletonList(minimum);
    }

    @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
        // 热区使用主题密度与控件大小共同决定的布局盒，并遵守当前裁剪。
        int side = Math.max(symbolSize(node), theme.get().spacing(44) - node.padding() * 2);
        return new MeasuredSize(side, side);
    }

    @Override void symbol(LayoutBox box, PaintPlanBuilder paint, LayoutBox clip, int color, boolean enabled) {
        int dot = Math.max(1, Math.round(box.width / 8f));
        int gap = Math.max(1, dot);
        int side = dot * 2 + gap;
        int x = box.x + (box.width - side) / 2, y = box.y + (box.height - side) / 2;
        for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++)
            ScenePaintShapes.rounded(paint, new LayoutBox(x + column * (dot + gap),
                    y + row * (dot + gap), dot, dot), clip, color, dot / 2);
    }
}
