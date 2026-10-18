package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** 唯一宿主消费者递交中性键与物理事实；复用原编辑门的可用性/历史释放规则。
 * 每键登记按压段早于IME和命令资格；repeat进入编辑但不能重新执行应用命令。 */
public final class SceneKeyInputAdapter {
    private final SceneEditInputAdapter edits = new SceneEditInputAdapter();
    private final Set<SceneKey> held = EnumSet.noneOf(SceneKey.class);
    private final Consumer<SceneKeyEvent> sink;

    public SceneKeyInputAdapter(Consumer<SceneKeyEvent> sink) { this.sink = Objects.requireNonNull(sink, "sink"); }
    public void availability(boolean active) { edits.availability(active); }

    public void key(SceneKeyEvent event, boolean down) {
        Objects.requireNonNull(event, "event");
        boolean previous = held.contains(event.key);
        boolean accepted = edits.key(event.key.ordinal(), down, event.repeat);
        if (!down) { held.remove(event.key); return; }
        // 没有已接收起手的repeat不建立按压；后续repeat也不能自行转为有效起手。
        if (event.repeat && !previous) return;
        held.add(event.key);
        if (!accepted) return;
        boolean repeated = event.repeat || previous;
        sink.accept(repeated == event.repeat ? event : new SceneKeyEvent(event.key,
                event.primary, event.shift, event.alt, event.extra, repeated, event.clipboard));
    }

    /** 只能在原生历史耗尽后提供真实物理集合，不旁读或消费队列。 */
    public void historyDrained(Set<SceneKey> physical) {
        Objects.requireNonNull(physical, "physical");
        Set<Integer> encoded = new HashSet<>(); for (SceneKey key : physical) encoded.add(key.ordinal());
        held.retainAll(physical); edits.historyDrained(encoded);
    }
}
