package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 只有键和纯高度事实的前缀索引，不持有节点、布局服务或输入联系。 */
final class VirtualHeightIndex<K> {
    private List<K> keys = Collections.emptyList();
    private final Map<K, Integer> positions = new HashMap<>();
    private final Map<K, Integer> measured = new HashMap<>();
    private final Set<K> omitted = new HashSet<>();
    private long[] sums = new long[1];
    private int estimate = 1, gap;

    void reconcile(List<K> order, int estimatedHeight, int spacing, boolean invalidate) {
        if (estimatedHeight <= 0 || spacing < 0)
            throw new IllegalArgumentException("集合估算行高须为正，间距不能为负");
        List<K> next = new ArrayList<>(order);
        Set<K> unique = new HashSet<>();
        for (K key : next)
            if (!unique.add(Objects.requireNonNull(key, "集合键")))
                throw new IllegalArgumentException("集合业务键重复: " + key);
        if (invalidate || estimate != estimatedHeight) { measured.clear(); omitted.clear(); }
        else { measured.keySet().retainAll(unique); omitted.retainAll(unique); }
        estimate = estimatedHeight;
        gap = spacing;
        keys = Collections.unmodifiableList(next);
        positions.clear();
        sums = new long[keys.size() + 1];
        for (int i = 0; i < keys.size(); i++) {
            positions.put(keys.get(i), i);
            add(i, weight(i));
        }
    }

    int size() { return keys.size(); }
    K key(int index) { return keys.get(index); }
    int position(K key) { Integer value = positions.get(key); return value == null ? -1 : value; }
    int height(int index) { K key = keys.get(index); if (omitted.contains(key)) return 0;
        Integer value = measured.get(key); return value == null ? estimate : value; }
    private long weight(int index) { return omitted.contains(keys.get(index)) ? 0 : (long)height(index) + gap; }
    boolean known(K key) { return measured.containsKey(key) || omitted.contains(key); }

    boolean measure(K key, int height) {
        if (height < 0) throw new IllegalArgumentException("集合行高不能为负: " + key);
        int index = position(key);
        if (index < 0) return false;
        long previous = weight(index);
        omitted.remove(key); measured.put(key, height);
        long next = (long)height + gap;
        if (previous == next) return false;
        add(index, next - previous);
        return true;
    }

    boolean omit(K key) {
        int index = position(key);
        if (index < 0 || omitted.contains(key)) return false;
        long previous = weight(index); omitted.add(key); measured.remove(key); add(index, -previous);
        return previous != 0;
    }

    void invalidate(K key) {
        int index = position(key);
        if (index < 0) return;
        long previous = weight(index);
        boolean changed = omitted.remove(key) | (measured.remove(key) != null);
        if (changed) add(index, (long)estimate + gap - previous);
    }

    long start(int index) {
        long value = 0;
        for (int i = Math.max(0, Math.min(keys.size(), index)); i > 0; i -= i & -i)
            value += sums[i];
        return value;
    }

    long total() { return keys.isEmpty() ? 0 : Math.max(0, start(keys.size()) - gap); }

    int extent() {
        long total = total();
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("集合内容高度超出scene像素范围: " + total);
        return (int) total;
    }

    /** 返回覆盖该内容位置的行；间距属于前一行的区段，末尾之后返回size。 */
    int at(long pixel) {
        if (keys.isEmpty() || pixel >= total()) return keys.size();
        long target = Math.max(0, pixel);
        int position = 0;
        for (int step = Integer.highestOneBit(keys.size()); step != 0; step >>= 1) {
            int next = position + step;
            if (next <= keys.size() && sums[next] <= target) {
                position = next;
                target -= sums[next];
            }
        }
        return position;
    }

    private void add(int index, long delta) {
        for (int i = index + 1; i < sums.length; i += i & -i) sums[i] += delta;
    }

    void clear() {
        keys = Collections.emptyList(); positions.clear(); measured.clear(); omitted.clear(); sums = new long[1];
    }
}
