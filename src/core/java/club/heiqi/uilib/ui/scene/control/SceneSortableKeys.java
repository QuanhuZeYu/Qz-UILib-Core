package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;

/** 以角色区分来源、行目标与容器目标，允许 C 与 K 使用相等的业务值。 */
final class SceneSortableKeys {
    private SceneSortableKeys() {}
    static final class SourceKey<K> {
        final K key;
        SourceKey(K key) { this.key = key; }
        @Override public int hashCode() { return key.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof SourceKey && Objects.equals(key, ((SourceKey<?>) other).key);
        }
        @Override public String toString() { return "sortable source " + key; }
    }
    static final class RowTarget<K> {
        final K key;
        RowTarget(K key) { this.key = key; }
        @Override public int hashCode() { return key.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof RowTarget && Objects.equals(key, ((RowTarget<?>) other).key);
        }
        @Override public String toString() { return "sortable row " + key; }
    }
    static final class ContainerTarget<C> {
        final C key;
        ContainerTarget(C key) { this.key = key; }
        @Override public int hashCode() { return key.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof ContainerTarget && Objects.equals(key, ((ContainerTarget<?>) other).key);
        }
        @Override public String toString() { return "sortable container " + key; }
    }
}
