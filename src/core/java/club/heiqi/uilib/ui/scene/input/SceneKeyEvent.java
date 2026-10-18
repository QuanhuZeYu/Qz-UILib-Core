package club.heiqi.uilib.ui.scene.input;

import java.util.Objects;

/** 宿主物理门接受的命名键事实；文本仍经编辑入口递交，不从按键合成字符。 */
public final class SceneKeyEvent {
    public enum Phase { DOWN, UP }
    public final SceneKey key;
    public final boolean primary, shift, alt, extra, repeat;
    public final SceneEditEvent.Clipboard clipboard;
    public final Phase phase;

    /** 既有调用继续表达按下或重复。真实宿主同时递交UP，Space激活由Core完成。 */
    public SceneKeyEvent(SceneKey key, boolean primary, boolean shift, boolean alt, boolean extra,
            boolean repeat, SceneEditEvent.Clipboard clipboard) {
        this(key, primary, shift, alt, extra, repeat, clipboard, Phase.DOWN);
    }
    public SceneKeyEvent(SceneKey key, boolean primary, boolean shift, boolean alt, boolean extra,
            boolean repeat, SceneEditEvent.Clipboard clipboard, Phase phase) {
        this.key = Objects.requireNonNull(key, "key");
        this.primary = primary; this.shift = shift; this.alt = alt; this.extra = extra;
        this.repeat = repeat; this.clipboard = clipboard; this.phase = Objects.requireNonNull(phase, "phase");
    }
    public SceneKeyEvent withPhase(Phase phase) {
        return new SceneKeyEvent(key, primary, shift, alt, extra, repeat, clipboard, phase);
    }
    public boolean applicationChord() { return primary || alt || extra; }
    public boolean textKey() { return !primary && !alt && !extra && key.printable(); }
    public boolean plainSpace() { return key == SceneKey.SPACE && !primary && !shift && !alt && !extra; }
}
