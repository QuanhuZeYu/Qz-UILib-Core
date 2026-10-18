package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.geometry.SceneMesh;
import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 原排列后一次解析累计矩阵与精确clip，保持普通布局、滚动单位和所属层呈现边界。 */
final class SceneGeometryProjection {
    static List<SceneGeometryView.Entry> project(List<SceneGeometryView.Entry> entries, LayoutBox viewport) {
        return project(entries, viewport, SceneTransform.IDENTITY, SceneClipRegion.UNBOUNDED);
    }
    static List<SceneGeometryView.Entry> project(List<SceneGeometryView.Entry> entries, LayoutBox viewport,
            SceneTransform inherited, SceneClipRegion external) {
        boolean unchanged = inherited.identity() && external.unbounded();
        for (SceneGeometryView.Entry entry : entries)
            unchanged &= entry.node.transform().identity() && entry.node.shapeClip() == null
                    && (entry.node.control() == null || entry.node.control().hitShape(entry.node) == null);
        if (unchanged) return entries;
        Map<SceneNode, SceneGeometryView.Entry> raw = new IdentityHashMap<>(), resolved = new IdentityHashMap<>();
        for (SceneGeometryView.Entry entry : entries) raw.put(entry.node, entry);
        Map<SceneNode, SceneClipRegion> childrenClip = new IdentityHashMap<>();
        List<SceneGeometryView.Entry> result = new ArrayList<>();
        for (SceneGeometryView.Entry entry : entries) result.add(resolve(entry.node, raw, resolved, childrenClip, viewport, inherited, external));
        return result;
    }
    private static SceneGeometryView.Entry resolve(SceneNode node, Map<SceneNode, SceneGeometryView.Entry> raw,
            Map<SceneNode, SceneGeometryView.Entry> resolved, Map<SceneNode, SceneClipRegion> childrenClip,
            LayoutBox viewport, SceneTransform inherited, SceneClipRegion external) {
        SceneGeometryView.Entry known = resolved.get(node); if (known != null) return known;
        SceneGeometryView.Entry entry = raw.get(node); LayoutBox box = entry.layoutBox;
        boolean layer = node.control() != null && node.control().layerPolicy(node) != null;
        SceneGeometryView.Entry parent = !layer && raw.containsKey(node.parent())
                ? resolve(node.parent(), raw, resolved, childrenClip, viewport, inherited, external) : null;
        SceneTransform base = layer ? SceneTransform.IDENTITY : parent == null ? inherited : parent.transform;
        SceneClipRegion clip = layer ? SceneClipRegion.rectangle(viewport) : parent == null ? external : childrenClip.get(parent.node);
        SceneTransform matrix = base.multiply(SceneTransform.around(box.x, box.y, node.transform()));
        SceneClipRegion own = node.clip() ? SceneClipRegion.rectangle(box).transformed(matrix) : SceneClipRegion.UNBOUNDED;
        if (node.shapeClip() != null) {
            double scale = matrix.maximumScale();
            double error = scale == 0 ? .25 : .25 / scale;
            SceneMesh shape = node.shapeClip().resolve(box.width, box.height, error)
                    .transformed(matrix.multiply(SceneTransform.translation(box.x, box.y)));
            own = own.intersect(shape);
        }
        clip = clip.intersect(own);
        club.heiqi.uilib.ui.scene.geometry.SceneShape hitShape = node.control() == null ? null : node.control().hitShape(node);
        SceneMesh hit = hitShape == null ? null : hitShape.resolve(box.width, box.height, .25 / Math.max(1e-12, matrix.maximumScale()))
                .transformed(matrix.multiply(SceneTransform.translation(box.x, box.y)));
        SceneGeometryView.Entry projected = entry.projected(matrix, clip, hit, own);
        resolved.put(node, projected);
        childrenClip.put(node, node.scrollable() ? clip.intersect(SceneClipRegion.rectangle(box).transformed(matrix)) : clip);
        return projected;
    }
}
