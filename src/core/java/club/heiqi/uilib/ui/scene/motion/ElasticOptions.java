package club.heiqi.uilib.ui.scene.motion;

import java.util.Objects;

/** 显式弹性边界：合法值不越界，越界距离仅产生逐渐加重的阻力和有限呈现形变。 */
public final class ElasticOptions {
    private static final double RESISTANCE = .55;
    public final double maximumDisplacement;
    public final MotionSpec releaseMotion;

    public ElasticOptions(double maximumDisplacement, MotionSpec releaseMotion) {
        if (!Double.isFinite(maximumDisplacement) || maximumDisplacement <= 0)
            throw new IllegalArgumentException("弹性位移上限必须是有限正数");
        this.releaseMotion = Objects.requireNonNull(releaseMotion, "弹性释放策略");
        if (releaseMotion.kind == MotionSpec.Kind.DECAY) throw new IllegalArgumentException("弹性释放需要回到合法目标");
        this.maximumDisplacement = maximumDisplacement;
    }

    /** 输入为距最近合法边缘的有符号距离；反向输入直接作用于当前指针，不积存越界量。 */
    public double displacement(double overshoot, MotionPolicy policy) {
        if (!Double.isFinite(overshoot)) throw new IllegalArgumentException("弹性输入必须有限");
        if (policy == MotionPolicy.OFF) return 0;
        double limit = limit(policy);
        if (limit == 0) return 0;
        double input = Math.abs(overshoot);
        // 分别按较小的一侧缩放，避免零点相消、比例溢出及大数相乘。
        double amount = input <= limit ? input / (1 / RESISTANCE + input / limit)
                : limit / (1 + limit / input / RESISTANCE);
        return Math.copySign(amount, overshoot);
    }

    /**
     * 从有符号呈现位移反求越界输入，供底层拾取几何接管当前形变。
     * 超出有限输入可达范围的距离投影到同方向的 Double.MAX_VALUE 输入；
     * OFF 或减少后的上限已下溢为零时返回零。像素取整及控件输入预算由调用方保留。
     */
    public double overshoot(double displacement, MotionPolicy policy) {
        if (!Double.isFinite(displacement)) throw new IllegalArgumentException("弹性呈现位移必须有限");
        if (policy == MotionPolicy.OFF) return 0;
        double limit = limit(policy);
        if (limit == 0) return 0;
        double amount = Math.abs(displacement);
        double input = amount >= limit ? Double.MAX_VALUE
                : Math.min(Double.MAX_VALUE, amount / (RESISTANCE * ((limit - amount) / limit)));
        return Math.copySign(input, displacement);
    }

    private double limit(MotionPolicy policy) {
        return maximumDisplacement * (policy == MotionPolicy.REDUCED ? .25 : 1);
    }
}
