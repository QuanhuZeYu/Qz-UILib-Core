package club.heiqi.uilib.ui.scene.input;

/** 宿主转换后的命名键；字符和原生整数键码不在此枚举中混用。 */
public enum SceneKey {
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN, BACKSPACE, DELETE, TAB, ENTER, SPACE, ESCAPE, KEYPAD_ENTER,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    SLASH, COMMA, PERIOD, MINUS, EQUAL, SEMICOLON, APOSTROPHE, LEFT_BRACKET, RIGHT_BRACKET, BACKSLASH, GRAVE, INSERT;

    public boolean letter() { return ordinal() <= Z.ordinal(); }
    public boolean printable() {
        return letter() || this == SPACE || ordinal() >= DIGIT_0.ordinal() && ordinal() <= GRAVE.ordinal();
    }
}
