package club.heiqi.uilib.ui.settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.Set;
import java.util.LinkedHashSet;

import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigDocument;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;

/** 设置工作区借用正式配置；保存基线归宿主持有的对象，不随页面释放。
 * 字体来源读取由宿主复用既有解析器，配置文档与其他模块字段仍由 session 保存。 */
public final class SettingsConfiguration implements AutoCloseable {
    public static final class Snapshot {
        private final List<String> candidates;
        private final boolean replace;
        private final ThemeMode theme;

        public Snapshot(List<String> candidates, boolean replace) {
            this(candidates, replace, ThemeMode.LIGHT);
        }

        public Snapshot(List<String> candidates, boolean replace, ThemeMode theme) {
            this.candidates = freeze(candidates);
            this.replace = replace;
            this.theme = Objects.requireNonNull(theme, "theme");
        }

        public List<String> candidates() {
            return candidates;
        }

        public boolean replace() {
            return replace;
        }

        public ThemeMode theme() {
            return theme;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) return false;
            Snapshot next = (Snapshot) other;
            return replace == next.replace
                    && theme == next.theme
                    && candidates.equals(next.candidates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(candidates, replace, theme);
        }
    }

    private final ReactiveDomain domain;
    private final ConfigSession session;
    private final ConfigKey<List<String>> fontKey;
    private final ConfigKey<Boolean> mcKey;
    private final Path path;
    private final List<String> builtins;
    private final Function<List<String>, CompletableFuture<Void>> validateCandidates;
    private final Signal<Snapshot> saved;
    private final Signal<String> resourceError;
    private final Runnable reloadResources;
    private final Signal<Boolean> persisting;
    private int pendingFiles;
    private volatile boolean closed;
    private final Object lifecycle = new Object();
    private final Set<CompletableFuture<?>> requests = new LinkedHashSet<>();
    private CompletableFuture<Void> fileTail = CompletableFuture.completedFuture(null);

    public SettingsConfiguration(
            ReactiveDomain domain,
            ConfigSession session,
            ConfigKey<List<String>> fontKey,
            ConfigKey<Boolean> mcKey,
            Path path,
            List<String> builtinCandidates,
            Consumer<List<String>> validateCandidates) {
        this(
                domain,
                session,
                fontKey,
                mcKey,
                path,
                builtinCandidates,
                validateCandidates,
                () -> {});
    }

    /** 显式文件加载完成后让宿主更新所属资源；同值加载仍刷新原地替换的字体文件。
     * 回调失败保留已经读取并发布的配置事实，不增加监视或恢复策略。 */
    public SettingsConfiguration(
            ReactiveDomain domain,
            ConfigSession session,
            ConfigKey<List<String>> fontKey,
            ConfigKey<Boolean> mcKey,
            Path path,
            List<String> builtinCandidates,
            Consumer<List<String>> validateCandidates,
            Runnable reloadResources) {
        this(immediate(validateCandidates), domain, session, fontKey, mcKey, path, builtinCandidates, reloadResources);
    }

    /** 字体准备可异步完成；调用者在完成后回到原域发布，后台不访问 Signal。 */
    public static SettingsConfiguration withAsyncValidation(
            ReactiveDomain domain,
            ConfigSession session,
            ConfigKey<List<String>> fontKey,
            ConfigKey<Boolean> mcKey,
            Path path,
            List<String> builtinCandidates,
            Function<List<String>, CompletableFuture<Void>> validateCandidates,
            Runnable reloadResources) {
        return new SettingsConfiguration(validateCandidates, domain, session, fontKey, mcKey, path,
                builtinCandidates, reloadResources);
    }

    private static Function<List<String>, CompletableFuture<Void>> immediate(Consumer<List<String>> validator) {
        Objects.requireNonNull(validator, "validateCandidates");
        return candidates -> {
            validator.accept(candidates);
            return CompletableFuture.completedFuture(null);
        };
    }

    private SettingsConfiguration(
            Function<List<String>, CompletableFuture<Void>> validateCandidates,
            ReactiveDomain domain,
            ConfigSession session,
            ConfigKey<List<String>> fontKey,
            ConfigKey<Boolean> mcKey,
            Path path,
            List<String> builtinCandidates,
            Runnable reloadResources) {
        this.domain = Objects.requireNonNull(domain, "domain");
        domain.checkAccess();
        this.session = Objects.requireNonNull(session, "session");
        this.fontKey = Objects.requireNonNull(fontKey, "fontKey");
        this.mcKey = mcKey;
        this.path = Objects.requireNonNull(path, "path");
        this.builtins = freeze(builtinCandidates);
        this.validateCandidates = Objects.requireNonNull(validateCandidates, "validateCandidates");
        // 宿主先完成实际 load/初始化；构造时的真实值即最近成功读取值。
        saved = domain.signal(current());
        resourceError = domain.signal("");
        persisting = domain.signal(false);
        this.reloadResources = Objects.requireNonNull(reloadResources, "reloadResources");
    }

    private static List<String> freeze(List<String> values) {
        List<String> copy = new ArrayList<>(Objects.requireNonNull(values, "candidates"));
        for (String value : copy) Objects.requireNonNull(value, "candidate");
        return Collections.unmodifiableList(copy);
    }

    public ReactiveDomain domain() {
        return domain;
    }

    public Path path() {
        return path;
    }

    public List<String> builtinCandidates() {
        return builtins;
    }

    public boolean hasMinecraft() {
        return mcKey != null;
    }

    public List<String> defaultCandidates() {
        return fontKey.defaultValue();
    }

    public boolean defaultReplace() {
        return mcKey != null && mcKey.defaultValue();
    }

    public ThemeMode defaultTheme() {
        return ThemeConfiguration.KEY.defaultValue();
    }

    public Snapshot current() {
        return new Snapshot(
                session.get(fontKey),
                mcKey != null && session.get(mcKey),
                session.get(ThemeConfiguration.KEY));
    }

    public ReadableSignal<Snapshot> saved() {
        return saved;
    }

    public ReadableSignal<String> resourceError() {
        return resourceError;
    }

    /** 仅为宿主字体资源更新提供已知失败边界；不捕获域、布局或绘制的一般异常。
     * 配置已经发布后资源失败仍保留当前值，错误交给共享工作区显示；无回滚或自动重试。 */
    public void updateResources(Runnable update) {
        updateResources(update, false);
    }

    /** 显式宿主资源入口记录相同字体诊断，并将原异常继续交所属资源通道。 */
    public void updateResourcesOrThrow(Runnable update) {
        updateResources(update, true);
    }

    private void updateResources(Runnable update, boolean propagate) {
        domain.checkWrite();
        Objects.requireNonNull(update, "update");
        try {
            update.run();
            resourceError.set("");
        } catch (RuntimeException failure) {
            String reason = failure.getMessage();
            if (reason == null || reason.isEmpty()) reason = failure.getClass().getSimpleName();
            resourceError.set("字体资源更新失败：" + path + " — " + reason);
            if (propagate) throw failure;
        }
    }

    CompletableFuture<Void> validateCandidates(List<String> candidates) {
        domain.checkWrite();
        checkOpen();
        List<String> frozen = freeze(candidates);
        CompletableFuture<Void> result = new CompletableFuture<>();
        requests.add(result);
        try {
            CompletableFuture<Void> preparing = domain.readOnly(() -> Objects.requireNonNull(validateCandidates.apply(frozen), "validation"));
            preparing.whenComplete((ignored, failure) -> {
                synchronized (lifecycle) {
                    if (closed) return;
                    domain.execute(() -> {
                        if (closed) return;
                        requests.remove(result);
                        if (failure == null) result.complete(null);
                        else result.completeExceptionally(failure);
                    }, result::completeExceptionally);
                }
            });
        } catch (RuntimeException failure) {
            requests.remove(result);
            result.completeExceptionally(failure);
        }
        return result;
    }

    boolean isClosed() { return closed; }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("设置配置已关闭");
    }

    /** 只由宿主关闭；页面释放不关闭共享配置。不等待或中断已开始的文件操作。 */
    @Override
    public void close() {
        domain.checkWrite();
        if (closed) return;
        synchronized (lifecycle) { closed = true; }
        CompletableFuture<?>[] pending = requests.toArray(new CompletableFuture<?>[0]);
        requests.clear();
        for (CompletableFuture<?> request : pending)
            request.completeExceptionally(new CancellationException("设置配置已关闭"));
    }

    /** 旧调用仅应用原有字段，保留当前全局主题。 */
    public CompletableFuture<Void> apply(List<String> candidates, boolean replace) {
        return apply(candidates, replace, current().theme());
    }

    public CompletableFuture<Void> apply(List<String> candidates, boolean replace, ThemeMode theme) {
        domain.checkWrite();
        checkOpen();
        List<String> frozen = freeze(candidates);
        Objects.requireNonNull(theme, "theme");
        return validateCandidates(frozen).thenCompose(ignored -> {
            CompletableFuture<Void> result = new CompletableFuture<>();
            requests.add(result);
            domain.enqueue(() -> {
                if (closed) return;
                domain.deferCleanup(() -> { requests.remove(result); result.complete(null); });
                try { applyValidated(frozen, replace, theme); }
                catch (RuntimeException failure) { result.completeExceptionally(failure); }
            }, result::completeExceptionally);
            return result;
        });
    }

    /** 仅由完成所属校验且仍持有请求身份的设置协调者调用。 */
    void applyValidated(List<String> candidates, boolean replace, ThemeMode theme) {
        domain.checkWrite();
        checkOpen();
        Map<ConfigKey<?>, Object> updates = new LinkedHashMap<>();
        updates.put(fontKey, candidates);
        if (mcKey != null) updates.put(mcKey, replace);
        updates.put(ThemeConfiguration.KEY, theme);
        session.setAll(updates);
    }

    /** 只在原域捕获当次完整值；编码和真实 Files 写入在后台完成。
     * 保存基线永远对应此请求写入的值，不读取完成时后来发布的配置。 */
    public CompletableFuture<Void> save() {
        domain.checkWrite();
        checkOpen();
        ConfigDocument captured = session.snapshot();
        Snapshot baseline = snapshot(captured);
        return fileOperation(() -> {
            captured.write(path);
            return captured;
        }, written -> saved.set(baseline));
    }

    /** 后台先全量读取、解析和校验，原域一次发布并更新文件基线。
     * 操作归宿主持有的配置对象，页面关闭不撤销已接受的文件请求。 */
    public CompletableFuture<Void> reload() {
        domain.checkWrite();
        checkOpen();
        ConfigSchema schema = session.schema();
        return fileOperation(() -> ConfigDocument.read(schema, path), loaded -> {
            domain.batch(() -> {
                saved.set(snapshot(loaded));
                session.publish(loaded);
            });
            reloadResources.run();
        });
    }

    public ReadableSignal<Boolean> persisting() { return persisting; }

    private Snapshot snapshot(ConfigDocument document) {
        return new Snapshot(document.get(fontKey), mcKey != null && document.get(mcKey), document.get(ThemeConfiguration.KEY));
    }

    @FunctionalInterface
    private interface FileOperation { ConfigDocument run() throws IOException; }

    /** 同一配置来源按请求顺序执行真实 I/O 及域内结算；不重试，不取消在途写入。
     * 复用公共后台执行器，不另建线程或资源运行时；只有文档和声明进入后台阶段。 */
    private CompletableFuture<Void> fileOperation(FileOperation operation, Consumer<ConfigDocument> publish) {
        CompletableFuture<Void> settled = new CompletableFuture<>();
        requests.add(settled);
        CompletableFuture<Void> previous = fileTail;
        fileTail = settled.handle((ignored, failure) -> null);
        pendingFiles++;
        previous.thenApplyAsync(ignored -> {
            if (closed) throw new CancellationException("设置配置已关闭");
            try { return operation.run(); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }).whenComplete((document, failure) -> {
            synchronized (lifecycle) {
                if (closed) return;
                Throwable[] outcome = {failure instanceof CompletionException ? failure.getCause() : failure};
                domain.enqueue(() -> {
                if (closed) return;
                // 在本次域操作及 Effect 结算后报告配置结果；另行启动的字体任务有自己的完成通道。
                domain.deferCleanup(() -> {
                    requests.remove(settled);
                    if (outcome[0] == null) settled.complete(null);
                    else settled.completeExceptionally(outcome[0]);
                });
                try {
                    if (outcome[0] == null) publish.accept(document);
                } catch (RuntimeException problem) { outcome[0] = problem; }
                finally { persisting.set(--pendingFiles != 0); }
                }, problem -> outcome[0] = problem);
            }
        });
        // 先接齐真实I/O与结算，再通知观察者；忙碌通知失败不能留下未启动的串行尾部。
        persisting.set(true);
        // 调用者取消自己的等待，不改变内部串行关系或已接受的文件事实。
        return settled.thenApply(ignored -> null);
    }
}
