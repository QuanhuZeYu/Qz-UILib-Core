package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.testing.TestApplication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

/** 用业务选择和独立线性前缀核验集合逻辑，不镜像树状索引实现。 */
public final class VirtualCollectionIndexScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new VirtualCollectionIndexScenario().run(args); }

    @Override protected void runTests() throws Exception {
        scenario("选择替换切换及按当前顺序范围选择", this::selection);
        scenario("可变高度前缀和像素定位", this::heights);
        scenario("换代重排与索引释放", this::revisions);
    }

    private void selection() {
        List<String> order = Arrays.asList("甲", "乙", "丙", "丁");
        SceneSelection<String> empty = SceneSelection.empty();
        require(empty.retain(new TreeSet<>()).selected().isEmpty(), "空选择允许不接收null的键集合");
        SceneSelection<String> first = empty.select("乙", SceneSelection.Mode.REPLACE, order);
        SceneSelection<String> toggled = first.select("丁", SceneSelection.Mode.TOGGLE, order);
        require(toggled.selected().equals(new LinkedHashSet<>(Arrays.asList("乙", "丁"))), "切换选择追加键");
        SceneSelection<String> range = toggled.select("甲", SceneSelection.Mode.RANGE, order);
        require(range.selected().equals(new LinkedHashSet<>(order)) && range.anchor().equals("丁")
                && range.active().equals("甲"), "反向区间沿锚点和当前顺序选择");
        SceneSelection<String> reordered = range.select("乙", SceneSelection.Mode.RANGE,
                Arrays.asList("丁", "乙", "甲", "丙"));
        require(reordered.selected().equals(new LinkedHashSet<>(Arrays.asList("丁", "乙"))), "重排后范围使用新顺序");
        SceneSelection<String> retained = reordered.retain(Collections.singleton("乙"));
        require(retained.selected().equals(Collections.singleton("乙")) && retained.anchor() == null
                && retained.active().equals("乙"), "显式保留解除失效锚点");
        require(first.selected().equals(Collections.singleton("乙")) && empty.selected().isEmpty(), "旧选择值不可变");
        require(retained.select("已删除", SceneSelection.Mode.REPLACE, order) == retained, "迟到目标不改变选择");
        require(retained.select("甲", SceneSelection.Mode.RANGE, order).selected().equals(Collections.singleton("甲")),
                "失效锚点从本次目标建立");
        try { first.selected().add("丙"); throw new AssertionError("选择集合泄漏写端"); }
        catch (UnsupportedOperationException expected) { }
    }

    private void heights() {
        VirtualHeightIndex<Integer> index = new VirtualHeightIndex<>();
        List<Integer> keys = Arrays.asList(0, 1, 2, 3, 4, 5);
        index.reconcile(keys, 20, 3, false);
        int[] heights = {0, 7, 42, 0, 5, 31};
        for (int i = 0; i < heights.length; i++) index.measure(i, heights[i]);
        checkLinear(index, heights, 3);
        heights[2] = 9; index.measure(2, 9);
        checkLinear(index, heights, 3);
        index.invalidate(1); heights[1] = 20;
        checkLinear(index, heights, 3);
        index.omit(0); index.omit(2); index.omit(5);
        require(index.start(1) == 0 && index.start(3) == 23 && index.total() == 31, "隐藏行不贡献高度或间距");
        require(index.at(0) == 1 && index.at(23) == 3 && index.at(26) == 4, "隐藏行像素定位");
        index.measure(0, 0); require(index.start(1) == 3, "可见零高与隐藏不混为同一事实");
        index.reconcile(keys, 20, 0, false);
        heights[0] = heights[1] = heights[2] = heights[3] = heights[4] = heights[5] = 0;
        for (int i = 0; i < heights.length; i++) index.measure(i, 0);
        checkLinear(index, heights, 0);
    }

    private void checkLinear(VirtualHeightIndex<Integer> index, int[] heights, int gap) {
        long total = 0;
        for (int i = 0; i < heights.length; i++) {
            require(index.start(i) == total, "实际高度累计起点错误: " + i);
            total += heights[i] + (i + 1 < heights.length ? gap : 0);
        }
        require(index.total() == total && index.extent() == total, "内容范围须扣除末尾间距");
        for (long pixel = -1; pixel <= total + 1; pixel++) {
            int expected = heights.length;
            if (pixel < total) {
                long end = 0;
                for (int i = 0; i < heights.length; i++) {
                    end += heights[i] + gap;
                    if (Math.max(0, pixel) < end) { expected = i; break; }
                }
            }
            require(index.at(pixel) == expected, "像素定位与独立线性区间不符: " + pixel);
        }
    }

    private void revisions() {
        VirtualHeightIndex<String> index = new VirtualHeightIndex<>();
        index.reconcile(Arrays.asList("甲", "乙", "丙"), 20, 0, false);
        index.measure("甲", 70); index.measure("乙", 10);
        index.reconcile(Arrays.asList("乙", "丙", "甲"), 20, 0, false);
        require(index.start(2) == 30 && index.height(2) == 70, "持续键重排保留当前度量");
        index.reconcile(Arrays.asList("乙", "丙"), 20, 0, false);
        index.reconcile(Arrays.asList("乙", "丙", "甲"), 20, 0, false);
        require(!index.known("甲") && index.height(2) == 20, "真实退出后旧度量不复活");
        index.reconcile(Arrays.asList("乙", "丙", "甲"), 20, 0, true);
        require(index.total() == 60 && !index.known("乙"), "宽度或资源换代结束旧精确度量");
        try { index.reconcile(Arrays.asList("甲", "甲"), 20, 0, false); throw new AssertionError("重复键被接受"); }
        catch (IllegalArgumentException expected) { require(expected.getMessage().contains("甲"), "重复键诊断缺少身份"); }
        require(index.total() == 60, "无效声明未改变既有索引");
        index.reconcile(Arrays.asList("甲", "乙"), Integer.MAX_VALUE, 0, true);
        require(index.total() == 2L * Integer.MAX_VALUE, "纯索引累计不能int溢出");
        try { index.extent(); throw new AssertionError("超出scene内容范围被截断"); }
        catch (IllegalArgumentException expected) { }
        index.clear(); require(index.size() == 0 && index.total() == 0 && index.at(0) == 0, "索引释放");
    }
}
