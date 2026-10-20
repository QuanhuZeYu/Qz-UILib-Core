package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayDeque;
import java.util.Iterator;

/** 只消费真实指针采集；几何换基与呈现时钟不能向窗口补样本。 */
final class ScenePointerVelocity {
    private static final long WINDOW = 64_000_000L;
    private static final class Sample {
        final int x, y;
        final long time;
        Sample(int x, int y, long time) { this.x = x; this.y = y; this.time = time; }
    }
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    void clear() { samples.clear(); }
    void sample(int x, int y, long now) {
        // 同采集时刻保留最终位置；UP 也必须进入窗口，停手才会归零。
        if (!samples.isEmpty() && now < samples.peekLast().time) samples.clear();
        if (!samples.isEmpty() && now == samples.peekLast().time) samples.removeLast();
        samples.addLast(new Sample(x, y, now));
        while (samples.size() > 2) {
            Iterator<Sample> iterator = samples.iterator(); iterator.next();
            if (now - iterator.next().time <= WINDOW) break;
            samples.removeFirst();
        }
    }
    long time() { return samples.isEmpty() ? 0 : samples.peekLast().time; }
    double speed(boolean horizontal, long now) {
        if (samples.size() < 2) return 0;
        Sample first = samples.peekFirst(), last = samples.peekLast();
        long beginning = Math.max(first.time, now - WINDOW);
        if (last.time <= beginning) return 0;
        double origin = horizontal ? first.x : first.y;
        if (first.time < beginning) {
            Iterator<Sample> iterator = samples.iterator(); iterator.next(); Sample next = iterator.next();
            double fraction = (double) (beginning - first.time) / (next.time - first.time);
            origin += ((horizontal ? next.x : next.y) - origin) * fraction;
        }
        return ((horizontal ? last.x : last.y) - origin) * 1_000_000_000.0 / (last.time - beginning);
    }
}
