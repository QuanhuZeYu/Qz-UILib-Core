package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 一份世界快照的只读消费者坐标视图；同批由SceneGeometry缓存身份。 */
final class SceneControlGeometry implements SceneGeometryView {
    private final SceneGeometryView source;
    private final SceneNode consumer;
    private final SceneTransform transform, inverse;
    private final Map<SceneNode, Entry> entries = new IdentityHashMap<>();
    private List<Entry> ordered, original;
    SceneControlGeometry(SceneGeometryView source, SceneNode consumer) {
        this.source = source.world(); this.consumer = consumer;
        transform = this.source.get(consumer).transform; inverse = transform.inverse();
    }
    public SceneGeometryView world() { return source; }
    public SceneTransform coordinateTransform() { return transform; }
    public SceneGeometryView asControl(SceneNode node) { return source.asControl(node); }
    public Entry get(SceneNode node) {
        Entry entry = source.get(node); return entry == null ? null : entries.computeIfAbsent(node, key -> entry.in(inverse, key == consumer));
    }
    public List<Entry> ordered() {
        if (ordered == null) { List<Entry> next = new ArrayList<>(); for (Entry entry : source.ordered()) next.add(get(entry.node)); ordered = java.util.Collections.unmodifiableList(next); }
        return ordered;
    }
    public List<Entry> sourceOrdered() {
        if (original == null) { List<Entry> next = new ArrayList<>(); for (Entry entry : source.sourceOrdered()) next.add(entry.in(inverse, entry.node == consumer)); original = java.util.Collections.unmodifiableList(next); }
        return original;
    }
    public LayoutBox viewport() {
        LayoutBox value = source.viewport();
        return inverse == null ? value : SceneGeometryView.bounds(club.heiqi.uilib.ui.scene.geometry.SceneMesh.rectangle(value.x, value.y, value.width, value.height).transformed(inverse));
    }
    public List<Group> groups() { List<Group> result = new ArrayList<>(); for (Group group : source.groups()) {
        List<Entry> local = new ArrayList<>(); for (Entry entry : group.entries) local.add(get(entry.node));
        result.add(new Group(group.root, group.layer, group.scope, group.policy, local));
    } return java.util.Collections.unmodifiableList(result); }
    public SceneNode layerBoundary(SceneNode node) { return source.layerBoundary(node); }
    public SceneNode logicalScope(SceneNode node) { return source.logicalScope(node); }
    public club.heiqi.uilib.ui.scene.control.SceneLayer.Policy layerPolicy(SceneNode node) { return source.layerPolicy(node); }
    public boolean layerSourceAvailable(SceneNode node) { return source.layerSourceAvailable(node); }
    public SceneNode activeModal() { return source.activeModal(); }
}
