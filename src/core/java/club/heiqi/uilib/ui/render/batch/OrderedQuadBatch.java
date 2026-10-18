package club.heiqi.uilib.ui.render.batch;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 有序提交段。软件与GL消费者共用载荷；资源版本由资源服务持有，不暴露GL纹理ID。 */
public final class OrderedQuadBatch {
    /** 四个真实顶点及各自UV；bbox仅保守剔除，旧UV端点保留兼容元数据。 */
    public static final class Quad {
        public final float x0, y0, x1, y1, x2, y2, x3, y3;
        public final float x, y, width, height, u0, v0, u1, v1;
        public final float s0, t0, s1, t1, s2, t2, s3, t3;
        public final int layer, color;

        /** 保留轴对齐构造，既有文字/图像及调用方无需改变。 */
        public Quad(
                float x,
                float y,
                float width,
                float height,
                float u0,
                float v0,
                float u1,
                float v1,
                int layer,
                int color) {
            this(
                    x,
                    y,
                    end(x, width),
                    y,
                    end(x, width),
                    end(y, height),
                    x,
                    end(y, height),
                    u0,
                    v0,
                    u1,
                    v1,
                    layer,
                    color);
        }

        public Quad(
                float x0,
                float y0,
                float x1,
                float y1,
                float x2,
                float y2,
                float x3,
                float y3,
                float u0,
                float v0,
                float u1,
                float v1,
                int layer,
                int color) {
            this(x0, y0, x1, y1, x2, y2, x3, y3,
                    u0, v0, u1, v0, u1, v1, u0, v1, layer, color);
        }

        /** 裁剪后的各顶点保留独立线性采样；旧端点字段对应顶点0与2。 */
        public Quad(
                float x0, float y0, float x1, float y1,
                float x2, float y2, float x3, float y3,
                float s0, float t0, float s1, float t1,
                float s2, float t2, float s3, float t3,
                int layer, int color) {
            if (!Float.isFinite(x0)
                    || !Float.isFinite(y0)
                    || !Float.isFinite(x1)
                    || !Float.isFinite(y1)
                    || !Float.isFinite(x2)
                    || !Float.isFinite(y2)
                    || !Float.isFinite(x3)
                    || !Float.isFinite(y3)
                    || !Float.isFinite(s0)
                    || !Float.isFinite(t0)
                    || !Float.isFinite(s1)
                    || !Float.isFinite(t1)
                    || !Float.isFinite(s2)
                    || !Float.isFinite(t2)
                    || !Float.isFinite(s3)
                    || !Float.isFinite(t3)
                    || layer < 0) throw new IllegalArgumentException("无效图元");
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.x3 = x3;
            this.y3 = y3;
            x = Math.min(Math.min(x0, x1), Math.min(x2, x3));
            y = Math.min(Math.min(y0, y1), Math.min(y2, y3));
            width = Math.max(Math.max(x0, x1), Math.max(x2, x3)) - x;
            height = Math.max(Math.max(y0, y1), Math.max(y2, y3)) - y;
            if (!Float.isFinite(width) || !Float.isFinite(height))
                throw new IllegalArgumentException("图元包络超出坐标范围");
            this.s0 = s0; this.t0 = t0;
            this.s1 = s1; this.t1 = t1;
            this.s2 = s2; this.t2 = t2;
            this.s3 = s3; this.t3 = t3;
            this.u0 = s0; this.v0 = t0;
            this.u1 = s2; this.v1 = t2;
            this.layer = layer;
            this.color = color;
        }

        private static float end(float start, float extent) {
            if (!Float.isFinite(extent) || extent < 0) throw new IllegalArgumentException("无效图元尺寸");
            return start + extent;
        }

        /** 同一法线几何用于GL及软件消费；平端点，零长度生成空quad。 */
        public static Quad line(
                float x0,
                float y0,
                float x1,
                float y1,
                float thickness,
                float u0,
                float v0,
                float u1,
                float v1,
                int layer,
                int color) {
            if (!Float.isFinite(thickness) || thickness <= 0)
                throw new IllegalArgumentException("无效线宽");
            double dx = (double) x1 - x0, dy = (double) y1 - y0;
            double length = Math.hypot(dx, dy);
            double nx = length == 0 ? 0 : dy / length * thickness / 2;
            double ny = length == 0 ? 0 : -dx / length * thickness / 2;
            return new Quad(
                    (float) (x0 + nx),
                    (float) (y0 + ny),
                    (float) (x1 + nx),
                    (float) (y1 + ny),
                    (float) (x1 - nx),
                    (float) (y1 - ny),
                    (float) (x0 - nx),
                    (float) (y0 - ny),
                    u0,
                    v0,
                    u1,
                    v1,
                    layer,
                    color);
        }
    }

    /** 相邻合批状态。局部矩形与当批变换/世界裁剪由Collector准备成最终世界顶点。 */
    public static final class State {
        public final long resource, generation;
        public final int material;
        /** 旧字段仍在本图元局部坐标中；单位变换保持原scissor含义。 */
        public final LayoutBox clip;
        public final SceneTransform transform;
        public final SceneClipRegion shapeClip;

        public State(int material, long resource, long generation, LayoutBox clip) {
            this(material, resource, generation, clip, SceneTransform.IDENTITY, SceneClipRegion.UNBOUNDED);
        }

        public State(int material, long resource, long generation, LayoutBox localRectClip,
                SceneTransform transform, SceneClipRegion externalWorldClip) {
            this.material = material;
            this.resource = resource;
            this.generation = generation;
            this.clip = localRectClip;
            this.transform = Objects.requireNonNull(transform, "图元变换");
            this.shapeClip = Objects.requireNonNull(externalWorldClip, "世界裁剪");
        }

        /** 局部矩形也须随原图元变换，旋转后不能只用其包络替代精确裁剪。 */
        public SceneClipRegion effectiveClip() {
            return shapeClip.intersect(SceneClipRegion.rectangle(clip).transformed(transform));
        }

        /** outer左乘当前几何；此前世界裁剪同样移入outer，再交外部当前裁剪。 */
        public State withGeometry(SceneTransform outer, SceneClipRegion external) {
            Objects.requireNonNull(outer, "外层变换"); Objects.requireNonNull(external, "外层裁剪");
            if (outer.identity() && external.unbounded()) return this;
            return new State(material, resource, generation, clip, outer.multiply(transform),
                    shapeClip.transformed(outer).intersect(external));
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof State)) return false;
            State other = (State) value;
            return material == other.material
                    && resource == other.resource
                    && generation == other.generation
                    && transform.equals(other.transform)
                    && shapeClip.equals(other.shapeClip)
                    && (clip == other.clip
                            || clip != null
                                    && other.clip != null
                                    && clip.x == other.clip.x
                                    && clip.y == other.clip.y
                                    && clip.width == other.clip.width
                                    && clip.height == other.clip.height);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    material,
                    resource,
                    generation,
                    clip == null ? 0 : Objects.hash(clip.x, clip.y, clip.width, clip.height),
                    transform, shapeClip);
        }
    }

    public static final class Draw {
        public final State state;
        public final int firstQuad, quadCount;

        Draw(State state, int firstQuad, int quadCount) {
            this.state = state;
            this.firstQuad = firstQuad;
            this.quadCount = quadCount;
        }
    }

    private final List<Quad> quads;
    private final List<Draw> draws;

    OrderedQuadBatch(List<Quad> quads, List<Draw> draws) {
        this.quads = Collections.unmodifiableList(new ArrayList<>(quads));
        this.draws = Collections.unmodifiableList(new ArrayList<>(draws));
    }

    public List<Quad> quads() {
        return quads;
    }

    public List<Draw> draws() {
        return draws;
    }

    /** 固定EBO的容量模板；只在建立或扩容时调用，不在每帧重复生成。 */
    public static int[] quadIndices(int capacity) {
        if (capacity <= 0 || capacity > Integer.MAX_VALUE / 6)
            throw new IllegalArgumentException("无效索引容量");
        int[] result = new int[capacity * 6];
        for (int q = 0; q < capacity; q++) {
            int v = q * 4, i = q * 6;
            result[i] = v;
            result[i + 1] = v + 1;
            result[i + 2] = v + 2;
            result[i + 3] = v;
            result[i + 4] = v + 2;
            result[i + 5] = v + 3;
        }
        return result;
    }
}
