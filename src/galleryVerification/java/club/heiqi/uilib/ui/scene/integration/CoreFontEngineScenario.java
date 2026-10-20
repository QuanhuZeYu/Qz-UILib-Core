package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontMetrics;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeRun;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 无MC/Forge/LWJGL classpath的注入与数据所有权验收；此engine是合同探针，不是发行字体实现。
 * 真实脚本/emoji/像素验收仍由独立真实FT/HB场景完成，不能用此探针代替。
 */
public final class CoreFontEngineScenario extends TestApplication {

    static final class ContractEngine implements FontEngine {
        int opened, closed, facesOpened, facesClosed;
        final List<Throwable> faceCloseFailures = new ArrayList<>();

        @Override
        public FontSession openSession() {
            opened++;
            return new FontSession() {
                final Thread thread = Thread.currentThread();
                final List<FontFace> faces = new ArrayList<>();
                boolean done;

                @Override
                public FontFace openFace(ByteBuffer source, long index, float em) {
                    require(Thread.currentThread() == thread && !done, "session线程/关闭合同");
                    final int ordinal = facesOpened++;
                    FontFace face =
                            new FontFace() {
                                boolean released;

                                private void checkOpen() {
                                    require(
                                            Thread.currentThread() == thread && !done && !released,
                                            "访问已关闭face");
                                }

                                @Override
                                public FontMetrics metrics() {
                                    checkOpen();
                                    return new FontMetrics(em * .75f, em * .25f, em, em);
                                }

                                @Override
                                public int glyphIndex(int cp) {
                                    checkOpen();
                                    return cp;
                                }

                                @Override
                                public ShapeRun shape(
                                        String text,
                                        int start,
                                        int end,
                                        ShapeDirection direction,
                                        String language,
                                        String script,
                                        boolean beginning,
                                        boolean ending) {
                                    checkOpen();
                                    List<ShapedGlyph> glyphs = new ArrayList<ShapedGlyph>();
                                    for (int i = start; i < end; ) {
                                        int cp = text.codePointAt(i);
                                        glyphs.add(new ShapedGlyph(cp, i, em * .5f, 0, 0, 0));
                                        i += Character.charCount(cp);
                                    }
                                    if (direction == ShapeDirection.RTL)
                                        Collections.reverse(glyphs);
                                    return new ShapeRun(glyphs);
                                }

                                @Override
                                public GlyphBitmap rasterize(int glyph, GlyphRasterStyle style) {
                                    checkOpen();
                                    return new GlyphBitmap(
                                                    1,
                                                    1,
                                                    0,
                                                    em * .75f,
                                                    em,
                                                    false,
                                                    new byte[] {-1, -1, -1, -1})
                                            .styled(style);
                                }

                                @Override
                                public InkBounds glyphInkBounds(int glyph, GlyphRasterStyle style) {
                                    checkOpen();
                                    return new InkBounds(true, 0, -em * .75f, em * .5f, 0);
                                }

                                @Override
                                public void close() {
                                    require(Thread.currentThread() == thread, "face关闭线程");
                                    if (released) return;
                                    released = true;
                                    facesClosed++;
                                    if (ordinal < faceCloseFailures.size())
                                        rethrow(faceCloseFailures.get(ordinal));
                                }
                            };
                    faces.add(face);
                    return face;
                }

                @Override
                public void close() {
                    require(Thread.currentThread() == thread, "session关闭线程");
                    if (!done) {
                        done = true;
                        closed++;
                        Throwable failure = null;
                        for (FontFace face : faces) {
                            try {
                                face.close();
                            } catch (RuntimeException | Error error) {
                                if (failure == null) failure = error;
                                else if (error != failure) failure.addSuppressed(error);
                            }
                        }
                        rethrow(failure);
                    }
                }
            };
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }

    private void evictionFailure(RuntimeException retiredFailure) {
        ContractEngine engine = new ContractEngine();
        engine.faceCloseFailures.add(retiredFailure);
        FontSource source =
                FontSource.fromResource(
                        "contract-probe",
                        1,
                        "assets/qz_uilib/fonts/unifont/unifont-16.0.04.otf",
                        false);
        FontTextService text =
                own(new FontTextService(Collections.singletonList(source), engine, 1, 8, 128));
        text.layout("A", new TextStyle("probe", 12, -1), 1000, TextPolicy.SINGLE_LINE);
        TextStyle next = new TextStyle("probe", 16, -1);
        RuntimeException observed = null;
        try {
            text.layout("A", next, 1000, TextPolicy.SINGLE_LINE);
        } catch (RuntimeException failure) {
            observed = failure;
        }
        require(observed == retiredFailure, "LRU关闭错误被吞掉或误归为新face加载失败");
        require(observed.getSuppressed().length == 0, "单次退役错误被无关异常替换");
        TextLayout current = text.layout("A", next, 1000, TextPolicy.SINGLE_LINE);
        require(
                engine.facesOpened == 2 && engine.facesClosed == 1 && text.cachedFaceCount() == 1,
                "退役失败后重新打开或丢失已取得的新face");
        require(
                current.diagnostics().isEmpty()
                        && current.lines().get(0).glyphs().get(0).source == source,
                "旧face关闭失败污染新face的资源诊断或来源");
    }

    private void invalidationFailure(boolean register, boolean closeFails) throws Exception {
        ContractEngine engine = new ContractEngine();
        Throwable firstClose =
                register
                        ? new AssertionError("first face close failure")
                        : new IllegalStateException("first face close failure");
        RuntimeException secondClose = new IllegalArgumentException("second face close failure");
        RuntimeException listenerFailure =
                new IllegalStateException("invalidation listener failure");
        Error nextListenerFailure = new AssertionError("next invalidation listener failure");
        if (closeFails) {
            engine.faceCloseFailures.add(firstClose);
            engine.faceCloseFailures.add(secondClose);
        }
        FontSource source =
                FontSource.fromResource(
                        "contract-probe",
                        1,
                        "assets/qz_uilib/fonts/unifont/unifont-16.0.04.otf",
                        false);
        FontSource replacement =
                FontSource.fromResource(
                        "contract-probe",
                        2,
                        "assets/qz_uilib/fonts/unifont/unifont-16.0.04.otf",
                        false);
        FontTextService text =
                own(new FontTextService(Collections.singletonList(source), engine, 4, 8, 128));
        TextLayout old = null;
        for (int size : new int[] {12, 16, 20}) {
            TextLayout layout =
                    text.layout(
                            "AB中", new TextStyle("probe", size, -1), 1000, TextPolicy.SINGLE_LINE);
            if (old == null) old = layout;
        }
        require(engine.facesOpened == 3 && text.cachedFaceCount() == 3, "反例没有建立多个真实服务face");
        long generation = text.generation();
        int[] notifications = {0, 0, 0};
        AutoCloseable first =
                text.onInvalidated(
                        () -> {
                            notifications[0]++;
                            throw listenerFailure;
                        });
        AutoCloseable second =
                text.onInvalidated(
                        () -> {
                            notifications[1]++;
                            throw nextListenerFailure;
                        });
        text.onInvalidated(() -> notifications[2]++);
        Throwable observed = null;
        try {
            if (register) text.register("probe", Collections.singletonList(replacement));
            else text.reload(Collections.singletonList(replacement));
        } catch (RuntimeException | Error failure) {
            observed = failure;
        }
        System.out.println(
                "INVALIDATION_FAILURE operation="
                        + (register ? "register" : "reload")
                        + " closeFails="
                        + closeFails
                        + " releasedFaces="
                        + engine.facesClosed
                        + " retainedFaces="
                        + text.cachedFaceCount()
                        + " notifications="
                        + Arrays.toString(notifications)
                        + " first="
                        + observed
                        + " suppressed="
                        + Arrays.toString(
                                observed == null ? new Throwable[0] : observed.getSuppressed()));
        require(engine.facesClosed == 3, "字体换代在首个关闭异常后跳过所属face清理: 已关闭=" + engine.facesClosed);
        require(text.cachedFaceCount() == 0 && text.cachedLayoutCount() == 0, "换代失败仍保留旧face或布局映射");
        require(Arrays.equals(notifications, new int[] {1, 1, 1}), "失效异常阻断其余有效监听");
        require(observed == (closeFails ? firstClose : listenerFailure), "换代没有保留首个异常身份");
        Throwable[] suppressed =
                closeFails
                        ? new Throwable[] {secondClose, listenerFailure, nextListenerFailure}
                        : new Throwable[] {nextListenerFailure};
        require(Arrays.equals(observed.getSuppressed(), suppressed), "后续清理与监听异常没有按发生顺序保留");
        require(text.generation() == generation + 1, "失败错误改变已发布代际");
        first.close();
        second.close();
        TextStyle style = new TextStyle("probe", 12, -1);
        TextLayout current = text.layout("AB中", style, 1000, TextPolicy.SINGLE_LINE);
        require(
                current != old && current.lines().get(0).glyphs().get(0).source == replacement,
                "失败后重用旧face或旧布局，而非当前来源");
        require(old.lines().get(0).glyphs().get(0).source == source, "换代修改旧不可变计划来源");
        if (register) text.register("probe", Collections.singletonList(source));
        else text.reload(Collections.singletonList(source));
        require(notifications[2] == 2 && engine.facesClosed == 4, "后续正常换代失效或重复释放旧face");
        require(
                text.layout("AB中", style, 1000, TextPolicy.SINGLE_LINE)
                                .lines()
                                .get(0)
                                .glyphs()
                                .get(0)
                                .source
                        == source,
                "后续正常换代未使用当前来源");
        text.close();
        text.close();
        require(
                engine.facesOpened == 5 && engine.facesClosed == 5 && engine.closed == 1,
                "换代后正常关闭未释放所属face或重复关闭session");
    }

    @Override
    protected void runTests() throws Exception {
        scenario(
                "不可变位图所有权",
                () -> {
                    byte[] rgba = {1, 2, 3, 4};
                    GlyphBitmap bitmap = new GlyphBitmap(1, 1, 2, 3, .5f, true, rgba);
                    rgba[0] = 9;
                    require(
                            bitmap.pixels()[0] == 1 && bitmap.pixelsBuffer().isReadOnly(),
                            "pure bitmap所有权");
                    byte[] clone = bitmap.pixels();
                    clone[0] = 7;
                    require(bitmap.pixels()[0] == 1, "pure bitmap泄露可变像素");
                });
        scenario(
                "注入engine布局采样及session生命周期",
                () -> {
                    ContractEngine engine = new ContractEngine();
                    FontSource source =
                            FontSource.fromResource(
                                    "contract-probe",
                                    1,
                                    "assets/qz_uilib/fonts/unifont/unifont-16.0.04.otf",
                                    false);
                    require(engine.opened == 0, "engine工厂构造触发native/session");
                    try (FontTextService text =
                            new FontTextService(
                                    Collections.singletonList(source), engine, 2, 4, 128)) {
                        TextLayout logical =
                                text.layout(
                                        "AB中",
                                        new TextStyle("probe", 12, -1),
                                        1000,
                                        TextPolicy.SINGLE_LINE);
                        TextLayout sampled = text.sample(logical, new RasterSampling(2, 1));
                        require(
                                logical.advanceWidth == 18
                                        && sampled.advanceWidth == 18
                                        && sampled.lines().get(0).glyphs().get(0).size == 24,
                                "注入布局与采样逻辑流程");
                        require(
                                logical.policy().equals(TextPolicy.SINGLE_LINE)
                                        && sampled.policy().equals(logical.policy())
                                        && logical.withStyle(logical.style.withColor(0xff123456))
                                                .policy()
                                                .equals(logical.policy()),
                                "正式请求事实在布局、采样或重色时丢失");
                        require(
                                logical.lines().get(0).glyphs().get(2).source == source
                                        && logical.input.sourceStart(2) == 2,
                                "纯核心计划来源事实");
                    }
                    require(engine.opened == 1 && engine.closed == 1, "session资源生命周期");
                });
        scenario(
                "LRU关闭异常保持退休face原错误身份",
                () -> {
                    evictionFailure(new IllegalStateException("retired face close failure"));
                    evictionFailure(new IllegalArgumentException("retired face argument failure"));
                });
        scenario("reload关闭失败仍释放全部face并通知", () -> invalidationFailure(false, true));
        scenario("register关闭Error仍释放全部face并通知", () -> invalidationFailure(true, true));
        scenario("失效监听失败不中断其余监听", () -> invalidationFailure(false, false));
        System.out.println(
                "CoreFontEngineScenario PASS injected lifecycle/data/layout/sample contract"
                        + " (非真实字体能力证据)");
    }
}
