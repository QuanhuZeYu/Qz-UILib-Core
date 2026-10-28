package club.heiqi.uilib.ui.scene.text.math;

/** 保存中性内联盒度量，非法尺寸由公式布局连同来源定位并整式降级。 */
public final class MathEmbedMetrics {
    public final float widthEm, heightEm, baselineEm;

    public MathEmbedMetrics(float widthEm, float heightEm, float baselineEm) {
        this.widthEm = widthEm;
        this.heightEm = heightEm;
        this.baselineEm = baselineEm;
    }

    boolean valid() {
        return Float.isFinite(widthEm)
                && widthEm > 0
                && Float.isFinite(heightEm)
                && heightEm > 0
                && Float.isFinite(baselineEm)
                && baselineEm >= 0
                && baselineEm <= heightEm;
    }
}
