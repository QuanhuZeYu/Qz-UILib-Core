package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 一次实际编辑消费者允许的有限续段；核心仍核验原挂载和范围。 */
public interface SceneEditContinuation {
    /** 只读核验唯一路由当刻传入的实际焦点。 */
    boolean accepts(SceneNode focused);
    /** 在原许可有效时接续，false 在原消费者处结束本段。 */
    boolean repeat(SceneEditEvent event);
}
