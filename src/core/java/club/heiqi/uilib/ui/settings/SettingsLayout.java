package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.*;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.*;

/** 只负责具名区域分配；测量不写信号、不读取上一帧几何。 */
final class SettingsLayout {
    private SettingsLayout() {}

    static SceneNode workspace(
            SettingsUi ui,
            SceneNode toolbar,
            SceneNode navigation,
            SceneNode body,
            SceneNode footer,
            SceneNode ledger) {
        return ui.stack(0)
                .size(Size.fill(1), Size.fill(1))
                .control(new Workspace(ui, toolbar, navigation, body, footer, ledger))
                .add(toolbar)
                .add(navigation)
                .add(body)
                .add(footer)
                .add(ledger);
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

    private static final class Workspace extends Arrangement {
        final SettingsUi ui;
        final SceneNode toolbar, navigation, body, footer, ledger;

        Workspace(
                SettingsUi ui,
                SceneNode toolbar,
                SceneNode navigation,
                SceneNode body,
                SceneNode footer,
                SceneNode ledger) {
            this.ui = ui;
            this.toolbar = toolbar;
            this.navigation = navigation;
            this.body = body;
            this.footer = footer;
            this.ledger = ledger;
        }

        public List<ReadableSignal<?>> measureDependencies(SceneNode n) {
            return Arrays.asList(ui.scale, ui.compact, ui.shortWindow);
        }

        Result layout(Constraints c, SceneChildMeasurer m) {
            boolean compact = ui.compact.get();
            int rail = compact ? 0 : ui.px(232), gutter = ui.px(compact ? 16 : 32);
            int main = Math.max(0, c.availableWidth - rail), width = Math.max(0, main - gutter * 2);
            boolean side = c.availableWidth >= ui.px(1440) && !ui.shortWindow.get();
            int ledgerWidth = side ? ui.px(260) : 0, gap = side ? ui.px(40) : 0;
            int reading = Math.max(0, Math.min(ui.px(720), width - ledgerWidth - gap)),
                    group = reading + ledgerWidth + gap;
            int x = rail + gutter + Math.max(0, (width - group) / 2);
            int
                    toolbarHeight =
                            m.measureChild(toolbar, new Constraints(group, c.availableHeight))
                                    .height,
                    top = toolbarHeight + ui.px(24);
            int nav =
                    compact
                            ? m.measureChild(navigation, new Constraints(width, c.availableHeight))
                                    .height
                            : 0;
            int footerHeight =
                    Math.min(
                            c.availableHeight / 2,
                            m.measureChild(footer, new Constraints(group, Integer.MAX_VALUE))
                                    .height);
            int start =
                    top + (compact ? nav + ui.px(12) : 0) + ui.px(ui.shortWindow.get() ? 8 : 24);
            int bottom = Math.max(start, c.availableHeight - footerHeight - ui.px(12));
            List<ChildPlacements.Entry> e = new ArrayList<>();
            place(e, toolbar, x, ui.px(12), group, toolbarHeight);
            m.measureChild(
                    navigation,
                    new Constraints(compact ? width : rail, compact ? nav : c.availableHeight));
            place(
                    e,
                    navigation,
                    compact ? rail + gutter : 0,
                    compact ? top : 0,
                    compact ? width : rail,
                    compact ? nav : c.availableHeight);
            m.measureChild(body, new Constraints(reading, Math.max(0, bottom - start)));
            place(e, body, x, start, reading, bottom - start);
            m.measureChild(footer, new Constraints(group, footerHeight));
            place(e, footer, x, c.availableHeight - footerHeight - ui.px(6), group, footerHeight);
            if (side) {
                int lh =
                        m.measureChild(ledger, new Constraints(ledgerWidth, bottom - start)).height;
                place(
                        e,
                        ledger,
                        x + reading + gap,
                        start,
                        ledgerWidth,
                        Math.min(lh, bottom - start));
            } else {
                m.measureChild(ledger, new Constraints(0, 0));
                place(e, ledger, 0, 0, 0, 0);
            }
            return new Result(c.availableWidth, c.availableHeight, e);
        }
    }
}
