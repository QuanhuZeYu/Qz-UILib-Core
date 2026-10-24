package club.heiqi.uilib.ui.scene.hud;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.*;

/** 所属规格：specs/scene/uilib-scene-hud-spec.md。只在 arrange 发布正式子盒。 */
final class SceneHudLayout {
    private SceneHudLayout() {}

    private static int dimension(double value) {
        if (!Double.isFinite(value) || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("HUD 缩放后尺寸超出 Scene 范围");
        return (int) Math.max(0, Math.ceil(value));
    }

    // 测量上界饱和到 Scene 的 int 域；实际缩放几何仍由 dimension 检查。
    private static int constraintDimension(double value) {
        return dimension(Math.min(Integer.MAX_VALUE, value));
    }

    private static ChildPlacements place(SceneNode child, int x, int y, int width, int height) {
        return new ChildPlacements(Collections.singletonList(
                new ChildPlacements.Entry(child, new LayoutBox(x, y, width, height))));
    }

    private abstract static class Layout implements SceneControl {
        public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public void paint(SceneNode n, SceneGeometryView geometry, PaintPlanBuilder paint) {}
    }

    /** 用户缩放只作上限；适配只改变当前呈现，不覆盖位置草稿或内容自然尺寸。 */
    static final class Content extends Layout {
        final ReadableSignal<Float> scale;
        final ReadableSignal<Integer> width, height;
        final SceneHud.Fit fit;
        Content(ReadableSignal<Float> scale, ReadableSignal<Integer> width, ReadableSignal<Integer> height, SceneHud.Fit fit) {
            this.scale = scale; this.width = width; this.height = height; this.fit = fit;
        }
        public List<ReadableSignal<?>> measureDependencies(SceneNode n) { return Arrays.asList(scale, width, height); }
        Constraints natural() {
            return new Constraints(constraintDimension(Math.floor(width.get() / (double) scale.get())), Integer.MAX_VALUE);
        }
        double factor(int w, int h) {
            double value = scale.get();
            if (fit == SceneHud.Fit.CLIP) return value;
            if (w > 0) value = Math.min(value, width.get() / (double) w);
            if (h > 0) value = Math.min(value, height.get() / (double) h);
            return Math.max(0, value);
        }
        public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(n, c, children);
        }
        public ChildPlacements arrange(SceneNode n, LayoutBox box, SceneChildMeasurer children) {
            return SceneFlowLayout.arrange(n, box, children);
        }
        public club.heiqi.uilib.ui.scene.geometry.SceneTransform presentationTransform(SceneNode n, LayoutBox box) {
            double value = factor(box.width, box.height);
            return club.heiqi.uilib.ui.scene.geometry.SceneTransform.scale(value, value);
        }
    }

    /** 外盒表达实际适配后尺寸；内部原尺寸和正式变换同时供绘制、Draggable 与命中消费。 */
    static final class Scaled extends Layout {
        final SceneNode content;
        final Content fit;
        Scaled(SceneNode content, Content fit) { this.content = content; this.fit = fit; }
        public List<ReadableSignal<?>> measureDependencies(SceneNode n) { return fit.measureDependencies(n); }
        private MeasuredSize natural(Constraints c, SceneChildMeasurer children) {
            double scale = fit.scale.get();
            Constraints limits = fit.fit == SceneHud.Fit.CONTAIN ? fit.natural() : new Constraints(
                    constraintDimension(Math.floor(c.availableWidth / scale)), constraintDimension(Math.floor(c.availableHeight / scale)));
            return children.measureChild(content, limits);
        }
        public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text, SceneChildMeasurer children) {
            MeasuredSize raw = natural(c, children);
            double factor = fit.factor(raw.width, raw.height);
            return new MeasuredSize(dimension(raw.width * factor), dimension(raw.height * factor));
        }
        public ChildPlacements arrange(SceneNode n, LayoutBox box, SceneChildMeasurer children) {
            MeasuredSize raw = natural(new Constraints(box.width, box.height), children);
            return place(content, 0, 0, raw.width, raw.height);
        }
    }

    static final class Anchored extends Layout {
        final ReadableSignal<SceneHudPlacement> placement;
        SceneNode content;
        // 一份有界的当批排列事实；每次 arrange 覆盖，正常 UP 前由生产输入管线重新结算。
        private Frame frame;
        Anchored(ReadableSignal<SceneHudPlacement> placement) {
            this.placement = placement;
        }
        public List<ReadableSignal<?>> arrangementDependencies(SceneNode n) { return Collections.<ReadableSignal<?>>singletonList(placement); }
        public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text, SceneChildMeasurer children) {
            return new MeasuredSize(c.availableWidth, c.availableHeight);
        }
        public ChildPlacements arrange(SceneNode n, LayoutBox box, SceneChildMeasurer children) {
            int padding = n.padding(), width = Math.max(0, box.width - padding * 2), height = Math.max(0, box.height - padding * 2);
            MeasuredSize size = children.measureChild(content, new Constraints(width, height));
            SceneHudPlacement value = Objects.requireNonNull(placement.get(), "HUD 位置");
            double baseX = anchorX(value.anchor) * Math.max(0, width - size.width);
            double baseY = anchorY(value.anchor) * Math.max(0, height - size.height);
            double rawX = baseX + value.offsetX;
            double rawY = baseY + value.offsetY;
            int x = clamp(rawX, width - size.width);
            int y = clamp(rawY, height - size.height);
            frame = new Frame(x, y, baseX, baseY, rawX, rawY,
                    width - size.width, height - size.height);
            return place(content, padding + x, padding + y, size.width, size.height);
        }
        void drop(Signal<SceneHudPlacement> target, int dx, int dy) {
            Frame current = Objects.requireNonNull(frame, "HUD 尚未排列");
            SceneHudPlacement value = Objects.requireNonNull(target.get(), "HUD 位置");
            double nextX = offsetAfterDrop(value.offsetX, dx, current.x, current.baseX,
                    current.roomX, current.clampedX);
            double nextY = offsetAfterDrop(value.offsetY, dy, current.y, current.baseY,
                    current.roomY, current.clampedY);
            SceneHudPlacement next = new SceneHudPlacement(value.anchor, nextX, nextY, value.scale);
            if (!value.equals(next)) target.set(next);
        }

        // 限位和像素对齐只形成呈现事实。零有效位移保持原值；真正移动受限起点时
        // 使用当前可见盒，否则沿原始小数增量，避免点击变脏或放下跳回。
        private static double offsetAfterDrop(double original, int delta, int current,
                double base, int room, boolean wasClamped) {
            int destination = clamp(current + (double) delta, room);
            if (delta == 0 || destination == current) return original;
            double candidate = wasClamped ? destination - base : original + delta;
            // IEEE 加法可能把半像素边界下侧的小数舍入到边界。优先保留小数；若其
            // 投影已偏离本次落点，就使用该像素的精确中心偏移，不用 epsilon 补偿。
            return clamp(base + candidate, room) == destination ? candidate : destination - base;
        }
    }

    private static int clamp(double value, int room) { return (int) Math.round(Math.max(0, Math.min(Math.max(0, room), value))); }
    private static double anchorX(SceneHudPlacement.Anchor anchor) {
        switch (anchor) {
            case TOP_LEFT: case CENTER_LEFT: case BOTTOM_LEFT: return 0;
            case TOP_CENTER: case CENTER: case BOTTOM_CENTER: return .5;
            default: return 1;
        }
    }
    private static double anchorY(SceneHudPlacement.Anchor anchor) {
        switch (anchor) {
            case TOP_LEFT: case TOP_CENTER: case TOP_RIGHT: return 0;
            case CENTER_LEFT: case CENTER: case CENTER_RIGHT: return .5;
            default: return 1;
        }
    }
    private static final class Frame {
        final int x, y, roomX, roomY;
        final double baseX, baseY;
        final boolean clampedX, clampedY;
        Frame(int x, int y, double baseX, double baseY, double rawX, double rawY, int roomX, int roomY) {
            this.x = x; this.y = y; this.baseX = baseX; this.baseY = baseY;
            this.roomX = roomX; this.roomY = roomY;
            this.clampedX = outside(rawX, roomX); this.clampedY = outside(rawY, roomY);
        }
        private static boolean outside(double value, int room) {
            int max = Math.max(0, room);
            return value < 0 || value > max;
        }
    }
}
