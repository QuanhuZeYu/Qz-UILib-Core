package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.State;
import club.heiqi.uilib.ui.render.batch.OrderedQuadCollector;
import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;
import club.heiqi.uilib.ui.scene.geometry.SceneMesh;
import club.heiqi.uilib.ui.scene.geometry.ScenePath;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 原有序顶点链的纯验证；独立坐标、面积与采样关系核验实际提交结果。 */
public final class OrderedQuadScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("旧构造保留坐标UV相邻合批顺序和容量", this::legacy);
        scenario("仿射反射保留四角和逐顶点UV且局部矩形随变换裁剪", this::transformed);
        scenario("孔裁剪的实际三角载荷保留面积覆盖和线性UV", this::shapeClip);
    }
    private static void near(double actual, double expected, double tolerance, String message) {
        require(Math.abs(actual - expected) <= tolerance, message + " got=" + actual + " expected=" + expected);
    }
    private void legacy() {
        List<OrderedQuadBatch> batches = new ArrayList<>();
        OrderedQuadCollector collector = new OrderedQuadCollector(3, batches::add);
        State a = new State(1, 101, 4, new LayoutBox(0, 0, 50, 50));
        State b = new State(1, 202, 4, a.clip);
        Quad first = new Quad(2, 3, 5, 7, .1f, .2f, .8f, .9f, 2, 0x80446688);
        Quad skewed = new Quad(2, 3, 8, 4, 7, 11, 1, 10, .1f, .2f, .8f, .9f, 2, first.color);
        collector.add(a, new Quad(80, 80, 5, 5, 0, 0, 1, 1, 0, first.color));
        collector.add(a, first); collector.add(a, skewed); collector.add(b, first); collector.add(a, first); collector.flush();
        require(batches.size() == 2 && batches.get(0).quads().size() == 3 && batches.get(1).quads().size() == 1, "容量或旧矩形保守剔除改变");
        require(batches.get(0).quads().get(0) == first && batches.get(0).quads().get(1) == skewed, "旧单位路径额外重建了顶点");
        require(batches.get(0).draws().size() == 2 && batches.get(0).draws().get(0).quadCount == 2
                && batches.get(0).draws().get(1).quadCount == 1, "相邻材质未保留原合批");
        require(resources(batches).equals(Arrays.asList(101L, 202L, 101L)), "A/B/A被按材质重排");
        near(first.x0, 2, 0, "旧角0x"); near(first.y0, 3, 0, "旧角0y");
        near(first.x2, 7, 0, "旧角2x"); near(first.y2, 10, 0, "旧角2y");
        near(first.s0, first.u0, 0, "旧UV0s"); near(first.t0, first.v0, 0, "旧UV0t");
        near(first.s1, first.u1, 0, "旧UV1s"); near(first.t1, first.v0, 0, "旧UV1t");
        near(first.s2, first.u1, 0, "旧UV2s"); near(first.t2, first.v1, 0, "旧UV2t");
        near(first.s3, first.u0, 0, "旧UV3s"); near(first.t3, first.v1, 0, "旧UV3t");
        require(a.transform.identity() && a.shapeClip.unbounded(), "旧State增加了调用前提");
    }
    private void transformed() {
        List<OrderedQuadBatch> batches = new ArrayList<>();
        OrderedQuadCollector collector = new OrderedQuadCollector(64, batches::add);
        Quad local = new Quad(1, 2, 5, 2, 5, 6, 1, 6,
                .12f, .23f, .45f, .27f, .81f, .89f, .22f, .65f, 3, 0xffabcdef);
        SceneTransform transform = SceneTransform.translation(20, 30).multiply(SceneTransform.of(0, 1, -1, 0, 0, 0))
                .multiply(SceneTransform.scale(-2, 3));
        State localState = new State(1, 303, 8, null, transform, SceneClipRegion.UNBOUNDED);
        collector.add(localState, local); collector.flush();
        require(batches.size() == 1 && batches.get(0).quads().size() == 1, "无裁剪仿射增加了图元");
        Quad world = batches.get(0).quads().get(0);
        float[] expected = {14, 28, 14, 20, 2, 20, 2, 28};
        double[] points = points(world), uv = uv(world), before = uv(local);
        for (int i = 0; i < points.length; i++) near(points[i], expected[i], 0, "反射四角" + i);
        for (int i = 0; i < uv.length; i++) near(uv[i], before[i], 0, "独立采样" + i);
        require(world.layer == local.layer && world.color == local.color, "变换改变图层或颜色");
        State prepared = batches.get(0).draws().get(0).state;
        require(prepared.transform.identity() && prepared.shapeClip.unbounded(), "消费者还会重复应用变换");
        near(world.u0, world.s0, 0, "新UV旧端点0"); near(world.v1, world.t2, 0, "新UV旧端点2");
        State equivalent = new State(1, 303, 8, null, transform, SceneClipRegion.UNBOUNDED);
        require(localState.equals(equivalent) && localState.hashCode() == equivalent.hashCode()
                && !localState.equals(new State(1, 303, 8, null)), "State未比较真实几何");
        State combined = new State(1, 303, 8, new LayoutBox(0, 0, 4, 4), SceneTransform.translation(2, 3),
                SceneClipRegion.rectangle(new LayoutBox(2, 3, 4, 4))).withGeometry(SceneTransform.translation(10, 20),
                SceneClipRegion.rectangle(new LayoutBox(13, 24, 2, 2)));
        near(combined.transform.x(1, 1), 13, 0, "外层左乘x"); near(combined.transform.y(1, 1), 24, 0, "外层左乘y");
        require(combined.clip.x == 0 && combined.clip.y == 0 && combined.effectiveClip().contains(14, 25)
                && !combined.effectiveClip().contains(12.5, 25), "withGeometry丢失局部矩形或外部交集");
        batches.clear();
        SceneTransform rotation = SceneTransform.rotationDegrees(45);
        collector.add(new State(1, 303, 8, new LayoutBox(-2, -2, 4, 4), rotation, SceneClipRegion.UNBOUNDED),
                new Quad(-5, -5, 10, 10, 0, 0, 1, 1, 0, 0xffffffff)); collector.flush();
        near(area(batches), 16, 1e-5, "旋转局部矩形被world包络代替");
        require(!covered(batches, 2.5, 2.5) && covered(batches, 0, 2.5), "旋转矩形真实边界未参与裁剪");
    }
    private void shapeClip() {
        ScenePath path = ScenePath.builder().rule(ScenePath.FillRule.EVEN_ODD)
                .moveTo(1, 1).lineTo(9, 1).lineTo(9, 9).lineTo(1, 9).close()
                .moveTo(4, 4).lineTo(6, 4).lineTo(6, 6).lineTo(4, 6).close().build();
        SceneTransform transform = SceneTransform.translation(12, -5);
        SceneMesh mask = path.fillMesh(.05).transformed(transform);
        List<OrderedQuadBatch> batches = new ArrayList<>(); OrderedQuadCollector collector = new OrderedQuadCollector(5, batches::add);
        Quad texture = new Quad(0, 0, 10, 0, 10, 10, 0, 10, .2f, .1f, .6f, .2f, .8f, .7f, .4f, .6f, 7, 0x80446688);
        collector.add(new State(1, 404, 9, null, transform, SceneClipRegion.of(mask)), texture); collector.flush();
        require(!batches.isEmpty(), "真实裁剪没有输出");
        for (OrderedQuadBatch batch : batches) {
            require(batch.quads().size() <= 5, "裁剪展开突破容量");
            for (OrderedQuadBatch.Draw draw : batch.draws()) require(draw.state.transform.identity() && draw.state.shapeClip.unbounded(), "输出没有归一到世界几何");
            for (Quad q : batch.quads()) {
                require(q.layer == texture.layer && q.color == texture.color && q.x2 == q.x3 && q.y2 == q.y3
                        && q.s2 == q.s3 && q.t2 == q.t3, "三角载荷或原材质属性改变");
                double[] xy = points(q), uv = uv(q);
                for (int i = 0; i < 4; i++) {
                    double x = xy[i * 2] - 12, y = xy[i * 2 + 1] + 5;
                    near(uv[i * 2], .2 + .04 * x + .02 * y, 1e-6, "裁后u的独立线性关系");
                    near(uv[i * 2 + 1], .1 + .01 * x + .05 * y, 1e-6, "裁后v的独立线性关系");
                }
                require(mask.contains((q.x0 + q.x1 + q.x2) / 3.0, (q.y0 + q.y1 + q.y2) / 3.0), "输出三角跨过透明孔");
            }
        }
        near(area(batches), 60, 1e-5, "裁后面积不等于8×8外形减2×2孔");
        for (int row = 0; row < 10; row++) for (int col = 0; col < 10; col++) {
            double x = col + .25, y = row + .25;
            boolean expected = x > 1 && x < 9 && y > 1 && y < 9 && !(x > 4 && x < 6 && y > 4 && y < 6);
            require(covered(batches, x + 12, y - 5) == expected, "实际顶点覆盖与矩形孔不符: " + x + "," + y);
        }
    }
    private static List<Long> resources(List<OrderedQuadBatch> batches) {
        List<Long> result = new ArrayList<>();
        for (OrderedQuadBatch batch : batches) for (OrderedQuadBatch.Draw draw : batch.draws())
            if (result.isEmpty() || result.get(result.size() - 1).longValue() != draw.state.resource) result.add(draw.state.resource);
        return result;
    }
    private static double[] points(Quad q) { return new double[] {q.x0, q.y0, q.x1, q.y1, q.x2, q.y2, q.x3, q.y3}; }
    private static double[] uv(Quad q) { return new double[] {q.s0, q.t0, q.s1, q.t1, q.s2, q.t2, q.s3, q.t3}; }
    private static double cross(double x0, double y0, double x1, double y1, double x2, double y2) {
        return (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
    }
    private static double area(List<OrderedQuadBatch> batches) {
        double result = 0;
        for (OrderedQuadBatch batch : batches) for (Quad q : batch.quads()) {
            result += Math.abs(cross(q.x0, q.y0, q.x1, q.y1, q.x2, q.y2)) / 2;
            result += Math.abs(cross(q.x0, q.y0, q.x2, q.y2, q.x3, q.y3)) / 2;
        }
        return result;
    }
    private static boolean triangle(double x0, double y0, double x1, double y1, double x2, double y2, double x, double y) {
        if (cross(x0, y0, x1, y1, x2, y2) == 0) return false;
        double a = cross(x0, y0, x1, y1, x, y), b = cross(x1, y1, x2, y2, x, y), c = cross(x2, y2, x0, y0, x, y);
        return a >= 0 && b >= 0 && c >= 0 || a <= 0 && b <= 0 && c <= 0;
    }
    private static boolean covered(List<OrderedQuadBatch> batches, double x, double y) {
        for (OrderedQuadBatch batch : batches) for (Quad q : batch.quads())
            if (triangle(q.x0, q.y0, q.x1, q.y1, q.x2, q.y2, x, y) || triangle(q.x0, q.y0, q.x2, q.y2, q.x3, q.y3, x, y)) return true;
        return false;
    }
}
