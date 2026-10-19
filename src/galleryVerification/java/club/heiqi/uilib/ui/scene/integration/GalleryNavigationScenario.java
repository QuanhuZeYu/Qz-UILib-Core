package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.*;
import club.heiqi.uilib.ui.config.*;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.font.*;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 正式共享目录、页面、输入与 PaintPlan 的无窗口闭环；字体可由所属宿主供给。 */
public final class GalleryNavigationScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("全部共享页面从HOME进入及三尺寸切换", () -> verify(new MultilineEditingScenario.PlainHost()));
    }
    public static void verify(SceneTextMeasurer text) throws Exception {
        ClassLoader loader = GalleryNavigationScenario.class.getClassLoader();
        List<FontSource> builtins = FontTextService.defaultSources(loader, 1);
        ConfigKey<List<String>> fontKey = FontCandidateConfiguration.key(builtins);
        ReactiveDomain domain = ReactiveDomain.direct();
        ConfigSession[] config = new ConfigSession[1]; SettingsConfiguration[] settings = new SettingsConfiguration[1];
        Path directory = Files.createTempDirectory("uilib-gallery-"); Path file = directory.resolve("settings.yaml");
        final class Host implements SceneHost {
            int width = 1400, height = 900; PaintPlan paint;
            public int width() { return width; } public int height() { return height; }
            public long metricsRevision() { return 1; } public void replay(PaintPlan plan) { paint = plan; }
        }
        Host host = new Host();
        try {
            domain.execute(() -> {
                config[0] = new ConfigSession(domain, ConfigSchema.of(fontKey, ThemeConfiguration.KEY));
                settings[0] = new SettingsConfiguration(domain, config[0], fontKey, null, file,
                        FontCandidateConfiguration.defaults(builtins), values -> {});
            });
            try (ScenePageSession session = ScenePageSession.open(domain, host, text, new TestGallery(() -> settings[0]))) {
                GalleryJourney.visitAll(new GalleryJourney.Driver() {
                    public void frame() { session.frame(); }
                    public SceneGeometryView geometry() {
                        SceneGeometryView[] result = new SceneGeometryView[1];
                        domain.execute(() -> result[0] = session.pipeline().current().geometry); return result[0];
                    }
                    public PaintPlan paint() { return host.paint; }
                    public void click(int x, int y) { session.pointer(ScenePointerEvent.down(0, x, y)); session.pointer(ScenePointerEvent.up(0, x, y)); }
                    public void replaceText(String value) { session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL, false, null)); session.edit(SceneEditEvent.text(value)); }
                    public void scroll(int x, int y, int delta) { session.pointer(ScenePointerEvent.scroll(x, y, delta)); }
                    public void command(SceneEditEvent.Command command, boolean shift) { session.edit(SceneEditEvent.command(command, shift, null)); }
                    public void escape() { session.escape(() -> { throw new AssertionError("目录操作意外关闭广场"); }); }
                    public void typeText(String value) { session.edit(SceneEditEvent.text(value)); }
                    public SceneNode focused() {
                        SceneNode[] result = new SceneNode[1];
                        domain.execute(() -> result[0] = session.runtime().focusRouter().focused().get()); return result[0];
                    }
                    public boolean resize(int width, int height) { host.width = width; host.height = height; return true; }
                });
            }
        } finally {
            Files.deleteIfExists(file); Files.deleteIfExists(directory);
        }
    }
}
