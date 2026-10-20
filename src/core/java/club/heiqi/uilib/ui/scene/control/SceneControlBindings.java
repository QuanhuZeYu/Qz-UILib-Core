package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.function.BiConsumer;

/** 观测只读声明来源；apply 读取的临时呈现不能反过来重启结构或有限时间线。 */
final class SceneControlBindings {
    private SceneControlBindings() {}
    /** 命令的可用呈现与执行时重验在同一标准动作节点装配；观察随该次挂载结束。 */
    static SceneNode command(SceneNode node, SceneCommand command) {
        java.util.Objects.requireNonNull(command, "command");
        return node.bind(NodeBinding.value(command.executable(), SceneNode::enabled))
                .onClick(command::tryExecute);
    }
    static <V> NodeBinding declaration(ReadableSignal<V> source, BiConsumer<SceneNode, V> apply) {
        return (runtime, node, owner) -> new BindingActivation() {
            private V desired;
            private boolean pending;
            private final Observation observation = runtime.domain().observe(owner,
                    () -> { desired = source.get(); pending = true; }, () -> runtime.invalidateBinding(node));
            @Override public void refresh() {
                observation.refresh();
                if (pending) { pending = false; apply.accept(node, desired); }
            }
            @Override public void close() { observation.close(); }
        };
    }
}
