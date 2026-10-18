package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** 只有业务键和值的树索引；校验、展开均不创建SceneNode，不递归遍历深层调用栈。 */
final class TreeIndex<T, K> {
    static final class Item<T, K> {
        final T value; final K key, parent; final List<K> children = new ArrayList<>();
        int position, siblingCount;
        Item(T value, K key, K parent) { this.value = value; this.key = key; this.parent = parent; }
    }
    final Map<K, Item<T, K>> items = new LinkedHashMap<>();
    private final List<K> roots = new ArrayList<>();

    TreeIndex(List<T> source, Function<T, K> key, Function<T, K> parent) {
        for (T value : Objects.requireNonNull(source, "树来源")) {
            K identity = Objects.requireNonNull(key.apply(value), "树条目键");
            if (items.containsKey(identity)) throw new IllegalArgumentException("树条目键重复: " + identity);
            items.put(identity, new Item<>(value, identity, parent.apply(value)));
        }
        for (Item<T, K> item : items.values()) {
            if (item.parent == null) roots.add(item.key);
            else {
                Item<T, K> ancestor = items.get(item.parent);
                if (ancestor == null) throw new IllegalArgumentException("树条目缺少父项: " + item.key + " -> " + item.parent);
                ancestor.children.add(item.key);
            }
        }
        // 每次结构来源只建立一次同级位置，有限行语义读取不重新扫描整个投影。
        indexSiblings(roots);
        for (Item<T, K> item : items.values()) indexSiblings(item.children);
        Set<K> checked = new HashSet<>();
        for (K identity : items.keySet()) {
            Set<K> path = new HashSet<>(); K cursor = identity;
            while (cursor != null && !checked.contains(cursor)) {
                if (!path.add(cursor)) throw new IllegalArgumentException("树父子关系循环: " + cursor);
                cursor = items.get(cursor).parent;
            }
            checked.addAll(path);
        }
    }

    private void indexSiblings(List<K> siblings) {
        for (int i = 0; i < siblings.size(); i++) {
            Item<T, K> item = items.get(siblings.get(i)); item.position = i + 1; item.siblingCount = siblings.size();
        }
    }

    private static final class Visit<K> {
        final K key; final int depth;
        Visit(K key, int depth) { this.key = key; this.depth = depth; }
    }
    List<SceneTreeView.Entry<T, K>> project(Set<K> expanded) {
        Objects.requireNonNull(expanded, "展开键集合");
        List<SceneTreeView.Entry<T, K>> result = new ArrayList<>();
        ArrayDeque<Visit<K>> pending = new ArrayDeque<>();
        for (int i = roots.size() - 1; i >= 0; i--) pending.push(new Visit<>(roots.get(i), 0));
        while (!pending.isEmpty()) {
            Visit<K> visit = pending.pop(); Item<T, K> item = items.get(visit.key);
            boolean branch = !item.children.isEmpty(), open = branch && expanded.contains(item.key);
            result.add(new SceneTreeView.Entry<>(item.value, item.key, item.parent, visit.depth, branch, open));
            if (open) for (int i = item.children.size() - 1; i >= 0; i--) pending.push(new Visit<>(item.children.get(i), visit.depth + 1));
        }
        return Collections.unmodifiableList(result);
    }
}
