package club.heiqi.uilib.ui.scene.input;

/** 宿主翻译后的中性逻辑导航意图。 */
public enum SceneNavigationEvent {
    NEXT,
    PREVIOUS,
    ACTIVATE,
    LEFT,
    RIGHT,
    UP,
    DOWN;

    public boolean directional() { return this == LEFT || this == RIGHT || this == UP || this == DOWN; }
}
