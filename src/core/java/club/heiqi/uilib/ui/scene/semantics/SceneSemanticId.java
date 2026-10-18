package club.heiqi.uilib.ui.scene.semantics;

import java.util.UUID;

/** 一次真实挂载的不透明值；保留它不会持有节点、运行时或业务来源。 */
public final class SceneSemanticId {
    private final UUID value;
    private SceneSemanticId(UUID value) { this.value = value; }
    public static SceneSemanticId create() { return new SceneSemanticId(UUID.randomUUID()); }
    @Override public boolean equals(Object other) { return other instanceof SceneSemanticId && value.equals(((SceneSemanticId) other).value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value.toString(); }
}
