package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.gallery.ProjectDetailsPage;
import club.heiqi.uilib.ui.scene.control.SceneLayer;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.control.SceneMenu;
import club.heiqi.uilib.ui.scene.control.SceneSelect;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 弹出选择、共享动作和关闭都走正式页面输入；不使用平台窗口或系统输入。 */
public final class PopupControlsScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new PopupControlsScenario().run(args); }
    @Override protected void runTests() throws Exception {
        scenario("打开当前选择方向只预览且确认读取业务拒绝", this::selection);
        scenario("外部主键关闭吞UP且隐藏来源恢复不重开", this::outsideAndSource);
        scenario("空与全禁用弹层的KEY及逻辑Tab仍关闭", this::emptyTab);
        scenario("屏外当前选择按键有限准备并在本次打开显露", this::farInitial);
        scenario("动作菜单和原按钮使用同一共享命令资格", this::menuCommand);
        scenario("合法模态覆盖保留弹层而来源完整裁剪结束本次打开", this::coverAndClip);
        scenario("当前来源重排和禁用后方向续段沿实际项且关闭不复活", this::dynamicRepeat);
        scenario("正式项目页分配负责人并从菜单执行原保存命令", this::projectWorkflow);
        scenario("菜单动作失败保留原错且不执行后续宿主", this::commandFailure);
        scenario("窗口缩短后弹层正文真实受限且末项完整显露", this::shortViewport);
        scenario("参考选择器闭合字符选择开启边缘及悬停预览", this::referenceSelection);
        scenario("参考菜单Up从末项开启且字符只预览后确认", this::menuOpening);
        scenario("原触发器按下即展开并可拖入选项释放选择", this::pointerOpening);
        scenario("关闭弹层释放失败后不执行旧Tab续行", this::failedTabExit);
    }
    private static SceneKeyEvent key(SceneKey value, boolean repeat) { return new SceneKeyEvent(value, false, false, false, false, repeat, null); }
    private void failedTabExit() {
        RuntimeException expected = new RuntimeException("弹层资源关闭失败");
        Fixture f = new Fixture(() -> { throw expected; }); onClose(f.session::close); f.session.frame(); f.open();
        f.session.key(key(SceneKey.TAB, false));
        try { f.session.frame(); throw new AssertionError("释放失败被吞掉"); }
        catch (RuntimeException failure) { require(failure == expected, "没有保留原关闭错误"); }
        f.session.frame();
        f.domain.execute(() -> require(f.modal() == null && f.focused() == f.trigger(), "失败后后续帧继续了旧Tab退出意图"));
    }

    private void menuOpening() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        ScenePageContext[] context = new ScenePageContext[1]; int[] count = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; SceneCommand action = SceneCommand.of(() -> true, () -> count[0]++);
            page.mount(SceneMenu.create(page, () -> "Actions", () -> Arrays.asList(new SceneMenu.Item<>("first", () -> "First", action),
                    new SceneMenu.Item<>("second", () -> "Second", action), new SceneMenu.Item<>("last", () -> "Last", action))));
        }); onClose(session::close); session.frame(); session.key(key(SceneKey.UP, false)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get().text().equals("Last"), "Up未从菜单末项开启"));
        session.edit(SceneEditEvent.text("s")); session.edit(SceneEditEvent.text("e")); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get().text().equals("Second") && count[0] == 0, "菜单字符未预览或提前执行"));
        session.key(key(SceneKey.ENTER, false)); session.frame(); require(count[0] == 1, "菜单确认未执行预览项");
    }
    private void pointerOpening() {
        Fixture f = new Fixture(); onClose(f.session::close); f.session.frame(); f.domain.execute(() -> f.accept.set(true));
        int[] point = new int[2]; f.domain.execute(() -> { LayoutBox box = f.session.pipeline().current().geometry.get(f.trigger()).box;
            point[0] = box.x + 5; point[1] = box.y + 5;
        }); f.session.pointer(ScenePointerEvent.down(0, point[0], point[1])); f.session.frame();
        f.domain.execute(() -> require(f.modal() != null && f.requests.isEmpty(), "菜单直到UP才展开或DOWN已提交"));
        f.session.pointer(ScenePointerEvent.up(0, point[0], point[1])); f.session.frame();
        f.domain.execute(() -> require(f.modal() != null && f.requests.isEmpty(), "触发器原UP错误关闭或确认了菜单"));
        f.session.escape(() -> {}); f.session.frame();
        f.session.pointer(ScenePointerEvent.down(0, point[0], point[1])); f.session.frame();
        f.domain.execute(() -> { LayoutBox box = f.session.pipeline().current().geometry.get(find(f.select, "甲")).box;
            point[0] = box.x + 5; point[1] = box.y + 5;
        }); f.session.pointer(ScenePointerEvent.move(point[0], point[1])); f.session.pointer(ScenePointerEvent.up(0, point[0], point[1])); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null && f.selected.get().equals("a") && f.requests.equals(Collections.singletonList("a")),
                "原捕获不能拖入项释放选择或重复提交"));
    }

    private void referenceSelection() {
        Fixture f = new Fixture(); onClose(f.session::close); f.session.frame();
        f.domain.execute(() -> {
            f.options.set(Arrays.asList(new SceneSelect.Option<>("alpha", () -> "Alpha"), new SceneSelect.Option<>("alpine", () -> "Alpine"),
                    new SceneSelect.Option<>("beta", () -> "Beta"), new SceneSelect.Option<>("bravo", () -> "Bravo")));
            f.selected.set("beta"); f.accept.set(true);
            f.context.runtime().focusRouter().request(f.context.runtime().captureMount(f.trigger()));
        }); f.session.frame();
        f.domain.execute(() -> f.selected.set(null)); f.session.key(key(SceneKey.LEFT, false)); f.session.frame();
        f.domain.execute(() -> require(f.selected.get().equals("alpha"), "无当前值Left没有选择首个可用项"));
        f.domain.execute(() -> f.selected.set(null)); f.session.key(key(SceneKey.RIGHT, false)); f.session.frame();
        f.domain.execute(() -> require(f.selected.get().equals("alpha"), "无当前值Right没有选择首个可用项"));
        f.domain.execute(() -> {
            f.options.set(Arrays.asList(new SceneSelect.Option<>("alpha", () -> "Alpha"), new SceneSelect.Option<>("alpine", () -> "Alpine"),
                    new SceneSelect.Option<>("beta", () -> "Beta", () -> false), new SceneSelect.Option<>("bravo", () -> "Bravo"))); f.selected.set("beta");
        }); f.session.key(key(SceneKey.LEFT, false)); f.session.frame();
        f.domain.execute(() -> require(f.selected.get().equals("alpine"), "禁用当前项丢失向前位置锚点"));
        f.domain.execute(() -> f.selected.set("beta")); f.session.key(key(SceneKey.RIGHT, false)); f.session.frame();
        f.domain.execute(() -> require(f.selected.get().equals("bravo"), "禁用当前项丢失向后位置锚点"));
        f.domain.execute(() -> { f.options.set(Arrays.asList(new SceneSelect.Option<>("alpha", () -> "Alpha"), new SceneSelect.Option<>("alpine", () -> "Alpine"),
                new SceneSelect.Option<>("beta", () -> "Beta"), new SceneSelect.Option<>("bravo", () -> "Bravo"))); f.selected.set("beta"); });
        f.session.edit(SceneEditEvent.text("b")); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null && f.selected.get().equals("bravo"), "闭合选择器字符选择未按当前项继续"));
        f.session.key(key(SceneKey.LEFT, false)); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null && f.selected.get().equals("beta"), "闭合选择器方向未直接选择"));
        f.open(); f.session.key(key(SceneKey.END, false)); f.session.key(key(SceneKey.DOWN, false)); f.session.frame();
        f.domain.execute(() -> require(f.focused().text().equals("Bravo") && f.selected.get().equals("beta"), "选择器边缘错误循环或预览提前提交"));
        f.session.edit(SceneEditEvent.text("a")); f.session.edit(SceneEditEvent.text("l")); f.session.frame();
        f.domain.execute(() -> require(f.focused().text().equals("Alpha"), "弹出前缀未定位到匹配项"));
        int[] point = new int[2]; f.domain.execute(() -> {
            SceneNode bravo = find(f.select, "Bravo"); LayoutBox box = f.session.pipeline().current().geometry.get(bravo).box;
            point[0] = box.x + 4; point[1] = box.y + 4;
        }); f.session.pointer(ScenePointerEvent.move(point[0], point[1])); f.session.frame();
        f.domain.execute(() -> require(f.focused().text().equals("Bravo") && f.selected.get().equals("beta")
                && !f.context.runtime().focusRouter().focusVisible(f.focused()), "鼠标悬停未预览实际项或使用了键盘线索"));
        f.session.key(key(SceneKey.TAB, false)); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null && f.focused() == f.after && f.selected.get().equals("beta"), "Tab没有一次离开或提交了预览"));
    }

    private void shortViewport() {
        Fixture f = new Fixture(); onClose(f.session::close); f.session.frame();
        f.domain.execute(() -> {
            List<SceneSelect.Option<String>> source = new ArrayList<>();
            for (int i = 0; i < 1000; i++) { final String name = "条目" + i; source.add(new SceneSelect.Option<>(String.valueOf(i), () -> name)); }
            f.options.set(source); f.selected.set("900");
        }); f.open();
        f.host.height = 120; f.session.frame();
        f.session.key(key(SceneKey.END, false)); f.session.frame();
        f.domain.execute(() -> {
            SceneGeometryView geometry = f.session.pipeline().current().geometry; SceneGeometryView.Entry target = geometry.get(f.focused());
            require(f.focused().text().equals("条目999") && geometry.get(f.modal()).box.height <= f.host.height
                    && target.box.y >= target.clip.y && (long)target.box.y + target.box.height <= Math.min(f.host.height, (long)target.clip.y + target.clip.height),
                    "缩短窗口后的弹层仍按自然高建立过大正文，末项在页面之外");
        });
    }

    private void projectWorkflow() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        host.width = 820; host.height = 700; ScenePageContext[] context = new ScenePageContext[1]; SceneNode[] root = new SceneNode[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; root[0] = ProjectDetailsPage.create(page, page.theme(), null); page.mount(root[0]);
        }); onClose(session::close); session.frame();
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(find(root[0], "林岚"))));
        session.key(key(SceneKey.ENTER, false)); session.key(key(SceneKey.DOWN, false)); session.key(key(SceneKey.ENTER, false)); session.frame();
        domain.execute(() -> require(find(root[0], "负责人已改为周宁。") != null && find(root[0], "周宁") != null, "正式选择器没更新项目负责人和反馈"));
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(find(root[0], "更多操作"))));
        session.key(key(SceneKey.ENTER, false)); session.key(key(SceneKey.ENTER, false)); session.frame();
        domain.execute(() -> require(find(root[0], "项目名称和备注已保存。") != null, "菜单没执行原保存命令"));
    }
    private static SceneNode find(SceneNode root, String name) {
        if (name.equals(root.text())) return root;
        for (SceneNode child : root.children()) { SceneNode found = find(child, name); if (found != null) return found; }
        return null;
    }
    private void commandFailure() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        RuntimeException expected = new RuntimeException("菜单保存失败"); int[] fallback = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> page.mount(SceneMenu.create(page, () -> "执行操作",
                () -> Collections.singletonList(new SceneMenu.Item<>("fail", () -> "失败操作", SceneCommand.of(() -> true, () -> { throw expected; }))))));
        onClose(session::close); session.frame(); session.navigation(SceneNavigationEvent.ACTIVATE); session.frame();
        session.key(key(SceneKey.ENTER, false), () -> fallback[0]++);
        try { session.frame(); throw new AssertionError("菜单失败被吞掉"); } catch (RuntimeException failure) { require(failure == expected, "菜单没有保留原始错误"); }
        session.key(key(SceneKey.ENTER, true), () -> fallback[0]++); session.frame();
        require(fallback[0] == 0, "动作失败或旧键repeat被转给宿主");
    }

    private void selection() {
        Fixture f = new Fixture(); onClose(f.session::close); f.session.frame(); f.open();
        f.domain.execute(() -> require(f.modal() != null && f.focused().text().equals("乙"), "没有从当前有效选择进入弹层: modal=" + (f.modal() != null) + ", focus=" + (f.focused() == null ? "null" : f.focused().text()) + ", owned=" + f.trigger().children().size()));
        f.session.key(key(SceneKey.UP, false)); f.session.frame();
        f.domain.execute(() -> require(f.focused().text().equals("甲") && f.selected.get().equals("b") && f.requests.isEmpty(), "方向移动提前提交或没跳过禁用项"));
        f.session.key(key(SceneKey.ENTER, false)); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null && f.selected.get().equals("b") && f.requests.equals(Collections.singletonList("a"))
                && f.focused() == f.trigger(), "确认拒绝伪造了选择或没关闭返回"));
        f.domain.execute(() -> f.accept.set(true)); f.open();
        f.session.key(key(SceneKey.UP, false)); f.session.key(key(SceneKey.ENTER, false)); f.session.frame();
        f.domain.execute(() -> require(f.selected.get().equals("a") && f.trigger().text().equals("甲"), "业务接受没有反映到触发名称"));
    }
    private void outsideAndSource() {
        Fixture f = new Fixture(); onClose(f.session::close); f.session.frame(); f.open(); int[] point = new int[2];
        f.domain.execute(() -> { LayoutBox box = f.session.pipeline().current().geometry.get(f.behind).box; point[0] = box.x + 4; point[1] = box.y + 4; });
        f.session.pointer(ScenePointerEvent.down(0, point[0], point[1])); f.session.pointer(ScenePointerEvent.up(0, point[0], point[1])); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null && f.background[0] == 0 && f.focused() == f.trigger(), "外部关闭的UP激活后台或丢原焦点"));
        f.open(); f.domain.execute(() -> f.shown.set(false)); f.session.frame();
        f.domain.execute(() -> f.shown.set(true)); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null, "失效来源重新出现使旧弹出层复活"));
    }
    private void emptyTab() {
        for (boolean disabled : new boolean[] {false, true}) for (boolean physical : new boolean[] {false, true}) {
            Fixture f = new Fixture(); onClose(f.session::close); f.session.frame();
            f.domain.execute(() -> f.options.set(disabled ? Collections.singletonList(new SceneSelect.Option<>("x", () -> "不可用", () -> false)) : Collections.emptyList()));
            f.open(); f.domain.execute(() -> require(f.modal() != null && f.focused() == null, "空结果不应制造焦点占位项"));
            if (physical) f.session.key(key(SceneKey.TAB, false)); else f.session.navigation(SceneNavigationEvent.PREVIOUS);
            f.session.frame();
            f.domain.execute(() -> require(f.modal() == null && f.focused() == (physical ? f.after : f.behind), "无焦点弹层的Tab没有一次关闭并继续前后顺序"));
        }
    }
    private void farInitial() {
        Fixture f = new Fixture(); onClose(f.session::close); f.session.frame(); int[] names = {0};
        f.domain.execute(() -> {
            List<SceneSelect.Option<String>> source = new ArrayList<>();
            for (int i = 0; i < 1000; i++) { final String name = "选项" + i; source.add(new SceneSelect.Option<>(String.valueOf(i), () -> { names[0]++; return name; })); }
            f.options.set(source); f.selected.set("900");
        }); f.open();
        f.domain.execute(() -> {
            SceneGeometryView.Entry entry = f.session.pipeline().current().geometry.get(f.focused());
            require(f.focused().text().equals("选项900") && entry != null && entry.clip != null && entry.box.intersect(entry.clip).height > 0,
                    "屏外当前项只获焦却没有在本次打开显露");
            require(names[0] < 80, "打开当前项提前读取了全量昂贵行名称: " + names[0]);
        });
        f.session.escape(() -> { throw new AssertionError("弹层ESC落到宿主退出"); }); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null && f.focused() == f.trigger(), "ESC未关闭并还原触发焦点"));
    }
    private void coverAndClip() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        host.width = 560; host.height = 460;
        ScenePageContext[] context = new ScenePageContext[1]; Signal<Boolean>[] covering = new Signal[1];
        SceneNode[] select = new SceneNode[1], cover = new SceneNode[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; covering[0] = domain.signal(false);
            select[0] = SceneSelect.create(page, () -> Arrays.asList(new SceneSelect.Option<>("a", () -> "层内甲"),
                    new SceneSelect.Option<>("b", () -> "层内乙")), () -> "b", ignored -> {});
            SceneNode layer = SceneLayer.create(page,
                    () -> new SceneLayer.Policy(4, SceneLayer.Participation.INTERACTIVE))
                    .size(Size.fixed(320), Size.fixed(80)).add(select[0]);
            cover[0] = SceneLayer.create(page,
                    () -> new SceneLayer.Policy(4, SceneLayer.Participation.INTERACTIVE))
                    .size(Size.fixed(260), Size.fixed(80)).modal(true).add(SceneButton.create(page, () -> "独立模态", page.theme(), () -> {}))
                    .bind(NodeBinding.value(covering[0], SceneNode::visible));
            SceneNode anchor = SceneButton.create(page, () -> "层来源", page.theme(), () -> {}).add(layer);
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(anchor).add(cover[0]));
        }); onClose(session::close); session.frame();
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(select[0].children().get(0))));
        session.key(key(SceneKey.ENTER, false)); session.frame(); SceneNode[] captured = new SceneNode[1];
        domain.execute(() -> captured[0] = session.pipeline().current().geometry.activeModal());
        SceneNode popup = captured[0];
        domain.execute(() -> require(popup != null && context[0].runtime().focusRouter().focused().get().text().equals("层内乙"), "层内选择器未打开"));
        domain.execute(() -> covering[0].set(true)); session.frame();
        domain.execute(() -> require(session.pipeline().current().geometry.activeModal() == cover[0]
                && session.pipeline().current().geometry.get(select[0].children().get(0)) == null && !popup.destroyed(),
                "合法模态覆盖过滤了来源Entry后错误结束原弹层"));
        domain.execute(() -> covering[0].set(false)); session.frame();
        domain.execute(() -> require(session.pipeline().current().geometry.activeModal() == popup
                && context[0].runtime().focusRouter().focused().get().text().equals("层内乙"), "覆盖结束没恢复原合法弹层和焦点"));
        Fixture f = new Fixture(); onClose(f.session::close); f.session.frame(); f.open();
        f.domain.execute(() -> f.clipHeight.set(0)); f.session.frame();
        f.domain.execute(() -> f.clipHeight.set(48)); f.session.frame();
        f.domain.execute(() -> require(f.modal() == null, "触发器完整裁剪后恢复又打开了旧弹层"));
    }
    private void dynamicRepeat() {
        Fixture f = new Fixture(); onClose(f.session::close); f.session.frame(); f.domain.execute(() -> f.selected.set("a")); f.open();
        f.session.key(key(SceneKey.DOWN, false)); f.session.key(key(SceneKey.DOWN, true)); f.session.frame();
        f.domain.execute(() -> require(f.focused().text().equals("乙") && f.requests.isEmpty(), "弹层方向repeat没留在可用选项内或提前提交"));
        SceneNode[] original = new SceneNode[1]; f.domain.execute(() -> original[0] = f.focused());
        f.domain.execute(() -> f.options.set(Arrays.asList(new SceneSelect.Option<>("b", () -> "新乙"), new SceneSelect.Option<>("a", () -> "甲")))); f.session.frame();
        f.domain.execute(() -> require(f.focused() == original[0] && f.focused().text().equals("新乙"), "同键新值和重排破坏当前真实行身份"));
        f.session.key(key(SceneKey.DOWN, true)); f.session.frame();
        f.domain.execute(() -> require(f.focused().text().equals("甲"), "repeat未读取当前来源顺序"));
        f.session.escape(() -> { throw new AssertionError("ESC不应退出宿主"); }); f.session.frame(); f.open();
        f.session.key(key(SceneKey.DOWN, true)); f.session.frame();
        f.domain.execute(() -> require(f.focused().text().equals("甲") && f.requests.isEmpty(), "关闭再打开复活了旧方向续段"));
        f.domain.execute(() -> f.options.set(Collections.singletonList(new SceneSelect.Option<>("b", () -> "新乙", () -> false)))); f.session.frame();
        f.session.key(key(SceneKey.ENTER, false)); f.session.frame();
        f.domain.execute(() -> require(f.requests.isEmpty() && f.selected.get().equals("a"), "退出或禁用当前项仍被迟到确认"));
    }

    private void menuCommand() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<Boolean>[] allowed = new Signal[1]; ScenePageContext[] context = new ScenePageContext[1]; SceneNode[] primary = new SceneNode[1], menu = new SceneNode[1]; int[] count = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; allowed[0] = domain.signal(true); SceneCommand command = SceneCommand.of(allowed[0], () -> count[0]++);
            primary[0] = SceneButton.create(page, () -> "保存按钮", page.theme(), command::tryExecute);
            menu[0] = SceneMenu.create(page, () -> "项目操作", () -> Collections.singletonList(new SceneMenu.Item<>("save", () -> "保存命令", command)));
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(primary[0]).add(menu[0]));
        }); onClose(session::close); session.frame();
        session.navigation(SceneNavigationEvent.ACTIVATE); session.frame(); require(count[0] == 1, "共享按钮命令未执行");
        session.navigation(SceneNavigationEvent.NEXT); session.navigation(SceneNavigationEvent.ACTIVATE); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get().text().equals("保存命令"), "菜单未选择可执行命令"));
        session.navigation(SceneNavigationEvent.ACTIVATE); session.frame(); require(count[0] == 2, "菜单未执行同一命令");
        domain.execute(() -> allowed[0].set(false)); session.navigation(SceneNavigationEvent.ACTIVATE); session.frame();
        session.key(key(SceneKey.ENTER, false)); session.frame(); require(count[0] == 2, "禁用命令仍被空焦点确认执行");
        session.escape(() -> { throw new AssertionError("菜单ESC不应退出宿主"); }); session.frame();
    }
    private static final class Fixture {
        final ReactiveDomain domain = ReactiveDomain.direct(); final MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        final List<String> requests = new ArrayList<>(); final int[] background = {0}; final ScenePageSession session;
        ScenePageContext context; SceneNode select, behind, after; Signal<String> selected; Signal<Boolean> accept, shown; Signal<Integer> clipHeight; Signal<List<SceneSelect.Option<String>>> options;
        Fixture() { this(null); }
        Fixture(Runnable popupClosing) {
            host.width = 520; host.height = 420;
            session = ScenePageSession.open(domain, host, host, page -> {
                context = page; selected = domain.signal("b"); accept = domain.signal(false); shown = domain.signal(true); clipHeight = domain.signal(48);
                options = domain.signal(Arrays.asList(new SceneSelect.Option<>("a", () -> "甲"), new SceneSelect.Option<>("b", () -> "乙"), new SceneSelect.Option<>("c", () -> "禁用", () -> false)));
                select = SceneSelect.create(page, options, selected, next -> { requests.add(next); if (accept.get()) selected.set(next); })
                        .bind(NodeBinding.value(shown, SceneNode::visible));
                if (popupClosing != null) {
                    final boolean[] installed = {false};
                    select.bind(NodeBinding.value(() -> page.runtime().interactionRevision().get(), (self, revision) -> {
                        if (!installed[0]) for (SceneNode child : trigger().children()) if (child.modal()) {
                            installed[0] = true; child.own(popupClosing::run); break;
                        }
                    }));
                }
                behind = SceneButton.create(page, () -> "后台动作", page.theme(), () -> background[0]++);
                SceneNode clipped = new SceneNode().size(Size.fill(1), Size.fixed(48)).clip(true).add(select)
                        .bind(NodeBinding.value(clipHeight, (self, height) -> self.size(Size.fill(1), Size.fixed(height))));
                after = SceneButton.create(page, () -> "后续动作", page.theme(), () -> {});
                page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(behind).add(clipped).add(after));
            });
        }
        SceneNode trigger() { return select.children().get(0); }
        SceneNode focused() { return context.runtime().focusRouter().focused().get(); }
        SceneNode modal() { return session.pipeline().current().geometry.activeModal(); }
        void open() {
            domain.execute(() -> context.runtime().focusRouter().request(context.runtime().captureMount(trigger())));
            session.key(key(SceneKey.ENTER, false)); session.frame();
        }
    }
}
