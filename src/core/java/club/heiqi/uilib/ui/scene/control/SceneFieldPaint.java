package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
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
        private static final int SAMPLES = 4;
        private int preparedRadius = -1, maskRadius = -1;
        private int[] corner = new int[0];
        private LayoutBox box, clip;
        private List<PaintCommand> background = Collections.emptyList(), outline = Collections.emptyList();
        private int fillColor, edgeColor;

        void prepare(SceneGeometryView.Entry entry, SceneGeometryView geometry, int requested, int fill, int edge) {
            double scale = geometry.coordinateTransform().maximumScale();
            if (entry == null || scale == 0 || entry.box.width <= 0 || entry.box.height <= 0) {
                background = outline = Collections.emptyList();
                box = null;
                return;
            }
            int radius = Math.max(0, Math.min(requested, Math.min(entry.box.width, entry.box.height) / 2));
            boolean changed = !same(box, entry.box) || preparedRadius != radius;
            boolean maskChanged = maskRadius != radius;
            if (maskChanged) {
                corner = coverage(radius);
                maskRadius = radius;
            }
            boolean clipped = !same(clip, entry.clip);
            if (changed || clipped || fillColor != fill) {
                PaintPlanBuilder commands = new PaintPlanBuilder();
                ScenePaintShapes.rounded(commands, entry.box, entry.clip, fill, radius);
                background = commands.build().commands();
                fillColor = fill;
            }
            if (changed || clipped || maskChanged || edgeColor != edge) {
                PaintPlanBuilder commands = new PaintPlanBuilder();
                ring(commands, entry.box, entry.clip, edge, radius);
                outline = commands.build().commands();
                edgeColor = edge;
            }
            box = entry.box;
            clip = entry.clip;
            preparedRadius = radius;
        }

        void paint(PaintPlanBuilder target) {
            for (int i = 0; i < background.size(); i++) target.add(background.get(i));
            for (int i = 0; i < outline.size(); i++) target.add(outline.get(i));
        }

        private static int[] coverage(int radius) {
            int[] result = new int[radius * radius];
            double outer = (double) radius * radius, inner = (double) (radius - 1) * (radius - 1);
            // 与 ScenePaintShapes 相同的圆方程/4x4 coverage；只保留外圆减内圆的真实空心角区。
            for (int y = 0; y < radius; y++) for (int x = 0; x < radius; x++)
                for (int sy = 0; sy < SAMPLES; sy++) for (int sx = 0; sx < SAMPLES; sx++) {
                    double dx = radius - x - (sx + .5) / SAMPLES, dy = radius - y - (sy + .5) / SAMPLES;
                    double squared = dx * dx + dy * dy;
                    if (squared <= outer && squared > inner) result[y * radius + x]++;
                }
            return result;
        }

        private void ring(PaintPlanBuilder target, LayoutBox b, LayoutBox clip, int color, int r) {
            if ((color >>> 24) == 0) return;
            // 四条边及四个角互不覆盖；透明 panel 不会被整块边色填实。
            rect(target, clip, color, b.x + r, b.y, b.width - r * 2, 1);
            if (b.height > 1) rect(target, clip, color, b.x + r, b.y + b.height - 1, b.width - r * 2, 1);
            int inset = Math.max(1, r);
            rect(target, clip, color, b.x, b.y + inset, 1, b.height - inset * 2);
            if (b.width > 1) rect(target, clip, color, b.x + b.width - 1, b.y + inset, 1, b.height - inset * 2);
            for (int y = 0; y < r; y++) for (int x = 0; x < r; x++) {
                int alpha = ((color >>> 24) * corner[y * r + x] + SAMPLES * SAMPLES / 2) / (SAMPLES * SAMPLES);
                if (alpha == 0) continue;
                int sampled = color & 0xffffff | alpha << 24;
                rect(target, clip, sampled, b.x + x, b.y + y, 1, 1);
                rect(target, clip, sampled, b.x + b.width - x - 1, b.y + y, 1, 1);
                rect(target, clip, sampled, b.x + x, b.y + b.height - y - 1, 1, 1);
                rect(target, clip, sampled, b.x + b.width - x - 1, b.y + b.height - y - 1, 1, 1);
            }
        }

        private static void rect(PaintPlanBuilder target, LayoutBox clip, int color, int x, int y, int width, int height) {
            if (width > 0 && height > 0) target.add(PaintCommand.rect(new LayoutBox(x, y, width, height), clip, color));
        }

        private static boolean same(LayoutBox a, LayoutBox b) {
            return a == b || a != null && b != null && a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height;
        }
    }
}

