package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTabs;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics.Action;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 标签条和活动面板经过正式输入/生命周期，草稿由业务独立持有。 */
public final class TabsScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("只构造当前面板且切换后同批Tab文字进入新字段", this::activePanel);
        scenario("同键新声明保留当前面板而换键明确重置", this::definitionIdentity);
        scenario("窄窗标签横滚不增加Tab停靠且离条结束方向repeat", this::narrowStrip);
        scenario("正式项目详情保存名称备注并跨面板显示结果", this::projectDetails);
        scenario("纯文字面板自身参与Tab而编辑面板直接进入子项", this::plainPanel);
        scenario("标签语义沿真实选择重排更新且旧面板动作拒绝", this::semanticTabs);
        scenario("选中语义和面板即时更新但标记使用有限反馈", this::markerFeedback);
        scenario("通知偏好标签说明与业务选择经正式语义请求更新", this::preferencesSemantics);
    }
    private static SceneKeyEvent key(SceneKey key, boolean repeat) { return new SceneKeyEvent(key, false, false, false, false, repeat, null); }
    private static SceneSemanticSnapshot semantics(ReactiveDomain domain, ScenePageSession session) {
        SceneSemanticSnapshot[] result = new SceneSemanticSnapshot[1];
        domain.execute(() -> result[0] = session.pipeline().current().semantics); return result[0];
    }
    private static SceneSemanticSnapshot.Entry semanticEntry(SceneSemanticSnapshot snapshot, Role role, String name) {
        for (SceneSemanticSnapshot.Entry entry : snapshot.entries)
            if (entry.values.role == role && (name == null || name.equals(entry.values.name))) return entry;
        throw new AssertionError("缺少语义对象: " + role + " " + name);
    }
    private void semanticTabs() {
        Fixture f = new Fixture(520); onClose(f.session::close); f.session.frame();
        SceneSemanticSnapshot initial = semantics(f.domain, f.session);
        SceneSemanticSnapshot.Entry tab = semanticEntry(initial, Role.TAB, "概览与项目基本信息");
        SceneSemanticSnapshot.Entry panel = semanticEntry(initial, Role.TAB_PANEL, "概览与项目基本信息");
        SceneSemanticSnapshot.Entry oldField = semanticEntry(initial, Role.TEXT_FIELD, null);
        require(Boolean.TRUE.equals(tab.values.selected) && tab.values.index == 1 && tab.values.count == 3
                && semanticEntry(initial, Role.TAB_LIST, null).values.count == 3
                && panel.labelledBy.equals(Collections.singletonList(tab.id)) && !panel.focusable,
                "标签角色、逻辑位置或当前编辑面板关系不正确");
        SceneNode original = f.fields.get("a");
        f.domain.execute(() -> {
            f.title.set("更新后的完整概览标题"); f.allowed.set(false);
            List<SceneTabs.Tab<String>> next = new ArrayList<>(f.source.get()); Collections.reverse(next); f.source.set(next);
        }); f.session.frame();
        SceneSemanticSnapshot renamed = semantics(f.domain, f.session);
        SceneSemanticSnapshot.Entry current = renamed.get(tab.id);
        require("更新后的完整概览标题".equals(current.values.name) && current.values.index == 3
                && Boolean.TRUE.equals(current.values.selected) && !current.enabled && current.actions.isEmpty()
                && renamed.get(panel.id).labelledBy.equals(Collections.singletonList(tab.id))
                && "更新后的完整概览标题".equals(renamed.get(panel.id).values.name),
                "当前禁用标签丢失选择、重排位置或动态面板标签");
        f.domain.execute(() -> require(f.fields.get("a") == original && !original.destroyed(), "语义更新重建了原活动字段"));
        SceneSemanticSnapshot.Entry nextTab = semanticEntry(renamed, Role.TAB, "备注与独立编辑草稿");
        CompletableFuture<SceneSemanticRequest.Result> focus = f.session.semantic(SceneSemanticRequest.of(nextTab.id, Action.FOCUS));
        CompletableFuture<SceneSemanticRequest.Result> select = f.session.semantic(SceneSemanticRequest.of(nextTab.id, Action.SELECT));
        CompletableFuture<SceneSemanticRequest.Result> stale = f.session.semantic(SceneSemanticRequest.text(oldField.id, "不得进入新面板"));
        f.session.frame();
        require(focus.join() == SceneSemanticRequest.Result.APPLIED && select.join() == SceneSemanticRequest.Result.APPLIED
                && stale.join() == SceneSemanticRequest.Result.REJECTED, "标签切换后同批旧字段动作未按当前挂载拒绝");
        SceneSemanticSnapshot switched = semantics(f.domain, f.session);
        SceneSemanticSnapshot.Entry nextPanel = semanticEntry(switched, Role.TAB_PANEL, "备注与独立编辑草稿");
        require(switched.get(oldField.id) == null && nextPanel.labelledBy.equals(Collections.singletonList(nextTab.id))
                && nextTab.id.equals(switched.focused) && Boolean.TRUE.equals(switched.get(nextTab.id).values.selected)
                && Boolean.TRUE.equals(initial.get(tab.id).values.selected) && "概览与项目基本信息".equals(initial.get(tab.id).values.name),
                "切换后的实际焦点、面板关系或旧快照不正确");
        f.domain.execute(() -> require("b".equals(f.selected.get()) && f.drafts.get("b").snapshot().get().text.isEmpty(),
                "语义选择没更新原业务来源或旧动作投到了新字段"));
        SceneNode preserved = f.fields.get("b");
        f.domain.execute(() -> f.source.set(Collections.singletonList(new SceneTabs.Tab<>("b", () -> "同键新标题", () -> f.field("b")))));
        f.session.frame();
        SceneSemanticSnapshot replaced = semantics(f.domain, f.session);
        require("同键新标题".equals(replaced.get(nextTab.id).values.name)
                && semanticEntry(replaced, Role.TAB_PANEL, "同键新标题").labelledBy.equals(Collections.singletonList(nextTab.id)),
                "同键新声明破坏标签挂载身份或面板关系");
        f.domain.execute(() -> require(f.fields.get("b") == preserved && !preserved.destroyed(), "同键语义变化重置原面板"));
        f.domain.execute(() -> f.selected.set("missing")); f.session.frame();
        require(semantics(f.domain, f.session).entries.stream().noneMatch(entry -> entry.values.role == Role.TAB_PANEL),
                "无效活动键仍输出旧面板语义");
    }
    private void activePanel() {
        Fixture f = new Fixture(520); onClose(f.session::close); f.session.frame();
        require(f.created.equals(Collections.singletonMap("a", 1)), "建立了非活动标签面板");
        f.session.navigation(SceneNavigationEvent.NEXT); f.session.navigation(SceneNavigationEvent.NEXT);
        f.session.edit(SceneEditEvent.text("草稿甲")); f.session.frame(); SceneNode old = f.fields.get("a");
        f.session.navigation(SceneNavigationEvent.PREVIOUS);
        f.session.key(key(SceneKey.RIGHT, false));
        f.session.navigation(SceneNavigationEvent.NEXT); f.session.edit(SceneEditEvent.text("草稿乙")); f.session.frame();
        f.domain.execute(() -> require(old.destroyed() && f.selected.get().equals("b") && f.focused() == f.fields.get("b")
                && f.drafts.get("a").snapshot().get().text.equals("草稿甲") && f.drafts.get("b").snapshot().get().text.equals("草稿乙"),
                "切换面板后的Tab和文字没有进入实际新字段或丢了独立草稿"));
        f.session.navigation(SceneNavigationEvent.PREVIOUS); f.session.key(key(SceneKey.LEFT, false)); f.session.frame();
        f.domain.execute(() -> require(f.fields.get("a") != old && f.drafts.get("a").snapshot().get().text.equals("草稿甲")
                && f.created.get("a") == 2, "返回标签没有以独立草稿建立新呈现"));
    }
    private void definitionIdentity() {
        Fixture f = new Fixture(520); onClose(f.session::close); f.session.frame();
        SceneNode original = f.fields.get("a"), label = f.tab("概览与项目基本信息");
        f.domain.execute(() -> { f.title.set("新的概览标题"); List<SceneTabs.Tab<String>> next = new ArrayList<>(f.source.get()); Collections.reverse(next); f.source.set(next); f.allowed.set(false); });
        f.session.frame();
        f.domain.execute(() -> require(f.fields.get("a") == original && !original.destroyed() && f.tab("新的概览标题") == label
                && f.selected.get().equals("a"), "标题/顺序/当前禁用不应替换活动内容或标签"));
        f.domain.execute(() -> f.source.set(Arrays.asList(new SceneTabs.Tab<>("a", () -> "替换定义", () -> false, () -> f.field("a"))))); f.session.frame();
        f.domain.execute(() -> require(!original.destroyed() && f.fields.get("a") == original && f.tab("替换定义") == label,
                "同键新声明意外清掉当前面板或破坏标签身份"));
        f.domain.execute(() -> { f.source.set(Collections.singletonList(new SceneTabs.Tab<>("a-new", () -> "新业务键", () -> f.field("a")))); f.selected.set("a-new"); }); f.session.frame();
        f.domain.execute(() -> require(original.destroyed() && f.fields.get("a") != original, "换键没有明确重置原面板"));
        f.domain.execute(() -> f.selected.set("missing")); f.session.frame();
        require(f.fields.get("a").destroyed(), "活动键缺失仍保留旧面板");
        f.domain.execute(() -> f.source.set(Collections.emptyList())); f.session.frame();
        f.domain.execute(() -> require(f.selected.get().equals("missing"), "无效或空标签来源自动写回了业务默认值"));
    }
    private void narrowStrip() {
        Fixture f = new Fixture(180); onClose(f.session::close); f.session.frame();
        f.session.navigation(SceneNavigationEvent.NEXT); f.session.frame();
        f.domain.execute(() -> require(f.tabs.headerViewport().snapshot(f.session.pipeline().current().geometry).maximum > 0,
                "窄窗没有实际横向范围，不能证明显式滚动条停靠被包含"));
        f.session.key(key(SceneKey.RIGHT, false)); f.session.key(key(SceneKey.RIGHT, true)); f.session.frame();
        f.domain.execute(() -> require(f.selected.get().equals("c") && f.focused() == f.tab("活动记录与变更历史")
                && f.tabs.headerViewport().snapshot(f.session.pipeline().current().geometry).offset > 0, "标签方向repeat没有切换并显露实际远端目标: selected=" + f.selected.get() + ", focused=" + (f.focused() == null ? "null" : f.focused().text())
                        + ", offset=" + f.tabs.headerViewport().snapshot(f.session.pipeline().current().geometry).offset
                        + ", maximum=" + f.tabs.headerViewport().snapshot(f.session.pipeline().current().geometry).maximum));
        f.session.navigation(SceneNavigationEvent.NEXT); f.session.edit(SceneEditEvent.text("记录")); f.session.frame();
        f.domain.execute(() -> require(f.focused() == f.fields.get("c") && f.drafts.get("c").snapshot().get().text.equals("记录"),
                "窄窗横条成为了标签与面板间的额外Tab停靠"));
        f.session.key(key(SceneKey.RIGHT, true)); f.session.frame();
        f.domain.execute(() -> require(f.selected.get().equals("c") && f.focused() == f.fields.get("c"), "离开标签条的旧repeat改投了面板或新标签"));
    }
    private void plainPanel() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        ScenePageContext[] context = new ScenePageContext[1]; SceneTabs<String>[] tabs = new SceneTabs[1]; SceneNode[] content = new SceneNode[1], after = new SceneNode[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page;
            tabs[0] = SceneTabs.create(page, () -> Collections.singletonList(new SceneTabs.Tab<>("info", () -> "说明", () -> {
                content[0] = club.heiqi.uilib.ui.scene.control.SceneLabel.create(page, () -> "纯文字面板内容", page.theme()); return content[0];
            })), () -> "info", ignored -> {});
            after[0] = SceneButton.create(page, () -> "之后", page.theme(), () -> {});
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(tabs[0].node()).add(after[0]));
        }); onClose(session::close); session.frame(); session.navigation(SceneNavigationEvent.NEXT); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == content[0].parent(), "纯文字面板不能由Tab到达"));
        SceneSemanticSnapshot.Entry panel = semanticEntry(semantics(domain, session), Role.TAB_PANEL, "说明");
        require(panel.focusable && panel.focused && panel.actions.contains(Action.FOCUS), "纯文字面板没有语义停靠动作");
        CompletableFuture<SceneSemanticRequest.Result> focused = session.semantic(SceneSemanticRequest.of(panel.id, Action.FOCUS));
        session.frame(); require(focused.join() == SceneSemanticRequest.Result.APPLIED, "纯文字面板语义聚焦未沿原路由执行");
        session.navigation(SceneNavigationEvent.NEXT); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == after[0], "面板Tab没有继续外部顺序"));
    }

    private void projectDetails() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        host.width = 760; host.height = 640;
        ScenePageContext[] context = new ScenePageContext[1]; SceneNode[] root = new SceneNode[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; root[0] = club.heiqi.uilib.testing.gallery.ProjectDetailsPage.create(page, page.theme(), null); page.mount(root[0]);
        }); onClose(session::close); session.frame();
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(Fixture.find(root[0], "名称与备注"))));
        session.navigation(SceneNavigationEvent.ACTIVATE); session.navigation(SceneNavigationEvent.NEXT);
        session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL, false, null)); session.edit(SceneEditEvent.text("新项目名称"));
        session.navigation(SceneNavigationEvent.NEXT);
        session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL, false, null)); session.edit(SceneEditEvent.text("跨标签保留的新备注"));
        session.navigation(SceneNavigationEvent.NEXT); session.navigation(SceneNavigationEvent.ACTIVATE); session.frame();
        domain.execute(() -> require(Fixture.find(root[0], "项目名称和备注已保存。") != null, "正式项目保存没有提供成功反馈"));
        SceneSemanticSnapshot saved = semantics(domain, session);
        SceneSemanticSnapshot.Entry name = semanticEntry(saved, Role.TEXT_FIELD, "项目名称");
        SceneSemanticSnapshot.Entry notes = semanticEntry(saved, Role.TEXT_AREA, "项目备注");
        require("新项目名称".equals(name.values.value) && "跨标签保留的新备注".equals(notes.values.value)
                && name.labelledBy.equals(Collections.singletonList(semanticEntry(saved, Role.LABEL, "项目名称").id))
                && notes.labelledBy.equals(Collections.singletonList(semanticEntry(saved, Role.LABEL, "项目备注").id)),
                "真实项目字段没有把标签名称与当前值分开");
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(Fixture.find(root[0], "项目概览"))));
        session.navigation(SceneNavigationEvent.ACTIVATE); session.frame();
        domain.execute(() -> require(Fixture.find(root[0], "新项目名称") != null && Fixture.find(root[0], "跨标签保留的新备注") != null,
                "保存结果没有通过业务来源反映在新概览面板"));
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(Fixture.find(root[0], "活动记录"))));
        session.navigation(SceneNavigationEvent.ACTIVATE); session.frame();
        domain.execute(() -> require(Fixture.find(root[0], "已保存：新项目名称") != null, "活动面板没有消费实际保存记录"));
        require(semantics(domain, session).get(name.id) == null && semantics(domain, session).get(notes.id) == null,
                "退出编辑面板后字段和原标签关系仍在当前快照中");
    }

    private void preferencesSemantics() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        host.width = 760; host.height = 900;
        ScenePageSession session = ScenePageSession.open(domain, host, host,
                page -> page.mount(club.heiqi.uilib.testing.gallery.PreferencesPage.create(page, page.theme(), null)));
        onClose(session::close); session.frame();
        SceneSemanticSnapshot initial = semantics(domain, session);
        SceneSemanticSnapshot.Entry group = semanticEntry(initial, Role.RADIO_GROUP, "通知频率");
        SceneSemanticSnapshot.Entry instant = semanticEntry(initial, Role.RADIO, "实时通知");
        require(group.labelledBy.equals(Collections.singletonList(semanticEntry(initial, Role.LABEL, "通知频率").id))
                && group.describedBy.equals(Collections.singletonList(semanticEntry(initial, Role.LABEL, "当前离线，实时通知暂不可用。").id))
                && "当前离线，实时通知暂不可用。".equals(group.values.description) && !instant.enabled && instant.actions.isEmpty(),
                "通知频率缺少原标签、动态说明或禁用事实");
        CompletableFuture<SceneSemanticRequest.Result> connect = session.semantic(SceneSemanticRequest.of(
                semanticEntry(initial, Role.BUTTON, "连接网络").id, Action.ACTIVATE));
        session.frame(); require(connect.join() == SceneSemanticRequest.Result.APPLIED, "原网络动作未执行");
        SceneSemanticSnapshot online = semantics(domain, session);
        require(online.get(group.id).labelledBy.equals(group.labelledBy) && online.get(group.id).describedBy.equals(group.describedBy)
                && "网络连接正常，可选择实时通知。".equals(online.get(group.id).values.description) && online.get(instant.id).enabled,
                "原网络来源变化没更新说明和选项资格");
        CompletableFuture<SceneSemanticRequest.Result> select = session.semantic(SceneSemanticRequest.of(instant.id, Action.SELECT));
        session.frame(); require(select.join() == SceneSemanticRequest.Result.APPLIED
                && Boolean.TRUE.equals(semantics(domain, session).get(instant.id).values.checked), "通知语义选择未使用原业务来源");
        SceneSemanticSnapshot selected = semantics(domain, session);
        CompletableFuture<SceneSemanticRequest.Result> disable = session.semantic(SceneSemanticRequest.of(
                semanticEntry(selected, Role.SWITCH, "接收项目通知").id, Action.ACTIVATE));
        session.frame(); require(disable.join() == SceneSemanticRequest.Result.APPLIED, "通知开关未走原动作");
        SceneSemanticSnapshot disabled = semantics(domain, session);
        require(!disabled.get(group.id).enabled && disabled.get(instant.id).actions.isEmpty()
                && Boolean.TRUE.equals(disabled.get(instant.id).values.checked) && disabled.get(group.id).labelledBy.equals(group.labelledBy),
                "禁用通知丢失已选事实或标签阅读关系");
        CompletableFuture<SceneSemanticRequest.Result> rejected = session.semantic(SceneSemanticRequest.of(
                semanticEntry(disabled, Role.RADIO, "每周汇总").id, Action.SELECT));
        session.frame(); require(rejected.join() == SceneSemanticRequest.Result.REJECTED, "禁用频率仍接受语义选择");
    }
    private void markerFeedback() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost text = new MultilineEditingScenario.PlainHost();
        long[] now = {1_000_000_000L};
        club.heiqi.uilib.ui.scene.host.SceneHost host = new club.heiqi.uilib.ui.scene.host.SceneHost() {
            public int width() { return text.width; }
            public int height() { return text.height; }
            public long metricsRevision() { return 0; }
            public long frameNanos() { return now[0]; }
            public void replay(club.heiqi.uilib.ui.scene.paint.PaintPlan plan) { text.replay(plan); }
        };
        Signal<String>[] selected = new Signal[1]; SceneTabs<String>[] tabs = new SceneTabs[1];
        club.heiqi.uilib.ui.scene.control.SceneTheme markerTheme = club.heiqi.uilib.ui.scene.control.SceneTheme.light().withMotion(
                club.heiqi.uilib.ui.scene.motion.MotionSpec.tween(140_000_000L, club.heiqi.uilib.ui.scene.motion.MotionSpec.LINEAR),
                club.heiqi.uilib.ui.scene.control.SceneTheme.light().spatialMotion);
        try (ScenePageSession page = ScenePageSession.open(domain, host, text, context -> {
            selected[0] = domain.signal("a");
            tabs[0] = SceneTabs.create(context, () -> Arrays.asList(
                    new SceneTabs.Tab<>("a", () -> "甲", () -> SceneButton.create(context, () -> "面板甲", context.theme(), () -> { })),
                    new SceneTabs.Tab<>("b", () -> "乙", () -> SceneButton.create(context, () -> "面板乙", context.theme(), () -> { }))), selected[0], selected[0]::set, () -> markerTheme);
            context.mount(tabs[0].node());
        })) {
            page.frame();
            SceneNode[] labels = new SceneNode[2];
            domain.execute(() -> { labels[0] = Fixture.find(tabs[0].node(), "甲").parent(); labels[1] = Fixture.find(tabs[0].node(), "乙").parent(); });
            require(markerAlpha(domain, page, text.lastPlan, labels[0]) == 255, "首次挂载标记不应从零入场");
            now[0] += 1_000_000L; domain.execute(() -> selected[0].set("b")); page.frame();
            domain.execute(() -> require(Fixture.find(tabs[0].node(), "面板乙") != null && Fixture.find(tabs[0].node(), "面板甲") == null,
                    "反馈动效延迟了真实活动面板切换"));
            now[0] += 70_000_000L; page.frame();
            int fading = markerAlpha(domain, page, text.lastPlan, labels[0]), appearing = markerAlpha(domain, page, text.lastPlan, labels[1]);
            require(fading > 0 && fading < 255 && appearing > 0 && appearing < 255, "选中标记跳过共享反馈中间值");
            now[0] += 1_000_000_000L; page.frame();
            require(markerAlpha(domain, page, text.lastPlan, labels[0]) == 0 && markerAlpha(domain, page, text.lastPlan, labels[1]) == 255,
                    "有限标记反馈没有落定");
        }
    }
    private static int markerAlpha(ReactiveDomain domain, ScenePageSession page, club.heiqi.uilib.ui.scene.paint.PaintPlan plan, SceneNode label) {
        club.heiqi.uilib.ui.scene.layout.LayoutBox[] target = {null};
        domain.execute(() -> {
            club.heiqi.uilib.ui.scene.layout.LayoutBox box = page.pipeline().current().geometry.get(label).box;
            target[0] = new club.heiqi.uilib.ui.scene.layout.LayoutBox(box.x, box.y + Math.max(0, box.height - 2), box.width, Math.min(2, box.height));
        });
        for (club.heiqi.uilib.ui.scene.paint.PaintCommand command : plan.commands())
            // LayoutBox不定义值相等；新建期望盒必须比较四个坐标，不能按对象身份漏掉真实标记。
            if (command.kind == club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.RECT
                    && command.box.x == target[0].x && command.box.y == target[0].y
                    && command.box.width == target[0].width && command.box.height == target[0].height) return command.color >>> 24;
        return 0;
    }
    private static final class Fixture {
        final ReactiveDomain domain = ReactiveDomain.direct(); final MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        final Map<String, SceneEditState> drafts = new HashMap<>(); final Map<String, SceneNode> fields = new HashMap<>();
        final Map<String, Integer> created = new HashMap<>(); final ScenePageSession session;
        ScenePageContext context; SceneTabs<String> tabs; Signal<String> selected, title; Signal<Boolean> allowed;
        Signal<List<SceneTabs.Tab<String>>> source;
        Fixture(int width) {
            host.width = width;
            session = ScenePageSession.open(domain, host, host, page -> {
                context = page; selected = domain.signal("a"); title = domain.signal("概览与项目基本信息"); allowed = domain.signal(true);
                for (String id : Arrays.asList("a", "b", "c")) drafts.put(id, new SceneEditState(domain, ""));
                source = domain.signal(Arrays.asList(new SceneTabs.Tab<>("a", title, allowed, () -> field("a")),
                        new SceneTabs.Tab<>("b", () -> "备注与独立编辑草稿", () -> field("b")),
                        new SceneTabs.Tab<>("c", () -> "活动记录与变更历史", () -> field("c"))));
                tabs = SceneTabs.create(page, source, selected, selected::set);
                page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(SceneButton.create(page, () -> "之前", page.theme(), () -> {})).add(tabs.node()));
            });
        }
        SceneNode field(String id) { created.put(id, created.getOrDefault(id, 0) + 1); SceneNode node = SceneTextField.create(context, drafts.get(id), context.theme(), () -> false, request -> {}); fields.put(id, node); return node; }
        SceneNode focused() { return context.runtime().focusRouter().focused().get(); }
        SceneNode tab(String name) { return find(tabs.node(), name); }
        private static SceneNode find(SceneNode node, String text) {
            if (text.equals(node.text())) return node;
            for (SceneNode child : node.children()) { SceneNode found = find(child, text); if (found != null) return found; }
            return null;
        }
    }
}
