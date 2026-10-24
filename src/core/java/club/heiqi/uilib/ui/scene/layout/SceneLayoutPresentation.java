package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 框架求解后提供的当前分配；目标求解不消费该投影。 */
public interface SceneLayoutPresentation {
    Size size(SceneNode node, SceneNode.Axis axis);
    LayoutBox place(SceneNode node, LayoutBox allocated);
    /** 没有换基或小数余量时保留原单位矩阵快捷路径。 */
    default boolean transformed() { return false; }
    /** 框架在父累计矩阵已确定后组合局部呈现，保持布局与绘制阶段只读。 */
    default club.heiqi.uilib.ui.scene.geometry.SceneTransform transform(SceneNode node, LayoutBox allocated,
            club.heiqi.uilib.ui.scene.geometry.SceneTransform parent,
            club.heiqi.uilib.ui.scene.geometry.SceneTransform local) { return local; }
}
