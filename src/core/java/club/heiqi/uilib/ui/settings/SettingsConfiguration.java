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
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
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
import club.heiqi.uilib.ui.render.batch.BatchingConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ScrollConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;

/** 设置工作区借用正式配置，保存基线由宿主持有的配置对象维护。
 * 字体来源读取由宿主复用既有解析器，配置文档与其他模块字段仍由 session 保存。 */
public final class SettingsConfiguration implements AutoCloseable {
    public static final class Snapshot {
        private final List<String> candidates;
        private final boolean replace;
        private final ThemeMode theme;
        private final boolean aggressiveBatching;
        private final int scrollStep;

        public Snapshot(List<String> candidates, boolean replace) {
            this(candidates, replace, ThemeMode.LIGHT, false);
        }

        public Snapshot(List<String> candidates, boolean replace, ThemeMode theme) {
            this(candidates, replace, theme, false);
        }

        public Snapshot(List<String> candidates, boolean replace, ThemeMode theme, boolean aggressiveBatching) {
            this(candidates, replace, theme, aggressiveBatching, ScrollConfiguration.KEY.defaultValue());
        }

        public Snapshot(List<String> candidates, boolean replace, ThemeMode theme, boolean aggressiveBatching, int scrollStep) {
            if (!ScrollConfiguration.validStep(scrollStep)) throw new IllegalArgumentException("滚轮距离必须为正");
            this.scrollStep = scrollStep;
            this.aggressiveBatching = aggressiveBatching;
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

        public boolean aggressiveBatching() {
            return aggressiveBatching;
        }

        public int scrollStep() { return scrollStep; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) return false;
            Snapshot next = (Snapshot) other;
            return replace == next.replace
                    && aggressiveBatching == next.aggressiveBatching
                    && scrollStep == next.scrollStep
                    && theme == next.theme
                    && candidates.equals(next.candidates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(candidates, replace, theme, aggressiveBatching, scrollStep);
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
     * 回调失败沿原通道报告，已经读取并发布的配置事实保持生效。 */
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

    /** 字体准备由校验回调执行，异步结果在原域结算。 */
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

    /** 按 ConfigKey 实例身份检查应用 schema 是否声明合批配置。 */
    public boolean hasBatching() {
        return session.schema().keys().contains(BatchingConfiguration.KEY);
    }

    /** 按 ConfigKey 实例身份检查应用 schema 是否声明滚动配置，缺省时使用 Core 默认。 */
    public boolean hasScrolling() {
        return session.schema().keys().contains(ScrollConfiguration.KEY);
    }

    public int defaultScrollStep() { return ScrollConfiguration.KEY.defaultValue(); }

    public List<String> defaultCandidates() {
        return fontKey.defaultValue();
    }

    public boolean defaultReplace() {
        return mcKey != null && mcKey.defaultValue();
    }

    public ThemeMode defaultTheme() {
        return ThemeConfiguration.KEY.defaultValue();
    }

    public boolean defaultAggressiveBatching() {
        return BatchingConfiguration.KEY.defaultValue();
    }

    public Snapshot current() {
        return new Snapshot(
                session.get(fontKey),
                mcKey != null && session.get(mcKey),
                session.get(ThemeConfiguration.KEY),
                hasBatching() && session.get(BatchingConfiguration.KEY),
                hasScrolling() ? session.get(ScrollConfiguration.KEY) : defaultScrollStep());
    }

    public ReadableSignal<Snapshot> saved() {
        return saved;
    }

    public ReadableSignal<String> resourceError() {
        return resourceError;
    }

    /** 为宿主字体资源更新记录诊断，失败沿原配置事实继续报告给共享工作区。
     * 域、布局和绘制的一般异常由各自调用边界处理。 */
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

    /** 宿主负责关闭共享配置并结束待完成请求；已开始的文件操作按原队列收尾。 */
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

    /** 应用字体与宿主文字选项，其余字段沿用当前配置。 */
    public CompletableFuture<Void> apply(List<String> candidates, boolean replace) {
        return apply(candidates, replace, current().theme());
    }

    public CompletableFuture<Void> apply(List<String> candidates, boolean replace, ThemeMode theme) {
        // 此入口沿用当前合批与滚动值，在异步校验完成后的实际提交时读取。
        return applySelection(candidates, replace, theme, () -> current().aggressiveBatching(), () -> current().scrollStep());
    }

    public CompletableFuture<Void> apply(List<String> candidates, boolean replace, ThemeMode theme, boolean aggressiveBatching) {
        return applySelection(candidates, replace, theme, () -> aggressiveBatching, () -> current().scrollStep());
    }

    public CompletableFuture<Void> apply(List<String> candidates, boolean replace, ThemeMode theme, boolean aggressiveBatching, int scrollStep) {
        return applySelection(candidates, replace, theme, () -> aggressiveBatching, () -> scrollStep);
    }

    private CompletableFuture<Void> applySelection(List<String> candidates, boolean replace, ThemeMode theme, BooleanSupplier aggressiveBatching, IntSupplier scrollStep) {
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
                try { applyValidated(frozen, replace, theme, aggressiveBatching.getAsBoolean(), scrollStep.getAsInt()); }
                catch (RuntimeException failure) { result.completeExceptionally(failure); }
            }, result::completeExceptionally);
            return result;
        });
    }

    /** 仅由完成所属校验且仍持有请求身份的设置协调者调用。 */
    void applyValidated(List<String> candidates, boolean replace, ThemeMode theme, boolean aggressiveBatching) {
        applyValidated(candidates, replace, theme, aggressiveBatching, current().scrollStep());
    }

    void applyValidated(List<String> candidates, boolean replace, ThemeMode theme, boolean aggressiveBatching, int scrollStep) {
        domain.checkWrite();
        checkOpen();
        Map<ConfigKey<?>, Object> updates = new LinkedHashMap<>();
        updates.put(fontKey, candidates);
        if (mcKey != null) updates.put(mcKey, replace);
        updates.put(ThemeConfiguration.KEY, theme);
        if (hasBatching()) updates.put(BatchingConfiguration.KEY, aggressiveBatching);
        if (hasScrolling()) updates.put(ScrollConfiguration.KEY, scrollStep);
        session.setAll(updates);
    }

    /** 只在原域捕获当次完整值；编码和真实 Files 写入在后台完成。
     * 保存基线对应本请求实际写入的快照。 */
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
     * 已接受的文件请求归宿主配置对象管理，页面关闭后仍按原队列完成。 */
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
        return new Snapshot(
                document.get(fontKey),
                mcKey != null && document.get(mcKey),
                document.get(ThemeConfiguration.KEY),
                hasBatching() && document.get(BatchingConfiguration.KEY),
                hasScrolling() ? document.get(ScrollConfiguration.KEY) : defaultScrollStep());
    }

    @FunctionalInterface
    private interface FileOperation { ConfigDocument run() throws IOException; }

    /** 同一配置来源按请求顺序执行 I/O 与域内结算。
     * 后台执行器只处理文档与声明，已开始的 I/O 完成后再结算请求结果。 */
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
        // 先连接 I/O 与结算链，再发布忙碌事实，使观察者失败时队列仍可完成。
        persisting.set(true);
        // 对外等待与内部串行链分别持有，取消等待后已接受操作仍按序完成。
        return settled.thenApply(ignored -> null);
    }
}
