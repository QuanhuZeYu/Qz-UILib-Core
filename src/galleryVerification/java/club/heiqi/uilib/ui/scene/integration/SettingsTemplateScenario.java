package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.*;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.*;
import club.heiqi.uilib.ui.settings.*;
import java.util.concurrent.CompletableFuture;

/** 通用模板实际运行外部草稿与业务命令，不用模板代替配置事务。 */
public final class SettingsTemplateScenario extends TestApplication {
    protected void runTests() throws Exception {
        scenario("跨分类保留同一草稿和错误且业务拒绝不发布", this::draftAndRejection);
        scenario("动态文案主题窄屏与禁用沿原控件输入", this::dynamicEnvironment);
        scenario("关闭重开释放旧输入并保留外部草稿", this::reopen);
        scenario("外部单行换行初值与活跃恢复收敛且同值保留选区历史", this::singleLineExternalDraft);
    }

    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SettingsHudHost host = new SettingsHudHost();
        Signal<String> draft, live, error, notice, title, closeLabel, categoryLabel;
        Signal<Boolean> enabled, toggle;
        Owner owner;
        ConfigSession config;
        SettingsTemplate template;
        ScenePageSession page;
        SceneNode root;
        int leaves;
        Fixture() { this("初始"); }
        Fixture(String initial) {
            domain.execute(() -> {
                owner = domain.owner(); config = new ConfigSession(domain, ConfigSchema.of(ThemeConfiguration.KEY));
                host.themes = new ThemeConfiguration(domain, config, owner);
                draft = domain.signal(initial); live = domain.signal("初始");
                error = domain.signal(""); notice = domain.signal(""); title = domain.signal("业务设置");
                closeLabel = domain.signal("关闭设置"); categoryLabel = domain.signal("业务分类");
                enabled = domain.signal(true); toggle = domain.signal(false);
                domain.effect(owner, () -> error.set("拒绝".equals(draft.get()) ? "名称不可使用" : ""));
                template = new SettingsTemplate(title).description(() -> "共用外部草稿")
                        .labels(closeLabel, categoryLabel)
                        .section("name", () -> "基本", context -> new SettingsFields(context)
                                .text(() -> "名称", () -> "输入业务名称", draft, enabled, error))
                        .section("options", () -> "选项", context -> new SettingsFields(context)
                                .toggle(() -> "启用提示", () -> "只修改草稿", toggle, enabled, () -> ""))
                        .notice(notice).onClose(() -> leaves++)
                        .action(() -> "提交业务", SceneCommand.of(() -> true, () -> {
                            if (!error.get().isEmpty()) notice.set("业务拒绝，输入保留");
                            else { live.set(draft.get()); notice.set("业务已接受"); }
                        }), true);
            });
            open();
        }
        void open() {
            page = ScenePageSession.open(domain, host, host, context -> {
                context.motionPolicy(() -> MotionPolicy.OFF);
                root = template.create(context); context.mountOwned(root);
            });
            page.frame();
        }
        SceneSemanticSnapshot.Entry entry(SceneSemantics.Role role, String name) {
            for (SceneSemanticSnapshot.Entry e : host.semantics.entries)
                if (e.values.role == role && name.equals(e.values.name)) return e;
            throw new AssertionError("缺少模板控件 " + role + " " + name);
        }
        void semantic(SceneSemanticRequest request) {
            CompletableFuture<SceneSemanticRequest.Result> result = page.semantic(request);
            page.frame(); require(result.join() == SceneSemanticRequest.Result.APPLIED, "模板语义请求拒绝");
        }
        void activate(String name) { semantic(SceneSemanticRequest.of(entry(SceneSemantics.Role.BUTTON, name).id, SceneSemantics.Action.ACTIVATE)); }
        void click(SceneSemanticSnapshot.Entry entry) {
            int x = entry.box.x + entry.box.width / 2, y = entry.box.y + entry.box.height / 2;
            page.pointer(ScenePointerEvent.down(0, x, y)); page.pointer(ScenePointerEvent.up(0, x, y)); page.frame();
        }
        void type(String text) {
            SceneSemanticSnapshot.Entry field = entry(SceneSemantics.Role.TEXT_FIELD, "名称");
            click(field);
            page.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL, false, null));
            page.edit(SceneEditEvent.text(text)); page.frame();
        }
        public void close() { page.close(); domain.execute(owner::close); }
    }

    private void draftAndRejection() {
        try (Fixture f = new Fixture()) {
            SceneSemanticId id = f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id;
            f.type("拒绝");
            f.domain.execute(() -> require("拒绝".equals(f.draft.get()) && "初始".equals(f.live.get()), "输入草稿/生效值不符：draft=" + f.draft.get() + " live=" + f.live.get() + " focus=" + f.host.semantics.focused));
            require(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.description.contains("名称不可使用"), "错误未关联原字段语义");
            f.activate("选项"); f.click(f.entry(SceneSemantics.Role.SWITCH, "启用提示")); f.activate("基本");
            require(id.equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id)
                    && "拒绝".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value), "分类切换重建或清除了草稿");
            f.activate("提交业务");
            f.domain.execute(() -> require("初始".equals(f.live.get()) && f.toggle.get() && f.notice.get().contains("拒绝"), "模板越过业务拒绝"));
            f.type("接受"); f.activate("提交业务");
            f.domain.execute(() -> require("接受".equals(f.live.get()), "正式输入未进入业务命令"));
            require(f.host.replays > 0 && !f.host.plan.commands().isEmpty(), "模板未触达软件回放边界");
            f.domain.execute(() -> f.draft.set("业务恢复值")); f.page.frame();
            require(id.equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id)
                    && "业务恢复值".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value), "业务草稿重置没有同步原字段");
        }
    }

    private void dynamicEnvironment() {
        try (Fixture f = new Fixture()) {
            SceneSemanticId field = f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id;
            int oldCanvas = f.root.background();
            f.domain.execute(() -> {
                f.title.set("动态设置标题"); f.closeLabel.set("返回业务"); f.categoryLabel.set("动态业务分类");
                f.config.set(ThemeConfiguration.KEY, ThemeMode.DARK);
            });
            f.host.width = 480; f.host.height = 680; f.page.frame();
            require(f.root.background() != oldCanvas, "主题未刷新模板背景");
            f.entry(SceneSemantics.Role.BUTTON, "返回业务");
            SceneSemanticSnapshot.Entry selector = null;
            for (SceneSemanticSnapshot.Entry e : f.host.semantics.entries)
                if (e.values.role == SceneSemantics.Role.SELECT) selector = e;
            require(selector != null && selector.values.description.contains("动态业务分类"), "窄屏选择器缺少动态语义文案");
            f.click(selector);
            f.page.key(new SceneKeyEvent(SceneKey.DOWN, false, false, false, false, false, null)); f.page.frame();
            f.page.key(new SceneKeyEvent(SceneKey.ENTER, false, false, false, false, false, null)); f.page.frame();
            f.entry(SceneSemantics.Role.SWITCH, "启用提示");
            f.domain.execute(() -> f.enabled.set(false)); f.page.frame();
            SceneSemanticSnapshot.Entry toggle = f.entry(SceneSemantics.Role.SWITCH, "启用提示");
            require(toggle.actions.isEmpty(), "禁用开关仍允许激活");
            f.click(toggle); f.domain.execute(() -> require(!f.toggle.get(), "禁用点击提交了草稿"));
            f.host.width = 1200; f.host.height = 900; f.page.frame(); f.activate("基本");
            require(field.equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id), "动态环境丢失字段身份");
            f.type("禁止输入"); f.domain.execute(() -> require("初始".equals(f.draft.get()), "禁用字段接受了输入"));
        }
    }

    private void singleLineExternalDraft() {
        for (String separator : new String[] {"\n", "\r", "\r\n"}) {
            String initial = "left" + separator + "right";
            try (Fixture f = new Fixture(initial)) {
                SceneSemanticId field = f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id;
                require("leftright".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value),
                        "含换行初值未按单行合同呈现");
                f.domain.execute(() -> require(initial.equals(f.draft.get()), "呈现被动改写了外部草稿"));
                f.type("active");
                f.domain.execute(() -> f.draft.set("rest" + separator + "ored")); f.page.frame();
                require(field.equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id)
                        && "restored".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value),
                        "活跃字段恢复含换行草稿未收敛到同一节点");
                f.page.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null)); f.page.frame();
                require("restored".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value),
                        "显式恢复仍能撤销到旧草稿");
                f.type("edited");
                f.page.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL, false, null)); f.page.frame();
                f.domain.execute(() -> f.draft.set("edi" + separator + "ted")); f.page.frame();
                require(field.equals(f.host.semantics.focused), "同规范化文本改变了字段焦点");
                f.page.edit(SceneEditEvent.text("replacement")); f.page.frame();
                require("replacement".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value),
                        "同规范化文本重置了原选区");
                f.page.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null)); f.page.frame();
                require("edited".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value),
                        "替换输入的撤销历史丢失");
                f.page.edit(SceneEditEvent.command(SceneEditEvent.Command.UNDO, false, null)); f.page.frame();
                require("restored".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value),
                        "同规范化文本清除了已有历史");
            }
        }
    }

    private void reopen() {
        try (Fixture f = new Fixture()) {
            f.type("保留草稿"); SceneSemanticId old = f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id;
            SceneNode oldRoot = f.root; f.activate("关闭设置"); require(f.leaves == 1, "关闭未发布业务意图");
            f.page.close(); require(oldRoot.destroyed(), "页面关闭未释放模板树");
            f.page.edit(SceneEditEvent.text("迟到输入")); f.page.frame();
            f.domain.execute(() -> f.draft.set("外部新值")); f.open();
            require(!old.equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").id)
                    && "外部新值".equals(f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").values.value), "重开继承旧输入或丢失外部草稿");
            f.page.escape(() -> { throw new AssertionError("模板未消费 ESC"); }); f.page.frame();
            require(f.leaves == 2, "ESC 未进入业务关闭策略");
        }
    }
}
