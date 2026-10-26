package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** 只推进挂载且活跃的有限呈现绑定；时间由帧协调采样，不扫描静止节点。 */
final class SceneTransitions {
    interface Step {
        boolean advance(long now);
        default void settleVisibility() {}
    }
    private final Set<Step> active = new LinkedHashSet<>();
    private boolean sampled;
    private long now;
    private long visibilityRevision = Long.MIN_VALUE;
    private boolean visibilityDirty;

    long now() { return now; }
    void start(Step step) { if (active.add(step)) visibilityDirty = true; }
    void stop(Step step) { active.remove(step); }
    void clear() { active.clear(); }

    /** 声明结算后只重验活跃轨迹；资格未变化且没有新轨迹时不做逐帧扫描。 */
    void settleVisibility(long revision) {
        if (visibilityRevision == revision && !visibilityDirty) return;
        visibilityRevision = revision; visibilityDirty = false;
        for (Step step : new ArrayList<>(active))
            if (active.contains(step)) step.settleVisibility();
    }

    void advance(long frameNanos) {
        // 已排队的输入批次保留原时刻；外部current()先准备时不能让时钟倒退。
        if (sampled && frameNanos - now <= 0) return;
        sampled = true;
        now = frameNanos;
        if (active.isEmpty()) return;
        for (Step step : new ArrayList<>(active)) {
            if (active.contains(step) && !step.advance(now)) active.remove(step);
        }
    }
}
