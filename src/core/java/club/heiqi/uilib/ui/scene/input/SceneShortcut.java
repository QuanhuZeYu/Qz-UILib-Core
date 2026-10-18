package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.control.SceneCommand;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** 单个命名键与精确修饰键集合；Primary便利声明和显式范围角色保持原用法。 */
public final class SceneShortcut {
    public enum Scope { PAGE, MODAL }
    public enum Modifier { PRIMARY, SHIFT, ALT, EXTRA }
    public final SceneKey key;
    public final boolean primary, shift, alt, extra;
    public final SceneCommand command;

    private SceneShortcut(SceneKey key, SceneCommand command, Modifier... modifiers) {
        this.key = Objects.requireNonNull(key, "key");
        this.command = Objects.requireNonNull(command, "command");
        Set<Modifier> values = EnumSet.noneOf(Modifier.class);
        for (Modifier modifier : Objects.requireNonNull(modifiers, "modifiers")) values.add(Objects.requireNonNull(modifier, "modifier"));
        primary = values.contains(Modifier.PRIMARY); shift = values.contains(Modifier.SHIFT);
        alt = values.contains(Modifier.ALT); extra = values.contains(Modifier.EXTRA);
    }
    public static SceneShortcut of(SceneKey key, SceneCommand command, Modifier... modifiers) {
        return new SceneShortcut(key, command, modifiers);
    }
    public static SceneShortcut primary(SceneKey key, SceneCommand command) { return of(key, command, Modifier.PRIMARY); }
    public static SceneShortcut primaryShift(SceneKey key, SceneCommand command) { return of(key, command, Modifier.PRIMARY, Modifier.SHIFT); }
    public boolean matches(SceneKeyEvent event) {
        return event.phase == SceneKeyEvent.Phase.DOWN && key == event.key && primary == event.primary
                && shift == event.shift && alt == event.alt && extra == event.extra;
    }
}
