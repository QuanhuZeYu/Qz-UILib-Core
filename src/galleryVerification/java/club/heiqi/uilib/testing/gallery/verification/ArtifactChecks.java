package club.heiqi.uilib.testing.gallery.verification;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.integration.CoreArtifactScenario;
import club.heiqi.uilib.ui.scene.integration.PackagedUnicodeScenario;
import java.io.DataInputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

/** 成品检查通过 null-parent 加载器消费正式 Jar，不把开发输出当成隔离证据。 */
public final class ArtifactChecks extends TestApplication {
    @Override protected void runTests() throws Exception {
        String declared = System.getProperty("uilib.verification.core");
        File core = declared == null ? location(ScenePageSession.class) : new File(declared).getCanonicalFile();
        require(core.isFile(), "正式产物检查需要 core Jar；开发运行请设置 uilib.verification.core");
        boolean java21;
        try (JarFile jar = new JarFile(core); DataInputStream input = new DataInputStream(
                jar.getInputStream(jar.getJarEntry("club/heiqi/uilib/ui/scene/host/ScenePageSession.class")))) {
            require(input.readInt() == 0xCAFEBABE, "正式 Core class 无效"); input.readUnsignedShort();
            java21 = input.readUnsignedShort() >= 65;
        }
        File verification = location(ArtifactChecks.class), support = location(TestApplication.class);
        scenario("正式Core定义与公共消费者", () -> {
            String[] arguments = java21 ? new String[] {"--java21", core.getPath(), verification.getPath(), support.getPath()}
                    : new String[] {core.getPath(), verification.getPath(), support.getPath()};
            new CoreArtifactScenario().run(arguments);
        });
        scenario("正式Unicode双向字素及Thai字典", () -> new PackagedUnicodeScenario().run(core.getPath()));
        scenario("正式Core媒体会话与视频布局隔离", () -> {
            try (URLClassLoader loader = new URLClassLoader(new URL[] {
                    core.toURI().toURL(), verification.toURI().toURL(), support.toURI().toURL()}, null)) {
                for (String name : new String[] {"MediaCoreScenario", "MediaSourceScenario", "SceneVideoLayoutScenario"}) {
                    Class<?> type = loader.loadClass("club.heiqi.uilib.testing.media." + name);
                    try { type.getMethod("run", String[].class).invoke(type.getConstructor().newInstance(), (Object) new String[0]); }
                    catch (InvocationTargetException error) { rethrow(error.getCause()); }
                }
            }
        });
    }
    private static File location(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalFile();
    }
    private static void rethrow(Throwable error) throws Exception {
        if (error instanceof Error) throw (Error) error;
        if (error instanceof Exception) throw (Exception) error;
        throw new RuntimeException(error);
    }
}
