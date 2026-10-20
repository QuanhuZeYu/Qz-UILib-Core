package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

/** 字段装饰只由一个表面持有；编辑身份、作者主题与内距不随组合转移。 */
final class SceneFieldPaint {
    /** 仅正式单多行控件参与声明期装饰交接，不成为新的公共控件协议。 */
    interface Source {
        SceneFieldPaint fieldDecoration();
    }

    private final ReadableSignal<SceneTheme> theme;
    private final boolean framed;
    private final SceneControlMotion fill = new SceneControlMotion(), edge = new SceneControlMotion();
    private final Frame frame = new Frame();
    private SceneNode editor;
    private WeakReference<SceneNode> surface;
    private int radius;

    SceneFieldPaint(ReadableSignal<SceneTheme> theme, boolean framed) {
        this.theme = theme;
        this.framed = framed;
    }

    void install(SceneNode node) {
        editor = node;
        if (!framed) return;
        node.bind(NodeBinding.value(theme, (self, value) -> radius = value.metrics(SceneControlMetrics.Kind.FIELD).radius));
        fill.install(node, () -> theme.get().feedbackMotion, self -> fillColor());
        edge.install(node, () -> theme.get().feedbackMotion, self -> edgeColor(), "control.edge");
    }

    /** 组合只交接装饰的绘制位置；原编辑节点继续持有反馈动效、焦点及显式声明。 */
    static SceneFieldPaint take(SceneNode editor, SceneNode surface) {
        if (!(editor.control() instanceof Source)) return null;
        SceneFieldPaint decoration = ((Source) editor.control()).fieldDecoration();
        if (!decoration.framed) return null;
        decoration.surface = new WeakReference<>(surface);
        return decoration;
    }

    ReadableSignal<SceneTheme> theme() { return theme; }

    private boolean owns(SceneNode node) {
        SceneNode candidate = surface == null ? null : surface.get();
        // move/卸载不延续旧装饰归属；只读取当前唯一逻辑树和挂载事实，不补建另一套归属状态。
        return contains(candidate, editor) ? node == candidate : node == editor;
    }

    static boolean contains(SceneNode surface, SceneNode editor) {
        if (surface == null || surface.runtime() == null || surface.runtime() != editor.runtime()) return false;
        for (SceneNode at = editor.parent(); at != null; at = at.parent()) if (at == surface) return true;
        return false;
    }

    private int fillColor() {
        return SceneControlMotion.enabled(editor) ? theme.get().panel : theme.get().disabled;
    }

    private int edgeColor() {
        return SceneControlMotion.enabled(editor) && editor.runtime() != null
                && editor.runtime().focusRouter().focused().get() == editor
                ? theme.get().focus : theme.get().button;
    }

    void prepare(SceneNode node, SceneGeometryView geometry) {
        if (!framed || !owns(node)) return;
        SceneTheme colors = theme.get();
        boolean enabled = SceneControlMotion.enabled(editor);
        boolean focused = enabled && editor.runtime() != null && editor.runtime().focusRouter().focused().get() == editor;
        frame.prepare(geometry.get(node), geometry, radius,
                fill.color(enabled ? colors.panel : colors.disabled), edge.color(focused ? colors.focus : colors.button));
    }

    void paint(SceneNode node, PaintPlanBuilder target) {
        if (framed && owns(node)) frame.paint(target);
    }

    /** 仅在正式绘制准备中更新几何/命令缓存；paint 只追加不可变载荷。 */
    static final class Frame {
        private static final int SAMPLES = 4, SAMPLE_COUNT = SAMPLES * SAMPLES;
        private int preparedRadius = -1, maskRadius = -1;
        private int[] innerCorner = new int[0], ringCorner = new int[0];
        private LayoutBox box, clip;
        private List<PaintCommand> commands = Collections.emptyList();
        private int fillColor, edgeColor;

        void prepare(SceneGeometryView.Entry entry, SceneGeometryView geometry, int requested, int fill, int edge) {
            double scale = geometry.coordinateTransform().maximumScale();
            if (entry == null || scale == 0 || entry.box.width <= 0 || entry.box.height <= 0) {
                commands = Collections.emptyList();
                box = null;
                return;
            }
            int radius = Math.max(0, Math.min(requested, Math.min(entry.box.width, entry.box.height) / 2));
            if (maskRadius != radius) {
                coverage(radius);
                maskRadius = radius;
            }
            if (!same(box, entry.box) || preparedRadius != radius || !same(clip, entry.clip)
                    || fillColor != fill || edgeColor != edge) {
                PaintPlanBuilder prepared = new PaintPlanBuilder();
                surface(prepared, entry.box, entry.clip, fill, edge, radius);
                commands = prepared.build().commands();
                fillColor = fill;
                edgeColor = edge;
            }
            box = entry.box;
            clip = entry.clip;
            preparedRadius = radius;
        }

        void paint(PaintPlanBuilder target) {
            for (int i = 0; i < commands.size(); i++) target.add(commands.get(i));
        }

        private void coverage(int radius) {
            innerCorner = new int[radius * radius];
            ringCorner = new int[radius * radius];
            double outer = (double) radius * radius, inner = (double) (radius - 1) * (radius - 1);
            // 延续 ScenePaintShapes 的圆方程和 4x4 采样，内区与 ring 的几何样本互斥。
            for (int y = 0; y < radius; y++) for (int x = 0; x < radius; x++)
                for (int sy = 0; sy < SAMPLES; sy++) for (int sx = 0; sx < SAMPLES; sx++) {
                    double dx = radius - x - (sx + .5) / SAMPLES, dy = radius - y - (sy + .5) / SAMPLES;
                    double squared = dx * dx + dy * dy;
                    if (squared <= inner) innerCorner[y * radius + x]++;
                    else if (squared <= outer) ringCorner[y * radius + x]++;
                }
        }

        private void surface(PaintPlanBuilder target, LayoutBox b, LayoutBox clip, int fill, int edge, int r) {
            int inset = Math.max(1, r), border = sampled(fill, edge, 0, SAMPLE_COUNT);
            // 中段、直边及四角的像素区域互不覆盖；radius 0 与单像素尺寸沿同一分区处理。
            rect(target, clip, fill, b.x + 1, b.y + inset, b.width - 2, b.height - inset * 2);
            rect(target, clip, fill, b.x + r, b.y + 1, b.width - r * 2, r - 1);
            rect(target, clip, fill, b.x + r, b.y + b.height - r, b.width - r * 2, r - 1);
            rect(target, clip, border, b.x + r, b.y, b.width - r * 2, 1);
            if (b.height > 1) rect(target, clip, border, b.x + r, b.y + b.height - 1, b.width - r * 2, 1);
            rect(target, clip, border, b.x, b.y + inset, 1, b.height - inset * 2);
            if (b.width > 1) rect(target, clip, border, b.x + b.width - 1, b.y + inset, 1, b.height - inset * 2);
            for (int y = 0; y < r; y++) {
                int start = 0, color = sampled(fill, edge, innerCorner[y * r], ringCorner[y * r]);
                for (int x = 1; x <= r; x++) {
                    int next = x == r ? 0 : sampled(fill, edge, innerCorner[y * r + x], ringCorner[y * r + x]);
                    if (x == r || next != color) {
                        // 同色内区合并为行段，避免圆角内部展开成逐像素命令。
                        rect(target, clip, color, b.x + start, b.y + y, x - start, 1);
                        rect(target, clip, color, b.x + b.width - x, b.y + y, x - start, 1);
                        rect(target, clip, color, b.x + start, b.y + b.height - y - 1, x - start, 1);
                        rect(target, clip, color, b.x + b.width - x, b.y + b.height - y - 1, x - start, 1);
                        start = x;
                        color = next;
                    }
                }
            }
        }

        private static int sampled(int fill, int edge, int inner, int ring) {
            int fillAlpha = fill >>> 24, edgeAlpha = edge >>> 24;
            // 先在预乘域求 i*fill + r*(edge over fill)，再编码现有 straight ARGB 载荷。
            // 不可把完整外轮廓 fill 与独立 ring 再次 source-over：r8 的 (3,1) 仅有 9/16 ring，
            // 不透明边色必须完全遮住这些样本的 panel；半透明边色下仍保留合法的 panel 透色。
            int fillWeight = fillAlpha * (inner * 255 + ring * (255 - edgeAlpha));
            int edgeWeight = edgeAlpha * ring * 255, weight = fillWeight + edgeWeight;
            int denominator = SAMPLE_COUNT * 255;
            int alpha = (weight + denominator / 2) / denominator;
            if (alpha == 0) return 0;
            int color = alpha << 24;
            for (int shift = 0; shift < 24; shift += 8) {
                int channel = ((fill >>> shift & 255) * fillWeight + (edge >>> shift & 255) * edgeWeight + weight / 2) / weight;
                color |= channel << shift;
            }
            return color;
        }

        private static void rect(PaintPlanBuilder target, LayoutBox clip, int color, int x, int y, int width, int height) {
            if (width > 0 && height > 0 && (color >>> 24) != 0)
                target.add(PaintCommand.rect(new LayoutBox(x, y, width, height), clip, color));
        }

        private static boolean same(LayoutBox a, LayoutBox b) {
            return a == b || a != null && b != null && a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height;
        }
    }
}

