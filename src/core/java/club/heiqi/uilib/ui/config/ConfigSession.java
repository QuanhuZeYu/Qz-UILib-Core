package club.heiqi.uilib.ui.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;

/** 应用持有的配置权威值；构造和全部操作均在所属 ReactiveDomain 内执行。
 * 无自持订阅或线程；订阅释放交给使用者的 Owner。加载先全量验证，再发布整份快照。
 * I/O 与非法配置沿调用通道暴露；已经发布之后的观察者异常遵循状态域原有失败语义。 */
public final class ConfigSession {
    private final ReactiveDomain domain;
    private final ConfigSchema schema;
    private final Map<ConfigKey<?>, Entry<?>> entries = new LinkedHashMap<>();
    private Map<ConfigKey<?>, Object> values;
    private YamlDocument document = YamlDocument.empty();

    private final class Entry<T> implements ReadableSignal<T> {
        final ConfigKey<T> key;
        final Signal<T> notification;

        Entry(ConfigKey<T> key) {
            this.key = key;
            notification = domain.signal(key.defaultValue());
        }

        public T get() {
            // Signal 只负责追踪及键级通知；完整批次的值先进入 values。
            notification.get();
            return value(key);
        }

        void publish() { notification.set(value(key)); }
    }

    public ConfigSession(ReactiveDomain domain, ConfigSchema schema) {
        this.domain = Objects.requireNonNull(domain, "domain");
        domain.checkAccess();
        this.schema = Objects.requireNonNull(schema, "schema");
        Map<ConfigKey<?>, Object> initial = new LinkedHashMap<>();
        for (ConfigKey<?> key : schema.keys()) {
            initial.put(key, key.defaultValue());
            addEntry(key);
        }
        values = Collections.unmodifiableMap(initial);
    }

    private <T> void addEntry(ConfigKey<T> key) { entries.put(key, new Entry<>(key)); }

    @SuppressWarnings("unchecked")
    private <T> T value(ConfigKey<T> key) { return (T) values.get(key); }

    @SuppressWarnings("unchecked")
    private <T> Entry<T> entry(ConfigKey<T> key) {
        domain.checkAccess();
        Objects.requireNonNull(key, "key");
        Entry<?> entry = entries.get(key);
        if (entry == null) throw new IllegalArgumentException("未声明配置键: " + key.name());
        return (Entry<T>) entry;
    }

    public <T> T get(ConfigKey<T> key) { return entry(key).get(); }
    public <T> ReadableSignal<T> signal(ConfigKey<T> key) { return entry(key); }

    public <T> void set(ConfigKey<T> key, T value) {
        domain.checkWrite();
        entry(key);
        T next = domain.readOnly(() -> key.checked(value));
        if (Objects.equals(values.get(key), next)) return;
        Map<ConfigKey<?>, Object> changed = new LinkedHashMap<>(values);
        changed.put(key, next);
        publish(changed);
    }

    /** 有界的多键提交：全部键先按原有 checked 合同校验与冻结，再一次发布。
     * 不改变普通 set 的校验、相同值和观察者失败语义。 */
    public void setAll(Map<ConfigKey<?>, ?> updates) {
        domain.checkWrite();
        Objects.requireNonNull(updates, "updates");
        Map<ConfigKey<?>, Object> changed = domain.readOnly(() -> {
            Map<ConfigKey<?>, Object> next = new LinkedHashMap<>(values);
            for (Map.Entry<ConfigKey<?>, ?> update : updates.entrySet()) {
                entry(update.getKey());
                next.put(update.getKey(), checked(update.getKey(), update.getValue()));
            }
            return next;
        });
        publish(changed);
    }

    @SuppressWarnings("unchecked")
    private static <T> T checked(ConfigKey<T> key, Object value) { return key.checked((T) value); }

    /** 声明可交给后台文档读取；声明及其 codec/validator 沿纯值合同使用。 */
    public ConfigSchema schema() { domain.checkAccess(); return schema; }

    /** 在所属域内捕获整份一致值；快照不再依赖本 session 的后续编辑。 */
    public ConfigDocument snapshot() {
        domain.checkAccess();
        return new ConfigDocument(schema, document, values);
    }

    /** 后台已完成全部读取和校验；这里只在原域发布完整文档。 */
    public void publish(ConfigDocument next) {
        domain.checkWrite();
        Objects.requireNonNull(next, "document");
        if (!schema.keys().equals(next.schema().keys())) throw new IllegalArgumentException("配置文档声明与会话不一致");
        document = next.representation();
        publish(next.values());
    }

    public void load(Path path) throws IOException {
        domain.checkWrite();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            load(reader, path.toString());
        }
    }

    /** Reader 的关闭由调用者负责；缺失声明重置为声明默认值。 */
    public void load(Reader reader) throws IOException { load(reader, "<Reader>"); }

    /** 可指定输入来源，供语法和字段诊断使用。同步入口保留域的纯求值保护。 */
    public void load(Reader reader, String source) throws IOException {
        domain.checkWrite();
        YamlDocument parsed = YamlDocument.read(Objects.requireNonNull(reader, "reader"), Objects.requireNonNull(source, "source"));
        publish(domain.readOnly(() -> ConfigDocument.decode(schema, parsed)));
    }

    /** 仅供宿主显式一次迁移旧格式。成功保存 YAML 后宿主以 YAML 为唯一来源。 */
    public void loadProperties(Path path) throws IOException {
        domain.checkWrite();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loadProperties(reader, path.toString());
        }
    }
    public void loadProperties(Reader reader) throws IOException { loadProperties(reader, "<Properties Reader>"); }
    private void loadProperties(Reader reader, String source) throws IOException {
        domain.checkWrite();
        Properties input = new Properties();
        input.load(Objects.requireNonNull(reader, "reader"));
        publish(domain.readOnly(() -> ConfigDocument.decodeProperties(schema, input, source)));
    }

    private void publish(Map<ConfigKey<?>, Object> next) {
        if (values.equals(next)) return;
        values = Collections.unmodifiableMap(new LinkedHashMap<>(next));
        // 域先完成所有键的依赖失效，再同步通知观察者；直接值及派生读取属于同一批次。
        domain.batch(() -> { for (Entry<?> entry : entries.values()) entry.publish(); });
    }

    public void save(Path path) throws IOException {
        domain.checkWrite();
        String content = domain.readOnly(() -> snapshot().encode());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { writer.write(content); }
    }

    /** Writer 保持打开；持久化只由显式调用触发。 */
    public void save(Writer writer) throws IOException {
        domain.checkWrite();
        Objects.requireNonNull(writer, "writer").write(domain.readOnly(() -> snapshot().encode()));
    }
}
