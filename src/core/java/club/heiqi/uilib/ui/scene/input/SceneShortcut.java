package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.control.SceneCommand;
import java.util.Objects;

/** 精确Primary字母组合；范围角色由绑定显式声明，不从当前modal布尔猜测。 */
public final class SceneShortcut {
    public enum Scope { PAGE, MODAL }
    public final SceneKey key;
    public final boolean shift;
    public final SceneCommand command;

    private SceneShortcut(SceneKey key, boolean shift, SceneCommand command) {
        this.key = Objects.requireNonNull(key, "key");
        if (!key.letter()) throw new IllegalArgumentException("应用快捷键需要字母键: " + key);
        this.shift = shift; this.command = Objects.requireNonNull(command, "command");
    }
    public static SceneShortcut primary(SceneKey key, SceneCommand command) { return new SceneShortcut(key, false, command); }
    public static SceneShortcut primaryShift(SceneKey key, SceneCommand command) { return new SceneShortcut(key, true, command); }
    public boolean matches(SceneKeyEvent event) { return event.applicationChord() && key == event.key && shift == event.shift; }
}
