package club.heiqi.uilib.ui.scene.host;

import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;

/** 宿主Tab适配：原生消费者依历史顺序递交修饰键与Tab，再确认队列耗尽后的物理状态。
 * 保存可用性、修饰键与物理按压段；逻辑焦点归场景路由器。
 * 键盘与指针 gate 分别维护各自的物理段。 */
public final class SceneTabInputAdapter {
    private final Consumer<SceneNavigationEvent> sink;
    private boolean available, suspended = true, owned, leftShift, rightShift;

    public SceneTabInputAdapter(Consumer<SceneNavigationEvent> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** 失焦立即结束物理段；恢复须等历史耗尽并确认 Tab 物理释放。 */
    public void availability(boolean active) {
        available = active;
        if (!active) {
            suspended = true;
            owned = false;
            leftShift = false;
            rightShift = false;
        }
    }

    public void shift(boolean left, boolean down) {
        if (left) leftShift = down;
        else rightShift = down;
    }

    /** UP 结束按压段，重复 DOWN 只延续本适配器已接收的起手。 */
    public void tab(boolean down, boolean repeat) {
        if (!down) {
            owned = false;
            return;
        }
        if (!available || suspended || (repeat ? !owned : owned)) return;
        owned = true;
        sink.accept(
                leftShift || rightShift
                        ? SceneNavigationEvent.PREVIOUS
                        : SceneNavigationEvent.NEXT);
    }

    /** 唯一原生消费者耗尽历史后递交已创建设备的真实物理状态。 */
    public void historyDrained(boolean tabDown, boolean leftShiftDown, boolean rightShiftDown) {
        if (!available) return;
        leftShift = leftShiftDown;
        rightShift = rightShiftDown;
        if (!tabDown) {
            suspended = false;
            owned = false;
        }
    }
}
