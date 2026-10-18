package club.heiqi.uilib.ui.scene.layout;

import java.util.List;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.geometry.SceneMesh;
import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;

public interface SceneGeometryView {
    final class Entry {
        public final SceneNode node;
        /** 当前view中的保守边界；控件自身的view保留原布局盒。 */
        public final LayoutBox box, clip;
        public final LayoutBox layoutBox, worldBounds;
        public final SceneTransform transform, inverse;
        public final SceneClipRegion worldClip, shapeClip;
        /** 仅本节点的已解析裁剪；捕获和逐层显露复用同一mesh，不冻结祖先。 */
        public final SceneClipRegion ownWorldClip;
        /** 控件准备出的实际命中填充；null保持原盒命中，坐标固定为world。 */
        public final SceneMesh hitMesh;
        public final TextLayout text;
        public final int scrollOffset, scrollMaximum;

        public Entry(SceneNode node, LayoutBox box, LayoutBox clip, TextLayout text) { this(node, box, clip, text, 0, 0); }
        public Entry(SceneNode node, LayoutBox box, LayoutBox clip, TextLayout text, int scrollOffset, int scrollMaximum) {
            this(node, box, box, SceneTransform.IDENTITY, clip == null ? SceneClipRegion.UNBOUNDED : SceneClipRegion.rectangle(clip),
                    box, clip == null ? SceneClipRegion.UNBOUNDED : SceneClipRegion.rectangle(clip), text, scrollOffset, scrollMaximum, null, node.clip() ? SceneClipRegion.rectangle(box) : SceneClipRegion.UNBOUNDED);
        }
        private Entry(SceneNode node, LayoutBox layoutBox, LayoutBox worldBounds, SceneTransform transform,
                SceneClipRegion worldClip, LayoutBox box, SceneClipRegion shapeClip, TextLayout text, int scrollOffset, int scrollMaximum, SceneMesh hitMesh, SceneClipRegion ownWorldClip) {
            this.node = node; this.layoutBox = layoutBox; this.worldBounds = worldBounds; this.transform = transform;
            inverse = transform.inverse(); this.worldClip = worldClip; this.shapeClip = shapeClip; this.hitMesh = hitMesh; this.ownWorldClip = ownWorldClip;
            this.box = box; clip = shapeClip.bounds(); this.text = text; this.scrollOffset = scrollOffset; this.scrollMaximum = scrollMaximum;
        }
        public Entry projected(SceneTransform matrix, SceneClipRegion region) { return projected(matrix, region, hitMesh); }
        public Entry projected(SceneTransform matrix, SceneClipRegion region, SceneMesh hit) { return projected(matrix, region, hit, ownWorldClip); }
        public Entry projected(SceneTransform matrix, SceneClipRegion region, SceneMesh hit, SceneClipRegion ownClip) {
            LayoutBox bounds = hit != null ? bounds(hit) : matrix.identity() ? layoutBox
                    : bounds(SceneMesh.rectangle(layoutBox.x, layoutBox.y, layoutBox.width, layoutBox.height).transformed(matrix));
            return new Entry(node, layoutBox, bounds, matrix, region, bounds, region, text, scrollOffset, scrollMaximum, hit, ownClip);
        }
        public Entry in(SceneTransform frameInverse, boolean self) {
            if (frameInverse == null) return new Entry(node, layoutBox, worldBounds, transform, worldClip, layoutBox,
                    SceneClipRegion.UNBOUNDED, text, scrollOffset, scrollMaximum, hitMesh, ownWorldClip);
            // 同一累计frame的孩子直接保布局盒，避免逆乘浮点尾差经floor/ceil膨胀一个像素。
            SceneTransform relative = frameInverse.equals(inverse) ? SceneTransform.IDENTITY : frameInverse.multiply(transform);
            LayoutBox bounds = self ? layoutBox : hitMesh != null ? bounds(hitMesh.transformed(frameInverse)) : relative.identity() ? layoutBox
                    : bounds(SceneMesh.rectangle(layoutBox.x, layoutBox.y, layoutBox.width, layoutBox.height).transformed(relative));
            return new Entry(node, layoutBox, worldBounds, transform, worldClip, bounds, worldClip.transformed(frameInverse), text, scrollOffset, scrollMaximum, hitMesh, ownWorldClip);
        }
        public Entry withText(TextLayout value) {
            return new Entry(node, layoutBox, worldBounds, transform, worldClip, box, shapeClip, value, scrollOffset, scrollMaximum, hitMesh, ownWorldClip);
        }
        public SceneMesh worldShape() { return hitMesh != null ? hitMesh : SceneMesh.rectangle(layoutBox.x, layoutBox.y, layoutBox.width, layoutBox.height).transformed(transform); }
    }

    /** 同一正式快照的世界读取与按消费者缓存的局部读取；不另建布局或输入树。 */
    default SceneGeometryView world() { return this; }
    default SceneTransform coordinateTransform() { return SceneTransform.IDENTITY; }
    default SceneGeometryView asControl(SceneNode consumer) {
        SceneGeometryView world = world();
        if (world != this) return world.asControl(consumer);
        Entry entry = get(consumer);
        return entry == null || entry.transform.identity() && sameBox(entry.box, entry.layoutBox) ? this : new SceneControlGeometry(this, consumer);
    }
    default double mapX(SceneNode node, double x, double y) {
        Entry entry = world().get(node); if (entry == null || entry.inverse == null) return Double.NaN;
        SceneTransform frame = coordinateTransform(); return entry.inverse.x(frame.x(x, y), frame.y(x, y));
    }
    default double mapY(SceneNode node, double x, double y) {
        Entry entry = world().get(node); if (entry == null || entry.inverse == null) return Double.NaN;
        SceneTransform frame = coordinateTransform(); return entry.inverse.y(frame.x(x, y), frame.y(x, y));
    }
    default boolean clipContains(SceneNode node, double x, double y) {
        Entry entry = world().get(node); if (entry == null || entry.inverse == null) return false;
        SceneTransform frame = coordinateTransform(); return entry.worldClip.contains(frame.x(x, y), frame.y(x, y));
    }
    default boolean contains(SceneNode node, double x, double y) {
        if (!clipContains(node, x, y)) return false;
        Entry entry = world().get(node); double px = mapX(node, x, y), py = mapY(node, x, y);
        boolean box = px >= entry.layoutBox.x && py >= entry.layoutBox.y
                && px < (double) entry.layoutBox.x + entry.layoutBox.width && py < (double) entry.layoutBox.y + entry.layoutBox.height;
        SceneTransform frame = coordinateTransform();
        return entry.hitMesh != null ? entry.hitMesh.contains(frame.x(x, y), frame.y(x, y)) : box;
    }
    default SceneMesh visibleRegion(SceneNode node) {
        Entry entry = world().get(node); if (entry == null || entry.inverse == null) return SceneMesh.EMPTY;
        LayoutBox viewport = world().viewport();
        SceneMesh visible = entry.worldClip.intersect(SceneClipRegion.rectangle(viewport)).clip(entry.worldShape());
        SceneTransform inverse = coordinateTransform().inverse();
        return inverse == null ? SceneMesh.EMPTY : visible.transformed(inverse);
    }
    static boolean sameBox(LayoutBox a, LayoutBox b) {
        return a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height;
    }
    static LayoutBox bounds(SceneMesh mesh) {
        if (mesh.empty()) return new LayoutBox(0, 0, 0, 0);
        long left = (long) Math.floor(mesh.minX), top = (long) Math.floor(mesh.minY);
        long right = (long) Math.ceil(mesh.maxX), bottom = (long) Math.ceil(mesh.maxY);
        if (left < Integer.MIN_VALUE || top < Integer.MIN_VALUE || right > Integer.MAX_VALUE || bottom > Integer.MAX_VALUE
                || right - left > Integer.MAX_VALUE || bottom - top > Integer.MAX_VALUE)
            throw new IllegalArgumentException("呈现边界超出scene整数范围");
        return new LayoutBox((int) left, (int) top, (int) (right - left), (int) (bottom - top));
    }

    /** 同代宿主可用区域；被动呈现以此限界，不读取平台或布局后回写。 */
    default LayoutBox viewport() {
        int width = 0, height = 0;
        for (Entry entry : ordered())
            if (entry.node.parent() == null) {
                width = Math.max(width, entry.box.x + entry.box.width);
                height = Math.max(height, entry.box.y + entry.box.height);
            }
        return new LayoutBox(0, 0, width, height);
    }

    /** 同批呈现子树片段；每组独立闭合 paintAfterChildren 栈。 */
    final class Group {
        public final SceneNode root, layer, scope;
        public final club.heiqi.uilib.ui.scene.control.SceneLayer.Policy policy;
        public final List<Entry> entries;
        public Group(SceneNode root, SceneNode layer, SceneNode scope,
                club.heiqi.uilib.ui.scene.control.SceneLayer.Policy policy, List<Entry> entries) {
            this.root = root; this.layer = layer; this.scope = scope; this.policy = policy;
            this.entries = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(entries));
        }
    }
    default List<Group> groups() {
        return java.util.Collections.singletonList(new Group(null, null, null, null, ordered()));
    }
    /** 空值表示普通呈现路径；结果与 ordered 同代。 */
    default SceneNode layerBoundary(SceneNode node) { return null; }
    default SceneNode logicalScope(SceneNode node) { return null; }
    default club.heiqi.uilib.ui.scene.control.SceneLayer.Policy layerPolicy(SceneNode node) { return null; }
    default boolean layerSourceAvailable(SceneNode layer) { return false; }
    default SceneNode activeModal() { return null; }

    Entry get(SceneNode node);

    List<Entry> ordered();

    /** 同批原排列顺序，仅用于完整内容捕获；条目与 get 同一，输入/主绘制层序仍消费 ordered/groups。 */
    default List<Entry> sourceOrdered() { return ordered(); }
}
