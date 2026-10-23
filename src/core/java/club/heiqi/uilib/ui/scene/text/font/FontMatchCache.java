package club.heiqi.uilib.ui.scene.text.font;

import java.util.LinkedHashMap;
import java.util.List;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;

/** 字体服务所属的纯值事实；预算沿现有布局条目和文本长度，不持有 native face。
 * 字体/Unicode 换代由同一服务清空；失败不作为可跨请求复用的匹配结果。 */
final class FontMatchCache {
    private final int entries, units;
    private int resolvedUnits, matchedUnits;
    private final LinkedHashMap<CandidatesKey, List<FontSource>> resolved =
            new LinkedHashMap<CandidatesKey, List<FontSource>>(16, .75f, true);
    private final LinkedHashMap<MatchKey, FontSource> matched =
            new LinkedHashMap<MatchKey, FontSource>(16, .75f, true);

    FontMatchCache(int entries, int units) {
        this.entries = entries;
        this.units = units;
    }

    List<FontSource> resolved(CandidatesKey key) { return resolved.get(key); }
    FontSource matched(MatchKey key) { return matched.get(key); }

    void remember(CandidatesKey key, List<FontSource> value) {
        long weight = (long) key.sources.size() + value.size();
        if (entries == 0 || weight > units) return;
        while (resolved.size() >= entries || resolvedUnits > units - weight) {
            CandidatesKey oldest = resolved.keySet().iterator().next();
            resolvedUnits -= oldest.sources.size() + resolved.remove(oldest).size();
        }
        resolved.put(key, value);
        resolvedUnits += (int) weight;
    }

    void remember(MatchKey key, FontSource value) {
        long weight = (long) key.sources.size() + key.text.length();
        if (entries == 0 || weight > units) return;
        FontSource previous = matched.remove(key);
        if (previous != null) matchedUnits -= (int) weight;
        while (matched.size() >= entries || matchedUnits > units - weight) {
            MatchKey oldest = matched.keySet().iterator().next();
            matchedUnits -= oldest.sources.size() + oldest.text.length();
            matched.remove(oldest);
        }
        matched.put(key, value);
        matchedUnits += (int) weight;
    }

    void clear() {
        resolved.clear(); matched.clear();
        resolvedUnits = 0; matchedUnits = 0;
    }

    private static int sourceHash(List<FontSource> sources) {
        int hash = 1;
        for (FontSource source : sources) hash = 31 * hash + System.identityHashCode(source);
        return hash;
    }

    private static boolean sameSources(List<FontSource> a, List<FontSource> b) {
        if (a == b) return true;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    static final class CandidatesKey {
        final List<FontSource> sources;
        final FontStyle request;
        final boolean variations;
        private final int hash;

        CandidatesKey(List<FontSource> sources, FontStyle request, boolean variations) {
            this.sources = sources; this.request = request; this.variations = variations;
            hash = 31 * (31 * sourceHash(sources) + request.hashCode()) + Boolean.hashCode(variations);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof CandidatesKey)) return false;
            CandidatesKey key = (CandidatesKey) other;
            return variations == key.variations && request.equals(key.request)
                    && sameSources(sources, key.sources);
        }
    }

    static final class MatchKey {
        final List<FontSource> sources;
        final FontStyle request;
        final float em;
        final int script;
        final boolean emoji;
        final ShapeDirection direction;
        final String language, text;
        private final int hash;

        MatchKey(List<FontSource> sources, FontStyle request, float em, int script,
                boolean emoji, ShapeDirection direction, String language, String text) {
            this.sources = sources; this.request = request; this.em = em;
            this.script = script; this.emoji = emoji; this.direction = direction;
            this.language = language; this.text = text;
            int h = 31 * sourceHash(sources) + request.hashCode();
            h = 31 * h + Float.floatToIntBits(em); h = 31 * h + script;
            h = 31 * h + Boolean.hashCode(emoji); h = 31 * h + direction.hashCode();
            h = 31 * h + language.hashCode(); hash = 31 * h + text.hashCode();
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof MatchKey)) return false;
            MatchKey key = (MatchKey) other;
            return em == key.em && script == key.script && emoji == key.emoji
                    && direction == key.direction && request.equals(key.request)
                    && language.equals(key.language) && text.equals(key.text)
                    && sameSources(sources, key.sources);
        }
    }
}
