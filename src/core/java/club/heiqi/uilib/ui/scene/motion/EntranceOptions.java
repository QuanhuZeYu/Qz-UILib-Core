package club.heiqi.uilib.ui.scene.motion;

import java.util.Objects;

/** 显式挂载入场：位移使用局部逻辑单位，淡入与空间运动分别服从用户动效限制。 */
public final class EntranceOptions {
    public final MotionSpec spatial, feedback;
    public final float offsetX, offsetY;

    public EntranceOptions(MotionSpec spatial, MotionSpec feedback, float offsetX, float offsetY) {
        this.spatial = Objects.requireNonNull(spatial, "入场空间策略");
        this.feedback = Objects.requireNonNull(feedback, "入场反馈策略");
        if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY))
            throw new IllegalArgumentException("入场位移必须有限");
        this.offsetX = offsetX; this.offsetY = offsetY;
    }
}
