package club.heiqi.uilib.ui.render.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;
import club.heiqi.uilib.ui.scene.geometry.SceneMesh;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Draw;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.State;

/** 只合并相邻兼容图元；A/B/A绝不全局页分组。容量/宿主屏障自动输出封闭提交段。 */
public final class OrderedQuadCollector {
    private final int capacity;
    private final Consumer<OrderedQuadBatch> submit;
    private final List<Quad> quads = new ArrayList<>();
    private final List<Draw> draws = new ArrayList<>();
    private State active;
    private int first;
    private boolean submitting;

    public OrderedQuadCollector(int capacity, Consumer<OrderedQuadBatch> submit) {
        if (capacity <= 0 || capacity > Integer.MAX_VALUE / 6)
            throw new IllegalArgumentException("无效提交容量");
        this.capacity = capacity;
        this.submit = Objects.requireNonNull(submit, "submit");
    }

    public void add(State state, Quad quad) {
        if (submitting) throw new IllegalStateException("提交回调不能重入收集器");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(quad, "quad");
        // 原单位矩形路径保留图元与状态，不为既有文字/图像逐顶点分配或建立裁剪网格。
        if (state.transform.identity() && state.shapeClip.unbounded()) {
            addPrepared(state, quad);
            return;
        }
        SceneClipRegion region = state.effectiveClip();
        if (region.empty()) return;
        Quad world = state.transform.identity() ? quad : transformed(quad, state.transform);
        if (!visible(region.bounds(), world)) return;
        State prepared = new State(state.material, state.resource, state.generation, region.bounds());
        if (region.unbounded() || region.rectangular()) {
            addPrepared(prepared, world);
            return;
        }
        // 与原EBO相同的两个三角形分别裁剪，采样属性和各片段顺序不跨图元重排。
        SceneMesh mask = region.mesh();
        clipTriangle(mask, prepared, world, world.x0, world.y0, world.s0, world.t0,
                world.x1, world.y1, world.s1, world.t1, world.x2, world.y2, world.s2, world.t2);
        clipTriangle(mask, prepared, world, world.x0, world.y0, world.s0, world.t0,
                world.x2, world.y2, world.s2, world.t2, world.x3, world.y3, world.s3, world.t3);
    }

    /** 只接收已在世界坐标中准备的载荷，裁剪输出不能再次经过公开add的变换。 */
    private void addPrepared(State state, Quad quad) {
        if (!visible(state.clip, quad)) return;
        if (quads.size() == capacity) flush();
        if (!state.equals(active)) {
            endDraw();
            active = state;
            first = quads.size();
        }
        quads.add(quad);
    }

    private static Quad transformed(Quad q, SceneTransform t) {
        return new Quad((float) t.x(q.x0, q.y0), (float) t.y(q.x0, q.y0),
                (float) t.x(q.x1, q.y1), (float) t.y(q.x1, q.y1),
                (float) t.x(q.x2, q.y2), (float) t.y(q.x2, q.y2),
                (float) t.x(q.x3, q.y3), (float) t.y(q.x3, q.y3),
                q.s0, q.t0, q.s1, q.t1, q.s2, q.t2, q.s3, q.t3, q.layer, q.color);
    }

    private void clipTriangle(SceneMesh mask, State state, Quad source,
            double x0, double y0, double s0, double t0, double x1, double y1, double s1, double t1,
            double x2, double y2, double s2, double t2) {
        if (cross(x0, y0, x1, y1, x2, y2) == 0) return;
        mask.clipPolygon(new double[] { x0, y0, s0, t0, x1, y1, s1, t1, x2, y2, s2, t2 }, 4, polygon -> {
            for (int i = 8; i < polygon.length; i += 4) {
                if (cross(polygon[0], polygon[1], polygon[i - 4], polygon[i - 3], polygon[i], polygon[i + 1]) == 0) continue;
                // 第3点重复第2点，原EBO只留下一个真实三角形，不增加新的图元格式。
                addPrepared(state, new Quad((float) polygon[0], (float) polygon[1],
                        (float) polygon[i - 4], (float) polygon[i - 3], (float) polygon[i], (float) polygon[i + 1],
                        (float) polygon[i], (float) polygon[i + 1],
                        (float) polygon[2], (float) polygon[3], (float) polygon[i - 2], (float) polygon[i - 1],
                        (float) polygon[i + 2], (float) polygon[i + 3], (float) polygon[i + 2], (float) polygon[i + 3],
                        source.layer, source.color));
            }
        });
    }

    private static double cross(double x0, double y0, double x1, double y1, double x2, double y2) {
        return (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
    }

    private static boolean visible(LayoutBox clip, Quad q) {
        if (q.width == 0 || q.height == 0) return false;
        return clip == null
                || clip.width > 0
                        && clip.height > 0
                        && (double) q.x < (long) clip.x + clip.width
                        && (double) q.y < (long) clip.y + clip.height
                        && (double) q.x + q.width > clip.x
                        && (double) q.y + q.height > clip.y;
    }

    private void endDraw() {
        if (active != null) draws.add(new Draw(active, first, quads.size() - first));
        active = null;
    }

    /** 调用方在变换/深度/pass变化及委托其他渲染之前调用；业务组件不管理它。 */
    public void flush() {
        if (submitting) throw new IllegalStateException("提交回调不能重入收集器");
        if (quads.isEmpty()) return;
        endDraw();
        OrderedQuadBatch output = new OrderedQuadBatch(quads, draws);
        quads.clear();
        draws.clear();
        first = 0;
        // 回调只消费已封闭段，不能填入下一段；防止满容量提交后重入突破预算。
        submitting = true;
        try {
            submit.accept(output);
        } finally {
            submitting = false;
        }
    }
}
