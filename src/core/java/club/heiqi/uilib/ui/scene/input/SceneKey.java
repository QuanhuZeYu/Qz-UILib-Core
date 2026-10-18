package club.heiqi.uilib.ui.scene.input;

/** 宿主转换后的命名键；字符和原生整数键码不在此枚举中混用。 */
public enum SceneKey {
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN, BACKSPACE, DELETE, TAB, ENTER, SPACE, ESCAPE, KEYPAD_ENTER;

    public boolean letter() { return ordinal() <= Z.ordinal(); }
}
