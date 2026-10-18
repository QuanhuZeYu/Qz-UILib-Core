package club.heiqi.uilib.ui.scene.control;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 集合选择的不可变逻辑值；业务持有它，焦点及行视图不成为选择权威。
 * 行为见 specs/scene/2026-10-18-uilib-virtual-collection-spec.md 第5节。 */
public final class SceneSelection<K> {
    public enum Mode { REPLACE, TOGGLE, RANGE }

    private final Set<K> selected;
    private final K active, anchor;

    private SceneSelection(Set<K> selected, K active, K anchor) {
        this.selected = Collections.unmodifiableSet(new LinkedHashSet<>(selected));
        this.active = active;
        this.anchor = anchor;
    }

    public static <K> SceneSelection<K> empty() {
        return new SceneSelection<>(Collections.emptySet(), null, null);
    }

    public Set<K> selected() { return selected; }
    public K active() { return active; }
    public K anchor() { return anchor; }

    /** 已不在当前顺序中的迟到意图不改变选择；RANGE按当前顺序替换区间，不隐含追加。 */
    public SceneSelection<K> select(K key, Mode mode, List<K> order) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(order, "order");
        int target = order.indexOf(key);
        if (target < 0) return this;
        Set<K> next = new LinkedHashSet<>();
        K nextAnchor = key;
        if (mode == Mode.TOGGLE) {
            next.addAll(selected);
            if (!next.remove(key)) next.add(key);
        } else if (mode == Mode.RANGE) {
            int start = anchor == null ? -1 : order.indexOf(anchor);
            if (start < 0) start = target;
            else nextAnchor = anchor;
            for (int i = Math.min(start, target); i <= Math.max(start, target); i++)
                next.add(order.get(i));
        } else next.add(key);
        return new SceneSelection<>(next, key, nextAnchor);
    }

    /** 仅显式调用时限制业务状态；过滤或离屏本身不会调用它。 */
    public SceneSelection<K> retain(Set<K> keys) {
        Objects.requireNonNull(keys, "keys");
        Set<K> next = new LinkedHashSet<>(selected);
        next.retainAll(keys);
        return new SceneSelection<>(next, active != null && keys.contains(active) ? active : null,
                anchor != null && keys.contains(anchor) ? anchor : null);
    }
}
