package club.heiqi.uilib.ui.scene.runtime;

import java.util.Objects;
import java.util.function.Supplier;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 消费一次主动显露意图，沿所属层边界由内到外移动唯一滚动位置。
 * 核心规格第 6.2 节：普通重排不追焦；内层移动后先重排，外层再消费新几何。 */
final class SceneNavigationReveal {
    private final SceneRuntime runtime;
    private MountHandle pending;

    SceneNavigationReveal(SceneRuntime runtime) { this.runtime = runtime; }

    void request(MountHandle target) { pending = target; }
    void clear() { pending = null; }

    SceneGeometry reveal(SceneGeometry geometry, Supplier<SceneGeometry> relayout) {
        MountHandle focused = runtime.focusRouter().takeReveal();
        MountHandle target = focused == null ? pending : focused;
        pending = null;
        if (!active(target)) return geometry;
        if (prepareTarget(target.node(), geometry)) geometry = Objects.requireNonNull(relayout.get(), "geometry");
        SceneNode boundary = geometry.layerBoundary(target.node());
        for (SceneNode ancestor = parentWithin(target.node(), boundary); ancestor != null;
                ancestor = parentWithin(ancestor, boundary)) {
            if (focused != null ? !runtime.focusRouter().owns(target) : !active(target)) break;
            if (ancestor.scrollable() && revealIn(ancestor, target.node(), geometry))
                geometry = Objects.requireNonNull(relayout.get(), "geometry");
        }
        return geometry;
    }

    private boolean active(MountHandle target) {
        return target != null && target.active() && runtime.interactive(target.node());
    }

    private boolean prepareTarget(SceneNode node, SceneGeometry geometry) {
        if (node.control() == null || !node.control().prepareNavigation(node, geometry.asControl(node))) return false;
        runtime.invalidateGeometry();
        return true;
    }

    private static SceneNode parentWithin(SceneNode node, SceneNode boundary) {
        return node == boundary ? null : node.parent();
    }

    private boolean revealIn(SceneNode ancestor, SceneNode target, SceneGeometry geometry) {
        SceneGeometryView local = geometry.asControl(ancestor);
        SceneGeometryView.Entry entry = local.get(ancestor);
        SceneGeometry.RevealRegion reveal = runtime.domain().readOnly(() -> SceneGeometry.revealTarget(target, ancestor, local));
        LayoutBox region = reveal == null ? null : reveal.box;
        if (entry == null || empty(region)) return false;
        LayoutBox window = SceneGeometry.revealWindow(ancestor, local);
        if (window.width == 0 || window.height == 0) return false;
        boolean row = ancestor.axis() == SceneNode.Axis.ROW;
        long delta = SceneGeometry.revealDelta(row ? region.x : region.y, row ? region.width : region.height,
                row ? window.x : window.y, row ? window.width : window.height);
        delta = shapeVisibleDelta(entry, reveal.subject, window, delta, row);
        return runtime.revealScrollPosition(ancestor, entry, delta);
    }

    /** 视口包络里的洞不能当成已显露。用三角形的平移相交区间求最近合法整数偏移。 */
    private static long shapeVisibleDelta(SceneGeometryView.Entry entry, club.heiqi.uilib.ui.scene.geometry.SceneMesh subject,
            LayoutBox window, long preferred, boolean row) {
        club.heiqi.uilib.ui.scene.geometry.SceneTransform inverse = entry.inverse;
        if (inverse == null) return 0;
        club.heiqi.uilib.ui.scene.geometry.SceneClipRegion mask = entry.ownWorldClip.transformed(inverse)
                .intersect(club.heiqi.uilib.ui.scene.geometry.SceneClipRegion.rectangle(window));
        if (mask.rectangular() || mask.unbounded() || mask.empty()) return preferred;
        club.heiqi.uilib.ui.scene.geometry.SceneMesh shape = mask.mesh();
        long min = -entry.scrollOffset, max = (long) entry.scrollMaximum - entry.scrollOffset;
        preferred = Math.max(min, Math.min(max, preferred));
        if (mask.intersects(subject.transformed(club.heiqi.uilib.ui.scene.geometry.SceneTransform.translation(row ? -preferred : 0, row ? 0 : -preferred)))) return preferred;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < subject.vertexCount(); i += 3) for (int j = 0; j < shape.vertexCount(); j += 3) {
            double lo = -max, hi = -min;
            boolean possible = true;
            for (int source = 0; source < 2 && possible; source++) {
                club.heiqi.uilib.ui.scene.geometry.SceneMesh edges = source == 0 ? subject : shape;
                int first = source == 0 ? i : j;
                for (int edge = 0; edge < 3; edge++) {
                    int a = first + edge, b = first + (edge + 1) % 3;
                    double nx = edges.y(a) - edges.y(b), ny = edges.x(b) - edges.x(a), velocity = row ? nx : ny;
                    double s0 = Double.POSITIVE_INFINITY, s1 = Double.NEGATIVE_INFINITY, m0 = s0, m1 = s1;
                    for (int k = 0; k < 3; k++) {
                        double s = nx * subject.x(i + k) + ny * subject.y(i + k), m = nx * shape.x(j + k) + ny * shape.y(j + k);
                        s0 = Math.min(s0, s); s1 = Math.max(s1, s); m0 = Math.min(m0, m); m1 = Math.max(m1, m);
                    }
                    if (velocity == 0) { if (s1 <= m0 || m1 <= s0) possible = false; }
                    else { double t0 = (m0 - s1) / velocity, t1 = (m1 - s0) / velocity;
                        lo = Math.max(lo, Math.min(t0, t1)); hi = Math.min(hi, Math.max(t0, t1)); if (lo >= hi) possible = false; }
                }
            }
            if (!possible) continue;
            long first = Math.max(min, (long) Math.floor(integerBoundary(-hi)) + 1);
            long last = Math.min(max, (long) Math.ceil(integerBoundary(-lo)) - 1);
            if (first > last) continue;
            long candidate = Math.max(first, Math.min(last, preferred));
            if (best == Long.MAX_VALUE || Math.abs(candidate - preferred) < Math.abs(best - preferred)) best = candidate;
        }
        return best == Long.MAX_VALUE ? preferred : best;
    }

    /** 逆换基与SAT投影的几个ULP尾差不能把整数相切端点纳入严格开区间。 */
    private static double integerBoundary(double value) {
        double integer = Math.rint(value);
        return Math.abs(value - integer) <= Math.ulp(Math.max(1, Math.abs(value))) * 4 ? integer : value;
    }

    private static boolean empty(LayoutBox box) { return box == null || box.width == 0 || box.height == 0; }
}
