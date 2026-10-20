package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.*;
import club.heiqi.uilib.ui.reactive.*;
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
        scenario("正式设置单选字体仅改草稿且应用保存保留用户注释", this::fontSelection);
        scenario("路径拒绝保留原草稿与关联说明且模态返回按原确认", this::pathAndClose);
        scenario("分类与弹窗快速反向保留编辑身份且退出立即结束输入", this::motionLifecycle);
        scenario("弹窗退场绘制跟随弹簧且快速重开位置连续", this::motionPaint);
        scenario("宿主请求重载结束添加窗口所属的文件选择模态", this::pickerOwnership);
        scenario("宿主接管选项跨分类保留草稿且恢复真实默认", this::minecraftOption);
        scenario("同窗字体子视图的初始焦点与逐级返回焦点", this::editorFocus);
        scenario("外观分类的全局保存与宿主重载失败持续可见", this::operationFailure);
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
        int refreshes, leaves;
        Signal<MotionPolicy> motion;

        Fixture() throws Exception {
            this(false);
        }

        final ConfigKey<Boolean> replace =
                new ConfigKey<>("mc.replaceVanillaFont", ConfigCodecs.BOOLEAN, true);

        Fixture(boolean minecraft) throws Exception {
            file = Files.createTempFile("uilib-workspace-", ".yaml");
            onClose(() -> Files.deleteIfExists(file));
            host.width = 1280;
            host.height = 1100;
            domain.execute(
                    () -> {
                        motion = domain.signal(MotionPolicy.NORMAL);
                        config =
                                new ConfigSession(
                                        domain,
                                        minecraft
                                                ? ConfigSchema.of(
                                                        fonts, ThemeConfiguration.KEY, replace)
                                                : ConfigSchema.of(fonts, ThemeConfiguration.KEY));
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
                        configuration =
                                new SettingsConfiguration(
                                        domain,
                                        config,
                                        fonts,
                                        minecraft ? replace : null,
                                        file,
                                        FontCandidateConfiguration.defaults(builtins),
                                        values -> {
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
                                        },
                                        () -> refreshes++);
                        page = new SettingsPage(configuration, () -> leaves++);
                    });
            session =
                    ScenePageSession.open(
                            domain,
                            host,
                            host,
                            context -> {
                                context.motionPolicy(motion);
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
