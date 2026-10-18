package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import java.util.Objects;

/** 业务共享的可执行条件与动作；不持有控件或范围，不改变编辑历史。
 * 控件观察归其挂载，执行在调用方所属响应域内；见命令规格第1节。 */
public final class SceneCommand {
    private final ReadableSignal<Boolean> executable;
    private final Runnable action;

    private SceneCommand(ReadableSignal<Boolean> executable, Runnable action) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.action = Objects.requireNonNull(action, "action");
    }

    public static SceneCommand of(ReadableSignal<Boolean> executable, Runnable action) {
        return new SceneCommand(executable, action);
    }

    public ReadableSignal<Boolean> executable() { return executable; }

    /** 尝试时读取当前条件，不能把上一帧enabled当成执行许可；业务失败原样传播。 */
    public boolean tryExecute() {
        if (!executable.get()) return false;
        action.run();
        return true;
    }
}
