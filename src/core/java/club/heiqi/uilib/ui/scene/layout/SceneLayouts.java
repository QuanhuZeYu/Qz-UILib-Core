package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 常用区域排列；消费同批约束，不读回布局结果。合同见通用界面组合规格。 */
public final class SceneLayouts {
    private SceneLayouts() {}

    public static final class SplitOptions {
        public final int minimumInfoWidth, gap;

        public SplitOptions(int minimumInfoWidth, int gap) {
            if (minimumInfoWidth < 0 || gap < 0) throw new IllegalArgumentException("区域宽度与间距不能为负");
            this.minimumInfoWidth = minimumInfoWidth;
            this.gap = gap;
        }
    }

    public static SceneNode wrap(ReadableSignal<Integer> gap) {
        return new SceneNode()
                .size(Size.fill(1), Size.natural())
                .control(new Wrap(Objects.requireNonNull(gap)));
    }

    public static SceneNode wrap(int gap) {
        return wrap(() -> gap);
    }

    public static SceneNode overlay() {
        return new SceneNode().size(Size.fill(1), Size.fill(1)).control(new Overlay());
    }

    public static SceneNode center(SceneNode content) {
        return new SceneNode()
                .size(Size.fill(1), Size.fill(1))
                .control(new Center())
                .add(Objects.requireNonNull(content));
    }

    public static SceneNode frame(SceneNode header, SceneNode body, SceneNode footer) {
        Objects.requireNonNull(header);
        Objects.requireNonNull(body);
        Objects.requireNonNull(footer);
        return new SceneNode()
                .size(Size.fill(1), Size.fill(1))
                .control(new Frame(header, body, footer))
                .add(header)
                .add(body)
                .add(footer);
    }

    public static SceneNode split(
            SceneNode info, SceneNode actions, ReadableSignal<SplitOptions> options) {
        Objects.requireNonNull(info);
        Objects.requireNonNull(actions);
        return new SceneNode()
                .size(Size.fill(1), Size.natural())
                .control(new Split(info, actions, Objects.requireNonNull(options)))
                .add(info)
                .add(actions);
    }

    private static final class Result {
        final MeasuredSize size;
        final List<ChildPlacements.Entry> children;

        Result(int width, int height, List<ChildPlacements.Entry> children) {
            this.size = new MeasuredSize(Math.max(0, width), Math.max(0, height));
            this.children = children;
        }
    }

    private abstract static class Arrangement implements SceneControl {
        abstract Result layout(Constraints limits, SceneChildMeasurer children);

        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        public MeasuredSize measure(
                SceneNode node,
                Constraints limits,
                SceneTextMeasurer text,
                SceneChildMeasurer children) {
            return layout(limits, children).size;
        }

        public ChildPlacements arrange(
                SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            int padding = node.padding();
            Result result =
                    layout(
                            new Constraints(
                                    Math.max(0, allocated.width - padding * 2),
                                    Math.max(0, allocated.height - padding * 2)),
                            children);
            if (padding == 0) return new ChildPlacements(result.children);
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (ChildPlacements.Entry entry : result.children) {
                LayoutBox box = entry.relativeBox;
                place(placed, entry.child, box.x + padding, box.y + padding, box.width, box.height);
            }
            return new ChildPlacements(placed);
        }

        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            if ((node.background() >>> 24) != 0) {
                SceneGeometryView.Entry entry = geometry.get(node);
                paint.add(PaintCommand.rect(entry.box, entry.clip, node.background()));
            }
        }
    }

    private static void place(
            List<ChildPlacements.Entry> entries,
            SceneNode child,
            int x,
            int y,
            int width,
            int height) {
        entries.add(
                new ChildPlacements.Entry(
                        child, new LayoutBox(x, y, Math.max(0, width), Math.max(0, height))));
    }

    private static int resolve(Size size, int available, int natural) {
        return size.constrain(
                size.mode == Size.Mode.FIXED
                        ? size.value
                        : size.mode == Size.Mode.FILL && available != Integer.MAX_VALUE
                                ? available
                                : natural);
    }

    private static final class Wrap extends Arrangement {
        final ReadableSignal<Integer> gap;

        Wrap(ReadableSignal<Integer> gap) {
            this.gap = gap;
        }

        public List<ReadableSignal<?>> measureDependencies(SceneNode node) {
            return Collections.singletonList(gap);
        }

        Result layout(Constraints limits, SceneChildMeasurer children) {
            int spacing = gap.get();
            if (spacing < 0) throw new IllegalArgumentException("换行间距不能为负");
            int x = 0, y = 0, line = 0, width = 0;
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (SceneNode child : children.flowChildren()) {
                MeasuredSize size =
                        children.measureChild(
                                child, new Constraints(limits.availableWidth, Integer.MAX_VALUE));
                if (x > 0 && (long) x + size.width > limits.availableWidth) {
                    x = 0;
                    y += line + spacing;
                    line = 0;
                }
                place(placed, child, x, y, size.width, size.height);
                x += size.width + spacing;
                width = Math.max(width, x - spacing);
                line = Math.max(line, size.height);
            }
            return new Result(width, y + line, placed);
        }
    }

    private static final class Overlay extends Arrangement {
        Result layout(Constraints limits, SceneChildMeasurer children) {
            int width = 0, height = 0;
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (SceneNode child : children.flowChildren()) {
                MeasuredSize size = children.measureChild(child, limits);
                int w = resolve(child.width(), limits.availableWidth, size.width),
                        h = resolve(child.height(), limits.availableHeight, size.height);
                place(placed, child, 0, 0, w, h);
                width = Math.max(width, w);
                height = Math.max(height, h);
            }
            return new Result(width, height, placed);
        }
    }

    private static final class Center extends Arrangement {
        Result layout(Constraints limits, SceneChildMeasurer children) {
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            int width = 0, height = 0;
            for (SceneNode child : children.flowChildren()) {
                MeasuredSize size = children.measureChild(child, limits);
                int w = resolve(child.width(), limits.availableWidth, size.width),
                        h = resolve(child.height(), limits.availableHeight, size.height);
                place(
                        placed,
                        child,
                        Math.max(0, (limits.availableWidth - w) / 2),
                        Math.max(0, (limits.availableHeight - h) / 2),
                        w,
                        h);
                width = Math.max(width, w);
                height = Math.max(height, h);
            }
            return new Result(width, height, placed);
        }
    }

    private static final class Frame extends Arrangement {
        final SceneNode header, body, footer;

        Frame(SceneNode header, SceneNode body, SceneNode footer) {
            this.header = header;
            this.body = body;
            this.footer = footer;
        }

        Result layout(Constraints limits, SceneChildMeasurer children) {
            int hh =
                    children.measureChild(
                                    header,
                                    new Constraints(limits.availableWidth, Integer.MAX_VALUE))
                            .height;
            int fh =
                    children.measureChild(
                                    footer,
                                    new Constraints(limits.availableWidth, Integer.MAX_VALUE))
                            .height;
            int remaining =
                    limits.availableHeight == Integer.MAX_VALUE
                            ? Integer.MAX_VALUE
                            : Math.max(0, limits.availableHeight - hh - fh);
            MeasuredSize bodySize =
                    children.measureChild(body, new Constraints(limits.availableWidth, remaining));
            int bh = resolve(body.height(), remaining, bodySize.height);
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            place(placed, header, 0, 0, limits.availableWidth, hh);
            place(placed, body, 0, hh, limits.availableWidth, bh);
            place(placed, footer, 0, hh + bh, limits.availableWidth, fh);
            return new Result(limits.availableWidth, hh + bh + fh, placed);
        }
    }

    private static final class Split extends Arrangement {
        final SceneNode info, actions;
        final ReadableSignal<SplitOptions> options;

        Split(SceneNode info, SceneNode actions, ReadableSignal<SplitOptions> options) {
            this.info = info;
            this.actions = actions;
            this.options = options;
        }

        public List<ReadableSignal<?>> measureDependencies(SceneNode node) {
            return Collections.singletonList(options);
        }

        Result layout(Constraints limits, SceneChildMeasurer children) {
            SplitOptions policy = options.get();
            MeasuredSize action =
                    children.measureChild(
                            actions, new Constraints(limits.availableWidth, Integer.MAX_VALUE));
            boolean stacked =
                    limits.availableWidth - action.width - policy.gap < policy.minimumInfoWidth;
            int width =
                    stacked
                            ? limits.availableWidth
                            : Math.max(0, limits.availableWidth - action.width - policy.gap);
            MeasuredSize text =
                    children.measureChild(info, new Constraints(width, Integer.MAX_VALUE));
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            place(placed, info, 0, 0, width, text.height);
            place(
                    placed,
                    actions,
                    stacked ? 0 : width + policy.gap,
                    stacked ? text.height + policy.gap : 0,
                    action.width,
                    action.height);
            return new Result(
                    limits.availableWidth,
                    stacked
                            ? text.height + policy.gap + action.height
                            : Math.max(text.height, action.height),
                    placed);
        }
    }
}
