package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.*;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.render.batch.BatchingConfiguration;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.semantics.*;
import club.heiqi.uilib.ui.scene.text.font.*;
import club.heiqi.uilib.ui.settings.*;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 正式设置页面经原语义/快捷键入口消费真实配置文件，不以Gallery草稿替代应用保存。 */
public final class SettingsWorkspaceScenario extends TestApplication {
    @Override
    protected void runTests() throws Exception {
        scenario("异步添加失败保留输入且取消重开隔离旧完成", this::asynchronousAdd);
        scenario("正式设置单选字体仅改草稿且应用保存保留用户注释", this::fontSelection);
        scenario("路径拒绝保留原草稿与关联说明且模态返回按原确认", this::pathAndClose);
        scenario("分类与弹窗快速反向保留编辑身份且退出立即结束输入", this::motionLifecycle);
        scenario("弹窗退场绘制跟随弹簧且快速重开位置连续", this::motionPaint);
        scenario("宿主请求重载结束添加窗口所属的文件选择模态", this::pickerOwnership);
        scenario("宿主接管选项跨分类保留草稿且恢复真实默认", this::minecraftOption);
        scenario("性能开关真实点击跨分类应用与文件布尔往返", this::performanceOption);
        scenario("性能页面关闭释放Owner和旧动作且共享配置可重开", this::performanceLifecycle);
        scenario("旧schema不显示性能分类和合批开关", this::performanceUnavailable);
        scenario("同窗字体子视图的初始焦点与逐级返回焦点", this::editorFocus);
        scenario("外观分类的全局保存与宿主重载失败持续可见", this::operationFailure);
        scenario("共享模板外壳字段与正文同源预览且字体密度沿正式主题", this::previewTheme);
        scenario("窄屏共享分类选择器键鼠关闭和重复选择保留样本编辑身份", this::narrowNavigation);
        scenario("主题草稿背景清焦后ESC仍进入原关闭确认", this::escapeDirty);
        scenario("未改工作区无焦点与普通按钮焦点ESC直接关闭", this::escapeClean);
        scenario("已应用未保存工作区ESC确认且取消保留生效值", this::escapeUnsaved);
        scenario("ESC优先结束字体选择器与字体子视图再进入工作区确认", this::escapeNested);
    }

    final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final VectorPresentationScenario.Host host = new VectorPresentationScenario.Host();
        final List<FontSource> builtins =
                FontTextService.defaultSources(getClass().getClassLoader(), 1);
        final ConfigKey<List<String>> fonts = FontCandidateConfiguration.key(builtins);
        final Path file;
        ConfigSession config;
        SettingsConfiguration configuration;
        SettingsPage page;
        ScenePageSession session;
        Owner pageOwner;
        int refreshes, leaves;
        Signal<MotionPolicy> motion;
        Signal<SceneDensity> density;
        ScenePageContext context;

        Fixture() throws Exception {
            this(false);
        }

        final ConfigKey<Boolean> replace =
                new ConfigKey<>("mc.replaceVanillaFont", ConfigCodecs.BOOLEAN, true);

        Fixture(boolean minecraft) throws Exception { this(minecraft, null); }

        Fixture(boolean minecraft, java.util.function.Function<List<String>, CompletableFuture<Void>> asyncValidation) throws Exception {
            this(minecraft, asyncValidation, true);
        }

        Fixture(boolean minecraft, java.util.function.Function<List<String>, CompletableFuture<Void>> asyncValidation, boolean batching) throws Exception {
            file = Files.createTempFile("uilib-workspace-", ".yaml");
            onClose(() -> Files.deleteIfExists(file));
            host.width = 1280;
            host.height = 1100;
            domain.execute(
                    () -> {
                        motion = domain.signal(MotionPolicy.NORMAL);
                        density = domain.signal(SceneDensity.COMFORTABLE);
                        List<ConfigKey<?>> keys = new ArrayList<>();
                        keys.add(fonts);
                        keys.add(ThemeConfiguration.KEY);
                        if (minecraft) keys.add(replace);
                        if (batching) keys.add(BatchingConfiguration.KEY);
                        config = new ConfigSession(domain, ConfigSchema.of(keys.toArray(new ConfigKey<?>[0])));
                        try {
                            config.load(
                                    new StringReader(
                                            "# 用户字体说明\nfont:\n  candidates: [builtin:"
                                                    + builtins.get(0).id
                                                    + "] # 字体候选注释\n"));
                            config.save(file);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                        java.util.function.Consumer<List<String>> validate = values -> {
                                            try {
                                                FontCandidateConfiguration.resolve(
                                                        values,
                                                        builtins,
                                                        (path, index) ->
                                                                FontSource.file(
                                                                        "workspace-" + index,
                                                                        1,
                                                                        Paths.get(path),
                                                                        0,
                                                                        false,
                                                                        Collections.emptySet()));
                                            } catch (IOException failure) {
                                                throw new UncheckedIOException(failure);
                                            }
                                        };
                        configuration = asyncValidation == null
                                ? new SettingsConfiguration(domain, config, fonts, minecraft ? replace : null,
                                        file, FontCandidateConfiguration.defaults(builtins), validate, () -> refreshes++)
                                : SettingsConfiguration.withAsyncValidation(domain, config, fonts, minecraft ? replace : null,
                                        file, FontCandidateConfiguration.defaults(builtins), asyncValidation, () -> refreshes++);
                        page = new SettingsPage(configuration, () -> leaves++);
                    });
            session =
                    ScenePageSession.open(
                            domain,
                            host,
                            host,
                            context -> {
                                this.context = context;
                                pageOwner = context.scope();
                                context.motionPolicy(motion);
                                context.density(density);
                                page.build(context);
                            });
            session.frame();
        }

        SceneSemanticSnapshot.Entry entry(SceneSemantics.Role role, String name) {
            for (SceneSemanticSnapshot.Entry e : host.semantics.entries)
                if (e.values.role == role && name.equals(e.values.name)) return e;
            throw new AssertionError("缺少正式工作区控件 " + role + " " + name);
        }

        SceneSemanticRequest.Result request(SceneSemanticRequest request) {
            CompletableFuture<SceneSemanticRequest.Result> result = session.semantic(request);
            session.frame();
            return result.join();
        }

        void action(SceneSemantics.Role role, String name, SceneSemantics.Action action) {
            require(
                    request(SceneSemanticRequest.of(entry(role, name).id, action))
                            == SceneSemanticRequest.Result.APPLIED,
                    "正式动作拒绝 " + name);
            awaitFiles();
        }

        void click(SceneSemantics.Role role, String name) {
            SceneSemanticSnapshot.Entry target = entry(role, name);
            require(target.rangeInteractive && !target.offscreen && !target.actions.isEmpty(), "点击目标不可交互 " + name);
            int x = target.box.x + target.box.width / 2, y = target.box.y + target.box.height / 2;
            require(target.clip == null || target.clip.contains(x, y), "点击目标被裁剪 " + name);
            session.pointer(ScenePointerEvent.down(0, x, y));
            session.pointer(ScenePointerEvent.up(0, x, y));
            session.frame();
            awaitFiles();
        }

        void awaitFiles() {
            CompletableFuture<Void> settled = new CompletableFuture<>();
            Owner[] waiting = new Owner[1];
            domain.execute(() -> {
                waiting[0] = domain.owner();
                Observation observation = domain.observe(waiting[0], () -> configuration.persisting().get(), () -> {
                    if (!configuration.persisting().get()) domain.deferCleanup(() -> settled.complete(null));
                });
                observation.refresh();
                if (!configuration.persisting().get()) domain.deferCleanup(() -> settled.complete(null));
            });
            try { settled.get(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception failure) { throw new AssertionError("文件操作未完成", failure); }
            finally { domain.execute(waiting[0]::dispose); }
            session.frame();
        }

        String disk() throws IOException {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        }

        List<String> current() {
            final List<String>[] result = new List[1];
            domain.execute(() -> result[0] = configuration.current().candidates());
            return result[0];
        }

        boolean replaced() {
            final boolean[] result = {false};
            domain.execute(() -> result[0] = configuration.current().replace());
            return result[0];
        }

        boolean aggressiveBatching() {
            final boolean[] result = {false};
            domain.execute(() -> result[0] = configuration.current().aggressiveBatching());
            return result[0];
        }

        void openAdd() {
            action(SceneSemantics.Role.BUTTON, "管理字体来源", SceneSemantics.Action.ACTIVATE);
            action(SceneSemantics.Role.BUTTON, "+ 添加来源", SceneSemantics.Action.ACTIVATE);
        }

        public void close() throws Exception {
            try {
                session.close();
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    private void asynchronousAdd() throws Exception {
        // 仅延迟字体准备这一昂贵外部边界；使用真实页面、字段提交、状态域和模态生命周期。
        java.util.concurrent.LinkedBlockingQueue<CompletableFuture<Void>> validation = new java.util.concurrent.LinkedBlockingQueue<>();
        try (Fixture f = new Fixture(false, values -> {
            CompletableFuture<Void> ready = new CompletableFuture<>();
            validation.add(ready);
            return ready;
        })) {
            f.openAdd();
            f.action(SceneSemantics.Role.RADIO, "本地字体文件", SceneSemantics.Action.SELECT);
            SceneSemanticSnapshot.Entry field = f.entry(SceneSemantics.Role.TEXT_FIELD, "字体文件路径");
            String entered = f.file.resolveSibling("等待校验.ttf").toString();
            require(f.request(SceneSemanticRequest.text(field.id, entered)) == SceneSemanticRequest.Result.APPLIED, "真实路径编辑拒绝");
            f.action(SceneSemantics.Role.BUTTON, "添加到末尾", SceneSemantics.Action.ACTIVATE);
            CompletableFuture<Void> refused = validation.poll(10, java.util.concurrent.TimeUnit.SECONDS);
            require(refused != null && f.entry(SceneSemantics.Role.TEXT_FIELD, "字体文件路径").rangeInteractive,
                    "后台校验前提前关闭了添加输入");
            refused.completeExceptionally(new IllegalArgumentException("异步字体文件拒绝"));
            f.session.frame();
            require(entered.equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "字体文件路径").values.value)
                    && visibleError(f, "添加来源失败："), "异步失败丢失原字段输入或实际错误");
            f.action(SceneSemantics.Role.BUTTON, "添加到末尾", SceneSemantics.Action.ACTIVATE);
            CompletableFuture<Void> changed = validation.poll(10, java.util.concurrent.TimeUnit.SECONDS);
            require(changed != null, "再次提交未进入实际校验");
            require(f.request(SceneSemanticRequest.text(field.id, entered + ".new")) == SceneSemanticRequest.Result.APPLIED, "等待期间不能继续编辑路径");
            changed.complete(null);
            f.session.frame();
            require((entered + ".new").equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "字体文件路径").values.value), "旧路径完成覆盖新编辑");
            f.action(SceneSemantics.Role.BUTTON, "取消", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "+ 添加来源", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "添加到末尾", SceneSemantics.Action.ACTIVATE);
            CompletableFuture<Void> closed = validation.poll(10, java.util.concurrent.TimeUnit.SECONDS);
            require(closed != null, "内置提交未校验");
            f.action(SceneSemantics.Role.BUTTON, "取消", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "+ 添加来源", SceneSemantics.Action.ACTIVATE);
            closed.complete(null);
            f.session.frame();
            require(f.entry(SceneSemantics.Role.BUTTON, "添加到末尾").rangeInteractive, "旧完成关闭了新添加视图");
            f.action(SceneSemantics.Role.BUTTON, "取消", SceneSemantics.Action.ACTIVATE);
            require(f.entry(SceneSemantics.Role.BUTTON, "恢复默认").rangeInteractive, "返回后缺少来源列表");
            int removals = 0;
            for (SceneSemanticSnapshot.Entry e : f.host.semantics.entries)
                if (e.values.role == SceneSemantics.Role.BUTTON && "移除".equals(e.values.name) && e.rangeInteractive) removals++;
            require(removals == 1, "取消或旧路径完成仍追加了草稿来源");
            f.action(SceneSemantics.Role.BUTTON, "+ 添加来源", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "添加到末尾", SceneSemantics.Action.ACTIVATE);
            CompletableFuture<Void> accepted = validation.poll(10, java.util.concurrent.TimeUnit.SECONDS);
            require(accepted != null, "有效添加没有准备任务");
            accepted.complete(null);
            f.session.frame();
            require(f.entry(SceneSemantics.Role.BUTTON, "+ 添加来源").rangeInteractive, "有效完成没有回来源列表");
            removals = 0;
            for (SceneSemanticSnapshot.Entry e : f.host.semantics.entries)
                if (e.values.role == SceneSemantics.Role.BUTTON && "移除".equals(e.values.name) && e.rangeInteractive) removals++;
            require(removals == 2 && f.current().size() == 1, "有效完成没有只追加一次草稿或隐式发布");
        }
    }

    private void fontSelection() throws Exception {
        try (Fixture f = new Fixture()) {
            require(f.builtins.size() > 1, "正式字体候选不足以检查选择");
            String original = f.disk();
            List<String> before = new ArrayList<>(f.current());
            SceneSemanticSnapshot.Entry sample = f.entry(SceneSemantics.Role.TEXT_AREA, "文字样本");
            require(
                    !sample.labelledBy.isEmpty() && sample.values.description.contains("当前生效字体"),
                    "真实样本未关联标签或字体事实说明");
            f.openAdd();
            require(
                    Boolean.TRUE.equals(f.entry(SceneSemantics.Role.RADIO, "内置来源").values.selected),
                    "来源类型未表达原内置选择");
            f.action(SceneSemantics.Role.SELECT, "内置字体", SceneSemantics.Action.EXPAND);
            SceneSemanticSnapshot.Entry second = null;
            for (SceneSemanticSnapshot.Entry e : f.host.semantics.entries)
                if (e.values.role == SceneSemantics.Role.OPTION
                        && Integer.valueOf(2).equals(e.values.index)
                        && e.rangeInteractive) {
                    second = e;
                    break;
                }
            require(second != null, "公共单选未呈现真实第二字体来源");
            require(
                    f.request(SceneSemanticRequest.of(second.id, SceneSemantics.Action.FOCUS))
                            == SceneSemanticRequest.Result.APPLIED,
                    "真实选项不能预览焦点");
            require(f.current().equals(before) && f.disk().equals(original), "选项预览改写配置或文件");
            require(
                    f.request(SceneSemanticRequest.of(second.id, SceneSemantics.Action.SELECT))
                            == SceneSemanticRequest.Result.APPLIED,
                    "选项确认拒绝");
            f.action(SceneSemantics.Role.BUTTON, "添加到末尾", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "完成字体编辑", SceneSemantics.Action.ACTIVATE);
            require(f.current().equals(before) && f.disk().equals(original), "来源编辑隐式应用或保存");
            require(f.entry(SceneSemantics.Role.BUTTON, "保存到文件").actions.isEmpty(), "未应用草稿仍可保存");
            f.action(SceneSemantics.Role.BUTTON, "应用全部更改", SceneSemantics.Action.ACTIVATE);
            require(
                    f.current()
                            .equals(
                                    Arrays.asList(
                                            before.get(0), "builtin:" + f.builtins.get(1).id)),
                    "应用未发布真实字体顺序：" + f.current());
            require(f.refreshes == 0, "应用错误地调用了仅属于显式文件加载的资源回调");
            require(f.disk().equals(original), "应用动作隐式落盘");
            SceneKeyEvent save =
                    new SceneKeyEvent(SceneKey.S, true, false, false, false, false, null);
            f.session.key(save);
            f.session.key(save.withPhase(SceneKeyEvent.Phase.UP));
            f.session.frame();
            f.awaitFiles();
            require(
                    f.disk().contains(f.builtins.get(1).id)
                            && f.disk().contains("用户字体说明")
                            && f.disk().contains("字体候选注释"),
                    "公共保存命令未保留真实候选或用户注释");
            require(f.entry(SceneSemantics.Role.BUTTON, "保存到文件").actions.isEmpty(), "保存后命令仍可执行");
            f.action(SceneSemantics.Role.BUTTON, "配置文件", SceneSemantics.Action.ACTIVATE);
            require(
                    Boolean.TRUE.equals(
                            f.entry(SceneSemantics.Role.BUTTON, "配置文件").values.selected),
                    "当前分类未表达选中状态");
            f.action(SceneSemantics.Role.BUTTON, "从文件重新加载", SceneSemantics.Action.ACTIVATE);
            require(f.refreshes == 1 && f.current().size() == 2, "同值显式加载未沿真实资源通道刷新");
        }
    }

    private void pickerOwnership() throws Exception {
        try (Fixture f = new Fixture()) {
            f.action(SceneSemantics.Role.BUTTON, "深色外观", SceneSemantics.Action.ACTIVATE);
            f.openAdd();
            f.action(SceneSemantics.Role.RADIO, "本地字体文件", SceneSemantics.Action.SELECT);
            f.action(SceneSemantics.Role.BUTTON, "浏览字体文件", SceneSemantics.Action.ACTIVATE);
            SceneSemanticSnapshot.Entry browse = f.entry(SceneSemantics.Role.BUTTON, "前往");
            require(browse.rangeInteractive, "文件浏览没有进入实际所属模态");
            f.page.requestClose();
            f.session.frame();
            require(
                    f.entry(SceneSemantics.Role.TEXT_FIELD, "字体文件路径").rangeInteractive,
                    "宿主关闭文件选择器跳过添加子视图");
            require(
                    f.request(SceneSemanticRequest.of(browse.id, SceneSemantics.Action.ACTIVATE))
                            != SceneSemanticRequest.Result.APPLIED,
                    "顶层文件选择器关闭后仍接受输入");
            f.action(SceneSemantics.Role.BUTTON, "浏览字体文件", SceneSemantics.Action.ACTIVATE);
            browse = f.entry(SceneSemantics.Role.BUTTON, "前往");
            f.page.requestReload();
            f.session.frame();
            require(
                    f.entry(SceneSemantics.Role.BUTTON, "继续编辑").rangeInteractive,
                    "文件选择器脱离父模态并遮挡宿主重载确认");
            require(
                    f.request(SceneSemanticRequest.of(browse.id, SceneSemantics.Action.ACTIVATE))
                            != SceneSemanticRequest.Result.APPLIED,
                    "父窗口退出后文件选择器仍接受输入");
            f.action(SceneSemantics.Role.BUTTON, "放弃更改并加载", SceneSemantics.Action.ACTIVATE);
            require(
                    f.request(SceneSemanticRequest.of(browse.id, SceneSemantics.Action.ACTIVATE))
                            != SceneSemanticRequest.Result.APPLIED,
                    "完成重载后旧文件选择器恢复输入");
            require(
                    f.entry(SceneSemantics.Role.BUTTON, "管理字体来源").rangeInteractive
                            && f.refreshes == 1,
                    "完成宿主重载后旧文件模态复活或资源未刷新");
        }
    }

    private PaintCommand managerTitle(Fixture f) {
        PaintCommand title = null;
        for (PaintCommand command : f.session.pipeline().current().paint.commands())
            if (command.kind == PaintCommand.Kind.TEXT
                    && "管理字体来源".equals(command.text.logicalText)
                    && (title == null || command.text.style.logicalEm > title.text.style.logicalEm))
                title = command;
        require(title != null, "正式绘制计划中缺少字体管理标题");
        return title;
    }

    private double paintY(PaintCommand command) {
        return command.transform.y(command.box.x, command.box.y);
    }

    private void motionPaint() throws Exception {
        try (Fixture f = new Fixture()) {
            f.action(SceneSemantics.Role.BUTTON, "管理字体来源", SceneSemantics.Action.ACTIVATE);
            double[] position = new double[3];
            f.domain.execute(
                    () -> {
                        f.session
                                .runtime()
                                .preparePresentation(
                                        f.session.runtime().inputNanos() + 2_000_000_000L);
                        position[0] = paintY(managerTitle(f));
                    });
            f.action(SceneSemantics.Role.BUTTON, "+ 添加来源", SceneSemantics.Action.ACTIVATE);
            f.domain.execute(
                    () -> {
                        f.session
                                .runtime()
                                .preparePresentation(
                                        f.session.runtime().inputNanos() + 80_000_000L);
                        PaintCommand exit = managerTitle(f);
                        position[1] = paintY(exit);
                        require(
                                position[1] > position[0] + 1,
                                "退场仅隐藏节点内部运动，保留画面没有沿弹簧移动：" + Arrays.toString(position));
                        require(
                                (exit.color >>> 24) > 0 && (exit.color >>> 24) < 255,
                                "退场绘制没有透明度中间值");
                    });
            f.session.escape(
                    () -> {
                        throw new AssertionError("添加窗口未消费ESC");
                    });
            f.session.frame();
            f.domain.execute(() -> position[2] = paintY(managerTitle(f)));
            require(
                    Math.abs(position[2] - position[1]) < 3,
                    "快速返回跳离正在显示的退出位置：" + Arrays.toString(position));
            System.out.println(
                    "SETTINGS_MOTION titleY="
                            + Arrays.toString(position)
                            + " exitPaintMoves=true reverseContinuous=true");
        }
    }

    private void motionLifecycle() throws Exception {
        try (Fixture f = new Fixture()) {
            String saved = f.disk();
            List<String> current = new ArrayList<>(f.current());
            SceneSemanticSnapshot.Entry sample = f.entry(SceneSemantics.Role.TEXT_AREA, "文字样本");
            require(
                    f.request(SceneSemanticRequest.text(sample.id, "保留样本编辑 Aa 123"))
                            == SceneSemanticRequest.Result.APPLIED,
                    "样本拒绝实际编辑");
            // 同页持续挂载避免重建编辑器；退场画面不能保留原输入资格。
            for (MotionPolicy policy : MotionPolicy.values()) {
                f.domain.execute(() -> f.motion.set(policy));
                f.session.frame();
                f.action(SceneSemantics.Role.BUTTON, "配置文件", SceneSemantics.Action.ACTIVATE);
                require(
                        f.request(SceneSemanticRequest.text(sample.id, "不应写入"))
                                != SceneSemanticRequest.Result.APPLIED,
                        "退出分类仍接受文字：" + policy);
                f.action(SceneSemantics.Role.BUTTON, "外观", SceneSemantics.Action.ACTIVATE);
                SceneSemanticSnapshot.Entry returned =
                        f.entry(SceneSemantics.Role.TEXT_AREA, "文字样本");
                require(
                        returned.id.equals(sample.id)
                                && "保留样本编辑 Aa 123".equals(returned.values.value),
                        "分类反向重建编辑身份或丢草稿：" + policy);
                f.openAdd();
                f.action(SceneSemantics.Role.RADIO, "本地字体文件", SceneSemantics.Action.SELECT);
                SceneSemanticSnapshot.Entry path =
                        f.entry(SceneSemantics.Role.TEXT_FIELD, "字体文件路径");
                f.session.escape(
                        () -> {
                            throw new AssertionError("添加来源没有消费ESC");
                        });
                f.session.frame();
                require(
                        f.request(SceneSemanticRequest.text(path.id, "不应写入"))
                                != SceneSemanticRequest.Result.APPLIED,
                        "退出添加窗口仍接受文字：" + policy);
                f.action(SceneSemantics.Role.BUTTON, "完成字体编辑", SceneSemantics.Action.ACTIVATE);
            }
            require(
                    f.current().equals(current)
                            && f.disk().equals(saved)
                            && f.refreshes == 0
                            && f.leaves == 0,
                    "动效切换隐式应用、保存、重载或离开");
        }
    }

    private void editorFocus() throws Exception {
        try (Fixture f = new Fixture()) {
            f.action(SceneSemantics.Role.BUTTON, "管理字体来源", SceneSemantics.Action.FOCUS);
            f.openAdd();
            require(
                    Boolean.TRUE.equals(f.entry(SceneSemantics.Role.SELECT, "内置字体").focused),
                    "添加子视图没有进入来源选择焦点");
            SceneKeyEvent escape =
                    new SceneKeyEvent(SceneKey.ESCAPE, false, false, false, false, false, null);
            f.session.key(escape);
            f.session.key(escape.withPhase(SceneKeyEvent.Phase.UP));
            f.session.frame();
            require(
                    Boolean.TRUE.equals(f.entry(SceneSemantics.Role.BUTTON, "+ 添加来源").focused),
                    "返回字体列表没有恢复添加触发点");
            f.action(SceneSemantics.Role.BUTTON, "移除", SceneSemantics.Action.ACTIVATE);
            require(f.current().size() == 1, "移除最后来源提前发布合法空草稿");
            f.action(SceneSemantics.Role.BUTTON, "完成字体编辑", SceneSemantics.Action.ACTIVATE);
            require(
                    Boolean.TRUE.equals(f.entry(SceneSemantics.Role.BUTTON, "管理字体来源").focused),
                    "字体编辑器关闭没有恢复外观触发点");
        }
    }

    private void previewTheme() throws Exception {
        try (Fixture f = new Fixture()) {
            f.domain.execute(() -> f.motion.set(MotionPolicy.OFF));
            String disk = f.disk();
            for (SceneDensity density : SceneDensity.values()) {
                f.domain.execute(() -> f.density.set(density)); f.session.frame();
                float[] em = new float[1];
                f.domain.execute(() -> em[0] = f.context.theme().get().textStyle.logicalEm);
                require(Float.compare(paintedText(f, "界面主题").text.style.logicalEm, em[0]) == 0,
                        "共享字段仍相对正式主题缩小一档");
                f.click(SceneSemantics.Role.BUTTON, "深色外观");
                f.domain.execute(() -> require(f.configuration.current().theme() == ThemeMode.LIGHT,
                        "本页主题预览提前发布全局配置"));
                int dark = new SceneAppearance.Palette(true).text;
                require(paintedText(f, "QZ UILib / 工作区设置").text.style.color == dark
                                && paintedText(f, "界面主题").text.style.color == dark
                                && paintedText(f, "有未应用的更改").text.style.color == dark,
                        "模板外壳、共享字段与反馈没有跟随同一本页主题");
                f.click(SceneSemantics.Role.BUTTON, "性能");
                require(paintedText(f, "激进合批").text.style.color == dark
                                && Float.compare(paintedText(f, "激进合批").text.style.logicalEm, em[0]) == 0,
                        "共享布尔字段的主题或字号与模板分裂");
                f.click(SceneSemantics.Role.BUTTON, "浅色模式");
                require(paintedText(f, "激进合批").text.style.color == new SceneAppearance.Palette(false).text
                                && f.disk().equals(disk), "页头快捷切换未回到同一草稿或隐式保存");
                f.click(SceneSemantics.Role.BUTTON, "外观");
                System.out.println("SETTINGS_SHARED_THEME density=" + density + " em=" + em[0]
                        + " previewOnly=true shellFieldBodyUnified=true");
            }
        }
    }

    private PaintCommand paintedText(Fixture f, String text) {
        PaintCommand[] result = new PaintCommand[1];
        f.domain.execute(() -> {
            for (PaintCommand command : f.session.pipeline().current().paint.commands())
                if (command.kind == PaintCommand.Kind.TEXT && text.equals(command.text.logicalText)) { result[0] = command; break; }
        });
        require(result[0] != null, "缺少实际文字绘制：" + text);
        return result[0];
    }

    private SceneSemanticSnapshot.Entry category(Fixture f) {
        for (SceneSemanticSnapshot.Entry entry : f.host.semantics.entries)
            if (entry.values.role == SceneSemantics.Role.SELECT && entry.values.description.contains("设置分类")) return entry;
        throw new AssertionError("缺少窄屏公共分类选择器");
    }

    private void click(Fixture f, SceneSemanticSnapshot.Entry target) {
        int x = target.box.x + target.box.width / 2, y = target.box.y + target.box.height / 2;
        require(target.clip == null || target.clip.contains(x, y), "分类目标在实际裁剪外");
        f.session.pointer(ScenePointerEvent.down(0, x, y));
        f.session.pointer(ScenePointerEvent.up(0, x, y)); f.session.frame();
    }

    private void narrowNavigation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.domain.execute(() -> f.motion.set(MotionPolicy.OFF));
            SceneSemanticSnapshot.Entry sample = f.entry(SceneSemantics.Role.TEXT_AREA, "文字样本");
            require(f.request(SceneSemanticRequest.text(sample.id, "窄屏保留的样本编辑")) == SceneSemanticRequest.Result.APPLIED,
                    "真实样本输入被拒绝");
            f.host.width = 600; f.host.height = 800; f.session.frame();
            click(f, category(f));
            click(f, f.entry(SceneSemantics.Role.OPTION, "配置文件"));
            f.entry(SceneSemantics.Role.BUTTON, "从文件重新加载");
            click(f, category(f));
            f.session.key(new SceneKeyEvent(SceneKey.HOME, false, false, false, false, false, null)); f.session.frame();
            f.session.escape(() -> { throw new AssertionError("分类弹层未消费 ESC"); }); f.session.frame();
            require("配置文件".equals(category(f).values.value), "ESC 提交了预览分类");
            click(f, category(f));
            f.session.key(new SceneKeyEvent(SceneKey.HOME, false, false, false, false, false, null));
            f.session.key(new SceneKeyEvent(SceneKey.ENTER, false, false, false, false, false, null)); f.session.frame();
            require(sample.id.equals(f.entry(SceneSemantics.Role.TEXT_AREA, "文字样本").id)
                            && "窄屏保留的样本编辑".equals(f.entry(SceneSemantics.Role.TEXT_AREA, "文字样本").values.value),
                    "重复分类选择重建样本或清空原编辑");
            click(f, category(f));
            f.session.key(new SceneKeyEvent(SceneKey.TAB, false, false, false, false, false, null)); f.session.frame();
            require(f.host.semantics.currentModal == null && "外观".equals(category(f).values.value),
                    "窄屏分类 Tab 关闭产生选择或遗留所属层");
            require(f.leaves == 0, "内层 ESC 或 Tab 关闭了共享设置页");
        }
    }

    private void clearFocusOnBackground(Fixture f) {
        f.session.pointer(ScenePointerEvent.down(0, 1, 1));
        f.session.pointer(ScenePointerEvent.up(0, 1, 1)); f.session.frame();
        require(f.host.semantics.focused == null, "真实背景点击没有清除焦点");
    }

    private void escapeKey(Fixture f, int[] fallback) {
        SceneKeyEvent escape = new SceneKeyEvent(SceneKey.ESCAPE, false, false, false, false, false, null);
        f.session.key(escape, () -> fallback[0]++);
        f.session.key(escape.withPhase(SceneKeyEvent.Phase.UP)); f.session.frame();
    }

    private void escapeDirty() throws Exception {
        try (Fixture f = new Fixture()) {
            f.domain.execute(() -> f.motion.set(MotionPolicy.OFF));
            String disk = f.disk();
            f.click(SceneSemantics.Role.BUTTON, "深色外观");
            clearFocusOnBackground(f);
            int[] fallback = {0};
            escapeKey(f, fallback);
            System.out.println("SETTINGS_ROOT_ESCAPE state=dirty focus=none fallback=" + fallback[0]
                    + " leaves=" + f.leaves + " confirmation=" + (f.host.semantics.currentModal != null));
            require(fallback[0] == 0 && f.leaves == 0 && f.host.semantics.currentModal != null,
                    "背景清焦后的工作区ESC绕过草稿关闭确认");
            require(f.entry(SceneSemantics.Role.BUTTON, "继续编辑").rangeInteractive, "关闭确认没有提供继续编辑");
            escapeKey(f, fallback);
            require(fallback[0] == 0 && f.leaves == 0 && f.host.semantics.currentModal == null,
                    "确认中的ESC没有只取消当前关闭意图");
            require(visibleError(f, "有未应用的更改") && f.disk().equals(disk), "取消确认改写了草稿或文件");
            f.action(SceneSemantics.Role.BUTTON, "深色外观", SceneSemantics.Action.FOCUS);
            escapeKey(f, fallback);
            require(f.entry(SceneSemantics.Role.BUTTON, "不保存并离开").rangeInteractive,
                    "普通按钮焦点的ESC没有进入同一草稿确认");
            f.click(SceneSemantics.Role.BUTTON, "不保存并离开");
            f.domain.execute(() -> require(f.configuration.current().theme() == ThemeMode.LIGHT,
                    "离开时把未应用主题草稿发布了"));
            require(f.leaves == 1 && fallback[0] == 0 && f.disk().equals(disk), "原关闭意图没有恰好一次退出或隐式保存");
        }
    }

    private void escapeClean() throws Exception {
        for (boolean clear : new boolean[] {true, false}) {
            try (Fixture f = new Fixture()) {
                f.domain.execute(() -> f.motion.set(MotionPolicy.OFF));
                String disk = f.disk();
                f.action(SceneSemantics.Role.BUTTON, "返回", SceneSemantics.Action.FOCUS);
                if (clear) clearFocusOnBackground(f);
                int[] fallback = {0};
                escapeKey(f, fallback);
                require(f.leaves == 1 && fallback[0] == 0 && f.host.semantics.currentModal == null
                                && f.disk().equals(disk), "未修改工作区ESC没有直接执行原关闭意图：clear=" + clear);
                System.out.println("SETTINGS_ROOT_ESCAPE state=clean focus=" + (clear ? "none" : "button")
                        + " fallback=" + fallback[0] + " leaves=" + f.leaves);
            }
        }
    }

    private void escapeUnsaved() throws Exception {
        try (Fixture f = new Fixture()) {
            f.domain.execute(() -> f.motion.set(MotionPolicy.OFF));
            String disk = f.disk();
            f.click(SceneSemantics.Role.BUTTON, "深色外观");
            f.click(SceneSemantics.Role.BUTTON, "应用全部更改");
            require(visibleError(f, "已应用，未保存") && f.disk().equals(disk), "未构造真实已应用未保存状态");
            clearFocusOnBackground(f);
            int[] fallback = {0};
            escapeKey(f, fallback);
            require(fallback[0] == 0 && f.leaves == 0 && f.entry(SceneSemantics.Role.BUTTON, "继续编辑").rangeInteractive,
                    "已应用未保存状态ESC没有进入关闭确认");
            escapeKey(f, fallback);
            require(fallback[0] == 0 && f.leaves == 0 && f.host.semantics.currentModal == null
                            && visibleError(f, "已应用，未保存"), "取消关闭确认丢失原未保存状态");
            f.action(SceneSemantics.Role.BUTTON, "浅色模式", SceneSemantics.Action.FOCUS);
            escapeKey(f, fallback);
            f.click(SceneSemantics.Role.BUTTON, "不保存并离开");
            f.domain.execute(() -> require(f.configuration.current().theme() == ThemeMode.DARK,
                    "关闭确认回滚了已应用主题"));
            require(f.leaves == 1 && fallback[0] == 0 && f.disk().equals(disk), "不保存离开没有保持生效值和原文件");
            System.out.println("SETTINGS_ROOT_ESCAPE state=unsaved fallback=" + fallback[0]
                    + " leaves=" + f.leaves + " appliedValueRetained=true");
        }
    }

    private void escapeNested() throws Exception {
        try (Fixture f = new Fixture()) {
            f.domain.execute(() -> f.motion.set(MotionPolicy.OFF));
            f.click(SceneSemantics.Role.BUTTON, "深色外观");
            f.openAdd();
            f.action(SceneSemantics.Role.SELECT, "内置字体", SceneSemantics.Action.EXPAND);
            require(f.host.semantics.get(f.host.semantics.currentModal).values.role == SceneSemantics.Role.LISTBOX,
                    "没有打开字体内层公共选择器");
            int[] fallback = {0};
            escapeKey(f, fallback);
            require(fallback[0] == 0 && f.leaves == 0 && f.entry(SceneSemantics.Role.BUTTON, "添加到末尾").rangeInteractive,
                    "内层选择器ESC跳过添加视图或关闭工作区");
            escapeKey(f, fallback);
            require(fallback[0] == 0 && f.leaves == 0 && f.entry(SceneSemantics.Role.BUTTON, "+ 添加来源").rangeInteractive,
                    "字体添加ESC没有只返回字体列表");
            escapeKey(f, fallback);
            require(fallback[0] == 0 && f.leaves == 0 && f.host.semantics.currentModal == null
                            && f.entry(SceneSemantics.Role.BUTTON, "管理字体来源").rangeInteractive,
                    "字体列表ESC没有只结束字体管理模态");
            clearFocusOnBackground(f);
            escapeKey(f, fallback);
            require(fallback[0] == 0 && f.leaves == 0 && f.entry(SceneSemantics.Role.BUTTON, "继续编辑").rangeInteractive,
                    "内层全部结束后的ESC没有进入工作区草稿确认");
            System.out.println("SETTINGS_ROOT_ESCAPE nested=select/add/list/workspace fallback=" + fallback[0]
                    + " leaves=" + f.leaves + " currentConfirmation=true");
        }
    }

    private void operationFailure() throws Exception {
        try (Fixture f = new Fixture()) {
            f.action(SceneSemantics.Role.BUTTON, "深色外观", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "应用全部更改", SceneSemantics.Action.ACTIVATE);
            List<String> live = new ArrayList<>(f.current());
            Files.delete(f.file);
            Files.createDirectory(f.file);
            Path obstruction = f.file.resolve("阻止写入");
            Files.write(obstruction, new byte[] {1});
            try {
                f.action(SceneSemantics.Role.BUTTON, "保存到文件", SceneSemantics.Action.ACTIVATE);
                require(visibleError(f, "保存失败："), "外观分类的全局保存失败没有可见原因");
                f.page.requestReload();
                f.session.frame();
                f.action(SceneSemantics.Role.BUTTON, "放弃更改并加载", SceneSemantics.Action.ACTIVATE);
                require(
                        visibleError(f, "重新加载失败：") && f.current().equals(live),
                        "宿主重载失败没有保留原生效值或公共错误");
            } finally {
                Files.deleteIfExists(obstruction);
                Files.deleteIfExists(f.file);
            }
        }
    }

    private boolean visibleError(Fixture f, String prefix) {
        for (SceneSemanticSnapshot.Entry e : f.host.semantics.entries)
            if (e.values.name != null
                    && e.values.name.startsWith(prefix)
                    && e.rangeInteractive
                    && !e.offscreen) return true;
        return false;
    }

    private void performanceOption() throws Exception {
        for (boolean minecraft : Arrays.asList(false, true)) {
            try (Fixture f = new Fixture(minecraft)) {
                f.domain.execute(() -> f.motion.set(MotionPolicy.OFF));
                f.session.frame();
                String original = f.disk();
                f.click(SceneSemantics.Role.BUTTON, "性能");
                SceneSemanticSnapshot.Entry toggle = f.entry(SceneSemantics.Role.SWITCH, "激进合批");
                require(Boolean.FALSE.equals(toggle.values.checked) && !toggle.labelledBy.isEmpty(), "性能分类缺少默认关闭的关联开关");
                boolean risk = false;
                for (SceneSemanticId id : toggle.describedBy) {
                    SceneSemanticSnapshot.Entry help = f.host.semantics.get(id);
                    if (help != null && help.values.name.contains("可能改变重叠文字、阴影和透明内容的覆盖顺序。默认关闭。")) risk = true;
                }
                require(risk, "合批开关缺少关联风险说明");
                f.click(SceneSemantics.Role.SWITCH, "激进合批");
                require(!f.aggressiveBatching() && f.disk().equals(original), "开关点击提前发布或写文件");
                require(f.entry(SceneSemantics.Role.BUTTON, "保存到文件").actions.isEmpty()
                                && visibleError(f, "性能 · 未应用") && visibleError(f, "有未应用的更改"),
                        "合批草稿未接共同dirty提示及保存资格");
                f.click(SceneSemantics.Role.BUTTON, "外观");
                f.click(SceneSemantics.Role.BUTTON, "深色外观");
                f.click(SceneSemantics.Role.BUTTON, "配置文件");
                require(visibleError(f, "激进合批：关闭"), "文件摘要误用了未应用草稿");
                f.click(SceneSemantics.Role.BUTTON, "性能");
                require(toggle.id.equals(f.entry(SceneSemantics.Role.SWITCH, "激进合批").id)
                                && Boolean.TRUE.equals(f.entry(SceneSemantics.Role.SWITCH, "激进合批").values.checked),
                        "跨分类丢失合批草稿或重建开关身份");
                f.click(SceneSemantics.Role.BUTTON, "恢复默认");
                require(Boolean.FALSE.equals(f.entry(SceneSemantics.Role.SWITCH, "激进合批").values.checked)
                                && visibleError(f, "有未应用的更改") && !visibleError(f, "性能 · 未应用"),
                        "性能恢复默认没有只恢复所属草稿");
                f.click(SceneSemantics.Role.SWITCH, "激进合批");
                for (boolean savedValue : Arrays.asList(true, false)) {
                    if (Boolean.TRUE.equals(f.entry(SceneSemantics.Role.SWITCH, "激进合批").values.checked) != savedValue)
                        f.click(SceneSemantics.Role.SWITCH, "激进合批");
                    String before = f.disk();
                    f.click(SceneSemantics.Role.BUTTON, "外观");
                    f.click(SceneSemantics.Role.BUTTON, "应用全部更改");
                    require(f.aggressiveBatching() == savedValue && f.disk().equals(before)
                                    && visibleError(f, "已应用，未保存"),
                            "跨分类应用没有发布合批或隐式落盘");
                    f.domain.execute(() -> require(f.configuration.current().theme() == ThemeMode.DARK,
                            "性能与外观没有共同应用草稿"));
                    f.click(SceneSemantics.Role.BUTTON, "配置文件");
                    require(visibleError(f, "激进合批：" + (savedValue ? "开启" : "关闭")), "当前生效摘要漏掉合批值");
                    f.click(SceneSemantics.Role.BUTTON, "保存到文件");
                    String saved = f.disk();
                    require(saved.contains("aggressiveBatching: " + savedValue) && saved.contains("用户字体说明")
                                    && visibleError(f, "已保存"),
                            "保存未保留真实布尔值、注释或状态");
                    f.click(SceneSemantics.Role.BUTTON, "性能");
                    f.click(SceneSemantics.Role.SWITCH, "激进合批");
                    f.click(SceneSemantics.Role.BUTTON, "应用全部更改");
                    require(f.aggressiveBatching() != savedValue && f.disk().equals(saved), "相反合批值应用错误");
                    f.click(SceneSemantics.Role.BUTTON, "配置文件");
                    f.click(SceneSemantics.Role.BUTTON, "从文件重新加载");
                    require(f.entry(SceneSemantics.Role.GROUP, "重新加载配置").rangeInteractive, "未保存合批值绕过重载确认");
                    f.click(SceneSemantics.Role.BUTTON, "放弃更改并加载");
                    f.click(SceneSemantics.Role.BUTTON, "性能");
                    require(f.aggressiveBatching() == savedValue
                                    && Boolean.TRUE.equals(f.entry(SceneSemantics.Role.SWITCH, "激进合批").values.checked) == savedValue
                                    && visibleError(f, "已保存"),
                            "重载未将文件布尔值同步到开关与共同基线");
                }
            }
        }
    }

    private void performanceLifecycle() throws Exception {
        try (Fixture f = new Fixture()) {
            f.domain.execute(() -> f.motion.set(MotionPolicy.OFF));
            f.session.frame();
            String original = f.disk();
            f.click(SceneSemantics.Role.BUTTON, "性能");
            f.click(SceneSemantics.Role.SWITCH, "激进合批");
            f.page.requestClose();
            f.session.frame();
            require(f.leaves == 0 && f.entry(SceneSemantics.Role.GROUP, "离开配置工作区").rangeInteractive,
                    "合批草稿关闭未请求共同确认");
            f.click(SceneSemantics.Role.BUTTON, "继续编辑");
            f.click(SceneSemantics.Role.BUTTON, "应用全部更改");
            f.page.requestClose();
            f.session.frame();
            f.click(SceneSemantics.Role.BUTTON, "不保存并离开");
            require(f.leaves == 1 && f.aggressiveBatching() && f.disk().equals(original), "离开撤销已应用合批或隐式保存");
            SceneSemanticId old = f.entry(SceneSemantics.Role.SWITCH, "激进合批").id;
            Owner released = f.pageOwner;
            f.session.close();
            f.domain.execute(() -> {
                require(released.isDisposed(), "页面关闭没有释放所属Owner");
                f.config.set(BatchingConfiguration.KEY, false);
            });
            f.session = ScenePageSession.open(f.domain, f.host, f.host, context -> {
                f.pageOwner = context.scope();
                context.motionPolicy(f.motion);
                f.page.build(context);
            });
            f.session.frame();
            f.click(SceneSemantics.Role.BUTTON, "性能");
            require(f.request(SceneSemanticRequest.of(old, SceneSemantics.Action.ACTIVATE)) != SceneSemanticRequest.Result.APPLIED
                            && Boolean.FALSE.equals(f.entry(SceneSemantics.Role.SWITCH, "激进合批").values.checked),
                    "旧页面动作操作了重开的合批草稿");
            f.click(SceneSemantics.Role.SWITCH, "激进合批");
            f.session.close();
            f.domain.execute(() -> require(f.pageOwner.isDisposed(), "重开页面Owner未释放"));
            require(!f.aggressiveBatching() && f.disk().equals(original), "关闭未应用合批草稿仍影响配置或文件");
        }
    }

    private void performanceUnavailable() throws Exception {
        for (boolean minecraft : Arrays.asList(false, true)) {
            try (Fixture f = new Fixture(minecraft, null, false)) {
                f.domain.execute(() -> require(!f.configuration.hasBatching(), "旧schema误识别共享合批键"));
                for (SceneSemanticSnapshot.Entry e : f.host.semantics.entries)
                    require(!"性能".equals(e.values.name) && !"激进合批".equals(e.values.name), "旧schema仍装配性能入口");
                f.host.width = 640;
                f.session.frame();
                // SceneSelect 将占位说明声明为description；选中项标题并非name。
                SceneSemanticSnapshot.Entry selector = f.host.semantics.entries.stream()
                        .filter(e -> e.values.role == SceneSemantics.Role.SELECT && "设置分类".equals(e.values.description))
                        .findFirst().orElseThrow(() -> new AssertionError("窄窗缺少真实分类选择器"));
                require(f.request(SceneSemanticRequest.of(selector.id, SceneSemantics.Action.EXPAND)) == SceneSemanticRequest.Result.APPLIED,
                        "正式窄窗分类选择器无法展开");
                for (SceneSemanticSnapshot.Entry e : f.host.semantics.entries)
                    require(e.values.role != SceneSemantics.Role.OPTION || !e.values.name.startsWith("性能"),
                            "窄屏分类选择仍包含不可用性能项");
                require(!f.aggressiveBatching(), "旧schema合批值没有保持false");
            }
        }
    }

    private void minecraftOption() throws Exception {
        try (Fixture f = new Fixture(true)) {
            String original = f.disk();
            f.action(SceneSemantics.Role.BUTTON, "Minecraft 接入", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.SWITCH, "接管原版文字绘制", SceneSemantics.Action.ACTIVATE);
            require(f.replaced(), "宿主选项编辑提前发布");
            f.action(SceneSemantics.Role.BUTTON, "配置文件", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "Minecraft 接入", SceneSemantics.Action.ACTIVATE);
            require(
                    Boolean.FALSE.equals(
                            f.entry(SceneSemantics.Role.SWITCH, "接管原版文字绘制").values.checked),
                    "切分类丢失接管草稿");
            f.action(SceneSemantics.Role.BUTTON, "恢复默认", SceneSemantics.Action.ACTIVATE);
            require(
                    Boolean.TRUE.equals(
                            f.entry(SceneSemantics.Role.SWITCH, "接管原版文字绘制").values.checked),
                    "恢复默认写死为关闭");
            f.action(SceneSemantics.Role.SWITCH, "接管原版文字绘制", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "应用全部更改", SceneSemantics.Action.ACTIVATE);
            require(!f.replaced() && f.disk().equals(original), "接管选项应用隐式保存或没有发布");
            f.action(SceneSemantics.Role.BUTTON, "保存到文件", SceneSemantics.Action.ACTIVATE);
            require(f.disk().contains("replaceVanillaFont: false"), "接管选项未保存生效值");
        }
    }

    private void pathAndClose() throws Exception {
        try (Fixture f = new Fixture()) {
            String original = f.disk();
            f.openAdd();
            f.action(SceneSemantics.Role.RADIO, "本地字体文件", SceneSemantics.Action.SELECT);
            SceneSemanticSnapshot.Entry path = f.entry(SceneSemantics.Role.TEXT_FIELD, "字体文件路径");
            boolean described = false;
            for (SceneSemanticId id : path.describedBy) {
                SceneSemanticSnapshot.Entry help = f.host.semantics.get(id);
                if (help != null && help.values.name.contains("应用前会检查字体来源")) described = true;
            }
            require(
                    !path.labelledBy.isEmpty() && described,
                    "字体路径标签/说明关系不完整：labels="
                            + path.labelledBy
                            + ", descriptions="
                            + path.describedBy);
            require(
                    f.request(SceneSemanticRequest.text(path.id, "   "))
                            == SceneSemanticRequest.Result.APPLIED,
                    "路径字段拒绝草稿输入");
            require(f.entry(SceneSemantics.Role.BUTTON, "添加到末尾").actions.isEmpty(), "空路径的添加动作仍可执行");
            require(
                    f.request(SceneSemanticRequest.of(path.id, SceneSemantics.Action.SUBMIT))
                            == SceneSemanticRequest.Result.APPLIED,
                    "字段提交没有进入真实路径校验");
            require(
                    f.host.semantics.get(path.id) != null
                            && "   ".equals(f.host.semantics.get(path.id).values.value),
                    "路径校验拒绝丢失原草稿或字段");
            boolean error = false;
            for (SceneSemanticSnapshot.Entry e : f.host.semantics.entries)
                if ("请输入字体文件路径。".equals(e.values.name)) error = true;
            require(
                    error && f.disk().equals(original) && f.current().size() == 1,
                    "拒绝没有定位错误或修改实际配置");
            f.action(SceneSemantics.Role.BUTTON, "取消", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "完成字体编辑", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "深色外观", SceneSemantics.Action.ACTIVATE);
            require(
                    Boolean.TRUE.equals(
                            f.entry(SceneSemantics.Role.BUTTON, "深色外观").values.selected),
                    "外观选择缺少可读选中事实");
            f.action(SceneSemantics.Role.BUTTON, "返回", SceneSemantics.Action.ACTIVATE);
            require(
                    f.leaves == 0 && f.entry(SceneSemantics.Role.GROUP, "离开配置工作区") != null,
                    "有草稿的返回绕过原关闭确认");
            f.action(SceneSemantics.Role.BUTTON, "继续编辑", SceneSemantics.Action.ACTIVATE);
            require(
                    f.leaves == 0
                            && Boolean.TRUE.equals(
                                    f.entry(SceneSemantics.Role.BUTTON, "深色外观").values.selected),
                    "继续编辑丢失主题草稿");
            f.action(SceneSemantics.Role.BUTTON, "返回", SceneSemantics.Action.ACTIVATE);
            f.action(SceneSemantics.Role.BUTTON, "不保存并离开", SceneSemantics.Action.ACTIVATE);
            require(f.leaves == 1 && f.disk().equals(original), "确认离开没有沿原动作或隐式写文件");
        }
    }
}
