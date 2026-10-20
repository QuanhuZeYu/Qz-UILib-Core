package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.gallery.mail.MailboxModel;
import club.heiqi.uilib.testing.gallery.mail.MailboxPage;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.SceneKeyInputAdapter;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneCompositionSource;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneShortcut;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 共享可执行条件必须同时约束即时业务意图与正式按钮，不能依赖上一帧enabled。 */
public final class CommandScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("保存命令即时重读条件且正式按钮共享动作", this::sharedCommand);
        scenario("页面与条件模态按声明角色隔离同键", this::shortcutScopes);
        scenario("编辑Effect改变范围后旧键不重新分发", this::changedKeyRange);
        scenario("组字保留与搜索切焦先于同批后续文字", this::compositionAndSearch);
        scenario("中性键门按物理键隔离旧历史并保留编辑重复", this::physicalKeys);
        scenario("嵌入邮箱快捷键进入原搜索并随卸载结束", this::embeddedMailbox);
        scenario("同范围冲突先定位且不执行任一动作", this::shortcutConflict);
        scenario("命令和Effect失败不执行宿主继续或后续文字", this::failedCommand);
        scenario("关闭模态的应用粘贴键repeat不改外层草稿", this::applicationRepeat);
        scenario("Tab续段保留导航并读取当次Shift", this::tabRepeat);
        scenario("精确命名键组合保留编辑与组字默认", this::namedShortcuts);
        scenario("Space只在原完整按压释放时激活且失效不可复活", this::spacePress);
    }

    private static SceneKeyEvent plain(SceneKey key) {
        return new SceneKeyEvent(key, false, false, false, false, false, null);
    }

    private void namedShortcuts() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        ScenePageContext[] context = new ScenePageContext[1]; SceneNode[] targets = new SceneNode[2];
        SceneEditState[] state = new SceneEditState[1]; int[] actions = new int[5];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; state[0] = new SceneEditState(domain, "text");
            targets[0] = SceneButton.create(page, () -> "动作", page.theme(), () -> actions[4]++);
            targets[1] = SceneTextField.create(page, state[0], page.theme(), () -> false, request -> {});
            SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).initialFocus(targets[0]).add(targets[0]).add(targets[1]);
            root.bind(NodeBinding.shortcuts(SceneShortcut.Scope.PAGE,
                    SceneShortcut.of(SceneKey.ENTER, SceneCommand.of(() -> true, () -> actions[0]++), SceneShortcut.Modifier.ALT),
                    SceneShortcut.of(SceneKey.LEFT, SceneCommand.of(() -> true, () -> actions[1]++), SceneShortcut.Modifier.SHIFT),
                    SceneShortcut.of(SceneKey.F2, SceneCommand.of(() -> true, () -> actions[2]++)),
                    SceneShortcut.of(SceneKey.F, SceneCommand.of(() -> true, () -> actions[3]++)),
                    SceneShortcut.of(SceneKey.SPACE, SceneCommand.of(() -> true, () -> actions[0]++), SceneShortcut.Modifier.ALT)));
            page.mount(root);
        }); onClose(session::close); session.frame();
        session.key(new SceneKeyEvent(SceneKey.ENTER, false, false, true, false, false, null));
        session.key(new SceneKeyEvent(SceneKey.LEFT, false, true, false, false, false, null));
        session.key(plain(SceneKey.F2));
        session.key(new SceneKeyEvent(SceneKey.SPACE, false, false, true, false, false, null));
        session.key(plain(SceneKey.SPACE).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        require(actions[0] == 2 && actions[1] == 1 && actions[2] == 1 && actions[4] == 0, "非字母/Alt/Shift精确组合没有执行或被当作按钮Space");
        session.key(new SceneKeyEvent(SceneKey.ENTER, false, true, true, false, false, null)); session.frame();
        require(actions[0] == 2, "额外Shift错误匹配Alt+Enter");
        domain.execute(() -> { context[0].runtime().focusRouter().request(context[0].runtime().captureMount(targets[1])); state[0].select(2, 2); });
        session.key(plain(SceneKey.F)); session.edit(SceneEditEvent.text("f"));
        session.key(new SceneKeyEvent(SceneKey.LEFT, false, true, false, false, false, null)); session.frame();
        domain.execute(() -> require(actions[3] == 0 && actions[1] == 1 && state[0].snapshot().get().text.equals("tefxt")
                && state[0].snapshot().get().caret == 2, "字段普通字符或Shift方向被页命令抢占"));
        SceneCompositionSource source = new SceneCompositionSource();
        session.edit(SceneEditEvent.compositionBegin(source, "组字"));
        session.edit(SceneEditEvent.compositionUpdate(source, "", false));
        session.key(new SceneKeyEvent(SceneKey.ENTER, false, false, true, false, false, null)); session.frame();
        require(actions[0] == 2, "Alt命令越过UPDATE(false)真实组字联系");
        session.edit(SceneEditEvent.compositionCancel(source));
        session.key(new SceneKeyEvent(SceneKey.ENTER, false, false, true, false, false, null)); session.frame();
        require(actions[0] == 3, "组字结束后新的精确组合仍被拒绝");
    }

    private void spacePress() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        ScenePageContext[] context = new ScenePageContext[1]; SceneNode[] targets = new SceneNode[3];
        Signal<Boolean>[] enabled = new Signal[1], modal = new Signal[1], explode = new Signal[1];
        SceneEditState[] draft = new SceneEditState[1]; int[] clicks = {0}, fallbacks = {0};
        RuntimeException expected = new RuntimeException("释放动作失败");
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; enabled[0] = domain.signal(true); modal[0] = domain.signal(false); explode[0] = domain.signal(false);
            targets[0] = SceneButton.create(page, () -> "原按钮", page.theme(), () -> { if (explode[0].get()) throw expected; clicks[0]++; })
                    .bind(NodeBinding.value(enabled[0], SceneNode::enabled));
            targets[1] = SceneButton.create(page, () -> "另一目标", page.theme(), () -> {});
            draft[0] = new SceneEditState(domain, "");
            targets[2] = SceneTextField.create(page, draft[0], page.theme(), () -> false, request -> {});
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).initialFocus(targets[0]).add(targets[0]).add(targets[1]).add(targets[2]));
            page.mount(new SceneNode().size(Size.fixed(100), Size.fixed(60)).bind(NodeBinding.value(modal[0], SceneNode::modal))
                    .add(SceneButton.create(page, () -> "内层", page.theme(), () -> {})));
        }); onClose(session::close); session.frame();
        SceneKeyInputAdapter input = new SceneKeyInputAdapter(session::key);
        input.availability(true); input.historyDrained(java.util.Collections.emptySet());
        input.key(plain(SceneKey.SPACE), true); session.frame();
        domain.execute(() -> require(clicks[0] == 0 && context[0].runtime().pressed(targets[0]), "Space DOWN没有保留按压而直接激活"));
        input.key(new SceneKeyEvent(SceneKey.SPACE, false, false, false, false, true, null), true); session.frame();
        require(clicks[0] == 0, "Space repeat激活了原按钮");
        input.key(plain(SceneKey.SPACE), false); session.frame();
        domain.execute(() -> require(clicks[0] == 1 && !context[0].runtime().pressed(targets[0]), "物理UP没有结束原按压并只激活一次"));
        session.key(plain(SceneKey.SPACE).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        require(clicks[0] == 1, "没有原DOWN的UP产生了动作");
        session.key(plain(SceneKey.SPACE)); session.frame();
        domain.execute(() -> domain.batch(() -> {
            context[0].runtime().focusRouter().request(context[0].runtime().captureMount(targets[1]));
            context[0].runtime().focusRouter().request(context[0].runtime().captureMount(targets[0]));
        }));
        session.key(plain(SceneKey.SPACE).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        require(clicks[0] == 1, "合批离开再返回复活了Space按压");
        session.key(plain(SceneKey.SPACE)); session.frame();
        domain.execute(() -> enabled[0].set(false)); session.frame();
        domain.execute(() -> enabled[0].set(true)); session.frame();
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(targets[0])));
        session.key(plain(SceneKey.SPACE).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        require(clicks[0] == 1, "禁用恢复复活了旧Space按压");
        session.key(plain(SceneKey.SPACE)); session.frame();
        domain.execute(() -> modal[0].set(true)); session.frame();
        domain.execute(() -> modal[0].set(false)); session.frame();
        session.key(plain(SceneKey.SPACE).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        require(clicks[0] == 1, "模态覆盖返回复活了旧Space按压");
        domain.execute(() -> context[0].runtime().focusRouter().request(context[0].runtime().captureMount(targets[0])));
        session.key(plain(SceneKey.SPACE)); session.frame(); session.focusLost(); session.frame();
        session.key(plain(SceneKey.SPACE).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        require(clicks[0] == 1, "失焦后的旧UP激活了按钮");
        domain.execute(() -> { context[0].runtime().focusRouter().request(context[0].runtime().captureMount(targets[0])); explode[0].set(true); });
        session.key(plain(SceneKey.SPACE)); session.frame();
        session.key(plain(SceneKey.SPACE).withPhase(SceneKeyEvent.Phase.UP), () -> fallbacks[0]++);
        session.edit(SceneEditEvent.text("迟到"));
        try { session.frame(); throw new AssertionError("Space释放动作失败没有传播"); }
        catch (RuntimeException failure) { require(failure == expected, "Space释放动作错误身份改变"); }
        session.key(new SceneKeyEvent(SceneKey.SPACE, false, false, false, false, true, null)); session.frame();
        domain.execute(() -> {
            require(fallbacks[0] == 0 && !context[0].runtime().pressed(targets[0]) && draft[0].snapshot().get().text.isEmpty(), "释放失败后执行继续动作或保留按压");
            context[0].runtime().focusRouter().request(context[0].runtime().captureMount(targets[2]));
        });
        session.key(plain(SceneKey.SPACE)); session.edit(SceneEditEvent.text(" "));
        session.key(plain(SceneKey.SPACE).withPhase(SceneKeyEvent.Phase.UP)); session.frame();
        domain.execute(() -> require(draft[0].snapshot().get().text.equals(" ") && clicks[0] == 1, "字段Space没有只消费原字符来源"));
        domain.execute(() -> { explode[0].set(false); context[0].runtime().focusRouter().request(context[0].runtime().captureMount(targets[0])); });
        session.key(plain(SceneKey.ENTER)); session.frame(); require(clicks[0] == 2, "Enter没有保留DOWN激活");
        session.key(new SceneKeyEvent(SceneKey.ENTER, false, false, false, false, true, null)); session.frame();
        require(clicks[0] == 2, "Enter repeat触发了第二次动作");
        session.key(plain(SceneKey.SPACE)); session.frame(); session.close();
        domain.execute(() -> require(context[0].runtime().keyboardPressedTarget().get() == null, "关闭残留Space按压呈现"));
    }

    private void tabRepeat() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        SceneEditState[] state = new SceneEditState[1]; SceneNode[] targets = new SceneNode[3]; ScenePageContext[] context = new ScenePageContext[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page; state[0] = new SceneEditState(domain, "正文", SceneEditState.Mode.MULTI_LINE);
            targets[0] = SceneButton.create(page, () -> "前", page.theme(), () -> {});
            targets[1] = club.heiqi.uilib.ui.scene.control.SceneTextArea.create(page, state[0], page.theme(), () -> false,
                    request -> {}, SceneTextField.SubmitPolicy.CONFIRM,
                    () -> club.heiqi.uilib.ui.scene.control.SceneTextArea.TabMode.INSERT_TAB).size(Size.fill(1), Size.fixed(35));
            targets[2] = SceneButton.create(page, () -> "后", page.theme(), () -> {});
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).initialFocus(targets[0]).add(targets[0]).add(targets[1]).add(targets[2]));
        }); onClose(session::close); session.frame();
        session.key(new SceneKeyEvent(SceneKey.TAB, false, false, false, false, false, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == targets[1], "首个Tab没进多行字段"));
        SceneCompositionSource composing = new SceneCompositionSource();
        session.edit(SceneEditEvent.compositionBegin(composing, "组字"));
        session.key(new SceneKeyEvent(SceneKey.TAB, false, false, false, false, true, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == targets[1], "Tab导航续段越过有效组字"));
        session.edit(SceneEditEvent.compositionCancel(composing));
        session.key(new SceneKeyEvent(SceneKey.TAB, false, false, false, false, true, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == targets[2]
                && state[0].snapshot().get().text.equals("正文"), "导航repeat变成新字段的制表符编辑"));
        session.key(new SceneKeyEvent(SceneKey.TAB, false, true, false, false, true, null)); session.frame();
        domain.execute(() -> require(context[0].runtime().focusRouter().focused().get() == targets[1], "Tab续段未读取当次Shift"));
        session.key(new SceneKeyEvent(SceneKey.TAB, false, false, false, false, false, null)); session.frame();
        domain.execute(() -> require(state[0].snapshot().get().text.contains("\t"), "新完整按压未使用当前字段INSERT_TAB"));
    }

    private void applicationRepeat() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<Boolean>[] modal = new Signal[1]; SceneEditState[] state = new SceneEditState[1];
        int[] commands = {0};
        SceneEditEvent.Clipboard clipboard = new SceneEditEvent.Clipboard() {
            public String read() { return "不该粘贴"; }
            public void write(String value) { }
        };
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            modal[0] = domain.signal(false); state[0] = new SceneEditState(domain, "原草稿");
            SceneNode field = SceneTextField.create(page, state[0], page.theme(), () -> false, request -> {})
                    .size(Size.fill(1), Size.fixed(25));
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).initialFocus(field).add(field));
            SceneNode button = SceneButton.create(page, () -> "模态动作", page.theme(), () -> {});
            page.mount(new SceneNode().size(Size.fixed(80), Size.fixed(50)).initialFocus(button).add(button)
                    .bind(NodeBinding.value(modal[0], SceneNode::modal))
                    .bind(NodeBinding.shortcuts(SceneShortcut.Scope.MODAL,
                            SceneShortcut.primary(SceneKey.V, SceneCommand.of(() -> true, () -> { commands[0]++; modal[0].set(false); })) )));
        }); onClose(session::close); session.frame();
        domain.execute(() -> modal[0].set(true)); session.frame();
        session.key(new SceneKeyEvent(SceneKey.V, true, false, false, false, false, clipboard)); session.frame();
        session.key(new SceneKeyEvent(SceneKey.V, true, false, false, false, true, clipboard)); session.frame();
        domain.execute(() -> require(commands[0] == 1 && state[0].snapshot().get().text.equals("原草稿"),
                "应用命令repeat变为返回后的外层粘贴"));
        session.key(new SceneKeyEvent(SceneKey.V, true, false, false, false, false, clipboard)); session.frame();
        domain.execute(() -> require(state[0].snapshot().get().text.contains("不该粘贴"), "新的完整按压没有进入当前编辑"));
    }

    private void shortcutConflict() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        int[] actions = {0}; SceneNode[] anchors = new SceneNode[2];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1));
            for (int i = 0; i < 2; i++) {
                anchors[i] = new SceneNode().size(Size.fixed(50), Size.fixed(20))
                        .bind(NodeBinding.shortcuts(SceneShortcut.Scope.PAGE,
                                SceneShortcut.primary(SceneKey.F,
                                        SceneCommand.of(() -> true, () -> actions[0]++))));
                root.add(anchors[i]);
            }
            page.mount(root);
        }); onClose(session::close); session.frame();
        session.key(primary(SceneKey.F));
        try { session.frame(); throw new AssertionError("重复范围键没有定位冲突"); }
        catch (IllegalStateException failure) {
            require(failure.getMessage().contains("快捷键冲突") && failure.getMessage().contains(anchors[0].id().toString())
                    && failure.getMessage().contains(anchors[1].id().toString()), "范围冲突缺少实际anchor定位");
        }
        require(actions[0] == 0, "冲突检查前执行了候选动作");
    }

    private void failedCommand() {
        for (boolean effectFailure : new boolean[] {false, true}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
            RuntimeException original = new RuntimeException("命令失败");
            SceneEditState[] draft = new SceneEditState[1];
            int[] fallbacks = {0};
            ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
                Signal<Boolean> explode = domain.signal(false);
                domain.effect(page.scope(), () -> { if (explode.get()) throw original; });
                draft[0] = new SceneEditState(domain, "原草稿");
                SceneNode field = SceneTextField.create(page, draft[0], page.theme(), () -> false, request -> {})
                        .size(Size.fill(1), Size.fixed(25));
                SceneCommand broken = SceneCommand.of(() -> true, () -> { if (effectFailure) explode.set(true); else throw original; });
                page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).initialFocus(field).add(field)
                        .bind(NodeBinding.shortcuts(SceneShortcut.Scope.PAGE,
                                SceneShortcut.primary(SceneKey.S, broken))));
            }); onClose(session::close); session.frame();
            session.key(primary(SceneKey.S), () -> fallbacks[0]++);
            session.edit(SceneEditEvent.text("迟到"));
            try { session.frame(); throw new AssertionError("命令或Effect错误被吞掉"); }
            catch (RuntimeException failure) { require(failure == original, "错误身份改变: effect=" + effectFailure); }
            domain.execute(() -> require(fallbacks[0] == 0 && draft[0].snapshot().get().text.equals("原草稿"),
                    "失败后执行了继续动作或后续文字"));
        }
    }

    private void embeddedMailbox() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        MailboxModel[] model = new MailboxModel[1];
        Signal<Boolean>[] shown = new Signal[1];
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            shown[0] = domain.signal(true);
            model[0] = new MailboxModel(domain, java.util.Arrays.asList(
                    new MailboxModel.Message("a", "sender", "alpha", "preview", "body", "12:00", false, false, true),
                    new MailboxModel.Message("b", "sender", "beta", "preview", "body", "12:00", false, false, true)));
            page.own(model[0]);
            SceneNode content = MailboxPage.create(page, page.theme(), model[0], false);
            page.own(content::destroy);
            SceneNode deck = new SceneNode().size(Size.fill(1), Size.fill(1))
                    .bind(NodeBinding.children(() -> shown[0].get() ? java.util.Collections.singletonList(content) : java.util.Collections.emptyList()));
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(deck));
        }); onClose(session::close); session.frame();
        session.key(primary(SceneKey.F));
        session.edit(SceneEditEvent.text("alpha")); session.frame();
        domain.execute(() -> require(model[0].query.get().equals("alpha") && model[0].results.get().size() == 1,
                "内容根PAGE快捷键没有使用原搜索字段和查询模型"));
        domain.execute(() -> shown[0].set(false)); session.frame();
        int[] fallbacks = {0}; session.key(primary(SceneKey.F), () -> fallbacks[0]++); session.frame();
        require(fallbacks[0] == 1, "已卸载邮箱仍保留快捷键注册");
        domain.execute(() -> shown[0].set(true)); session.frame();
        session.key(primary(SceneKey.F)); session.frame();
        domain.execute(() -> require(model[0].query.get().equals("alpha"), "重挂或搜索聚焦重置了独立查询"));
    }

    private void physicalKeys() {
        java.util.List<SceneKeyEvent> accepted = new java.util.ArrayList<>();
        SceneKeyInputAdapter input = new SceneKeyInputAdapter(event -> {
            if (event.phase == SceneKeyEvent.Phase.DOWN) accepted.add(event);
        });
        SceneKey f = SceneKey.F;
        SceneKey s = SceneKey.S;
        input.availability(true); input.key(primary(f), true);
        input.historyDrained(java.util.Collections.singleton(f)); input.key(primary(f), true);
        require(accepted.isEmpty(), "开屏旧DOWN或持键未被隔离");
        input.key(primary(f), false); input.historyDrained(java.util.Collections.emptySet());
        input.key(primary(f), true); input.key(primary(s), true);
        require(accepted.size() == 2 && !accepted.get(0).repeat && !accepted.get(1).repeat, "多个字母错误共享同一held");
        input.key(primary(f), true);
        require(accepted.get(2).repeat, "重复DOWN变成应用命令的新按压");
        input.availability(false); input.availability(true);
        input.historyDrained(new java.util.HashSet<>(java.util.Arrays.asList(f, s)));
        input.key(primary(s), true); require(accepted.size() == 3, "失焦旧持键重新进入");
        input.key(primary(f), false); input.key(primary(s), false);
        input.historyDrained(java.util.Collections.emptySet());
        SceneKey left = SceneKey.LEFT;
        input.key(primary(left), true);
        input.key(new SceneKeyEvent(left, true, false, false, false, true, null), true);
        require(accepted.size() == 5 && accepted.get(4).repeat, "真实方向重复没有沿原编辑门进入");
        input.key(primary(left), false);
        input.key(new SceneKeyEvent(left, true, false, false, false, true, null), true);
        require(accepted.size() == 5, "没有起手的迟到repeat误进入");
        int count = accepted.size();
        input.key(primary(SceneKey.ENTER), true); input.key(primary(SceneKey.KEYPAD_ENTER), true);
        require(accepted.size() == count + 2 && !accepted.get(count).repeat && !accepted.get(count + 1).repeat,
                "主Enter和小键盘Enter共用了物理段");
    }

    private static SceneKeyEvent primary(SceneKey key) {
        return new SceneKeyEvent(key, true, false, false, false, false, null);
    }

    private void changedKeyRange() {
        for (boolean beginInside : new boolean[] {true, false}) {
            ReactiveDomain domain = ReactiveDomain.direct();
            MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
            Signal<Boolean>[] open = new Signal[1], change = new Signal[1]; int[] commands = {0}, fallbacks = {0};
            ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
                open[0] = domain.signal(beginInside); change[0] = domain.signal(false);
                domain.effect(page.scope(), () -> { if (change[0].get()) open[0].set(!beginInside); });
                SceneNode first = new SceneNode().focusable(true).size(Size.fixed(50), Size.fixed(20));
                SceneNode second = new SceneNode().focusable(true).size(Size.fixed(50), Size.fixed(20));
                SceneControl edit = new SceneControl() {
                    public MeasuredSize measure(SceneNode node,
                            Constraints limits, SceneTextMeasurer text) {
                        return new MeasuredSize(50, 20);
                    }
                    public ChildPlacements arrange(SceneNode node, LayoutBox box,
                            SceneChildMeasurer children) {
                        return new ChildPlacements(java.util.Collections.emptyList());
                    }
                    public void paint(SceneNode node, SceneGeometryView geometry,
                            PaintPlanBuilder paint) { }
                    @Override public boolean edit(SceneNode node, SceneEditEvent event) {
                        change[0].set(true); return false;
                    }
                };
                (beginInside ? second : first).control(edit);
                SceneShortcut shortcut = SceneShortcut.primary(
                        SceneKey.C, SceneCommand.of(() -> true, () -> commands[0]++));
                page.mount(new SceneNode().initialFocus(first).size(Size.fill(1), Size.fill(1)).add(first)
                        .bind(NodeBinding.shortcuts(SceneShortcut.Scope.PAGE, shortcut)));
                page.mount(new SceneNode().initialFocus(second).size(Size.fixed(50), Size.fixed(50)).add(second)
                        .bind(NodeBinding.value(open[0], SceneNode::modal))
                        .bind(NodeBinding.shortcuts(SceneShortcut.Scope.MODAL, shortcut)));
            }); onClose(session::close); session.frame();
            session.key(primary(SceneKey.C), () -> fallbacks[0]++); session.frame();
            require(commands[0] == 0 && fallbacks[0] == 0, "编辑改变范围后旧键误投: beginInside=" + beginInside);
        }
    }

    private void compositionAndSearch() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        SceneEditState[] draft = new SceneEditState[2];
        SceneNode[] fields = new SceneNode[2]; ScenePageContext[] context = new ScenePageContext[1];
        int[] copy = {0}, search = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            context[0] = page;
            for (int i = 0; i < 2; i++) {
                draft[i] = new SceneEditState(domain, "");
                fields[i] = SceneTextField.create(page, draft[i], page.theme(), () -> false,
                        submission -> {}, SceneTextField.SubmitPolicy.CONFIRM)
                        .size(Size.fill(1), Size.fixed(25));
            }
            SceneCommand find = SceneCommand.of(() -> true, () -> {
                search[0]++; page.runtime().focusRouter().request(page.runtime().captureMount(fields[1]));
            });
            SceneCommand accidental = SceneCommand.of(() -> true, () -> copy[0]++);
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).initialFocus(fields[0]).add(fields[0]).add(fields[1])
                    .bind(NodeBinding.shortcuts(SceneShortcut.Scope.PAGE,
                            SceneShortcut.primary(SceneKey.F, find),
                            SceneShortcut.primary(SceneKey.C, accidental),
                            SceneShortcut.primary(SceneKey.Z, accidental))));
        }); onClose(session::close); session.frame();
        session.key(primary(SceneKey.C));
        session.key(primary(SceneKey.Z)); session.frame();
        require(copy[0] == 0, "空选区或空历史的编辑消费漏到页面");
        SceneCompositionSource source = new SceneCompositionSource();
        session.edit(SceneEditEvent.compositionBegin(source, "拼"));
        session.edit(SceneEditEvent.compositionUpdate(source, "", false));
        session.key(primary(SceneKey.F));
        session.edit(SceneEditEvent.compositionCommit(source, "结果")); session.frame();
        domain.execute(() -> require(search[0] == 0 && draft[0].snapshot().get().text.equals("结果")
                && draft[1].snapshot().get().text.isEmpty(), "UPDATE(false)之后应用键抢走组字目标"));
        session.edit(SceneEditEvent.composition("旧组字", true));
        session.key(primary(SceneKey.F)); session.frame();
        require(search[0] == 0, "旧组字入口漏过应用键");
        session.edit(SceneEditEvent.composition("", false));
        session.key(primary(SceneKey.F));
        session.edit(SceneEditEvent.text("查询")); session.frame();
        domain.execute(() -> require(search[0] == 1 && draft[1].snapshot().get().text.equals("查询")
                && context[0].runtime().focusRouter().focused().get() == fields[1], "KEY后文字没有进入搜索字段"));
    }

    private void shortcutScopes() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<Boolean>[] modal = new Signal[1], allowed = new Signal[1];
        int[] outer = {0}, inner = {0}, hostFallback = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            modal[0] = domain.signal(false); allowed[0] = domain.signal(true);
            SceneCommand outside = SceneCommand.of(() -> true, () -> outer[0]++);
            SceneCommand inside = SceneCommand.of(allowed[0], () -> { inner[0]++; modal[0].set(false); });
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1))
                    .bind(NodeBinding.shortcuts(SceneShortcut.Scope.PAGE,
                            SceneShortcut.primary(SceneKey.F, outside))));
            page.mount(new SceneNode().size(Size.fixed(50), Size.fixed(50))
                    .bind(NodeBinding.value(modal[0], SceneNode::modal))
                    .bind(NodeBinding.shortcuts(SceneShortcut.Scope.MODAL,
                            SceneShortcut.primary(SceneKey.F, inside))));
        }); onClose(session::close); session.frame();
        java.util.function.Consumer<Boolean> key = repeat -> session.key(
                new SceneKeyEvent(SceneKey.F,
                        true, false, false, false, repeat, null), () -> hostFallback[0]++);
        key.accept(false); session.frame();
        require(outer[0] == 1 && inner[0] == 0, "首次false的模态声明参与页面键");
        domain.execute(() -> { modal[0].set(true); allowed[0].set(false); }); session.frame();
        key.accept(false); session.frame();
        require(outer[0] == 1 && inner[0] == 0 && hostFallback[0] == 0, "禁用内层快捷键漏到后台或宿主");
        domain.execute(() -> allowed[0].set(true)); key.accept(false); session.frame();
        require(inner[0] == 1 && outer[0] == 1, "内层动作关闭范围后本键又执行外层");
        key.accept(true); session.frame();
        require(outer[0] == 1, "结束内层的repeat变为外层新命令");
        key.accept(false); session.frame();
        require(outer[0] == 2 && inner[0] == 1, "modal属性关闭后绑定被提升页面或新键不可用");
    }

    private void sharedCommand() {
        ReactiveDomain domain = ReactiveDomain.direct();
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost();
        Signal<Boolean>[] dirty = new Signal[1], unsaved = new Signal[1];
        SceneCommand[] save = new SceneCommand[1]; SceneNode[] button = new SceneNode[1], menu = new SceneNode[1];
        int[] saved = {0}, legacy = {0};
        ScenePageSession session = ScenePageSession.open(domain, host, host, page -> {
            dirty[0] = domain.signal(true); unsaved[0] = domain.signal(true);
            save[0] = SceneCommand.of(() -> !dirty[0].get() && unsaved[0].get(), () -> {
                saved[0]++; unsaved[0].set(false);
            });
            button[0] = SceneButton.create(page, () -> "保存", page.theme(), save[0]);
            SceneNode oldButton = SceneButton.create(page, () -> "旧动作", page.theme(), () -> legacy[0]++);
            menu[0] = club.heiqi.uilib.ui.scene.control.SceneMenu.create(page, () -> "共享保存菜单",
                    () -> java.util.Collections.singletonList(new club.heiqi.uilib.ui.scene.control.SceneMenu.Item<>(
                            "save", () -> "菜单保存", save[0])), page.theme());
            page.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).add(button[0]).add(oldButton).add(menu[0]));
        }); onClose(session::close); session.frame();
        domain.execute(() -> {
            require(!save[0].tryExecute() && saved[0] == 0, "有未应用修改仍执行保存");
            dirty[0].set(false);
            require(save[0].tryExecute() && saved[0] == 1, "执行错误等待enabled重绘");
            require(!save[0].tryExecute() && saved[0] == 1, "动作结束后的当前条件未生效");
            unsaved[0].set(true);
        }); session.frame();
        LayoutBox[] box = new LayoutBox[1];
        domain.execute(() -> box[0] = session.pipeline().current().geometry.get(button[0]).box);
        int x = box[0].x + box[0].width / 2, y = box[0].y + box[0].height / 2;
        session.pointer(ScenePointerEvent.down(0, x, y)); session.pointer(ScenePointerEvent.up(0, x, y)); session.frame();
        require(saved[0] == 2 && legacy[0] == 0, "按钮未使用共享保存动作");
        domain.execute(() -> unsaved[0].set(true)); session.frame();
        domain.execute(() -> {
            require(button[0].enabled(), "命令重载没有同步可用呈现");
            dirty[0].set(true);
            session.runtime().dispatchClick(button[0]);
            require(saved[0] == 2, "命令按钮沿用了上一帧enabled许可");
            dirty[0].set(false);
            SceneNode trigger = session.pipeline().current().geometry.ordered().stream()
                    .map(e -> e.node).filter(n -> n.focusable() && n.text().equals("共享保存菜单")).findFirst().get();
            box[0] = session.pipeline().current().geometry.get(trigger).box;
        });
        int menuX = box[0].x + box[0].width/2, menuY = box[0].y + box[0].height/2;
        session.pointer(ScenePointerEvent.down(0, menuX, menuY)); session.pointer(ScenePointerEvent.up(0, menuX, menuY)); session.frame();
        session.key(plain(SceneKey.ENTER)); session.frame();
        require(saved[0] == 3 && legacy[0] == 0, "菜单没有执行同一保存命令");
        domain.execute(() -> require(!button[0].enabled(), "菜单执行后的命令状态没有回到同一按钮"));
        domain.execute(() -> {
            RuntimeException failure = new RuntimeException("保存失败");
            SceneCommand broken = SceneCommand.of(() -> true, () -> { throw failure; });
            try { broken.tryExecute(); throw new AssertionError("业务失败被吞掉"); }
            catch (RuntimeException actual) { require(actual == failure, "命令改写原业务错误"); }
        });
    }
}
