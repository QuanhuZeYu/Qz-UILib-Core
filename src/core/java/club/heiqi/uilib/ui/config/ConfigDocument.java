package club.heiqi.uilib.ui.config;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** 完整且已校验的配置值与只读文档表示，按 codec 合同深层冻结。
 * 编解码、对象工厂和校验处理纯值，宿主可在后台执行读写与解析。 */
public final class ConfigDocument {
    private final ConfigSchema schema;
    private final YamlDocument document;
    private final Map<ConfigKey<?>, Object> values;

    ConfigDocument(ConfigSchema schema, YamlDocument document, Map<ConfigKey<?>, Object> values) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.document = Objects.requireNonNull(document, "document");
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static ConfigDocument defaults(ConfigSchema schema) {
        Objects.requireNonNull(schema, "schema");
        Map<ConfigKey<?>, Object> values = new LinkedHashMap<>();
        for (ConfigKey<?> key : schema.keys()) values.put(key, key.defaultValue());
        return new ConfigDocument(schema, YamlDocument.empty(), values);
    }

    public static ConfigDocument read(ConfigSchema schema, Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(schema, reader, path.toString());
        }
    }

    /** Reader 归调用者，全量解析和校验后返回独立文档，发布由配置会话负责。 */
    public static ConfigDocument read(ConfigSchema schema, Reader reader, String source) throws IOException {
        return decode(schema, YamlDocument.read(Objects.requireNonNull(reader, "reader"), Objects.requireNonNull(source, "source")));
    }

    static ConfigDocument decode(ConfigSchema schema, YamlDocument parsed) {
        Objects.requireNonNull(schema, "schema");
        try {
            schema.checkTree(parsed.tree());
            Map<ConfigKey<?>, Object> values = new LinkedHashMap<>();
            for (ConfigKey<?> key : schema.keys()) values.put(key, decodeKey(parsed.tree(), key));
            return new ConfigDocument(schema, parsed, values);
        } catch (ConfigException failure) { throw parsed.locate(failure); }
    }

    private static <T> T decodeKey(Map<String, Object> tree, ConfigKey<T> key) {
        Object branch = tree;
        for (String segment : key.name().split("\\.")) {
            Map<?, ?> map = (Map<?, ?>) branch;
            if (!map.containsKey(segment)) return key.checked(key.defaultValue());
            branch = map.get(segment);
        }
        try { return key.read(branch); }
        catch (ConfigException failure) { throw failure; }
        catch (RuntimeException failure) { throw new ConfigException(key.name(), failure.getMessage(), failure); }
    }

    /** 仅供显式旧格式迁移；成功写入 YAML 后由宿主选择正式来源。 */
    public static ConfigDocument readProperties(ConfigSchema schema, Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readProperties(schema, reader, path.toString());
        }
    }

    public static ConfigDocument readProperties(ConfigSchema schema, Reader reader, String source) throws IOException {
        Properties input = new Properties();
        input.load(Objects.requireNonNull(reader, "reader"));
        return decodeProperties(schema, input, Objects.requireNonNull(source, "source"));
    }

    static ConfigDocument decodeProperties(ConfigSchema schema, Properties input, String source) {
        try { return new ConfigDocument(schema, YamlDocument.empty(), LegacyProperties.read(schema, input)); }
        catch (ConfigException failure) { throw new ConfigException(failure.path(), source + ": " + failure.getMessage(), failure); }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ConfigKey<T> key) {
        Objects.requireNonNull(key, "key");
        if (!values.containsKey(key)) throw new IllegalArgumentException("未声明配置键: " + key.name());
        return (T) values.get(key);
    }

    /** 校验多键新值并返回独立文档，原文档保持当前值与表示。 */
    public ConfigDocument withAll(Map<ConfigKey<?>, ?> updates) {
        Objects.requireNonNull(updates, "updates");
        Map<ConfigKey<?>, Object> next = new LinkedHashMap<>(values);
        for (Map.Entry<ConfigKey<?>, ?> update : updates.entrySet()) {
            ConfigKey<?> key = Objects.requireNonNull(update.getKey(), "key");
            if (!values.containsKey(key)) throw new IllegalArgumentException("未声明配置键: " + key.name());
            next.put(key, checked(key, update.getValue()));
        }
        return new ConfigDocument(schema, document, next);
    }

    @SuppressWarnings("unchecked")
    private static <T> T checked(ConfigKey<T> key, Object value) { return key.checked((T) value); }

    public ConfigSchema schema() { return schema; }
    YamlDocument representation() { return document; }
    Map<ConfigKey<?>, Object> values() { return values; }

    public void write(Path path) throws IOException {
        // 完整编码成功后再打开目标，使编码失败时原文件保持完整。
        String text = encode();
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { writer.write(text); }
    }

    /** Writer 归调用者，每次写入使用独立表示副本，同一快照可并发写出。 */
    public void write(Writer writer) throws IOException { Objects.requireNonNull(writer, "writer").write(encode()); }

    String encode() {
        Map<String, Object> tree = new LinkedHashMap<>();
        Map<String, String> comments = new LinkedHashMap<>();
        for (ConfigKey<?> key : schema.keys()) encodeKey(tree, comments, key);
        StringWriter content = new StringWriter();
        document.write(tree, comments, content);
        return content.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> void encodeKey(Map<String, Object> tree, Map<String, String> comments, ConfigKey<T> key) {
        T value = get(key);
        Object encoded;
        try { encoded = key.codec().write(value); }
        catch (RuntimeException failure) { throw new ConfigException(key.name(), failure.getMessage(), failure); }
        String[] path = key.name().split("\\.");
        Map<String, Object> branch = tree;
        for (int i = 0; i < path.length - 1; i++) {
            Object child = branch.get(path[i]);
            if (child == null) { child = new LinkedHashMap<String, Object>(); branch.put(path[i], child); }
            branch = (Map<String, Object>) child;
        }
        branch.put(path[path.length - 1], encoded);
        if (key.comment() != null) comments.put(key.name(), key.comment());
        for (Map.Entry<String, String> description : key.codec().comments(value).entrySet())
            comments.put(ConfigCodecs.child(key.name(), description.getKey()), description.getValue());
    }
}
