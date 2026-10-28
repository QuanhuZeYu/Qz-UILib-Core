package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** 按帧协调采样的时间推进已挂载活跃绑定，完成时移出活跃集合。 */
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

    /** 声明结算后，在资格修订或活跃集合变化时重验活跃轨迹。 */
    void settleVisibility(long revision) {
        if (visibilityRevision == revision && !visibilityDirty) return;
        visibilityRevision = revision; visibilityDirty = false;
        for (Step step : new ArrayList<>(active))
            if (active.contains(step)) step.settleVisibility();
    }

    void advance(long frameNanos) {
        // 排队输入保留采集批次时刻，已发布时钟按单调顺序推进。
        if (sampled && frameNanos - now <= 0) return;
        sampled = true;
        now = frameNanos;
        if (active.isEmpty()) return;
        for (Step step : new ArrayList<>(active)) {
            if (active.contains(step) && !step.advance(now)) active.remove(step);
        }
    }
}
