package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import java.util.Objects;

/** 封装共享可执行条件与业务动作，在调用方所属响应域内执行。
 * 控件观察归各自挂载，范围与编辑历史由原机制管理；见命令规格第 1 节。 */
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

    /** 组合获准后的内部收尾；当前条件只由组合命令检查一次，收尾后直接执行原动作。
     * 当前条件获准后依次执行收尾与业务动作，失败原样传播。 */
    public SceneCommand beforeExecute(Runnable preparation) {
        Objects.requireNonNull(preparation, "preparation");
        return new SceneCommand(executable, () -> {
            preparation.run();
            action.run();
        });
    }

    /** 读取当前条件后尝试执行，业务失败原样传播。 */
    public boolean tryExecute() {
        if (!executable.get()) return false;
        action.run();
        return true;
    }
}
