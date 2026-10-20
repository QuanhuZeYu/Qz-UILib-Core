package club.heiqi.uilib.ui.scene.control;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** 与React Aria字符跳转一致的有限键入前缀；仅匹配业务名称，不构造行视图。 */
final class SceneTypeahead {
    private String query = "";
    private long lastInput;
    private final long timeoutNanos;
    private final int maximumLength;

    SceneTypeahead() { this(1_000_000_000L, Integer.MAX_VALUE); }
    SceneTypeahead(long timeoutNanos, int maximumLength) {
        if (timeoutNanos <= 0 || maximumLength <= 0) throw new IllegalArgumentException("字符导航窗口与前缀长度必须为正");
        this.timeoutNanos = timeoutNanos; this.maximumLength = maximumLength;
    }

    /** 只消费所属输入批次的时刻；匹配本身不采钟，也不推进框架时间。 */
    <T> int find(List<T> items, Function<T, String> name, int focused, String text, long now) {
        if (text == null || text.trim().isEmpty() || text.codePoints().anyMatch(Character::isISOControl)) return -1;
        if (now - lastInput > timeoutNanos) query = "";
        lastInput = now;
        String input = normalize(text); if (input.isEmpty()) return -1; query += input;
        if (query.length() > maximumLength) query = query.substring(query.length() - maximumLength);
        // 连续输入同一字符循环同前缀项；不同字符组成完整前缀。
        boolean repeated = query.codePoints().allMatch(value -> value == query.codePointAt(0));
        String prefix = repeated ? new String(Character.toChars(query.codePointAt(0))) : query;
        int start = prefix.equals(input) ? focused + 1 : Math.max(0, focused);
        for (int step = 0; step < items.size(); step++) {
            int index = Math.floorMod(start + step, items.size());
            if (normalize(name.apply(items.get(index))).startsWith(prefix)) return index;
        }
        return -1;
    }
    void reset() { query = ""; lastInput = 0; }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
