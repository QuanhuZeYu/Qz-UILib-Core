package club.heiqi.uilib.ui.scene.text.font;

import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.TextLayout;

/** 同一布局 LRU 条目复用几何并保存最近一次颜色投影。
 * 只保留不可变声明/布局；条目逐出、字体换代或关闭同时释放。 */
final class FontLayoutEntry {
    final TextLayout geometry;
    private AttributedText request;
    private TextLayout painted;

    FontLayoutEntry(TextLayout geometry) { this.geometry = geometry; }

    TextLayout paint(AttributedText value) {
        if (request == value) return painted;
        if (request != null && samePaint(request, value)) {
            request = value;
            return painted;
        }
        painted = geometry.withColors(value.baseStyle, offset -> value.styleAt(offset).color);
        request = value;
        return painted;
    }

    private static boolean samePaint(AttributedText a, AttributedText b) {
        // 调用前完整定位键已经相等；这里只比较颜色分段和当前基础样式。
        if (!a.baseStyle.equals(b.baseStyle) || a.trailingStyle.color != b.trailingStyle.color
                || a.spans().size() != b.spans().size()) return false;
        for (int i = 0; i < a.spans().size(); i++) {
            StyleSpan x = a.spans().get(i), y = b.spans().get(i);
            if (x.logicalStart != y.logicalStart || x.logicalEnd != y.logicalEnd
                    || x.style.color != y.style.color) return false;
        }
        return true;
    }
}
