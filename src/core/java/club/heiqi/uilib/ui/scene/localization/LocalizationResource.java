package club.heiqi.uilib.ui.scene.localization;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** 独立语言资料入口，每次打开返回由语言流负责关闭的新输入。 */
@FunctionalInterface
public interface LocalizationResource {
    InputStream open(String language) throws IOException;

    /** 从 ClassLoader 指定根目录读取 UTF-8 properties。 */
    static LocalizationResource classpath(ClassLoader loader, String root) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(root, "root");
        String path = root;
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        final String prefix = path.isEmpty() ? "" : path + "/";
        return new LocalizationResource() {
            @Override
            public InputStream open(String language) throws IOException {
                String location = prefix + requireLanguage(language) + ".properties";
                InputStream input = loader.getResourceAsStream(location);
                if (input == null) throw new FileNotFoundException("classpath:" + location);
                return input;
            }

            @Override
            public String toString() {
                return "classpath:" + prefix;
            }
        };
    }

    /** 读取独立目录已有资料，业务写入后显式 reload。 */
    static LocalizationResource directory(Path root) {
        final Path directory = Objects.requireNonNull(root, "root");
        return new LocalizationResource() {
            @Override
            public InputStream open(String language) throws IOException {
                return Files.newInputStream(
                        directory.resolve(requireLanguage(language) + ".properties"));
            }

            @Override
            public String toString() {
                return directory.toString();
            }
        };
    }

    /** 将语言标识限制为资源根内的安全单路径段。 */
    static String requireLanguage(String language) {
        LocalizationSnapshot.requireKey(language);
        if (".".equals(language)
                || "..".equals(language)
                || language.endsWith(".")
                || language.endsWith(" "))
            throw new IllegalArgumentException("非法语言标识: " + language);
        for (int i = 0; i < language.length(); i++) {
            char value = language.charAt(i);
            if (value == '/'
                    || value == '\\'
                    || Character.isISOControl(value)
                    || "<>:\"|?*".indexOf(value) >= 0)
                throw new IllegalArgumentException("非法语言标识: " + language);
        }
        return language;
    }
}
