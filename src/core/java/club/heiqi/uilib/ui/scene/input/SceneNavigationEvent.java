package club.heiqi.uilib.ui.scene.input;

/** 宿主翻译后的逻辑导航意图；不携带原生键码、字符或输入法数据。 */
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
