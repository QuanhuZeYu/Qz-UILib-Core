package club.heiqi.uilib.ui.scene.input;

import java.util.Objects;

/** 一个已被宿主物理门接受的按下或重复事实；不合成文本，不保存范围或焦点。 */
public final class SceneKeyEvent {
    public final SceneKey key;
    public final boolean primary, shift, alt, extra, repeat;
    public final SceneEditEvent.Clipboard clipboard;

    public SceneKeyEvent(SceneKey key, boolean primary, boolean shift, boolean alt, boolean extra,
            boolean repeat, SceneEditEvent.Clipboard clipboard) {
        this.key = Objects.requireNonNull(key, "key");
        this.primary = primary; this.shift = shift; this.alt = alt; this.extra = extra;
        this.repeat = repeat; this.clipboard = clipboard;
    }

    public boolean applicationChord() { return primary && key.letter() && !alt && !extra; }
}
