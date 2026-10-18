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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 标签条和活动面板经过正式输入/生命周期，草稿由业务独立持有。 */
public final class TabsScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new TabsScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("只构造当前面板且切换后同批Tab文字进入新字段", this::activePanel);
        scenario("标题重排及禁用保留内容而同键新定义替换面板", this::definitionIdentity);
        scenario("窄窗标签横滚不增加Tab停靠且离条结束方向repeat", this::narrowStrip);
        scenario("正式项目详情保存名称备注并跨面板显示结果", this::projectDetails);
    }
    private static SceneKeyEvent key(SceneKey key, boolean repeat) { return new SceneKeyEvent(key, false, false, false, false, repeat, null); }
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
        f.domain.execute(() -> require(original.destroyed() && f.fields.get("a") != original && f.tab("替换定义") == label,
                "同键新定义未结束旧内容或破坏标签身份"));
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
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(Fixture.find(root[0], "项目概览"))));
        session.navigation(SceneNavigationEvent.ACTIVATE); session.frame();
        domain.execute(() -> require(Fixture.find(root[0], "新项目名称") != null && Fixture.find(root[0], "跨标签保留的新备注") != null,
                "保存结果没有通过业务来源反映在新概览面板"));
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(Fixture.find(root[0], "活动记录"))));
        session.navigation(SceneNavigationEvent.ACTIVATE); session.frame();
        domain.execute(() -> require(Fixture.find(root[0], "已保存：新项目名称") != null, "活动面板没有消费实际保存记录"));
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
