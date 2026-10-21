package club.heiqi.uilib.ui.scene.integration;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigCodec;
import club.heiqi.uilib.ui.config.ConfigDocument;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.text.font.FontCandidateConfiguration;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.settings.SettingsConfiguration;
import club.heiqi.uilib.ui.settings.SettingsModel;
import club.heiqi.uilib.ui.settings.SettingsModel.Source;

/** 真实配置文件与正式状态域：替代页面状态的手工验收，不冒充字体像素或 MC 实机。 */
public final class SettingsConfigurationScenario extends TestApplication {
    private List<FontSource> builtins;

    @Override
    protected void setup() throws IOException {
        builtins = FontTextService.defaultSources(getClass().getClassLoader(), 1);
    }

    private final class Fixture {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final ConfigKey<List<String>> fonts = FontCandidateConfiguration.key(builtins);
        final ConfigKey<Boolean> mc;
        final ConfigKey<Map<String, List<String>>> profile =
                new ConfigKey<>(
                        "profile",
                        ConfigCodecs.mapOf(ConfigCodecs.STRING_LIST),
                        Collections.emptyMap());
        final Path file;
        ConfigSession session;
        SettingsConfiguration configuration;
        SettingsModel model;
        Owner owner;

        Fixture(boolean minecraft, boolean rejectFalse) throws Exception {
            this(minecraft, rejectFalse, () -> {});
        }

        Fixture(boolean minecraft, boolean rejectFalse, Runnable reloadResources) throws Exception {
            mc =
                    minecraft
                            ? new ConfigKey<>(
                                    "mc.replaceVanillaFont",
                                    ConfigCodecs.BOOLEAN,
                                    true,
                                    value -> !rejectFalse || value)
                            : null;
            file = Files.createTempFile("uilib-settings-中文-", ".yaml");
            onClose(() -> Files.deleteIfExists(file));
            domain.execute(
                    () -> {
                        session =
                                new ConfigSession(
                                        domain,
                                        minecraft
                                                ? ConfigSchema.of(
                                                        fonts, mc, profile, ThemeConfiguration.KEY)
                                                : ConfigSchema.of(
                                                        fonts, profile, ThemeConfiguration.KEY));
                        try {
                            session.load(
                                    new StringReader(
                                            "# 用户字体说明\nfont:\n  candidates: [builtin:"
                                                    + builtins.get(0).id
                                                    + "] # 用户候选说明\n"
                                                    + (minecraft
                                                            ? "mc:\n  replaceVanillaFont: true\n"
                                                            : "")
                                                    + "# 用户其他模块说明\nprofile:\n  groups: [甲, 乙] # 用户组说明\n"));
                            session.save(file);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                        configuration =
                                new SettingsConfiguration(
                                        domain,
                                        session,
                                        fonts,
                                        mc,
                                        file,
                                        FontCandidateConfiguration.defaults(builtins),
                                        values -> {
                                            try {
                                                FontCandidateConfiguration.resolve(
                                                        values,
                                                        builtins,
                                                        (path, position) ->
                                                                FontSource.file(
                                                                        "settings-file-" + position,
                                                                        1,
                                                                        java.nio.file.Paths.get(
                                                                                path),
                                                                        0,
                                                                        false,
                                                                        Collections.emptySet()));
                                            } catch (IOException failure) {
                                                throw new UncheckedIOException(failure);
                                            }
                                        },
                                        reloadResources);
                        owner = domain.owner();
                        model = new SettingsModel(configuration, owner);
                    });
            onClose(() -> domain.execute(owner::dispose));
        }

        void run(Runnable action) {
            CompletableFuture<Void> settled = new CompletableFuture<>();
            domain.execute(() -> {
                domain.deferCleanup(() -> settled.complete(null));
                action.run();
            }, settled::completeExceptionally);
            try { settled.join(); }
            catch (java.util.concurrent.CompletionException failure) {
                if (failure.getCause() instanceof RuntimeException) throw (RuntimeException) failure.getCause();
                if (failure.getCause() instanceof Error) throw (Error) failure.getCause();
                throw failure;
            }
        }

        void apply() {
            CompletableFuture<Void>[] result = new CompletableFuture[1];
            run(() -> result[0] = model.apply());
            result[0].join();
        }

        void save() throws Exception {
            CompletableFuture<Void>[] result = new CompletableFuture[1];
            run(() -> result[0] = model.save());
            result[0].get(10, TimeUnit.SECONDS);
        }

        void reload() throws Exception {
            CompletableFuture<Void>[] result = new CompletableFuture[1];
            run(() -> result[0] = model.reload());
            result[0].get(10, TimeUnit.SECONDS);
        }

        String declaration(int index) {
            return "builtin:" + builtins.get(index).id;
        }

        String disk() throws IOException {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        }
    }

    @Override
    protected void runTests() throws Exception {
        scenario("后台真实文件请求与原域全量递交及关闭后基线", this::asynchronousFiles);
        scenario("异步字体校验拒绝迟到草稿和释放后的发布", this::asynchronousValidation);
        scenario("公开应用等待已完成校验后的资源Effect失败", this::publicApplyFailure);
        scenario("忙碌观察者失败后真实保存链仍可继续", this::persistingObserverFailure);
        scenario("主题草稿放弃应用与真实文件新会话往返", this::themeLifecycle);
        scenario("主题外部正式发布保留草稿与同步", this::themeExternal);
        scenario("完整草稿应用保存与注释及其他模块字段保留", this::applyAndSave);
        scenario("重复来源排序身份与空列表及语义改回", this::identityAndEmpty);
        scenario("外部正式发布与关闭作用域及重开保存基线", this::externalAndLifecycle);
        scenario("来源拒绝与多键全校验不半发布", this::validation);
        scenario("草稿期间真实字体文件失效仍保留编辑", this::sourceDisappears);
        scenario("真实文件错误保留路径草稿和保存基线", this::fileErrors);
        scenario("显式同值重载资源与失败后的文件事实", this::reloadResources);
        scenario("load同步观察失败仍记录真实文件基线", this::reloadObserverFailure);
        scenario("同步观察及延后资源Effect错误按已发布事实反馈", this::resourceErrors);
        scenario("显式宿主资源失败记录诊断且继续原异常通道", this::explicitResourceFailure);
    }

    /** 队列只能由测试所属线程推进；后台只负责递交，不能借 CURRENT 伪证线程亲和。 */
    private static final class QueuedDomain {
        final Thread owner = Thread.currentThread();
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final ReactiveDomain domain = new ReactiveDomain(queue::add);

        void run(Runnable action) throws Exception {
            CompletableFuture<Void> done = new CompletableFuture<>();
            domain.execute(() -> { action.run(); done.complete(null); });
            await(done);
        }

        void await(CompletableFuture<?> result) throws Exception {
            while (!result.isDone()) {
                Runnable action = queue.poll(10, TimeUnit.SECONDS);
                require(action != null, "原域递交没有到达");
                require(Thread.currentThread() == owner, "域队列在后台执行");
                action.run();
            }
            result.get(10, TimeUnit.SECONDS);
        }
    }

    /** 只控制真实 codec 执行阶段；文档解析、Files 和 Signal 都使用生产实现。 */
    private static final class PreparationGate {
        final CompletableFuture<Thread> entered = new CompletableFuture<>();
        final CountDownLatch release = new CountDownLatch(1);
        private ForkJoinPool workerPool;

        void enter(Thread owner) {
            Thread worker = Thread.currentThread();
            require(worker != owner, "文档 I/O 准备阻塞了 UI 所属线程");
            workerPool = ForkJoinTask.getPool();
            entered.complete(worker);
            // 普通门闩不将进行中的任务计为空闲；CompletableFuture 等待会触发 managed blocking。
            try { require(release.await(10, TimeUnit.SECONDS), "后台准备未获确定性释放"); }
            catch (InterruptedException failure) { throw new AssertionError("后台准备被中断", failure); }
        }

        void awaitCompletion() throws Exception {
            Thread worker = entered.get(10, TimeUnit.SECONDS);
            // 低 CPU 时 CompletableFuture 使用专用线程；只能等待本次实际 worker，不能猜 common pool。
            if (workerPool == null) {
                worker.join(TimeUnit.SECONDS.toMillis(10));
                require(!worker.isAlive(), "已开始文件操作没有结束");
            } else require(workerPool.awaitQuiescence(10, TimeUnit.SECONDS), "已开始文件操作没有结束");
        }
    }

    private void asynchronousFiles() throws Exception {
        QueuedDomain ui = new QueuedDomain();
        LinkedBlockingQueue<PreparationGate> writes = new LinkedBlockingQueue<>();
        AtomicReference<PreparationGate> reading = new AtomicReference<>();
        ConfigKey<String> extra = new ConfigKey<>("other", new ConfigCodec<String>() {
            public String read(Object value, String path) {
                PreparationGate gate = reading.get();
                if (gate != null) gate.enter(ui.owner);
                return ConfigCodecs.STRING.read(value, path);
            }
            public Object write(String value) {
                PreparationGate gate = writes.poll();
                if (gate != null) gate.enter(ui.owner);
                return value;
            }
            public String snapshot(String value) { return ConfigCodecs.STRING.snapshot(value); }
        }, "初值");
        ConfigKey<List<String>> fonts = FontCandidateConfiguration.key(builtins);
        ConfigSchema schema = ConfigSchema.of(fonts, ThemeConfiguration.KEY, extra);
        Path file = Files.createTempFile("uilib-settings-async-", ".yaml");
        onClose(() -> Files.deleteIfExists(file));
        String initial = "# 其他模块注释\nother: 初值 # 行尾说明\nfont:\n  candidates: [builtin:" + builtins.get(0).id + "]\nui: {theme: light}\n";
        Files.write(file, initial.getBytes(StandardCharsets.UTF_8));
        ConfigDocument loaded = ConfigDocument.read(schema, file);
        ConfigSession[] config = new ConfigSession[1];
        SettingsConfiguration[] settings = new SettingsConfiguration[1];
        SettingsModel[] model = new SettingsModel[1];
        Owner[] page = new Owner[2];
        List<String> observed = new ArrayList<>();
        int[] resourceReloads = {0};
        ui.run(() -> {
            config[0] = new ConfigSession(ui.domain, schema);
            config[0].publish(loaded);
            settings[0] = new SettingsConfiguration(ui.domain, config[0], fonts, null, file,
                    FontCandidateConfiguration.defaults(builtins), values -> {}, () -> {
                        require(Thread.currentThread() == ui.owner, "资源发布离开所属线程");
                        resourceReloads[0]++;
                    });
            page[0] = ui.domain.owner();
            model[0] = new SettingsModel(settings[0], page[0]);
            page[1] = ui.domain.owner();
            ui.domain.effect(page[1], () -> {
                require(Thread.currentThread() == ui.owner, "配置通知离开所属线程");
                observed.add(config[0].get(extra) + ":" + config[0].get(ThemeConfiguration.KEY) + ":" + config[0].get(fonts));
            });
        });
        onClose(() -> ui.run(() -> { page[0].dispose(); page[1].dispose(); }));
        PreparationGate firstWrite = new PreparationGate(), secondWrite = new PreparationGate();
        writes.add(firstWrite); writes.add(secondWrite);
        onClose(() -> { firstWrite.release.countDown(); secondWrite.release.countDown(); });
        CompletableFuture<Void>[] requests = new CompletableFuture[3];
        ui.run(() -> {
            config[0].set(ThemeConfiguration.KEY, ThemeMode.DARK);
            requests[0] = model[0].save();
        });
        firstWrite.entered.get(10, TimeUnit.SECONDS);
        ui.run(() -> {
            require(settings[0].persisting().get(), "在途保存没有真实 pending");
            config[0].set(fonts, Collections.singletonList("builtin:" + builtins.get(1).id));
            requests[1] = model[0].save();
            model[0].setTheme(ThemeMode.LIGHT);
        });
        require(!secondWrite.entered.isDone(), "连续文件请求并发写入同一来源");
        firstWrite.release.countDown();
        ui.await(requests[0]);
        secondWrite.entered.get(10, TimeUnit.SECONDS);
        ui.run(() -> {
            require(settings[0].saved().get().theme() == ThemeMode.DARK
                    && settings[0].saved().get().candidates().equals(Collections.singletonList("builtin:" + builtins.get(0).id)),
                    "旧保存错误地把后来配置记为已落盘");
            require(model[0].dirty().get() && model[0].message().get().isEmpty(), "旧完成覆盖后续编辑反馈");
            page[0].dispose();
            page[0] = ui.domain.owner();
            model[0] = new SettingsModel(settings[0], page[0]);
            model[0].setTheme(ThemeMode.LIGHT);
        });
        ConfigDocument firstDisk = ConfigDocument.read(schema, file);
        require(firstDisk.get(fonts).equals(Collections.singletonList("builtin:" + builtins.get(0).id)), "首个真实文件值不是捕获快照");
        secondWrite.release.countDown();
        ui.await(requests[1]);
        ui.run(() -> require(!model[0].unsaved().get() && model[0].dirty().get(), "页面关闭后真实保存基线未完成或覆盖重开草稿"));
        String disk = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        require(disk.contains("其他模块注释") && disk.contains("行尾说明"), "后台保存丢失其他模块注释");
        Files.write(file, initial.replace("初值", "文件新值").getBytes(StandardCharsets.UTF_8));
        PreparationGate reload = new PreparationGate();
        reading.set(reload);
        onClose(() -> reload.release.countDown());
        ui.run(() -> requests[2] = model[0].reload());
        reload.entered.get(10, TimeUnit.SECONDS);
        ui.run(() -> {
            require(config[0].get(extra).equals("初值"), "后台读取未全量验证就发布");
            model[0].add("builtin:" + builtins.get(2).id);
        });
        reload.release.countDown();
        ui.await(requests[2]);
        ui.run(() -> {
            require(config[0].get(extra).equals("文件新值") && config[0].get(ThemeConfiguration.KEY) == ThemeMode.LIGHT,
                    "重载没有发布包含其他模块的完整配置");
            require(model[0].sources().get().size() == 2 && model[0].dirty().get(), "迟到重载覆盖后来编辑");
            require(!model[0].unsaved().get() && !settings[0].persisting().get(), "重载后的真实文件基线或 pending 不准确");
        });
        require(resourceReloads[0] == 1 && observed.get(observed.size() - 1).startsWith("文件新值:LIGHT:"), "全量通知或资源发布未完成");
        reading.set(null);
        PreparationGate closing = new PreparationGate();
        writes.add(closing);
        onClose(() -> closing.release.countDown());
        ui.run(() -> {
            config[0].set(ThemeConfiguration.KEY, ThemeMode.DARK);
            requests[0] = settings[0].save();
        });
        closing.entered.get(10, TimeUnit.SECONDS);
        int notifications = observed.size();
        ui.run(() -> {
            settings[0].close();
            require(requests[0].isCompletedExceptionally(), "宿主关闭后文件 future 永久等待已停止的域");
            try { settings[0].save(); throw new AssertionError("关闭后允许新保存"); }
            catch (IllegalStateException expected) {}
            try { settings[0].apply(Collections.emptyList(), false); throw new AssertionError("关闭后允许新应用"); }
            catch (IllegalStateException expected) {}
        });
        closing.release.countDown();
        closing.awaitCompletion();
        require(ui.queue.isEmpty() && observed.size() == notifications, "宿主关闭后后台仍递交或发布配置");
        ConfigDocument afterClose = ConfigDocument.read(schema, file);
        require(afterClose.get(ThemeConfiguration.KEY) == ThemeMode.DARK, "宿主关闭中断已开始的真实保存");
    }

    private void asynchronousValidation() throws Exception {
        QueuedDomain ui = new QueuedDomain();
        ConfigKey<List<String>> fonts = FontCandidateConfiguration.key(builtins);
        LinkedBlockingQueue<CompletableFuture<Void>> validations = new LinkedBlockingQueue<>();
        ConfigSession[] session = new ConfigSession[1];
        SettingsModel[] model = new SettingsModel[1];
        Owner[] owner = new Owner[1];
        Path file = Files.createTempFile("uilib-validation-", ".yaml");
        onClose(() -> Files.deleteIfExists(file));
        ui.run(() -> {
            session[0] = new ConfigSession(ui.domain, ConfigSchema.of(fonts, ThemeConfiguration.KEY));
            SettingsConfiguration configuration = SettingsConfiguration.withAsyncValidation(ui.domain, session[0], fonts,
                    null, file, FontCandidateConfiguration.defaults(builtins), values -> {
                        CompletableFuture<Void> preparation = new CompletableFuture<>();
                        validations.add(preparation);
                        return preparation;
                    }, () -> {});
            owner[0] = ui.domain.owner();
            model[0] = new SettingsModel(configuration, owner[0]);
        });
        onClose(() -> ui.run(owner[0]::dispose));
        CompletableFuture<Void>[] result = new CompletableFuture[1];
        ui.run(() -> { model[0].setTheme(ThemeMode.DARK); result[0] = model[0].apply(); });
        CompletableFuture<Void> stale = validations.take();
        ui.run(() -> model[0].setTheme(ThemeMode.LIGHT));
        CompletableFuture.runAsync(() -> stale.complete(null)).get(10, TimeUnit.SECONDS);
        ui.await(result[0]);
        ui.run(() -> {
            require(session[0].get(ThemeConfiguration.KEY) == ThemeMode.LIGHT, "迟到校验发布过期草稿");
            model[0].setTheme(ThemeMode.DARK);
            result[0] = model[0].apply();
        });
        CompletableFuture<Void> closed = validations.take();
        ui.run(owner[0]::dispose);
        closed.complete(null);
        ui.await(result[0]);
        ui.run(() -> {
            require(session[0].get(ThemeConfiguration.KEY) == ThemeMode.LIGHT, "关闭页面仍发布等待中的应用");
            owner[0] = ui.domain.owner();
            model[0] = new SettingsModel(model[0].configuration(), owner[0]);
            model[0].setTheme(ThemeMode.DARK);
            result[0] = model[0].apply();
        });
        validations.take().completeExceptionally(new IllegalStateException("字体准备失败"));
        ui.await(result[0]);
        ui.run(() -> {
            require(session[0].get(ThemeConfiguration.KEY) == ThemeMode.LIGHT && model[0].fontError().get().contains("字体准备失败"), "校验失败未保留草稿和原值");
            result[0] = model[0].apply();
        });
        CompletableFuture<Void> current = validations.take();
        CompletableFuture.runAsync(() -> current.complete(null)).get(10, TimeUnit.SECONDS);
        ui.await(result[0]);
        ui.run(() -> require(session[0].get(ThemeConfiguration.KEY) == ThemeMode.DARK && !model[0].dirty().get(), "有效请求没有在原域发布"));
    }

    private void persistingObserverFailure() throws Exception {
        Fixture f = new Fixture(false, false);
        IllegalStateException expected = new IllegalStateException("忙碌观察者失败");
        Owner[] observer = new Owner[1];
        f.run(() -> {
            observer[0] = f.domain.owner();
            Observation watch = f.domain.observe(observer[0], () -> f.configuration.persisting().get(), () -> {
                if (f.configuration.persisting().get()) throw expected;
            });
            watch.refresh();
        });
        try {
            f.run(() -> {
                f.session.set(ThemeConfiguration.KEY, ThemeMode.DARK);
                f.configuration.save();
            });
            throw new AssertionError("同步忙碌观察者异常没有继续原错误通道");
        } catch (RuntimeException failure) {
            require(failure == expected, "忙碌观察者原错被替换");
        }
        CompletableFuture<Void>[] next = new CompletableFuture[1];
        f.run(() -> {
            observer[0].dispose();
            f.session.set(ThemeConfiguration.KEY, ThemeMode.LIGHT);
            next[0] = f.configuration.save();
        });
        next[0].get(10, TimeUnit.SECONDS);
        ConfigDocument written = ConfigDocument.read(ConfigSchema.of(f.fonts, f.profile, ThemeConfiguration.KEY), f.file);
        require(written.get(ThemeConfiguration.KEY) == ThemeMode.LIGHT, "坏观察者释放后第二次保存没有真实落盘");
        f.run(() -> require(!f.configuration.persisting().get()
                && f.configuration.saved().get().theme() == ThemeMode.LIGHT, "保存链没有结清真实请求和busy基线"));
    }

    private void publicApplyFailure() throws Exception {
        Fixture f = new Fixture(true, false);
        IllegalStateException expected = new IllegalStateException("公开应用资源Effect失败");
        Owner[] resource = new Owner[1];
        CompletableFuture<Void>[] applied = new CompletableFuture[1];
        try {
            f.run(() -> {
                resource[0] = f.domain.owner();
                f.domain.effect(resource[0], () -> {
                    if (!f.session.get(f.mc)) throw expected;
                });
                applied[0] = f.configuration.apply(f.configuration.current().candidates(), false, ThemeMode.DARK);
            });
        } catch (RuntimeException failure) {
            require(failure == expected, "域的既有错误通道丢失资源Effect原错");
        }
        try {
            applied[0].get(10, TimeUnit.SECONDS);
            throw new AssertionError("公开应用在顶层Effect失败前报告成功");
        } catch (java.util.concurrent.ExecutionException failure) {
            require(failure.getCause() == expected, "公开应用没有保留资源Effect原始失败");
        }
        f.run(() -> {
            require(!f.configuration.current().replace() && f.configuration.current().theme() == ThemeMode.DARK,
                    "资源失败撤销已经发布的配置");
            resource[0].dispose();
        });
    }

    private void explicitResourceFailure() throws Exception {
        Fixture f = new Fixture(true, false);
        try {
            Path missing = f.file.resolveSibling("missing-font.ttf");
            Files.deleteIfExists(missing);
            f.run(
                    () -> {
                        SettingsConfiguration.Snapshot live = f.configuration.current();
                        SettingsConfiguration.Snapshot saved = f.configuration.saved().get();
                        final RuntimeException[] raised = {null};
                        try {
                            f.configuration.updateResourcesOrThrow(
                                    () -> {
                                        try {
                                            Files.readAllBytes(missing);
                                        } catch (IOException failure) {
                                            raised[0] = new java.io.UncheckedIOException(failure);
                                            throw raised[0];
                                        }
                                    });
                            throw new AssertionError("显式资源入口吞掉原异常");
                        } catch (java.io.UncheckedIOException failure) {
                            require(failure == raised[0], "异常继续原资源通道，不能另包改变身份");
                        }
                        require(
                                f.configuration.resourceError().get().contains("missing-font.ttf"),
                                "实际资源失败没有写共享诊断");
                        require(
                                f.configuration.current().equals(live)
                                        && f.configuration.saved().get().equals(saved),
                                "资源失败回滚了配置或文件基线");
                        f.configuration.updateResourcesOrThrow(() -> {});
                        require(f.configuration.resourceError().get().isEmpty(), "实际资源更新成功后未清诊断");
                    });
        } finally {
            f.run(f.owner::dispose);
        }
    }

    private void themeLifecycle() throws Exception {
        for (boolean minecraft : Arrays.asList(false, true)) {
            Fixture f = new Fixture(minecraft, false);
            String original = f.disk();
            f.run(
                    () -> {
                        require(
                                f.configuration.defaultTheme() == ThemeMode.LIGHT
                                        && f.model.theme().get() == ThemeMode.LIGHT,
                                "缺少主题字段采用正式声明默认浅色");
                        require(
                                new SettingsConfiguration.Snapshot(Collections.emptyList(), false)
                                                .theme()
                                        == ThemeMode.LIGHT,
                                "旧快照构造默认浅色");
                        f.model.setTheme(ThemeMode.DARK);
                        require(
                                f.model.theme().get().isDark()
                                        && f.model.dirty().get()
                                        && !f.model.unsaved().get(),
                                "主题预览纳入草稿但不冒充已发布或未保存");
                        require(
                                f.session.get(ThemeConfiguration.KEY) == ThemeMode.LIGHT,
                                "主题编辑不发布全局值");
                        f.model.setTheme(ThemeMode.LIGHT);
                        require(!f.model.dirty().get(), "主题改回基线恢复无变化");
                        f.model.setTheme(ThemeMode.DARK);
                        f.model.discard();
                        require(
                                f.model.theme().get() == ThemeMode.LIGHT && !f.model.dirty().get(),
                                "放弃恢复当前主题");
                        f.model.setTheme(ThemeMode.DARK);
                    });
            f.save();
            require(f.disk().equals(original), "未应用主题不能保存");
            f.apply();
            f.run(
                    () -> {
                        require(
                                f.configuration.current().theme() == ThemeMode.DARK
                                        && !f.model.dirty().get()
                                        && f.model.unsaved().get(),
                                "应用主题发布全局且准确标记未保存");
                        f.model.setTheme(ThemeMode.LIGHT);
                        f.model.discard();
                        require(
                                f.model.theme().get() == ThemeMode.DARK && f.model.unsaved().get(),
                                "放弃不撤销已应用主题");
                        f.configuration.apply(
                                f.configuration.current().candidates(),
                                f.configuration.current().replace());
                        require(f.configuration.current().theme() == ThemeMode.DARK, "旧应用接口保留当前主题");
                    });
            require(f.disk().equals(original), "应用主题不落盘");
            f.save();
            f.run(
                    () -> {
                        require(
                                !f.model.unsaved().get()
                                        && f.configuration.saved().get().theme() == ThemeMode.DARK,
                                "保存主题更新工作区基线");
                        ConfigSession reopened =
                                new ConfigSession(
                                        f.domain,
                                        minecraft
                                                ? ConfigSchema.of(
                                                        f.fonts,
                                                        f.mc,
                                                        f.profile,
                                                        ThemeConfiguration.KEY)
                                                : ConfigSchema.of(
                                                        f.fonts,
                                                        f.profile,
                                                        ThemeConfiguration.KEY));
                        try {
                            reopened.load(f.file);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                        require(
                                reopened.get(ThemeConfiguration.KEY) == ThemeMode.DARK,
                                "新配置会话从真实文件读取深色");
                        f.model.setTheme(ThemeMode.LIGHT);
                    });
            f.reload();
            f.run(
                    () ->
                            require(
                                    f.model.theme().get() == ThemeMode.DARK
                                            && !f.model.dirty().get(),
                                    "显式加载同步主题草稿与文件值"));
        }
    }

    private void themeExternal() throws Exception {
        Fixture f = new Fixture(false, false);
        f.run(() -> f.session.set(ThemeConfiguration.KEY, ThemeMode.DARK));
        f.run(
                () -> {
                    require(
                            f.model.theme().get() == ThemeMode.DARK && !f.model.dirty().get(),
                            "无草稿同步外部主题");
                    f.model.add(f.declaration(1));
                });
        f.run(() -> f.session.set(ThemeConfiguration.KEY, ThemeMode.LIGHT));
        f.run(
                () -> {
                    require(
                            f.model.theme().get() == ThemeMode.DARK
                                    && f.model.sources().get().size() == 2,
                            "外部主题保留整个已修改草稿");
                    require(f.model.externalChanged().get(), "主题参与外部变化提示");
                    f.model.setTheme(ThemeMode.LIGHT);
                    require(
                            f.model.dirty().get() && f.model.externalChanged().get(),
                            "外部变化后仍可编辑主题");
                    f.model.discard();
                    require(
                            f.model.theme().get() == ThemeMode.LIGHT
                                    && !f.model.dirty().get()
                                    && !f.model.externalChanged().get(),
                            "放弃同步外部主题并清理提示");
                    f.model.setTheme(ThemeMode.DARK);
                });
        f.run(() -> f.session.set(f.fonts, Collections.singletonList(f.declaration(2))));
        f.run(
                () -> {
                    require(
                            f.model.theme().get() == ThemeMode.DARK
                                    && f.model
                                            .sources()
                                            .get()
                                            .get(0)
                                            .declaration()
                                            .equals(f.declaration(0)),
                            "只有主题草稿时外部字体变化仍保留完整草稿");
                    require(f.model.externalChanged().get(), "主题草稿可观察外部字体变化");
                });
        f.apply();
        f.run(
                () ->
                        require(
                                f.configuration.current().theme() == ThemeMode.DARK
                                        && !f.model.externalChanged().get()
                                        && !f.model.dirty().get(),
                                "外部变化后可明确再次应用全部草稿"));
    }

    private void applyAndSave() throws Exception {
        Fixture f = new Fixture(true, false);
        String original = f.disk();
        List<String> notifications = new ArrayList<>();
        f.run(
                () -> {
                    require(
                            f.configuration
                                    .saved()
                                    .get()
                                    .candidates()
                                    .equals(Collections.singletonList(f.declaration(0))),
                            "saved取实际loaded值而非模块默认");
                    f.domain.effect(
                            f.owner,
                            () ->
                                    notifications.add(
                                            f.session.get(f.fonts)
                                                    + "/"
                                                    + f.session.get(f.mc)
                                                    + "/"
                                                    + f.session.get(ThemeConfiguration.KEY)));
                    f.model.add(f.declaration(1));
                    f.model.setReplace(false);
                    f.model.setTheme(ThemeMode.DARK);
                    require(f.model.dirty().get() && !f.model.unsaved().get(), "草稿不自动生效");
                    require(f.model.saveDisabledReason().get().equals("先应用更改"), "未应用保存禁用原因");
                });
        f.save();
        require(f.disk().equals(original), "有草稿时保存不能落盘");
        f.apply();
        f.run(
                () -> {
                    require(!f.model.dirty().get() && f.model.unsaved().get(), "应用后未保存");
                    require(!f.model.externalChanged().get(), "自己的发布不报外部变化");
                    require(!f.session.get(f.mc), "whole page包含MC字段");
                });
        require(f.disk().equals(original), "应用不写文件");
        require(
                notifications.size() == 2 && notifications.get(1).endsWith("/false/DARK"),
                "字体、MC与主题在同一批次通知");
        f.apply();
        require(notifications.size() == 2, "无变化应用不通知");
        f.save();
        f.run(() -> require(!f.model.unsaved().get(), "成功保存更新基线"));
        String saved = f.disk();
        require(
                saved.contains("用户候选说明") && saved.contains("用户其他模块说明") && saved.contains("用户组说明"),
                "页面保存保留用户注释");
        f.run(
                () -> {
                    f.model.setReplace(true);
                    f.model.add(f.declaration(2));
                });
        f.reload();
        f.run(
                () -> {
                    require(
                            !f.model.dirty().get()
                                    && !f.model.unsaved().get()
                                    && !f.model.replace().get(),
                            "显式加载同步全部基线与草稿");
                    require(
                            f.session.get(f.profile).get("groups").equals(Arrays.asList("甲", "乙")),
                            "保存仍保留其他模块复杂字段");
                });
    }

    private void identityAndEmpty() throws Exception {
        Fixture f = new Fixture(false, false);
        f.run(
                () -> {
                    f.model.add(f.declaration(0));
                    Source first = f.model.sources().get().get(0);
                    Source duplicate = f.model.sources().get().get(1);
                    require(first.identity() != duplicate.identity(), "合法重复来源有独立身份");
                    f.model.move(duplicate, -1);
                    require(
                            f.model.sources().get().get(0) == duplicate
                                    && f.model.sources().get().get(1) == first,
                            "移动保持实际对象身份");
                    f.model.remove(first);
                    require(!f.model.dirty().get(), "删回同值恢复无变化，身份不参与dirty");
                    f.model.setReplace(true);
                    require(!f.model.replace().get(), "非MC无可修改开关");
                    f.model.remove(duplicate);
                    require(f.model.dirty().get(), "空列表是可应用草稿");
                    try {
                        f.model.sources().get().clear();
                        throw new AssertionError("行列表可修改");
                    } catch (UnsupportedOperationException expected) {
                    }
                    try {
                        f.configuration.current().candidates().clear();
                        throw new AssertionError("快照可修改");
                    } catch (UnsupportedOperationException expected) {
                    }
                });
        f.apply();
        f.save();
        f.reload();
        f.run(
                () -> {
                    require(
                            f.configuration.current().candidates().isEmpty()
                                    && f.model.sources().get().isEmpty(),
                            "空候选真实往返不补默认");
                    f.model.resetFonts();
                    require(
                            f.model.dirty().get()
                                    && f.model.sources().get().size()
                                            == f.fonts.defaultValue().size(),
                            "恢复默认只改草稿");
                    require(f.configuration.current().candidates().isEmpty(), "恢复默认不发布");
                    f.model.discard();
                    require(
                            !f.model.dirty().get() && f.model.sources().get().isEmpty(),
                            "放弃恢复live空值");
                });
    }

    private void externalAndLifecycle() throws Exception {
        Fixture f = new Fixture(true, false);
        f.run(() -> f.session.set(f.fonts, Collections.singletonList(f.declaration(1))));
        f.run(
                () -> {
                    require(
                            f.model.sources().get().get(0).declaration().equals(f.declaration(1))
                                    && !f.model.dirty().get(),
                            "pristine自动同步外部live");
                    f.model.add(f.declaration(2));
                });
        f.run(() -> f.session.set(f.mc, false));
        f.run(
                () -> {
                    require(
                            f.model.sources().get().size() == 2 && f.model.replace().get(),
                            "外部变化保留整个dirty草稿");
                    require(f.model.externalChanged().get(), "外部变化提示可读");
                    f.model.discard();
                    require(
                            !f.model.replace().get()
                                    && !f.model.dirty().get()
                                    && !f.model.externalChanged().get(),
                            "放弃同步当前生效值");
                    f.model.add(f.declaration(2));
                });
        f.apply();
        f.run(
                () -> {
                    require(f.model.unsaved().get(), "发布未保存");
                    f.owner.dispose();
                    f.owner = f.domain.owner();
                    f.model = new SettingsModel(f.configuration, f.owner);
                    require(f.model.unsaved().get() && !f.model.dirty().get(), "重开不将live冒充saved");
                });
        List<List<Source>> old = new ArrayList<>();
        f.run(
                () -> {
                    old.add(f.model.sources().get());
                    f.owner.dispose();
                });
        f.run(() -> f.session.set(f.fonts, Collections.emptyList()));
        f.run(() -> require(f.model.sources().get() == old.get(0), "释放后结束本页同步，不释放独立配置"));
    }

    private void validation() throws Exception {
        Fixture f = new Fixture(true, true);
        f.run(
                () -> {
                    f.model.add("builtin:missing");
                    require(
                            !f.model.fontError().get().isEmpty() && !f.model.dirty().get(),
                            "非法标识拒绝并定位字体区");
                    f.model.add("file: ");
                    require(!f.model.fontError().get().isEmpty(), "空路径拒绝");
                    f.model.add("file:" + f.file.resolveSibling("不存在的字体.ttf"));
                    require(f.model.fontError().get().contains("不存在的字体.ttf"), "真实来源读取失败保留具体路径与原因");
                    f.model.add(f.declaration(1));
                    f.model.setReplace(false);
                    f.model.setTheme(ThemeMode.DARK);
                });
        f.apply();
        f.run(
                () -> {
                    require(
                            f.model.dirty().get()
                                    && f.model.sources().get().size() == 2
                                    && f.model.theme().get() == ThemeMode.DARK,
                            "校验失败保留包括主题的完整草稿");
                    require(
                            f.session
                                            .get(f.fonts)
                                            .equals(Collections.singletonList(f.declaration(0)))
                                    && f.session.get(f.mc)
                                    && f.session.get(ThemeConfiguration.KEY) == ThemeMode.LIGHT,
                            "MC字段拒绝时字体与主题也不发布");
                    require(
                            f.model.fontError().get().contains("mc.replaceVanillaFont"),
                            "全字段诊断保留实际声明路径");
                    Map<ConfigKey<?>, Object> updates = new LinkedHashMap<>();
                    updates.put(f.fonts, Collections.emptyList());
                    updates.put(new ConfigKey<>("foreign", ConfigCodecs.STRING, ""), "非法外键");
                    try {
                        f.session.setAll(updates);
                        throw new AssertionError("接受外键");
                    } catch (IllegalArgumentException expected) {
                    }
                    require(
                            f.session
                                    .get(f.fonts)
                                    .equals(Collections.singletonList(f.declaration(0))),
                            "未知键不部分发布");
                    f.model.setTheme(ThemeMode.LIGHT);
                    require(
                            f.model.fontError().get().isEmpty() && f.model.dirty().get(),
                            "应用错误不阻断继续编辑主题");
                    f.model.setReplace(true);
                    f.model.setTheme(ThemeMode.DARK);
                });
        f.apply();
        f.run(
                () ->
                        require(
                                f.configuration.current().theme() == ThemeMode.DARK
                                        && !f.model.dirty().get(),
                                "修正拒绝字段后仍可应用主题"));
    }

    private void sourceDisappears() throws Exception {
        Fixture f = new Fixture(true, false);
        Path source = Files.createTempFile("uilib-settings-font-", ".otf");
        onClose(() -> Files.deleteIfExists(source));
        try (java.io.InputStream input =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(
                                builtins.get(0).origin.substring("classpath:".length()))) {
            if (input == null) throw new IOException("实际内置字体资源缺失");
            Files.copy(input, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Source[] added = new Source[1];
        f.run(
                () -> {
                    f.model.add("file:" + source);
                    require(f.model.fontError().get().isEmpty(), "真实可读字体来源可加入草稿");
                    added[0] = f.model.sources().get().get(1);
                    f.model.setReplace(false);
                });
        Files.delete(source);
        f.apply();
        f.run(
                () -> {
                    require(
                            f.model.dirty().get() && f.model.sources().get().get(1) == added[0],
                            "应用时来源失效保留草稿与行身份");
                    require(f.model.fontError().get().contains(source.toString()), "来源错误保留实际字体路径");
                    require(
                            f.session.get(f.mc) && f.session.get(f.fonts).size() == 1,
                            "来源失败不发布任何所属字段");
                });
    }

    private void fileErrors() throws Exception {
        Fixture f = new Fixture(true, false);
        f.run(() -> f.model.setReplace(false));
        f.apply();
        Files.delete(f.file);
        Files.createDirectory(f.file);
        f.save();
        f.run(
                () -> {
                    require(
                            f.model.unsaved().get()
                                    && f.model.fileError().get().contains(f.file.toString()),
                            "真实写入失败有准确路径，仍未保存");
                    require(f.model.message().get().isEmpty(), "保存失败不伪成功");
                    f.model.add(f.declaration(1));
                });
        f.reload();
        f.run(
                () -> {
                    require(
                            f.model.dirty().get() && f.model.sources().get().size() == 2,
                            "真实读取失败保留草稿");
                    require(
                            f.model.fileError().get().contains(f.file.toString())
                                    && f.model.message().get().isEmpty(),
                            "重载失败只在文件区呈现");
                });
    }

    private void reloadResources() throws Exception {
        int[] callbacks = {0};
        boolean[] fail = {false};
        Fixture f =
                new Fixture(
                        true,
                        false,
                        () -> {
                            callbacks[0]++;
                            if (fail[0]) throw new IllegalStateException("显式资源重载失败");
                        });
        String initial = f.disk();
        List<List<String>> notifications = new ArrayList<>();
        f.run(() -> f.domain.effect(f.owner, () -> notifications.add(f.session.get(f.fonts))));
        f.reload();
        require(callbacks[0] == 1 && notifications.size() == 1, "同值load无值通知但仍显式刷新资源");
        Files.write(f.file, "font: [非法字段结构]".getBytes(StandardCharsets.UTF_8));
        f.reload();
        require(callbacks[0] == 1, "文件load校验失败不执行资源回调");
        f.run(
                () ->
                        require(
                                f.configuration.saved().get().equals(f.configuration.current()),
                                "load拒绝保持原保存事实"));
        Files.write(
                f.file,
                initial.replace("replaceVanillaFont: true", "replaceVanillaFont: false")
                        .getBytes(StandardCharsets.UTF_8));
        Source[] original = new Source[1];
        f.run(
                () -> {
                    f.model.add(f.declaration(1));
                    original[0] = f.model.sources().get().get(1);
                });
        fail[0] = true;
        f.reload();
        require(callbacks[0] == 2, "成功load后执行资源回调");
        f.run(
                () -> {
                    require(
                            !f.configuration.current().replace()
                                    && !f.configuration.saved().get().replace(),
                            "资源失败仍保留loaded与saved事实");
                    require(!f.model.unsaved().get() && f.model.dirty().get(), "实际文件基线已更新，原草稿仍存在");
                    require(f.model.sources().get().get(1) == original[0], "资源回调失败不丢草稿身份");
                    require(
                            f.model.fileError().get().contains("显式资源重载失败")
                                    && f.model.fileError().get().contains(f.file.toString()),
                            "资源失败定位文件操作及原因");
                    require(f.model.message().get().isEmpty(), "资源回调失败不显示重载成功");
                });
    }

    private void reloadObserverFailure() throws Exception {
        Fixture f = new Fixture(true, false);
        Files.write(
                f.file,
                f.disk()
                        .replace("replaceVanillaFont: true", "replaceVanillaFont: false")
                        .getBytes(StandardCharsets.UTF_8));
        RuntimeException expected = new UncheckedIOException(new IOException("load发布后资源观察失败"));
        Owner[] resource = new Owner[1];
        Source[] draft = new Source[1];
        f.run(
                () -> {
                    resource[0] = f.domain.owner();
                    f.domain
                            .observe(
                                    resource[0],
                                    () -> f.session.get(f.mc),
                                    () -> {
                                        throw expected;
                                    })
                            .refresh();
                    f.model.add(f.declaration(1));
                    draft[0] = f.model.sources().get().get(1);
                });
        CompletableFuture<Void>[] loading = new CompletableFuture[1];
        f.run(() -> loading[0] = f.configuration.reload());
        try {
            loading[0].get(10, TimeUnit.SECONDS);
            throw new AssertionError("load必须继续报告资源观察原错");
        } catch (java.util.concurrent.ExecutionException failure) {
            require(failure.getCause() == expected, "load资源异常被转换或替换");
        }
        f.run(() -> require(!f.configuration.current().replace() && !f.configuration.saved().get().replace(),
                "文件已成功载入时资源观察错误不能留下旧文件基线"));
        onClose(() -> f.domain.execute(resource[0]::dispose));
        f.run(
                () -> {
                    require(
                            !f.model.unsaved().get() && f.model.dirty().get(),
                            "文件事实准确，未应用草稿继续独立存在");
                    require(f.model.sources().get().get(1) == draft[0], "资源观察失败丢失草稿身份");
                });
    }

    private void resourceErrors() throws Exception {
        for (boolean deferred : Arrays.asList(false, true)) {
            Fixture f = new Fixture(true, false);
            Owner[] resource = new Owner[1];
            RuntimeException expected = new IllegalStateException(deferred ? "延后资源失败" : "同步资源失败");
            Source[] added = new Source[1];
            f.run(
                    () -> {
                        resource[0] = f.domain.owner();
                        if (deferred)
                            f.domain.effect(
                                    resource[0],
                                    () -> {
                                        if (f.session.get(f.fonts).size() > 1) throw expected;
                                    });
                        else {
                            Observation observation =
                                    f.domain.observe(
                                            resource[0],
                                            () -> f.session.get(f.fonts),
                                            () -> {
                                                throw expected;
                                            });
                            observation.refresh();
                        }
                        f.model.add(f.declaration(1));
                        added[0] = f.model.sources().get().get(1);
                        f.model.setReplace(false);
                        f.model.setTheme(ThemeMode.DARK);
                    });
            onClose(() -> f.domain.execute(resource[0]::dispose));
            try {
                f.apply();
            } catch (RuntimeException actual) {
                require(deferred && actual == expected, "延后异常保留原域通道与身份");
            }
            f.run(
                    () -> {
                        require(
                                f.session.get(f.fonts).size() == 2
                                        && !f.session.get(f.mc)
                                        && f.configuration.current().theme() == ThemeMode.DARK,
                                "资源错误发生后字体、MC和主题已完整发布");
                        require(f.model.sources().get().get(1) == added[0], "资源失败保留原草稿身份");
                        require(!f.model.dirty().get() && f.model.unsaved().get(), "状态按已发布事实比较");
                        require(
                                f.model.fontError().get().contains("配置已发布")
                                        && f.model
                                                .fontError()
                                                .get()
                                                .contains(expected.getMessage()),
                                "错误包含阶段与资源原因");
                        require(
                                !f.model.externalChanged().get()
                                        && f.model.message().get().isEmpty(),
                                "自己的失败发布不报外部、不伪成功");
                        resource[0].dispose();
                        f.model.setTheme(ThemeMode.LIGHT);
                        require(
                                f.model.fontError().get().isEmpty() && f.model.dirty().get(),
                                "资源异常不永久阻断修改主题");
                    });
            f.apply();
            f.run(
                    () ->
                            require(
                                    f.configuration.current().theme() == ThemeMode.LIGHT
                                            && !f.model.dirty().get(),
                                    "资源异常解除后可继续应用主题"));
        }
    }
}
