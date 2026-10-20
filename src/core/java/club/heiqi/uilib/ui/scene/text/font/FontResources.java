package club.heiqi.uilib.ui.scene.text.font;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一次资源准备的不可变发布事实；只含 bytes/metadata，不持 native 会话或宿主线程。
 * prepare 由资源 owner 的 worker 调用，UI 只构造/重载 FontTextService 消费本对象。
 * 普通、具名数学、数学正文和工作区 UI 的完整候选在同一次准备中封口。
 */
public final class FontResources {
    public final long revision;
    public final club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile unicodeProfile;
    public final List<FontSource> defaults;
    public final Map<String, List<FontSource>> named;
    public static final String MATH_PATH = "assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf";
    public static final String UI_PATH = "assets/qz_uilib/fonts/ui/NotoSansSC-VF.ttf";

    private FontResources(long revision, List<FontSource> defaults, Map<String, List<FontSource>> named,
            club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile unicodeProfile) {
        this.revision = revision;
        this.unicodeProfile = java.util.Objects.requireNonNull(unicodeProfile, "unicodeProfile");
        this.defaults = prepared(defaults);
        Map<String, List<FontSource>> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<FontSource>> entry : named.entrySet())
            values.put(entry.getKey(), prepared(entry.getValue()));
        this.named = Collections.unmodifiableMap(values);
    }

    /** 同步低级入口；UI owner 必须在资源 worker 上调用。builtins 和 defaults 共用来源对象。 */
    public static FontResources prepare(ClassLoader loader, long revision,
            List<FontSource> builtins, List<FontSource> defaults) {
        for (FontSource source : builtins) source.prepare();
        for (FontSource source : defaults) source.prepare();
        Map<String, List<FontSource>> mathText = MathTextFontSources.namedSources(loader, revision);
        Map<String, List<FontSource>> named = new LinkedHashMap<>();
        for (Map.Entry<String, List<FontSource>> entry : mathText.entrySet()) {
            for (FontSource source : entry.getValue()) source.prepare();
            named.put(entry.getKey(), chain(entry.getValue(), defaults));
        }
        club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile unicode;
        try {
            unicode = new club.heiqi.uilib.ui.scene.text.unicode.UnicodeGraphemes.Profile(
                    club.heiqi.uilib.ui.scene.text.unicode.EmojiData.bundled(loader));
            FontSource math = FontSource.resource("stix-two-math", revision, loader, MATH_PATH, false, Collections.emptySet()).prepare();
            named.put("math", Collections.singletonList(math));
            FontSource ui = FontSource.resource("uilib-settings-ui", revision, loader, UI_PATH, false, Collections.emptySet()).prepare();
            List<FontSource> uiChain = chain(Collections.singletonList(ui), builtins);
            named.put("uilib-ui", uiChain);
            named.put("settings-ui", uiChain);
            named.put("settings-path", chain(mathText.get(MathTextFontSources.MONO), uiChain));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        return new FontResources(revision, defaults, named, unicode);
    }

    /** 无宿主批处理的显式同步准备；不能从 UI 初始化、事件或测量路径调用。 */
    public static FontResources prepare(ClassLoader loader, long revision, List<FontSource> defaults) {
        try { return prepare(loader, revision, FontTextService.defaultSources(loader, revision), defaults); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** 同一资源代的候选变更复用全部已准备的具名头链和 UI 来源；不重读资源包。 */
    public FontResources select(List<FontSource> candidates) {
        Map<String, List<FontSource>> selected = new LinkedHashMap<>(named);
        for (String name : Arrays.asList(MathTextFontSources.ROMAN, MathTextFontSources.SANS, MathTextFontSources.MONO))
            selected.put(name, chain(named.get(name).subList(0, 1), candidates));
        return new FontResources(revision, candidates, selected, unicodeProfile);
    }

    public static List<FontSource> prepared(List<FontSource> sources) {
        List<FontSource> copy = new ArrayList<>(sources);
        for (FontSource source : copy)
            if (!source.isPrepared()) throw new IllegalStateException("字体资源尚未准备: " + source.id + " / " + source.origin);
        return Collections.unmodifiableList(copy);
    }

    private static List<FontSource> chain(List<FontSource> head, List<FontSource> tail) {
        List<FontSource> result = new ArrayList<>(head);
        result.addAll(tail);
        return Collections.unmodifiableList(result);
    }

    /** 纯路径目录；MC 可在主线程取得全部独立 stream，再交本代 worker 读、hash、close。 */
    public static List<String> resourcePaths() {
        List<String> result = new ArrayList<>();
        try {
            for (FontSource source : FontTextService.defaultSources(FontResources.class.getClassLoader(), 0))
                result.add(source.origin.substring("classpath:".length()));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        for (String family : Arrays.asList("roman/NotoSerif", "sans/NotoSans", "mono/IBMPlexMono"))
            for (String style : Arrays.asList("Regular", "Bold", "Italic", "BoldItalic"))
                result.add("assets/qz_uilib/fonts/math-text/" + family + "-" + style + ".ttf");
        result.add(MATH_PATH);
        result.add(UI_PATH);
        for (String file : Arrays.asList("emoji-data.txt", "emoji-sequences.txt", "emoji-zwj-sequences.txt", "emoji-variation-sequences.txt"))
            result.add("assets/qz_uilib/unicode/emoji17/" + file);
        return Collections.unmodifiableList(result);
    }
}
