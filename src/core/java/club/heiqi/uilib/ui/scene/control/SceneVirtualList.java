package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** 有序展示数据的有限行视图；整行参与资格用工厂外的Computed数据投影表达。
 * 独立草稿归调用方，离屏行不在不同业务键间改绑，视图结束即结束其节点绑定。
 * 合同见 specs/scene/2026-10-18-uilib-virtual-collection-spec.md。 */
public final class SceneVirtualList<T, K> {
    public static final class Options {
        final int estimate, overscan;
        public Options() { this(0, -1); }
        private Options(int estimate, int overscan) { this.estimate = estimate; this.overscan = overscan; }
        public Options estimatedRowHeight(int pixels) {
            if (pixels <= 0) throw new IllegalArgumentException("估算行高须为正");
            return new Options(pixels, overscan);
        }
        public Options overscan(int pixels) {
            if (pixels < 0) throw new IllegalArgumentException("预取距离不能为负");
            return new Options(estimate, pixels);
        }
    }

    private final VirtualCollectionController<T, K> controller;
    private final SceneNode node;
    private final SceneScrollViewport viewport;

    private SceneVirtualList(ScenePageContext page, ReadableSignal<List<T>> items, Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration, ReadableSignal<Options> options, ReadableSignal<?> measurementCondition) {
        controller = new VirtualCollectionController<>(page, items, key, declaration, options, measurementCondition);
        node = new SceneNode().size(Size.fill(1), Size.fill(1)).clip(true).scrollable(true).control(controller);
        controller.attach(node);
        viewport = SceneScrollViewport.of(page, node, SceneNode.Axis.COLUMN);
    }

    public static <T, K> SceneVirtualList<T, K> create(ScenePageContext page, ReadableSignal<List<T>> items,
            Function<T, K> key, Function<ReadableSignal<T>, SceneNode> declaration) {
        return create(page, items, key, declaration, () -> new Options());
    }

    public static <T, K> SceneVirtualList<T, K> create(ScenePageContext page, ReadableSignal<List<T>> items,
            Function<T, K> key, Function<ReadableSignal<T>, SceneNode> declaration, ReadableSignal<Options> options) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneVirtualList<>(page, Objects.requireNonNull(items, "items"), Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(declaration, "declaration"), Objects.requireNonNull(options, "options"), () -> null);
    }

    /** 复合数据视图的列条件只结束旧高度事实；既有公开入口没有新增依赖前提。 */
    static <T, K> SceneVirtualList<T, K> measured(ScenePageContext page, ReadableSignal<List<T>> items, Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration, ReadableSignal<Options> options, ReadableSignal<?> measurementCondition) {
        return new SceneVirtualList<>(page, items, key, declaration, options, measurementCondition);
    }

    /** 所属数据视图声明自己的语义角色；普通公开集合仍使用LIST和LIST_ITEM。 */
    void semanticRoles(club.heiqi.uilib.ui.scene.semantics.SceneSemantics.Role collection, club.heiqi.uilib.ui.scene.semantics.SceneSemantics.Role item) {
        controller.semanticRoles(collection, item);
    }
    int logicalPosition(K key) { return controller.logicalPosition(key); }
    int logicalCount() { return controller.logicalCount(); }

    public SceneNode node() { return node; }
    public SceneScrollViewport viewport() { return viewport; }

    /** 稳定键的显露意图；不激活、提交或改变当前焦点，已删除目标返回false。 */
    public boolean reveal(K key) { return controller.request(key, false); }

    /** 准备该行并请求其首个合法焦点，最终选择仍归唯一焦点路由。 */
    public boolean focus(K key) { return controller.request(key, true); }

    /** 所属复合视图只选自己的主目标，不改变公开focus的首合法后代语义。 */
    boolean focus(K key, Function<SceneNode, SceneNode> target) { return controller.request(key, true, target); }

    /** 弹出复合内容按明确起点准备并显露，普通初始遍历仍保留原条件。 */
    club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Cursor beginAt(K key, boolean backward) { return controller.beginAt(key, backward); }
    club.heiqi.uilib.ui.scene.input.SceneFocusTraversal.Cursor beginAt(K key, boolean backward, boolean reveal) { return controller.beginAt(key, backward, reveal); }
}
