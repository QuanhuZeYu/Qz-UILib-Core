package club.heiqi.uilib.ui.scene.text.font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Consumer;

/** worker 内的一次资源准备。目录只索引一次；被选字族保留索引时的同一字节快照。 */
public final class FontFamilyResolver {
    private final Map<String, List<FontSource>> families = new LinkedHashMap<>();
    private final Map<Path, String> seeds = new LinkedHashMap<>();

    public FontFamilyResolver(List<Path> configured, List<Path> availableDirectories,
            long revision, Consumer<String> diagnostics) throws IOException {
        LinkedHashSet<Path> required = new LinkedHashSet<>();
        LinkedHashSet<Path> directories = new LinkedHashSet<>();
        for (Path path : configured) {
            Path absolute = path.toAbsolutePath().normalize();
            required.add(absolute);
            if (absolute.getParent() != null) directories.add(absolute.getParent());
        }
        Map<Path, List<FontSource>> selectedFiles = new LinkedHashMap<>();
        // 先确定被声明字族；随后扫描只保留这些族的完整字节，不让 family() 再开文件。
        for (Path path : required) {
            try {
                List<FontSource> faces = read(path, revision);
                String family = faces.get(0).metadata().family;
                seeds.put(path, family);
                families.computeIfAbsent(family, key -> new ArrayList<>());
                selectedFiles.put(path, faces);
            } catch (IOException | RuntimeException failure) {
                throw new IOException("配置字体元数据读取失败: " + path, failure);
            }
        }
        for (List<FontSource> faces : selectedFiles.values()) add(faces, families.keySet());
        if (required.isEmpty()) return;
        for (Path path : availableDirectories) directories.add(path.toAbsolutePath().normalize());
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        for (Path directory : directories) if (Files.isDirectory(directory)) {
            try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
                entries.filter(Files::isRegularFile).filter(FontFamilyResolver::fontFile).sorted()
                        .forEach(path -> files.add(path.toAbsolutePath().normalize()));
            }
        }
        for (Path path : files) if (!required.contains(path)) {
            try { add(read(path, revision), families.keySet()); }
            catch (IOException | RuntimeException failure) {
                diagnostics.accept("忽略不可解析字体: " + path + " / " + failure);
            }
        }
    }

    private static List<FontSource> read(Path path, long revision) throws IOException {
        FontSource source = FontSource.file(path.toString(), revision, path, 0, false, Collections.emptySet());
        List<FontSource> faces = source.collectionFaces();
        for (FontSource face : faces) face.prepare();
        return faces;
    }

    private void add(List<FontSource> faces, Set<String> selected) {
        for (FontSource face : faces) {
            String family = face.metadata().family;
            if (selected.contains(family)) families.get(family).add(face);
        }
    }

    private static boolean fontFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttf") || name.endsWith(".ttc")
                || name.endsWith(".otf") || name.endsWith(".otc");
    }

    /** 无 I/O；配置文件声明 face0 所属字族，显式 FontSource.file 仍只表示指定 face。 */
    public FontSource family(Path configured, String id) throws IOException {
        Path seed = configured.toAbsolutePath().normalize();
        String family = seeds.get(seed);
        if (family == null) throw new IOException("字体没有可用face0: " + seed);
        return FontSource.family(id, families.get(family)).prepare();
    }
}
