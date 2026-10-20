package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.testing.gallery.GalleryCompositeLabels;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControlSize;
import club.heiqi.uilib.ui.scene.control.SceneDensity;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticId;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** 原邮箱控件及正式页面会话的确定时刻回归，不依赖 sleep 或源码结构。 */
final class MailMotionPresentation {
    private MailMotionPresentation() {}

    static void verify(FontTextService fonts) {
        themeRoles();
        try (View view = new View(fonts)) {
            SceneNode field = view.find(n -> n.control() != null && n.control().acceptsTextInput(n));
            SceneNode search = field.parent();
            SceneNode primary = view.find(n -> n.control() instanceof MailReceiveSurface);
            SceneNode entry = view.find(n -> n.control() instanceof MailNavigationSurface);
            int initialColor = view.fill(search);
            SceneGeometryView initialGeometry = view.geometry();
            view.inspection("before-focus");
            view.click(field);
            SceneGeometryView focusedGeometry = view.geometry();
            view.inspection("after-focus");
            require(view.fill(search) == initialColor, "搜索聚焦起点没有使用本批宿主时刻");
            // 聚焦和caret先完成本批即时排列；后续颜色运动必须复用此时的几何。
            view.advance(25_000_000L);
            SceneInspectionSnapshot earlyColor = view.inspection("color-25ms");
            require(view.geometry() == focusedGeometry && earlyColor.latest.measureCount == 0
                    && earlyColor.latest.arrangeCount == 0, "搜索颜色前半段触发了测量或排列");
            view.advance(25_000_000L);
            int middle = view.fill(search);
            SceneInspectionSnapshot middleColor = view.inspection("color-50ms");
            System.out.println("MAIL_SEARCH_GEOMETRY focusChanged=" + (focusedGeometry != initialGeometry)
                    + " colorReused=" + (view.geometry() == focusedGeometry));
            require(middle != initialColor && middle != view.colors.surface, "搜索在100ms ease-in中点没有实际颜色呈现");
            require(view.geometry() == focusedGeometry && middleColor.latest.measureCount == 0
                    && middleColor.latest.arrangeCount == 0, "搜索颜色运动触发了测量或排列");
            view.domain.execute(() -> view.policy.set(MotionPolicy.OFF));
            view.frame();
            require(view.fill(search) == view.colors.surface, "关闭动效未立即收敛搜索背景");
            view.still();
            view.domain.execute(() -> view.policy.set(MotionPolicy.NORMAL));
            view.page.pointer(ScenePointerEvent.down(0, 1399, 849).at(view.now));
            view.page.pointer(ScenePointerEvent.up(0, 1399, 849).at(view.now));
            view.frame();
            view.advance(100_000_000L);
            require(view.fill(search) == initialColor, "搜索失焦没有回到原背景");

            view.move(primary);
            view.advance(40_000_000L);
            int halfShadow = view.shadow(primary);
            view.advance(40_000_000L);
            int fullShadow = view.shadow(primary);
            System.out.println("MAIL_SHADOW half40ms=" + halfShadow + " full80ms=" + fullShadow);
            require(halfShadow > 0 && halfShadow < fullShadow, "主动作80ms linear阴影没有有限中间值");
            view.still();
            view.move(1399, 849);
            view.advance(80_000_000L);
            require(view.shadow(primary) == 0, "主动作离开后阴影未结束");
            view.still();

            int expandedWidth = view.box(entry).width;
            view.domain.execute(() -> view.state.toggleNavigation());
            view.frame();
            require(!view.paintedText("收件箱"), "折叠时导航文字没有立即隐藏");
            view.advance(30_000_000L);
            int reversingWidth = view.box(entry).width;
            require(reversingWidth < expandedWidth, "导航没有使用内部短过渡");
            view.domain.execute(() -> view.state.toggleNavigation());
            view.frame();
            require(view.box(entry).width == reversingWidth, "导航中途反向发生跳变");
            require(view.paintedText("收件箱"), "反向展开时导航文字没有立即出现");
            view.advance(60_000_000L);
            require(view.box(entry).width == expandedWidth, "导航反向没有沿通用TWEEN比例缩时返回");
            view.still();

            SceneSemanticId primaryId = view.semanticId(primary), fieldId = view.semanticId(field);
            CompletableFuture<SceneSemanticRequest.Result> visibleAction = view.page.semantic(
                    SceneSemanticRequest.of(primaryId, SceneSemantics.Action.ACTIVATE));
            view.frame();
            require(visibleAction.getNow(null) == SceneSemanticRequest.Result.APPLIED && view.businessActions == 1,
                    "隐藏保护的业务计数没有命中真实语义动作");
            CompletableFuture<SceneSemanticRequest.Result> visibleEdit = view.page.semantic(
                    SceneSemanticRequest.text(fieldId, "隐藏前真实查询"));
            view.frame();
            require(visibleEdit.getNow(null) == SceneSemanticRequest.Result.APPLIED
                    && "隐藏前真实查询".equals(view.query()), "隐藏保护的旧字段ID没有命中真实查询编辑");
            System.out.println("MAIL_VISIBLE_INPUT activate=" + visibleAction.getNow(null)
                    + " edit=" + visibleEdit.getNow(null) + " businessActions=" + view.businessActions
                    + " query=" + view.query());
            LayoutBox oldPrimaryBox = view.box(primary);
            view.click(field);
            view.advance(20_000_000L);
            int businessBeforeHidden = view.businessActions;
            String queryBeforeHidden = view.query();
            view.host.hidden = true; view.hiddenAt = view.now;
            view.domain.execute(() -> view.shown.set(false));
            view.hiddenFrame(0, businessBeforeHidden, queryBeforeHidden);
            CompletableFuture<SceneSemanticRequest.Result> hiddenAction = view.page.semantic(
                    SceneSemanticRequest.of(primaryId, SceneSemantics.Action.ACTIVATE));
            CompletableFuture<SceneSemanticRequest.Result> hiddenEdit = view.page.semantic(
                    SceneSemanticRequest.text(fieldId, "隐藏后旧输入"));
            int oldX = oldPrimaryBox.x + oldPrimaryBox.width / 2, oldY = oldPrimaryBox.y + oldPrimaryBox.height / 2;
            view.page.pointer(ScenePointerEvent.down(0, oldX, oldY).at(view.now));
            view.page.pointer(ScenePointerEvent.up(0, oldX, oldY).at(view.now));
            // 隐藏保留挂载；中途可能产生新的空计划，只约束实际输出和动作资格。
            view.hiddenFrame(16_000_000L, businessBeforeHidden, queryBeforeHidden);
            require(hiddenAction.getNow(null) == SceneSemanticRequest.Result.REJECTED
                    && hiddenEdit.getNow(null) == SceneSemanticRequest.Result.REJECTED, "隐藏后旧语义目标仍接受动作或编辑");
            System.out.println("MAIL_HIDDEN_INPUT activate=" + hiddenAction.getNow(null) + " edit=" + hiddenEdit.getNow(null));
            view.hiddenFrame(16_000_000L, businessBeforeHidden, queryBeforeHidden);
            view.hiddenFrame(168_000_000L, businessBeforeHidden, queryBeforeHidden);
            view.still();
            view.host.hidden = false;
            view.domain.execute(() -> view.shown.set(true));
            view.frame();
            view.page.close();
            PaintPlan closed = view.host.plan;
            int closedReplays = view.host.replays, closedActions = view.businessActions;
            view.advance(200_000_000L);
            require(view.host.plan == closed && view.host.replays == closedReplays, "卸载后仍发布迟到呈现");
            require(view.businessActions == closedActions && view.host.semantics.entries.isEmpty(), "卸载后仍有业务或可达语义");
            System.out.println("MAIL_CLOSED replayDelta=" + (view.host.replays - closedReplays)
                    + " samePlan=" + (view.host.plan == closed) + " businessActions=" + view.businessActions
                    + " semantics=" + view.host.semantics.entries.size());
        }
    }

    private static void themeRoles() {
        MotionSpec feedback = MotionSpec.tween(73_000_000L, MotionSpec.LINEAR);
        MotionSpec spatial = MotionSpec.spring(.24, .8);
        SceneTheme base = SceneTheme.light().withDensity(SceneDensity.COMPACT)
                .withControlSize(SceneControlSize.SMALL).withMotion(feedback, spatial);
        SceneTheme colored = base.withColors(1, 2, 3, 4, 5, 6, 7, 8, 9);
        require(colored.panel == 1 && colored.button == 2 && colored.hover == 3 && colored.pressed == 4
                && colored.focus == 5 && colored.disabled == 6 && colored.disabledText == 7
                && colored.tooltip == 8 && colored.tooltipText == 9, "纯配色派生没有映射完整角色色");
        sameRoles(base, colored);
        SceneTheme expected = base.withDensity(SceneDensity.COMFORTABLE).withControlSize(SceneControlSize.LARGE);
        SceneTheme actual = colored.withDensity(SceneDensity.COMFORTABLE).withControlSize(SceneControlSize.LARGE);
        require(expected.textStyle.logicalEm == actual.textStyle.logicalEm
                && expected.padding == actual.padding && expected.gap == actual.gap, "纯配色派生丢失了字号或间距声明来源");
        SceneTheme mail = MailPalette.from(base, ThemeMode.LIGHT).theme;
        sameRoles(base, mail);
        require(mail.textStyle.logicalEm == base.textStyle.logicalEm, "邮箱明确局部字号被角色二次缩放");
        SceneTheme button = MailStyle.buttonTheme(mail, false);
        sameRoles(mail, button);
        require(button.padding == mail.padding && button.gap == mail.gap, "按钮配色重新计算了主题度量");
        SceneTheme explicit = mail.withDensity(SceneDensity.COMFORTABLE).withControlSize(SceneControlSize.LARGE);
        require(explicit.textStyle.logicalEm == mail.textStyle.logicalEm, "邮箱明确局部字号没有保持");
    }
    private static void sameRoles(SceneTheme source, SceneTheme derived) {
        require(source.density == derived.density && source.controlSize == derived.controlSize
                && source.feedbackMotion == derived.feedbackMotion && source.spatialMotion == derived.spatialMotion,
                "主题配色丢失密度、尺寸或动效角色");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    private static final class Host implements SceneHost {
        final FontTextService fonts;
        PaintPlan plan;
        SceneSemanticSnapshot semantics = SceneSemanticSnapshot.EMPTY;
        int replays, semanticPublications, hiddenNonEmptyReplays, hiddenNonEmptySemantics;
        boolean hidden;
        long now;
        Host(FontTextService fonts) { this.fonts = fonts; }
        @Override public int width() { return 1400; }
        @Override public int height() { return 850; }
        @Override public long frameNanos() { return now; }
        @Override public long metricsRevision() { return fonts.generation(); }
        @Override public void replay(PaintPlan value) {
            plan = value; replays++;
            if (hidden && !value.commands().isEmpty()) hiddenNonEmptyReplays++;
        }
        @Override public void semanticsChanged(SceneSemanticSnapshot value) {
            semantics = value; semanticPublications++;
            if (hidden && !value.entries.isEmpty()) hiddenNonEmptySemantics++;
        }
    }
    private static final class View implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final Host host;
        final ScenePageSession page;
        Signal<MotionPolicy> policy;
        Signal<Boolean> shown;
        MailViewState state;
        MailPalette colors;
        MailboxModel model;
        int businessActions;
        long now, hiddenAt;
        View(FontTextService fonts) {
            host = new Host(fonts);
            page = ScenePageSession.open(domain, host, fonts, p -> {
                policy = domain.signal(MotionPolicy.NORMAL); p.motionPolicy(policy);
                p.inspectionRecording(() -> true);
                shown = domain.signal(true);
                SceneTheme local = SceneTheme.light().withTextStyle(SceneTheme.light().textStyle.withEm(14));
                state = new MailViewState(p, () -> local, () -> ThemeMode.LIGHT, () -> false, false);
                colors = state.palette.get();
                model = new MailboxModel(domain, MailSamples.messages(1)); p.scope().own(model);
                MailNavigation navigation = new MailNavigation(p, state)
                        .primary("接收邮件", SceneIconButton.Symbol.MAIL, () -> businessActions++)
                        .item("收件箱", SceneIconButton.Symbol.INBOX, () -> true, () -> 1, () -> businessActions++);
                SceneNode search = MailSearch.create(p, state.theme, state.palette, model, GalleryCompositeLabels.defaults()).node;
                SceneNode body = new SceneNode().axis(SceneNode.Axis.COLUMN).size(Size.fill(1), Size.fill(1))
                        .add(search).add(new SceneNode().size(Size.fill(1), Size.fill(1)));
                SceneNode root = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.fill(1))
                        .add(navigation.node).add(body).bind(NodeBinding.value(shown, SceneNode::visible)).own(state);
                p.mountOwned(root);
            });
            frame();
        }
        SceneSemanticId semanticId(SceneNode node) {
            // 字段提示属于description；沿公共检查快照取挂载ID，不把提示误当语义名称。
            SceneSemanticId id = page.inspect().join().entries.stream()
                    .filter(e -> node.id().equals(e.nodeId)).findFirst().get().id;
            require(host.semantics.get(id) != null, "实际控件没有当前可达语义");
            return id;
        }
        String query() {
            String[] value = {null}; domain.execute(() -> value[0] = model.query.get()); return value[0];
        }
        void hiddenFrame(long delta, int expectedActions, String expectedQuery) {
            PaintPlan previous = host.plan; int replayStart = host.replays;
            advance(delta);
            require(host.plan.commands().isEmpty() && host.hiddenNonEmptyReplays == 0, "隐藏中途回放了旧邮箱内容");
            require(host.semantics.entries.isEmpty() && host.hiddenNonEmptySemantics == 0, "隐藏中途重新发布了旧邮箱语义");
            require(businessActions == expectedActions && expectedQuery.equals(query()), "隐藏中途出现迟到业务或查询修改");
            System.out.println("MAIL_HIDDEN elapsedNanos=" + (now - hiddenAt) + " replays=" + host.replays
                    + " replayDelta=" + (host.replays - replayStart) + " planIdentity=" + System.identityHashCode(host.plan)
                    + " samePlan=" + (host.plan == previous) + " commands=" + host.plan.commands().size()
                    + " businessActions=" + businessActions + " queryUnchanged=" + expectedQuery.equals(query())
                    + " semanticPublications=" + host.semanticPublications + " semantics=" + host.semantics.entries.size());
        }
        SceneInspectionSnapshot inspection(String stage) {
            SceneInspectionSnapshot snapshot = page.inspect().join();
            System.out.println("MAIL_SEARCH_LAYOUT stage=" + stage + " nanos=" + now
                    + " geometryIdentity=" + System.identityHashCode(geometry())
                    + " generation=" + snapshot.generation + " geometryRevision=" + snapshot.geometryRevision
                    + " measures=" + snapshot.latest.measureCount + " arrangements=" + snapshot.latest.arrangeCount
                    + " paints=" + snapshot.latest.paintCollectCount);
            return snapshot;
        }
        void frame() { host.now = now; page.frame(); }
        void advance(long delta) { now += delta; frame(); }
        SceneGeometryView geometry() {
            SceneGeometryView[] value = {null};
            domain.execute(() -> value[0] = page.pipeline().current().geometry); return value[0];
        }
        SceneNode find(Predicate<SceneNode> match) {
            SceneNode[] value = {null};
            domain.execute(() -> value[0] = page.pipeline().current().geometry.ordered().stream()
                    .map(e -> e.node).filter(match).findFirst().get()); return value[0];
        }
        LayoutBox box(SceneNode node) { return geometry().get(node).box; }
        void click(SceneNode node) {
            LayoutBox box = box(node); int x = box.x + box.width / 2, y = box.y + box.height / 2;
            page.pointer(ScenePointerEvent.down(0, x, y).at(now));
            page.pointer(ScenePointerEvent.up(0, x, y).at(now)); frame();
        }
        void move(SceneNode node) {
            LayoutBox box = box(node); move(box.x + box.width / 2, box.y + box.height / 2);
            domain.execute(() -> require(page.runtime().pointerRouter().hoverTarget().get() == node, "MOVE未结算到主动作"));
        }
        void move(int x, int y) {
            page.pointer(ScenePointerEvent.move(x, y).at(now));
            // MOVE经过既有300Hz主动输入门后才形成hover；动效时长从实际目标生效后取样。
            advance((1_000_000_000L + 299) / 300);
        }
        int fill(SceneNode node) {
            LayoutBox box = box(node); int value = 0, x = box.x + box.width / 2, y = box.y + 2;
            for (PaintCommand command : host.plan.commands())
                if (command.kind == PaintCommand.Kind.RECT && command.box.contains(x, y)
                        && (command.clip == null || command.clip.contains(x, y))) value = command.color;
            return value;
        }
        int shadow(SceneNode node) {
            LayoutBox box = box(node); int alpha = 0, x = box.x + box.width / 2, y = box.y - 1;
            for (PaintCommand command : host.plan.commands())
                if (command.kind == PaintCommand.Kind.RECT && (command.color & 0xffffff) == 0x3c4043
                        && command.box.contains(x, y) && (command.clip == null || command.clip.contains(x, y)))
                    alpha = Math.max(alpha, command.color >>> 24);
            return alpha;
        }
        boolean paintedText(String text) {
            return host.plan.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT && c.text.logicalText.equals(text));
        }
        void still() {
            PaintPlan settled = host.plan; advance(20_000_000L);
            require(host.plan == settled, "静止邮箱仍每帧重建绘制计划");
        }
        @Override public void close() { page.close(); }
    }
}
