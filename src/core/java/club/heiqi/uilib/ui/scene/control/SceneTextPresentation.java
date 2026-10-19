package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 文字呈现的定位投影与省略请求；最终字形来自共同字体管线，保持全原文来源。 */
final class SceneTextPresentation {
    private SceneTextPresentation() {}

    /** 全部绝对坐标作同一平移；来源、Bidi、字形样式、advance与代际保持同一载荷。 */
    static TextLayout translate(TextLayout full, int dx, int dy) {
        if (dx == 0 && dy == 0) return full;
        List<TextLayout.Line> lines = new ArrayList<>();
        for (TextLayout.Line line : full.lines()) {
            List<TextLayout.Glyph> glyphs = new ArrayList<>();
            for (TextLayout.Glyph g : line.glyphs())
                glyphs.add(move(g, dx, dy, g.clusterIndex, g.logicalStart, g.logicalEnd, g.sourceStart, g.sourceEnd));
            List<TextLayout.Cluster> clusters = new ArrayList<>();
            for (TextLayout.Cluster c : line.clusters())
                clusters.add(
                        new TextLayout.Cluster(
                                c.logicalStart,
                                c.logicalEnd,
                                c.sourceStart,
                                c.sourceEnd,
                                c.glyphIndexStart,
                                c.glyphIndexEnd,
                                c.x + dx,
                                c.advance,
                                c.style,
                                c.rtl));
            List<TextLayout.Decoration> decorations = new ArrayList<>();
            for (TextLayout.Decoration d : line.decorations())
                decorations.add(
                        new TextLayout.Decoration(
                                d.kind,
                                d.x + dx,
                                d.y + dy,
                                d.width,
                                d.height,
                                d.color,
                                d.logicalStart,
                                d.logicalEnd,
                                d.sourceStart,
                                d.sourceEnd));
            lines.add(
                    new TextLayout.Line(
                            line.text,
                            line.x + dx,
                            line.y + dy,
                            line.logicalStart,
                            line.logicalEnd,
                            line.sourceStart,
                            line.sourceEnd,
                            line.advance,
                            line.baseline + dy,
                            line.ascent,
                            line.descent,
                            line.lineHeight,
                            glyphs,
                            clusters,
                            decorations,
                            line.paragraphRtl));
        }
        return new TextLayout(
                full.width,
                full.height,
                full.advanceWidth,
                full.advanceHeight,
                full.style,
                lines,
                full.logicalText,
                full.originalText,
                full.generation,
                full.diagnostics(),
                full.input).withPolicy(full.policy());
    }

    static TextLayout fit(
            TextLayout full,
            int width,
            int height,
            SceneTextPolicy policy,
            SceneTextMeasurer measurer) {
        if (policy == SceneTextPolicy.OVERFLOW) return full;
        List<TextLayout.Line> visible = new ArrayList<>();
        for (TextLayout.Line line : full.lines()) {
            if (policy == SceneTextPolicy.ELLIPSIS_SINGLE_LINE && !visible.isEmpty()) break;
            if (bottom(line) > height) break;
            visible.add(line);
        }
        boolean hidden = visible.size() < full.lines().size();
        boolean wide = false;
        for (TextLayout.Line line : visible) wide |= right(line) > width;
        if (!hidden && !wide) return full;
        List<TextLayout.Diagnostic> diagnostics = new ArrayList<>(full.diagnostics());
        for (int i = 0; i < visible.size(); i++) {
            TextLayout.Line line = visible.get(i);
            if (right(line) > width || hidden && i == visible.size() - 1) {
                TextLayout.Line shortened = shorten(full, line, width, height, measurer, diagnostics);
                if (shortened == null) {
                    visible.subList(i, visible.size()).clear();
                    break;
                }
                visible.set(i, shortened);
            }
        }
        float w = 0, h = 0;
        for (TextLayout.Line line : visible) {
            w = Math.max(w, right(line));
            h = Math.max(h, bottom(line));
        }
        return new TextLayout(
                (int) Math.ceil(w),
                (int) Math.ceil(h),
                w,
                h,
                full.style,
                visible,
                full.logicalText,
                full.originalText,
                full.generation,
                diagnostics,
                full.input).withPolicy(full.policy());
    }

    /** 列对齐是纯定位投影；字形、装饰、簇和原子项共用偏移，输入仍读同一TextLayout。 */
    static TextLayout align(
            TextLayout full,
            int width,
            club.heiqi.uilib.ui.scene.text.TableContent.Alignment alignment) {
        if (alignment != club.heiqi.uilib.ui.scene.text.TableContent.Alignment.CENTER
                && alignment != club.heiqi.uilib.ui.scene.text.TableContent.Alignment.RIGHT)
            return full;
        List<TextLayout.Line> lines = new ArrayList<>();
        for (TextLayout.Line line : full.lines()) {
            float extra = Math.max(0, width - right(line));
            float dx =
                    alignment == club.heiqi.uilib.ui.scene.text.TableContent.Alignment.CENTER
                            ? extra / 2
                            : extra;
            List<TextLayout.Glyph> glyphs = new ArrayList<>();
            for (TextLayout.Glyph glyph : line.glyphs())
                glyphs.add(
                        move(
                                glyph,
                                dx,
                                0,
                                glyph.clusterIndex,
                                glyph.logicalStart,
                                glyph.logicalEnd,
                                glyph.sourceStart,
                                glyph.sourceEnd));
            List<TextLayout.Cluster> clusters = new ArrayList<>();
            for (TextLayout.Cluster cluster : line.clusters())
                clusters.add(
                        new TextLayout.Cluster(
                                cluster.logicalStart,
                                cluster.logicalEnd,
                                cluster.sourceStart,
                                cluster.sourceEnd,
                                cluster.glyphIndexStart,
                                cluster.glyphIndexEnd,
                                cluster.x + dx,
                                cluster.advance,
                                cluster.style,
                                cluster.rtl));
            List<TextLayout.Decoration> decorations = new ArrayList<>();
            for (TextLayout.Decoration decoration : line.decorations())
                decorations.add(
                        new TextLayout.Decoration(
                                decoration.kind,
                                decoration.x + dx,
                                decoration.y,
                                decoration.width,
                                decoration.height,
                                decoration.color,
                                decoration.logicalStart,
                                decoration.logicalEnd,
                                decoration.sourceStart,
                                decoration.sourceEnd));
            lines.add(
                    new TextLayout.Line(
                            line.text,
                            line.x,
                            line.y,
                            line.logicalStart,
                            line.logicalEnd,
                            line.sourceStart,
                            line.sourceEnd,
                            line.advance + dx,
                            line.baseline,
                            line.ascent,
                            line.descent,
                            line.lineHeight,
                            glyphs,
                            clusters,
                            decorations,
                            line.paragraphRtl));
        }
        return new TextLayout(
                Math.max(full.width, width),
                full.height,
                Math.max(full.advanceWidth, width),
                full.advanceHeight,
                full.style,
                lines,
                full.logicalText,
                full.originalText,
                full.generation,
                full.diagnostics(),
                full.input).withPolicy(full.policy());
    }

    static float right(TextLayout.Line line) {
        float right = line.advance;
        for (TextLayout.Glyph g : line.glyphs())
            right = Math.max(right, g.x + (g.inkBounds.known ? g.inkBounds.right : g.xAdvance));
        for (TextLayout.Decoration d : line.decorations()) right = Math.max(right, d.x + d.width);
        return Math.max(0, right);
    }

    static float bottom(TextLayout.Line line) {
        float bottom = Math.max(line.y + line.lineHeight, line.baseline + line.descent);
        for (TextLayout.Glyph g : line.glyphs())
            if (g.inkBounds.known) bottom = Math.max(bottom, g.y + g.inkBounds.bottom);
        for (TextLayout.Decoration d : line.decorations())
            bottom = Math.max(bottom, d.y + d.height);
        return bottom;
    }

    private static TextLayout.Glyph move(TextLayout.Glyph g, float dx, float dy, int cluster,
            int logicalStart, int logicalEnd, int sourceStart, int sourceEnd) {
        return new TextLayout.Glyph(g.source, g.size, g.glyphId, logicalStart, logicalEnd,
                sourceStart, sourceEnd, g.x + dx, g.y + dy, g.xAdvance, g.yAdvance,
                g.color, g.replacement, g.inkBounds, g.logicalEm, g.rasterToLayout,
                g.rasterStyle, g.resolvedStyle, cluster, g.paintClip, g.xExtension);
    }

    /** 省略改变了最终行的文字边界；共同字体管线重新整形，呈现只投回全原文坐标。 */
    private static TextLayout.Line shorten(
            TextLayout full, TextLayout.Line line, int width, int height,
            SceneTextMeasurer measurer, List<TextLayout.Diagnostic> diagnostics) {
        FinalLine request = new FinalLine(full, line, measurer);
        TextLayout marker = request.shape(line.logicalStart);
        if (marker.lines().isEmpty()) return null;
        List<Integer> cuts = new ArrayList<>();
        List<Float> advances = new ArrayList<>();
        java.util.BitSet unsafe = new java.util.BitSet();
        for (TextLayout.Glyph g : line.glyphs())
            if (g.logicalEnd > g.logicalStart + 1) unsafe.set(g.logicalStart + 1, g.logicalEnd);
        float advance = 0;
        cuts.add(line.logicalStart);
        advances.add(0f);
        for (TextLayout.Cluster cluster : request.logicalClusters) {
            if (cluster.logicalEnd > cluster.logicalStart + 1)
                unsafe.set(cluster.logicalStart + 1, cluster.logicalEnd);
            advance += cluster.advance;
            cuts.add(cluster.logicalEnd);
            advances.add(advance);
        }
        if (cuts.get(cuts.size() - 1) != line.logicalEnd) {
            cuts.add(line.logicalEnd);
            advances.add(line.advance);
        }
        for (int i = cuts.size() - 1; i > 0; i--) {
            if (!unsafe.get(cuts.get(i))) continue;
            cuts.remove(i);
            advances.remove(i);
        }
        // 原行advance只估算候选；是否容纳由最终行真实字形及墨迹裁决。
        float budget = width - line.x - right(marker.lines().get(0));
        int choice = 0;
        while (choice + 1 < cuts.size() && advances.get(choice + 1) <= budget) choice++;
        TextLayout shaped = request.shape(cuts.get(choice));
        TextLayout.Line result = request.project(shaped, cuts.get(choice));
        while (!fits(result, width, height)) {
            if (choice == 0) return null;
            shaped = request.shape(cuts.get(--choice));
            result = request.project(shaped, cuts.get(choice));
        }
        while (choice + 1 < cuts.size()) {
            TextLayout next = request.shape(cuts.get(choice + 1));
            TextLayout.Line nextLine = request.project(next, cuts.get(choice + 1));
            if (!fits(nextLine, width, height)) break;
            choice++;
            shaped = next;
            result = nextLine;
        }
        request.diagnostics(shaped, cuts.get(choice), diagnostics);
        return result;
    }

    private static boolean fits(TextLayout.Line line, int width, int height) {
        return line != null && right(line) <= width && bottom(line) <= height;
    }

    /** 一次省略请求的局部上下文，不写入布局结果或跨批缓存。 */
    private static final class FinalLine {
        final TextLayout full;
        final TextLayout.Line original;
        final SceneTextMeasurer measurer;
        final TextPolicy policy;
        final List<TextLayout.Cluster> logicalClusters;

        FinalLine(TextLayout full, TextLayout.Line line, SceneTextMeasurer measurer) {
            this.full = full;
            original = line;
            this.measurer = measurer;
            TextPolicy source = full.policy();
            policy = source == null ? null : new TextPolicy(
                    line.paragraphRtl ? TextPolicy.Direction.RTL : TextPolicy.Direction.LTR,
                    TextPolicy.Wrap.NONE, source.language, source.tabSize, source.lineHeight,
                    TextPolicy.HardBreak.MARKER);
            logicalClusters = new ArrayList<>(line.clusters());
            logicalClusters.sort(Comparator.comparingInt(c -> c.logicalStart));
        }

        TextLayout shape(int end) {
            int length = end - original.logicalStart;
            String value = original.text.substring(0, length) + "…";
            // 旧结果未声明策略事实，仍通过原三参入口最终整形，不给旧measurer增加能力前提。
            if (policy == null) return measurer.layout(value, full.style, Integer.MAX_VALUE);
            List<StyleSpan> spans = new ArrayList<>();
            int cursor = original.logicalStart;
            for (TextLayout.Cluster c : logicalClusters) {
                if (c.logicalStart >= end) break;
                if (c.logicalStart > cursor) span(spans, cursor, c.logicalStart, full.style);
                int next = Math.min(end, c.logicalEnd);
                if (next > cursor) span(spans, Math.max(cursor, c.logicalStart), next, c.style);
                cursor = Math.max(cursor, next);
            }
            if (cursor < end) span(spans, cursor, end, full.style);
            TextStyle markerStyle = spans.isEmpty()
                    ? logicalClusters.isEmpty() ? full.style : logicalClusters.get(0).style
                    : spans.get(spans.size() - 1).style;
            span(spans, end, end + 1, markerStyle);
            // 单样式保留旧String入口；原已支持的属性请求才需要Attributed入口。
            if (spans.size() == 1)
                return measurer.layout(value, spans.get(0).style, Integer.MAX_VALUE, policy);
            return measurer.layout(new AttributedText(
                    TextInput.string(value), value, full.style, spans),
                    Integer.MAX_VALUE, policy);
        }

        void span(List<StyleSpan> spans,
                int start, int end, TextStyle style) {
            int a = start - original.logicalStart, b = end - original.logicalStart;
            if (!spans.isEmpty()) {
                StyleSpan last = spans.get(spans.size() - 1);
                if (last.logicalEnd == a && last.style.equals(style)) {
                    spans.set(spans.size() - 1,
                            new StyleSpan(last.logicalStart, b, style));
                    return;
                }
            }
            spans.add(new StyleSpan(a, b, style));
        }

        int logical(int offset, int end) {
            return Math.min(end, original.logicalStart + offset);
        }

        int source(int offset, boolean end) {
            if (full.input != null)
                return end ? full.input.sourceLeft(offset) : full.input.sourceRight(offset);
            for (TextLayout.Cluster c : logicalClusters)
                if (end ? c.logicalStart < offset && offset <= c.logicalEnd
                        : c.logicalStart <= offset && offset < c.logicalEnd)
                    return end ? c.sourceEnd : c.sourceStart;
            return original.sourceStart + offset - original.logicalStart;
        }

        TextLayout.Line project(TextLayout shaped, int end) {
            if (shaped.lines().isEmpty()) return null;
            TextLayout.Line line = shaped.lines().get(0);
            float dy = original.baseline - line.baseline;
            List<TextLayout.Glyph> glyphs = new ArrayList<>();
            for (TextLayout.Glyph g : line.glyphs()) {
                int a = logical(g.logicalStart, end), b = logical(g.logicalEnd, end);
                glyphs.add(move(g, original.x, dy, g.clusterIndex, a, b,
                        a == b ? source(a, true) : source(a, false), source(b, true)));
            }
            List<TextLayout.Cluster> clusters = new ArrayList<>();
            for (TextLayout.Cluster c : line.clusters()) {
                int a = logical(c.logicalStart, end), b = logical(c.logicalEnd, end);
                clusters.add(new TextLayout.Cluster(a, b,
                        a == b ? source(a, true) : source(a, false), source(b, true),
                        c.glyphIndexStart, c.glyphIndexEnd, c.x + original.x, c.advance, c.style, c.rtl));
            }
            List<TextLayout.Decoration> decorations = new ArrayList<>();
            for (TextLayout.Decoration d : line.decorations()) {
                int a = logical(d.logicalStart, end), b = logical(d.logicalEnd, end);
                decorations.add(new TextLayout.Decoration(d.kind, d.x + original.x, d.y + dy,
                        d.width, d.height, d.color, a, b,
                        a == b ? source(a, true) : source(a, false), source(b, true)));
            }
            return new TextLayout.Line(original.text, original.x, original.y,
                    original.logicalStart, original.logicalEnd, original.sourceStart, original.sourceEnd,
                    line.advance, original.baseline, line.ascent, line.descent,
                    Math.max(original.lineHeight, line.lineHeight), glyphs, clusters, decorations,
                    original.paragraphRtl);
        }

        void diagnostics(TextLayout shaped, int end, List<TextLayout.Diagnostic> target) {
            for (TextLayout.Diagnostic d : shaped.diagnostics()) {
                int a = logical(d.sourceStart, end), b = logical(d.sourceEnd, end);
                int start = a == b ? source(a, true) : source(a, false), stop = source(b, true);
                boolean present = false;
                for (TextLayout.Diagnostic old : target)
                    present |= old.kind == d.kind && old.sourceStart == start && old.sourceEnd == stop
                            && old.message.equals(d.message);
                if (!present) target.add(new TextLayout.Diagnostic(d.kind, start, stop, d.message));
            }
        }
    }
}
