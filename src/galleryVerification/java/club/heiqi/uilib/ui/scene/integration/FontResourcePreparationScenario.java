package club.heiqi.uilib.ui.scene.integration;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontResources;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.FontFamilyResolver;

/** 真实字体字节的 CPU 准备与代际合同；native/像素仍由 Platform 真实 FT/HB 场景取证。 */
public final class FontResourcePreparationScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new FontResourcePreparationScenario().run(); }
    @Override protected void runTests() throws Exception {
        scenario("完整候选先在worker开流，测量仅消费当代准备事实", this::preparedGeneration);
        scenario("字族索引后文件改变不会混入同代family", this::familySnapshot);
    }

    private void preparedGeneration() throws Exception {
        Thread owner = Thread.currentThread();
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger unicodeReads = new AtomicInteger();
        ClassLoader parent = getClass().getClassLoader();
        ClassLoader loader = new ClassLoader(parent) {
            @Override public InputStream getResourceAsStream(String path) {
                require(Thread.currentThread() != owner, "UI线程开字体流: " + path);
                InputStream stream = parent.getResourceAsStream(path);
                if (stream == null) return null;
                reads.incrementAndGet();
                if (path.startsWith("assets/qz_uilib/unicode/")) unicodeReads.incrementAndGet();
                return new FilterInputStream(stream) {
                    private void worker() { require(Thread.currentThread() != owner, "UI线程read/close字体流"); }
                    @Override public int read() throws IOException { worker(); return in.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) throws IOException { worker(); return in.read(bytes, offset, length); }
                    @Override public void close() throws IOException { worker(); in.close(); }
                };
            }
        };
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            FontResources first = CompletableFuture.supplyAsync(() -> prepare(loader, 10), worker).get();
            int preparedReads = reads.get();
            require(unicodeReads.get() == 4, "Unicode默认表未由同代worker准备");
            require(preparedReads == FontResources.resourcePaths().size(), "完整资源存在遗漏或重复开流");
            CoreFontEngineScenario.ContractEngine engine = new CoreFontEngineScenario.ContractEngine();
            try (FontTextService fonts = new FontTextService(first, engine, 4, 8, 128)) {
                TextStyle style = new TextStyle("default", 16, -1);
                TextLayout old = fonts.layout("ABC", style, 500);
                fonts.layout("math text", new TextStyle("math-text-roman", 16, -1), 500);
                fonts.layout("UI", new TextStyle("settings-path", 16, -1), 500);
                fonts.mathFont(style);
                require(reads.get() == preparedReads, "布局或数学入口重新读取字体");
                FontResources next = CompletableFuture.supplyAsync(() -> prepare(loader, 11), worker).get();
                fonts.reload(next);
                TextLayout changed = fonts.layout("ABC", style, 500);
                require(changed != old && changed.generation > old.generation, "重载未使布局失效");
                require(old.lines().get(0).glyphs().get(0).source.revision == 10, "旧布局混入新代来源");
                require(changed.lines().get(0).glyphs().get(0).source.revision == 11, "新布局没有消费当代来源");
            }
            System.out.println("FONT_RESOURCE_PREPARATION workerOpenReadClose=true completeNamedAndTail=true unicodeWorkerPrepared=true ownerMeasureOnly=true reloadInvalidated=true");
        } finally { worker.shutdown(); }
    }

    private static FontResources prepare(ClassLoader loader, long revision) {
        try {
            List<FontSource> builtins = FontTextService.defaultSources(loader, revision);
            return FontResources.prepare(loader, revision, builtins, builtins);
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private void familySnapshot() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        Path directory = Files.createTempDirectory("uilib-font-snapshot-");
        Path file = directory.resolve("selected.ttf");
        try {
            try (InputStream source = loader.getResourceAsStream("assets/qz_uilib/fonts/math-text/sans/NotoSans-Regular.ttf")) { Files.copy(source, file); }
            FontFamilyResolver resolver = new FontFamilyResolver(Collections.singletonList(file), Collections.emptyList(), 20, System.err::println);
            FontSource original = resolver.family(file, "selected");
            String fingerprint = original.contentFingerprint();
            try (InputStream source = loader.getResourceAsStream("assets/qz_uilib/fonts/math-text/mono/IBMPlexMono-Regular.ttf")) { Files.copy(source, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            FontSource sameGeneration = resolver.family(file, "selected-again");
            require(fingerprint.equals(sameGeneration.contentFingerprint()), "family重新开文件导致代际串内容");
            FontFamilyResolver next = new FontFamilyResolver(Collections.singletonList(file), Collections.emptyList(), 21, System.err::println);
            require(!fingerprint.equals(next.family(file, "next").contentFingerprint()), "新代没有观察真实字体文件变化");
            System.out.println("FONT_FAMILY_SNAPSHOT sameGenerationStable=true nextGenerationChanged=true");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
    }
}
