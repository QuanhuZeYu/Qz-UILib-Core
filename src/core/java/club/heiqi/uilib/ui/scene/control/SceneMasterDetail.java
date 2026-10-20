package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import java.util.Objects;

/** 主从区域组合；并列策略、详情活动与返回意图仍由业务提供。 */
public final class SceneMasterDetail {
    private SceneMasterDetail() {}

    public static final class Policy {
        public final boolean split, detailActive;
        public final int masterWeight, detailWeight, dividerWidth, dividerColor;

        public Policy(
                boolean split,
                boolean detailActive,
                int masterWeight,
                int detailWeight,
                int dividerWidth,
                int dividerColor) {
            if (masterWeight <= 0 || detailWeight <= 0 || dividerWidth < 0)
                throw new IllegalArgumentException("主从权重必须为正，分隔宽度不能为负");
            this.split = split;
            this.detailActive = detailActive;
            this.masterWeight = masterWeight;
            this.detailWeight = detailWeight;
            this.dividerWidth = dividerWidth;
            this.dividerColor = dividerColor;
        }
    }

    public static SceneNode create(
            ScenePageContext page,
            SceneNode master,
            SceneNode detail,
            ReadableSignal<Policy> policy) {
        Objects.requireNonNull(page).domain().checkWrite();
        Objects.requireNonNull(policy);
        SceneNode list =
                SceneLayouts.overlay()
                        .add(Objects.requireNonNull(master))
                        .bind(
                                NodeBinding.value(
                                        policy,
                                        (node, value) ->
                                                node.visible(value.split || !value.detailActive)
                                                        .size(
                                                                Size.fill(value.masterWeight),
                                                                Size.fill(1))));
        SceneNode reader =
                SceneLayouts.overlay()
                        .add(Objects.requireNonNull(detail))
                        .bind(
                                NodeBinding.value(
                                        policy,
                                        (node, value) ->
                                                node.visible(value.detailActive)
                                                        .size(
                                                                Size.fill(value.detailWeight),
                                                                Size.fill(1))));
        SceneNode divider =
                new SceneNode()
                        .bind(
                                NodeBinding.value(
                                        policy,
                                        (node, value) ->
                                                node.visible(value.split && value.detailActive)
                                                        .size(
                                                                Size.fixed(value.dividerWidth),
                                                                Size.fill(1))
                                                        .background(value.dividerColor)));
        return new SceneNode()
                .axis(SceneNode.Axis.ROW)
                .size(Size.fill(1), Size.fill(1))
                .add(list)
                .add(divider)
                .add(reader);
    }
}
