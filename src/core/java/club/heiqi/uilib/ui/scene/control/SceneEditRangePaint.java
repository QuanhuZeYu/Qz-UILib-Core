package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.edit.SceneEditState.Snapshot;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.Arrays;
import java.util.List;

/**
 * 单多行共用的选区/组字覆盖域；来源和视觉位置只消费正式 Line/Cluster。
 * 透明色逐簇外扩取整会在边界重复合成：先合并同一行的像素区间，再输出互不相交的矩形。
 * 缓存随布局、选区、主题和裁剪失效；preparePaint 复用工作区，paint 只追加已准备的不可变命令。
 */
final class SceneEditRangePaint {
    private final boolean hardBreaks;
    private long[] spans = new long[16];
    private PaintCommand[] commands = new PaintCommand[8];
    private int spanCount, count;
    private TextLayout layout;
    private int start, end, color, originX, originY, width, left, top, right, bottom;
    private boolean underline;
    private LayoutBox clip;

    SceneEditRangePaint(boolean hardBreaks) {
        this.hardBreaks = hardBreaks;
    }

    void prepare(SceneGeometryView.Entry entry, int padding, Snapshot value, SceneTheme theme) {
        TextLayout text = entry.text;
        if (text == null || value == null) {
            layout = null;
            clip = null;
            Arrays.fill(commands, 0, count, null);
            count = spanCount = 0;
            return;
        }
        int a = Math.min(value.anchor, value.caret);
        int b = value.composing ? a + value.composition.length() : Math.max(value.anchor, value.caret);
        int tint = value.composing ? theme.focus : theme.hover;
        int x = entry.box.x + padding, y = entry.box.y + padding;
        int w = Math.max(0, entry.box.width - padding * 2);
        int h = Math.max(0, entry.box.height - padding * 2);
        int l = entry.clip == null ? x : Math.max(x, entry.clip.x);
        int t = entry.clip == null ? y : Math.max(y, entry.clip.y);
        int r = entry.clip == null ? x + w : Math.min(x + w, entry.clip.x + entry.clip.width);
        int d = entry.clip == null ? y + h : Math.min(y + h, entry.clip.y + entry.clip.height);
        if (layout == text && start == a && end == b && color == tint && underline == value.composing
                && originX == x && originY == y && width == w && left == l && top == t && right == r && bottom == d)
            return;
        layout = null;
        start = a; end = b; color = tint; underline = value.composing;
        originX = x; originY = y; width = w; left = l; top = t; right = r; bottom = d;
        int previous = count;
        count = spanCount = 0;
        clip = null;
        if (a != b && (tint >>> 24) != 0 && r > l && d > t) {
            clip = new LayoutBox(l, t, r - l, d - t);
            build(text);
        }
        if (count < previous) Arrays.fill(commands, count, previous, null);
        layout = text;
    }

    void paint(PaintPlanBuilder target) {
        for (int i = 0; i < count; i++) target.add(commands[i]);
    }

    private void build(TextLayout text) {
        List<TextLayout.Line> lines = text.lines();
        for (int i = 0; i < lines.size(); i++) {
            TextLayout.Line line = lines.get(i);
            int y = underline ? (int) Math.floor(line.baseline + line.descent) : line.y;
            int endY = y + (underline ? 1 : (int) Math.ceil(line.lineHeight));
            // Line.y 已由生产排版取整；相邻行共享下一行起点，不各自 ceil 后重叠一行像素。
            // 下划线仍取原基线位置，不能被行槽截掉或改成整行背景。
            if (!underline && i + 1 < lines.size()) endY = Math.min(endY, lines.get(i + 1).y);
            int rowTop = Math.max(top, originY + y), rowBottom = Math.min(bottom, originY + endY);
            if (rowBottom <= rowTop) continue;
            spanCount = 0;
            List<TextLayout.Cluster> clusters = line.clusters();
            float visualEnd = line.x;
            for (int j = 0; j < clusters.size(); j++) {
                TextLayout.Cluster cluster = clusters.get(j);
                float edge = line.paragraphRtl ? Math.min(cluster.leadingX(), cluster.trailingX())
                        : Math.max(cluster.leadingX(), cluster.trailingX());
                if (j == 0 || (line.paragraphRtl ? edge < visualEnd : edge > visualEnd)) visualEnd = edge;
                if (cluster.sourceEnd <= start || cluster.sourceStart >= end || cluster.advance <= 0) continue;
                span(originX + (int) Math.floor(cluster.x), originX + (int) Math.ceil(cluster.x + cluster.advance));
            }
            // LF 没有簇；与本行选区一起求并集，避免文字尾部与换行延展再次覆盖同一像素。
            // 延展从段落方向的视觉末端开始；混排的逻辑末端可能位于行中，不能涂过未选文字。
            if (hardBreaks && line.sourceEnd >= start && line.sourceEnd < end
                    && line.sourceEnd < text.originalText.length() && hardBreak(text.originalText.charAt(line.sourceEnd))) {
                int x = (int) Math.floor(visualEnd);
                if (line.paragraphRtl) span(originX, originX + Math.max(1, x));
                else span(originX + x, originX + Math.max(x + 1, width));
            }
            if (spanCount == 0) continue;
            // source 顺序不能当视觉顺序；只合并相交/相邻区间，保留 Bidi 选区之间的真实空洞。
            Arrays.sort(spans, 0, spanCount);
            int from = (int) (spans[0] >> 32), to = (int) spans[0];
            for (int j = 1; j < spanCount; j++) {
                int next = (int) (spans[j] >> 32), limit = (int) spans[j];
                if (next <= to) to = Math.max(to, limit);
                else {
                    rectangle(from, to, rowTop, rowBottom);
                    from = next; to = limit;
                }
            }
            rectangle(from, to, rowTop, rowBottom);
        }
    }

    private void span(int from, int to) {
        from = Math.max(left, from);
        to = Math.min(right, to);
        if (to <= from) return;
        if (spanCount == spans.length) spans = Arrays.copyOf(spans, spans.length * 2);
        spans[spanCount++] = ((long) from << 32) | (to & 0xffffffffL);
    }

    private void rectangle(int from, int to, int rowTop, int rowBottom) {
        if (count == commands.length) commands = Arrays.copyOf(commands, commands.length * 2);
        commands[count++] = PaintCommand.rect(new LayoutBox(from, rowTop, to - from, rowBottom - rowTop), clip, color);
    }

    private static boolean hardBreak(char value) {
        return value == '\n' || value == 0x2028 || value == 0x2029;
    }
}
