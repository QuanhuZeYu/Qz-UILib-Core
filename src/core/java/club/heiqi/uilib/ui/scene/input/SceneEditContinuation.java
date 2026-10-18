package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 一次实际编辑消费者允许的有限续段；核心仍核验原挂载和范围。 */
public interface SceneEditContinuation {
    /** 只读查询；参数是唯一路由当刻实际取得的焦点，不能改为读取合批后的快照。 */
    boolean accepts(SceneNode focused);
    /** 只在原许可仍有效时执行；false结束该段，不转投新的编辑者或应用命令。 */
    boolean repeat(SceneEditEvent event);
}
