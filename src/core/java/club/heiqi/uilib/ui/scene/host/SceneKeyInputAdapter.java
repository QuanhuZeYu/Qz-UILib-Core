package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** 唯一宿主消费者递交中性键与物理事实；复用原编辑门的可用性/历史释放规则。
 * 每键先登记物理按压，再核验 IME 和命令资格；repeat 沿编辑续段处理。 */
public final class SceneKeyInputAdapter {
    private final SceneEditInputAdapter edits = new SceneEditInputAdapter();
    private final Set<SceneKey> held = EnumSet.noneOf(SceneKey.class);
    private final Set<SceneKey> dispatched = EnumSet.noneOf(SceneKey.class);
    private final Consumer<SceneKeyEvent> sink;

    public SceneKeyInputAdapter(Consumer<SceneKeyEvent> sink) { this.sink = Objects.requireNonNull(sink, "sink"); }
    public void availability(boolean active) { edits.availability(active); if (!active) dispatched.clear(); }

    public void key(SceneKeyEvent event) { key(event, event.phase == SceneKeyEvent.Phase.DOWN); }

    public void key(SceneKeyEvent event, boolean down) {
        Objects.requireNonNull(event, "event");
        boolean previous = held.contains(event.key);
        boolean accepted = edits.key(event.key.ordinal(), down, event.repeat);
        if (!down) {
            held.remove(event.key);
            if (dispatched.remove(event.key)) sink.accept(new SceneKeyEvent(event.key,
                    event.primary, event.shift, event.alt, event.extra, false, event.clipboard, SceneKeyEvent.Phase.UP));
            return;
        }
        // repeat 仅延续已接收起手的按压段。
        if (event.repeat && !previous) return;
        held.add(event.key);
        if (!accepted) return;
        boolean repeated = event.repeat || previous;
        dispatched.add(event.key);
        sink.accept(new SceneKeyEvent(event.key, event.primary, event.shift, event.alt, event.extra,
                repeated, event.clipboard, SceneKeyEvent.Phase.DOWN));
    }

    /** 唯一原生消费者耗尽历史后，递交真实物理集合以确认释放。 */
    public void historyDrained(Set<SceneKey> physical) {
        Objects.requireNonNull(physical, "physical");
        Set<Integer> encoded = new HashSet<>(); for (SceneKey key : physical) encoded.add(key.ordinal());
        held.retainAll(physical); dispatched.retainAll(physical); edits.historyDrained(encoded);
    }
}
