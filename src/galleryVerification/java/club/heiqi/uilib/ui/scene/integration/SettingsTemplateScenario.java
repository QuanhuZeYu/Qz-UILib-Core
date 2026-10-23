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
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.*;
import club.heiqi.uilib.ui.settings.*;
import java.util.concurrent.CompletableFuture;

/** 通用模板实际运行外部草稿与业务命令，不用模板代替配置事务。 */
public final class SettingsTemplateScenario extends TestApplication {
    protected void runTests() throws Exception {
        scenario("跨分类保留同一草稿和错误且业务拒绝不发布", this::draftAndRejection);
        scenario("动态文案主题窄屏与禁用沿原控件输入", this::dynamicEnvironment);
        scenario("关闭重开释放旧输入并保留外部草稿", this::reopen);
        scenario("外部单行换行初值与活跃恢复收敛且同值保留选区历史", this::singleLineExternalDraft);
        scenario("现代分类整行命中与统一字段选择及窄屏操作可达", this::modernComposition);
        scenario("重复字段移除释放局部外观且共享 helper 的兄弟仍可编辑", this::fieldOwnership);
        scenario("真实低窗页脚最大滚动后主操作在客户端内可点击", this::shortWindowActions);
        scenario("浅深主题的字段文字边框和开关轨道实际绘制对比", this::fieldPaintColors);
        scenario("宽窄高低窗口导航沿实际滚动与输入切换分类", this::responsiveNavigation);
        scenario("设置字段与窄屏分类弹层在明暗切换中保持不透明表面", this::selectorPopupSurface);
    }

    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SettingsHudHost host = new SettingsHudHost();
        Signal<String> draft, live, error, notice, title, closeLabel, categoryLabel, sectionDescription, choice;
        Signal<SceneDensity> density;
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
                sectionDescription = domain.signal("管理业务名称与本次编辑草稿。");
                choice = domain.signal("safe"); density = domain.signal(SceneDensity.COMFORTABLE);
                domain.effect(owner, () -> error.set("拒绝".equals(draft.get()) ? "名称不可使用" : ""));
                template = new SettingsTemplate(title).description(() -> "共用外部草稿")
                        .labels(closeLabel, categoryLabel)
                        .section("name", () -> "基本", sectionDescription, SceneIconButton.Symbol.SLIDERS,
                                context -> new SettingsFields(context).text(() -> "名称", () -> "输入业务名称", draft, enabled, error))
                        .section("options", () -> "选项", () -> "调整提示与处理策略。", SceneIconButton.Symbol.MONITOR, context -> {
                            SettingsFields fields = new SettingsFields(context);
                            return SceneAppearance.column(24)
                                    .add(fields.toggle(() -> "启用提示", () -> "只修改草稿", toggle, enabled, () -> ""))
                                    .add(fields.choice(() -> "处理策略", () -> "选择本次处理方式",
                                            () -> Arrays.asList(new SceneSelect.Option<>("safe", () -> "稳妥"),
                                                    new SceneSelect.Option<>("fast", () -> "快速")), choice, enabled, () -> ""));
                        })
                        .notice(notice).onClose(() -> leaves++)
                        .action(() -> "恢复此页默认设置", SceneCommand.of(() -> true, () -> draft.set("初始")), false)
                        .action(() -> "重新载入生效配置", SceneCommand.of(() -> true, () -> draft.set(live.get())), false)
                        .action(() -> "提交业务", SceneCommand.of(() -> true, () -> {
                            if (!error.get().isEmpty()) notice.set("业务拒绝，输入保留");
                            else { live.set(draft.get()); notice.set("业务已接受"); }
                        }), true);
            });
            open();
        }
        void open() {
            page = ScenePageSession.open(domain, host, host, context -> {
                context.motionPolicy(() -> MotionPolicy.OFF); context.density(density);
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

    private void modernComposition() {
        try (Fixture f = new Fixture()) {
            SceneSemanticSnapshot.Entry navigation = f.entry(SceneSemantics.Role.BUTTON, "选项");
            require(navigation.box.width > navigation.box.height * 3, "分类导航仍按短文字收窄");
            int x = navigation.box.x + navigation.box.width - 4, y = navigation.box.y + navigation.box.height / 2;
            f.page.pointer(ScenePointerEvent.down(0, x, y)); f.page.pointer(ScenePointerEvent.up(0, x, y)); f.page.frame();
            SceneSemanticSnapshot.Entry toggle = f.entry(SceneSemantics.Role.SWITCH, "启用提示");
            SceneSemanticSnapshot.Entry toggleLabel = f.entry(SceneSemantics.Role.LABEL, "启用提示");
            require(toggle.box.x > toggleLabel.box.x + toggleLabel.box.width, "开关未排列到说明右侧");
            SceneSemanticSnapshot.Entry choice = f.entry(SceneSemantics.Role.SELECT, "处理策略");
            f.click(choice);
            f.page.key(new SceneKeyEvent(SceneKey.DOWN, false, false, false, false, false, null)); f.page.frame();
            f.page.key(new SceneKeyEvent(SceneKey.ENTER, false, false, false, false, false, null)); f.page.frame();
            f.domain.execute(() -> require("fast".equals(f.choice.get()), "现代 choice 未提交正式选择意图"));
            for (PaintCommand command : f.host.plan.commands()) if (command.kind == PaintCommand.Kind.TEXT && !command.text.logicalText.isEmpty())
                require(!"default".equals(command.text.style.font), "模板或字段仍使用默认业务字族：" + command.text.logicalText);
            SceneSemanticId choiceId = f.entry(SceneSemantics.Role.SELECT, "处理策略").id;
            int comfortable = f.entry(SceneSemantics.Role.SWITCH, "启用提示").box.height;
            f.domain.execute(() -> f.density.set(SceneDensity.COMPACT)); f.page.frame();
            require(choiceId.equals(f.entry(SceneSemantics.Role.SELECT, "处理策略").id)
                    && f.entry(SceneSemantics.Role.SWITCH, "启用提示").box.height < comfortable,
                    "密度变化重建字段或没有更新真实控件度量");
            f.host.width = 220; f.host.height = 700;
            f.domain.execute(() -> f.notice.set("有未保存的更改，请确认后提交业务。")); f.page.frame();
            SceneSemanticSnapshot.Entry secondary = f.entry(SceneSemantics.Role.BUTTON, "恢复此页默认设置");
            SceneSemanticSnapshot.Entry primary = f.entry(SceneSemantics.Role.BUTTON, "提交业务");
            require(primary.box.y > secondary.box.y, "窄屏操作没有按可用空间换行");
            require(primary.box.x >= 0 && primary.box.x + primary.box.width <= f.host.width
                    && primary.box.y + primary.box.height <= f.host.height, "窄屏主操作超出可用视口");
            f.click(primary); f.domain.execute(() -> require(f.notice.get().equals("业务已接受"), "窄屏主操作不可点击"));
        }
    }

    private void responsiveNavigation() {
        for (SceneDensity density : new SceneDensity[] {SceneDensity.COMFORTABLE, SceneDensity.COMPACT}) {
            for (int[] viewport : new int[][] {{1200, 180}, {1200, 900}, {480, 700}, {480, 180}}) {
                try (Fixture f = new Fixture()) {
                    f.host.width = viewport[0]; f.host.height = viewport[1];
                    f.domain.execute(() -> { f.density.set(density); f.draft.set("导航草稿"); }); f.page.frame();
                    boolean compact = viewport[0] == 480;
                    SceneScrollViewport.Snapshot[] document = new SceneScrollViewport.Snapshot[1];
                    f.domain.execute(() -> document[0] = new SceneScrollViewport(f.page.runtime(),
                            f.root.children().get(0), SceneNode.Axis.COLUMN).snapshot(f.page.pipeline().current().geometry));
                    SceneSemanticSnapshot.Entry navigation = null;
                    for (SceneSemanticSnapshot.Entry entry : f.host.semantics.entries) {
                        if (compact && entry.values.role == SceneSemantics.Role.SELECT
                                && entry.values.description != null && entry.values.description.contains("业务分类")) navigation = entry;
                        if (!compact && entry.values.role == SceneSemantics.Role.BUTTON && "选项".equals(entry.values.name)) navigation = entry;
                    }
                    System.out.println("RESPONSIVE_NAV " + viewport[0] + "x" + viewport[1] + " " + density
                            + " mode=" + (compact ? "select" : "rail") + " found=" + (navigation != null)
                            + " documentMaximum=" + document[0].maximum);
                    require(navigation != null, "宽窄高低窗口缺少当前空间可用的分类导航");
                    SceneSemanticSnapshot.Entry before = navigation;
                    f.domain.execute(() -> {
                        SceneScrollViewport port = new SceneScrollViewport(f.page.runtime(), f.root.children().get(0), SceneNode.Axis.COLUMN);
                        port.moveToPixels(document[0].offset + before.box.y - document[0].box.y);
                    });
                    f.page.frame();
                    SceneSemanticSnapshot.Entry visible = null;
                    for (SceneSemanticSnapshot.Entry entry : f.host.semantics.entries) if (entry.id.equals(before.id)) visible = entry;
                    require(visible != null, "文档滚动丢失原分类控件身份");
                    LayoutBox client = new LayoutBox(0, 0, f.host.width, f.host.height);
                    LayoutBox hit = visible.box.intersect(client);
                    if (visible.clip != null) hit = hit.intersect(visible.clip);
                    require(hit.width > 0 && hit.height > 0, "文档滚动后分类仍被空裁剪");
                    int x = hit.x + hit.width / 2, y = hit.y + hit.height / 2;
                    f.page.pointer(ScenePointerEvent.down(0, x, y)); f.page.pointer(ScenePointerEvent.up(0, x, y)); f.page.frame();
                    if (compact) {
                        f.page.key(new SceneKeyEvent(SceneKey.DOWN, false, false, false, false, false, null)); f.page.frame();
                        f.page.key(new SceneKeyEvent(SceneKey.ENTER, false, false, false, false, false, null)); f.page.frame();
                    }
                    f.entry(SceneSemantics.Role.SWITCH, "启用提示");
                    f.domain.execute(() -> require("导航草稿".equals(f.draft.get()), "导航切换改写了原字段草稿"));
                }
            }
        }
    }

    private void selectorPopupSurface() {
        try (Fixture f = new Fixture()) {
            f.activate("选项");
            SceneSemanticSnapshot.Entry select = f.entry(SceneSemantics.Role.SELECT, "处理策略");
            f.semantic(SceneSemanticRequest.of(select.id, SceneSemantics.Action.FOCUS));
            f.page.navigation(SceneNavigationEvent.NEXT); f.page.navigation(SceneNavigationEvent.PREVIOUS); f.page.frame();
            int edge = f.host.rectanglePixel(select.box.x + select.box.width / 2.0, select.box.y + .5);
            require(edge == new SceneAppearance.Palette(false).muted, "去框选择器外表面未跟随真实键盘焦点");
            f.click(f.entry(SceneSemantics.Role.SELECT, "处理策略"));
            popupSurface(f, "字段", ThemeMode.LIGHT);
            f.domain.execute(() -> f.config.set(ThemeConfiguration.KEY, ThemeMode.DARK)); f.page.frame();
            popupSurface(f, "字段打开后切换", ThemeMode.DARK);
            f.page.escape(() -> { throw new AssertionError("选择器未消费 ESC"); }); f.page.frame();
            f.host.width = 480; f.page.frame();
            SceneSemanticSnapshot.Entry navigation = null;
            for (SceneSemanticSnapshot.Entry entry : f.host.semantics.entries)
                if (entry.values.role == SceneSemantics.Role.SELECT && entry.values.description.contains("业务分类")) navigation = entry;
            require(navigation != null, "窄屏分类选择器不存在");
            f.click(navigation); popupSurface(f, "窄屏分类", ThemeMode.DARK);
            f.domain.execute(() -> f.config.set(ThemeConfiguration.KEY, ThemeMode.LIGHT)); f.page.frame();
            popupSurface(f, "窄屏分类打开后切换", ThemeMode.LIGHT);
            f.page.key(new SceneKeyEvent(SceneKey.TAB, false, false, false, false, false, null)); f.page.frame();
            require(f.host.semantics.currentModal == null, "分类选择器 Tab 未结束所属弹层");
        }
    }

    private void popupSurface(Fixture f, String source, ThemeMode mode) {
        SceneSemanticSnapshot.Entry popup = null;
        for (SceneSemanticSnapshot.Entry entry : f.host.semantics.entries)
            if (entry.values.role == SceneSemantics.Role.LISTBOX) popup = entry;
        require(popup != null && popup.id.equals(f.host.semantics.currentModal), "实际选择器未建立所属模态");
        LayoutBox box = popup.box;
        int color = f.host.rectanglePixel(box.x + box.width / 2.0, box.y + 2.5);
        int expected = new SceneAppearance.Palette(mode.isDark()).background;
        System.out.println("SELECT_POPUP_SURFACE source=" + source + " theme=" + mode
                + " color=" + Integer.toHexString(color) + " expected=" + Integer.toHexString(expected));
        require(color == expected && (color >>> 24) == 255, "设置选择器弹层没有独立不透明主题表面：" + source);
    }

    private void fieldPaintColors() {
        try (Fixture f = new Fixture()) {
            for (ThemeMode mode : ThemeMode.values()) {
                f.domain.execute(() -> f.config.set(ThemeConfiguration.KEY, mode)); f.page.frame(); f.activate("基本");
                SceneAppearance.Palette palette = new SceneAppearance.Palette(mode.isDark());
                boolean namePainted = false;
                for (PaintCommand command : f.host.plan.commands())
                    if (command.kind == PaintCommand.Kind.TEXT && "名称".equals(command.text.logicalText))
                        namePainted = command.text.style.color == palette.text && command.box.width > 0 && command.box.height > 0;
                require(namePainted, "字段名称真实 TEXT 颜色不符合当前主题");
                LayoutBox editor = f.entry(SceneSemantics.Role.TEXT_FIELD, "名称").box;
                int border = f.host.rectanglePixel(editor.x + editor.width / 2.0, editor.y + .5);
                System.out.println("FIELD_PAINT " + mode + " text=" + Integer.toHexString(palette.text)
                        + " border=" + Integer.toHexString(border));
                require(border == palette.border, "无框编辑与现代外表面未输出可见边线");
                f.activate("选项"); f.domain.execute(() -> f.toggle.set(false)); f.page.frame();
                LayoutBox toggle = f.entry(SceneSemantics.Role.SWITCH, "启用提示").box;
                double left = toggle.x + toggle.width / 3.0, right = toggle.x + toggle.width * 2.0 / 3;
                double middle = toggle.y + toggle.height / 2.0;
                int offThumb = f.host.rectanglePixel(left, middle), offTrack = f.host.rectanglePixel(right, middle);
                require(offTrack == palette.border && offThumb != offTrack && (offTrack >>> 24) != 0,
                        "关闭开关没有可见轨道或轨道拇指无对比");
                f.click(f.entry(SceneSemantics.Role.SWITCH, "启用提示"));
                int onTrack = f.host.rectanglePixel(left, middle), onThumb = f.host.rectanglePixel(right, middle);
                System.out.println("SWITCH_PAINT " + mode + " off=" + Integer.toHexString(offTrack) + "/" + Integer.toHexString(offThumb)
                        + " on=" + Integer.toHexString(onTrack) + "/" + Integer.toHexString(onThumb));
                require(onTrack == palette.primary && onThumb == palette.onPrimary && onTrack != onThumb,
                        "开启开关未绘制相互区分的轨道与拇指");
            }
        }
    }

    private void shortWindowActions() {
        for (int[] viewport : new int[][] {{220, 240}, {600, 180}, {220, 300}}) {
            try (Fixture f = new Fixture()) {
                f.host.width = viewport[0]; f.host.height = viewport[1];
                f.domain.execute(() -> {
                    f.draft.set("低窗业务");
                    f.notice.set("Pending changes, confirm before applying.");
                });
                f.page.frame();
                SceneScrollViewport.Snapshot[] atMaximum = new SceneScrollViewport.Snapshot[1];
                LayoutBox[] footerBox = new LayoutBox[1];
                f.domain.execute(() -> {
                    SceneNode footer = f.root.children().get(f.root.children().size() - 1);
                    SceneScrollViewport port = new SceneScrollViewport(f.page.runtime(), footer, SceneNode.Axis.COLUMN);
                    SceneGeometryView geometry = f.page.pipeline().current().geometry;
                    SceneScrollViewport.Snapshot before = port.snapshot(geometry);
                    require(before != null, "低窗页脚没有正式滚动端口");
                    port.moveToPixels(before.maximum);
                });
                f.page.frame();
                f.domain.execute(() -> {
                    SceneNode footer = f.root.children().get(f.root.children().size() - 1);
                    SceneGeometryView geometry = f.page.pipeline().current().geometry;
                    footerBox[0] = geometry.get(footer).box;
                    atMaximum[0] = new SceneScrollViewport(f.page.runtime(), footer, SceneNode.Axis.COLUMN).snapshot(geometry);
                });
                require(atMaximum[0].offset == atMaximum[0].maximum, "页脚未实际滚动到原端口最大位置");
                LayoutBox footer = footerBox[0];
                SceneSemanticSnapshot.Entry primary = f.entry(SceneSemantics.Role.BUTTON, "提交业务");
                LayoutBox button = primary.box;
                System.out.println("SHORT_WINDOW " + viewport[0] + "x" + viewport[1]
                        + " footer=" + footer.x + "," + footer.y + "," + footer.width + "," + footer.height
                        + " scroll=" + atMaximum[0].offset + "/" + atMaximum[0].maximum
                        + " primary=" + button.x + "," + button.y + "," + button.width + "," + button.height);
                require(footer.y >= 0 && footer.y + footer.height <= f.host.height,
                        "低窗页脚仍越出宿主：" + viewport[0] + "x" + viewport[1]);
                require(button.y >= footer.y && button.y + button.height <= footer.y + footer.height
                        && button.x >= 0 && button.x + button.width <= f.host.width,
                        "最大滚动后的主操作仍不完整位于客户端");
                f.click(primary);
                f.domain.execute(() -> require("低窗业务".equals(f.live.get()), "客户端内主操作真实点击没有应用业务"));
            }
        }
    }

    private void fieldOwnership() throws Exception {
        ReactiveDomain domain = ReactiveDomain.direct(); SettingsHudHost host = new SettingsHudHost();
        Owner[] pageScope = new Owner[1]; Signal<Boolean>[] show = new Signal[1];
        Signal<String>[] draft = new Signal[1]; List<SceneNode> generated = new ArrayList<>();
        java.lang.reflect.Field children = Owner.class.getDeclaredField("children"); children.setAccessible(true);
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, scope -> {
            pageScope[0] = scope.scope(); scope.motionPolicy(() -> MotionPolicy.OFF);
            SettingsFields fields = new SettingsFields(scope);
            show[0] = domain.signal(false); draft[0] = domain.signal("兄弟字段");
            SceneNode permanent = fields.text(() -> "保留字段", () -> "", draft[0], () -> true, () -> "");
            SceneNode repeated = new SceneNode().bind(NodeBinding.repeat(
                    () -> show[0].get() ? Collections.singletonList("row") : Collections.emptyList(), key -> key, key -> {
                        Signal<String> text = domain.signal("成员"), value = domain.signal("first");
                        Signal<Boolean> checked = domain.signal(false);
                        SceneNode row = SceneAppearance.column(8)
                                .add(fields.text(() -> "成员名称", () -> "", text, () -> true, () -> ""))
                                .add(fields.toggle(() -> "成员开关", () -> "", checked, () -> true, () -> ""))
                                .add(fields.choice(() -> "成员选项", () -> "", () -> Collections.singletonList(
                                        new SceneSelect.Option<>("first", () -> "第一项")), value, () -> true, () -> ""))
                                .add(new SettingsFields(scope).row(() -> "成员说明", () -> "", new SceneNode(), () -> ""));
                        generated.add(row); return row;
                    }));
            scope.mountOwned(SceneAppearance.column(12).size(Size.fill(1), Size.fill(1)).add(permanent).add(repeated));
        })) {
            page.frame(); int baseline = ((List<?>) children.get(pageScope[0])).size();
            for (int i = 0; i < 4; i++) {
                domain.execute(() -> show[0].set(true)); page.frame();
                domain.execute(() -> show[0].set(false)); page.frame();
                require(generated.get(generated.size() - 1).destroyed(), "重复字段移除未销毁所属子树");
                require(((List<?>) children.get(pageScope[0])).size() == baseline, "移除字段后局部外观作用域仍积累");
            }
            domain.execute(() -> draft[0].set("仍可编辑")); page.frame();
            boolean retained = false;
            for (SceneSemanticSnapshot.Entry entry : host.semantics.entries)
                if (entry.values.role == SceneSemantics.Role.TEXT_FIELD && "仍可编辑".equals(entry.values.value)) retained = true;
            require(retained, "移除一行提前释放了共享 helper 的其他字段");
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
