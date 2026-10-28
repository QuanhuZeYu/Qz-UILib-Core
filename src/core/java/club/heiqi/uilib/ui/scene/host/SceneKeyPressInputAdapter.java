package club.heiqi.uilib.ui.scene.host;

import java.util.Objects;

/** 唯一原生消费者递交单键历史事件；一段按压发一次意图。
 * 失焦或开屏持键须等历史耗尽并确认物理释放；范围和页面资格由路由器核验。 */
public class SceneKeyPressInputAdapter {
    private final Runnable sink;
    private boolean available, suspended = true, held;

    public SceneKeyPressInputAdapter(Runnable sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public void availability(boolean active) {
        available = active;
        if (!active) {
            suspended = true;
            held = false;
        }
    }

    public void key(boolean down, boolean repeat) {
        if (!down) {
            held = false;
            return;
        }
        if (!available || suspended || held || repeat) return;
        held = true;
        sink.run();
    }

    public void historyDrained(boolean down) {
        if (available && !down) {
            suspended = false;
            held = false;
        }
    }
}
