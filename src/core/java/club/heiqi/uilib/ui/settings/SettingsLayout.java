package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneControlMetrics;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.*;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.*;

/** 只负责具名区域分配；测量不写信号、不读取上一帧几何。 */
final class SettingsLayout {
    private SettingsLayout() {}

    static SceneNode template(ReadableSignal<SceneAppearance.Palette> colors, ReadableSignal<Boolean> compact,
            SceneNode toolbar, SceneNode navigation, SceneNode body, SceneNode footer, SceneNode aside) {
        SceneNode document = new SceneNode().size(Size.fill(1), Size.fill(1)).clip(true).scrollable(true)
                .control(new TemplateDocument(colors, compact, toolbar, navigation, body, aside))
                .add(toolbar).add(navigation).add(body);
        if (aside != null) document.add(aside);
        return new SceneNode().size(Size.fill(1), Size.fill(1))
                .control(new TemplateWorkspace(colors, compact, document, footer, aside))
                .add(document).add(footer);
    }

    static SceneNode split(SettingsUi ui, SceneNode info, SceneNode actions) {
        return SceneLayouts.split(
                info, actions, () -> new SceneLayouts.SplitOptions(ui.px(200), ui.px(12)));
    }

    static final class Result {
        final MeasuredSize size;
        final ChildPlacements children;

        Result(int width, int height, List<ChildPlacements.Entry> children) {
            size = new MeasuredSize(Math.max(0, width), Math.max(0, height));
            this.children = new ChildPlacements(children);
        }
    }

    abstract static class Arrangement implements SceneControl {
        abstract Result layout(Constraints limits, SceneChildMeasurer children);

        public MeasuredSize measure(SceneNode n, Constraints c, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        public MeasuredSize measure(
                SceneNode n, Constraints c, SceneTextMeasurer text, SceneChildMeasurer children) {
            return layout(c, children).size;
        }

        public ChildPlacements arrange(SceneNode n, LayoutBox b, SceneChildMeasurer children) {
            int pad = n.padding();
            Result r =
                    layout(
                            new Constraints(
                                    Math.max(0, b.width - pad * 2),
                                    Math.max(0, b.height - pad * 2)),
                            children);
            if (pad == 0) return r.children;
            List<ChildPlacements.Entry> result = new ArrayList<>();
            for (ChildPlacements.Entry e : r.children.entries()) {
                LayoutBox q = e.relativeBox;
                place(result, e.child, q.x + pad, q.y + pad, q.width, q.height);
            }
            return new ChildPlacements(result);
        }

        public void paint(SceneNode n, SceneGeometryView g, PaintPlanBuilder p) {
            if ((n.background() >>> 24) != 0) {
                SceneGeometryView.Entry e = g.get(n);
                p.add(PaintCommand.rect(e.box, e.clip, n.background()));
            }
        }

        static void place(
                List<ChildPlacements.Entry> result, SceneNode child, int x, int y, int w, int h) {
            result.add(
                    new ChildPlacements.Entry(
                            child, new LayoutBox(x, y, Math.max(0, w), Math.max(0, h))));
        }
    }

    /** 操作区先按宿主空间分配；短窗的文档溢出由正式视口承接，不把页脚推到客户端外。 */
    private static final class TemplateWorkspace extends Arrangement {
        final ReadableSignal<SceneAppearance.Palette> colors;
        final ReadableSignal<Boolean> compact;
        final SceneNode document, footer, aside;

        TemplateWorkspace(ReadableSignal<SceneAppearance.Palette> colors, ReadableSignal<Boolean> compact,
                SceneNode document, SceneNode footer, SceneNode aside) {
            this.colors = colors; this.compact = compact; this.document = document; this.footer = footer; this.aside = aside;
        }
        public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Arrays.asList(colors, compact); }

        Result layout(Constraints limits, SceneChildMeasurer children) {
            SceneAppearance.Palette palette = colors.get();
            float em = palette.textStyle.logicalEm;
            int gap = palette.source.metrics(SceneControlMetrics.Kind.PANEL).gap;
            int margin = Math.max(gap * 2, Math.round(em * 1.25f));
            int rail = compact.get() ? 0 : Math.round(em * 12), columns = compact.get() ? 0 : gap * 5;
            int side = aside != null && aside.visible() ? Math.round(em * 16) : 0, sideGap = side == 0 ? 0 : gap * 5;
            int width = Math.max(0, Math.min(limits.availableWidth - margin * 2, rail + columns + Math.round(em * 40) + side + sideGap));
            int reading = Math.max(0, width - rail - columns - side - sideGap);
            int x = Math.max(0, (limits.availableWidth - width) / 2);
            int footerHeight = Math.min(limits.availableHeight / 2,
                    children.measureChild(footer, new Constraints(reading, Integer.MAX_VALUE)).height);
            int remaining = Math.max(0, limits.availableHeight - footerHeight);
            int minimumDocument = Math.min(remaining, Math.round(em * 3));
            int separation = Math.min(gap * 2, Math.max(0, remaining - minimumDocument));
            int verticalMargin = Math.min(margin, Math.max(0, (remaining - minimumDocument - separation) / 2));
            int footerY = limits.availableHeight - verticalMargin - footerHeight;
            int documentHeight = Math.max(0, footerY - verticalMargin - separation);
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            children.measureChild(document, new Constraints(width, documentHeight));
            place(placed, document, x, verticalMargin, width, documentHeight);
            children.measureChild(footer, new Constraints(reading, footerHeight));
            place(placed, footer, x + rail + columns, footerY, reading, footerHeight);
            return new Result(limits.availableWidth, limits.availableHeight, placed);
        }
    }

    /** 保留普通窗口的头部/分类/正文排列，低窗按内容范围滚动而不压坏固定高度控件。 */
    private static final class TemplateDocument extends Arrangement {
        final ReadableSignal<SceneAppearance.Palette> colors;
        final ReadableSignal<Boolean> compact;
        final SceneNode toolbar, navigation, body, aside;
        TemplateDocument(ReadableSignal<SceneAppearance.Palette> colors, ReadableSignal<Boolean> compact,
                SceneNode toolbar, SceneNode navigation, SceneNode body, SceneNode aside) {
            this.colors = colors; this.compact = compact;
            this.toolbar = toolbar; this.navigation = navigation; this.body = body; this.aside = aside;
        }
        public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Arrays.asList(colors, compact); }
        Result layout(Constraints limits, SceneChildMeasurer children) {
            SceneAppearance.Palette palette = colors.get();
            float em = palette.textStyle.logicalEm;
            int gap = palette.source.metrics(SceneControlMetrics.Kind.PANEL).gap;
            boolean narrow = compact.get();
            int rail = narrow ? 0 : Math.round(em * 12), columns = narrow ? 0 : gap * 5;
            int side = aside != null && aside.visible() ? Math.round(em * 16) : 0, sideGap = side == 0 ? 0 : gap * 5;
            int reading = Math.max(0, limits.availableWidth - rail - columns - side - sideGap);
            int toolbarHeight = children.measureChild(toolbar, new Constraints(limits.availableWidth, Integer.MAX_VALUE)).height;
            int start = toolbarHeight + gap * 3;
            int navigationHeight = narrow
                    ? children.measureChild(navigation, new Constraints(limits.availableWidth, Integer.MAX_VALUE)).height : 0;
            int bodyY = start + (narrow ? navigationHeight + gap * 3 : 0);
            int bodyHeight = Math.max(Math.round(em * 10), limits.availableHeight - bodyY);
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            place(placed, toolbar, 0, 0, limits.availableWidth, toolbarHeight);
            // rail 与正文共享可滚动文档的内容预算，不能被短视口压成永久空裁剪区。
            int navHeight = narrow ? navigationHeight : Math.max(0, bodyY + bodyHeight - start);
            int navWidth = narrow ? limits.availableWidth : rail;
            children.measureChild(navigation, new Constraints(navWidth, navHeight));
            place(placed, navigation, 0, start, navWidth, navHeight);
            children.measureChild(body, new Constraints(reading, bodyHeight));
            place(placed, body, rail + columns, bodyY, reading, bodyHeight);
            if (side > 0) {
                int height = children.measureChild(aside, new Constraints(side, bodyHeight)).height;
                place(placed, aside, rail + columns + reading + sideGap, bodyY, side, Math.min(height, bodyHeight));
            }
            return new Result(limits.availableWidth, Math.max(limits.availableHeight, bodyY + bodyHeight), placed);
        }
    }

}
