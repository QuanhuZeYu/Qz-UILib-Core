package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 提供当批直属子项测量，每个子项复用自身同代请求缓存，使纯滚动沿用有效公式度量。 */
public interface SceneChildMeasurer {
    /** 返回当批只读的可见普通流直属子集合，按逻辑顺序排列，限当批使用。 */
    java.util.List<SceneNode> flowChildren();

    /** 当前分配使用的尺寸意图；目标测量沿原声明，显式尺寸动效沿当批框架投影。 */
    default Size sizeIntent(SceneNode child, SceneNode.Axis axis) { return axis == SceneNode.Axis.ROW ? child.width() : child.height(); }

    /** 仅测量普通流直属子；所属层由核心按页面约束求解。 */
    MeasuredSize measureChild(SceneNode child, Constraints constraints);
}
