package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/** 正式通知区、PageSession 和真实路由；显式推进统一帧时间，不使用 sleep。 */
public final class NotificationsScenario extends TestApplication {
    private static final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final TooltipScenario.Host host = new TooltipScenario.Host();
        final club.heiqi.uilib.ui.scene.text.font.FontTextService text = new club.heiqi.uilib.ui.scene.text.font.FontTextService(
                java.util.Collections.singletonList(club.heiqi.uilib.ui.scene.text.font.FontSource.fromResource(
                        "notification-probe", 1, "assets/qz_uilib/fonts/unifont/unifont-16.0.04.otf", false)),
                new CoreFontEngineScenario.ContractEngine());
        final ScenePageSession page;
        SceneNotifications toast, notices;
        Signal<SceneTheme> theme;
        Signal<SceneNotifications.Options> options;
        Signal<String> content;
        SceneNode button, root;
        club.heiqi.uilib.ui.scene.runtime.MountHandle mount;
        int clicks, calls, scrolls;
        Fixture() {
            host.width = 900; host.height = 600;
            page = ScenePageSession.open(domain, host, text, ctx -> {
                theme = domain.signal(SceneAppearance.theme(false));
                options = domain.signal(new SceneNotifications.Options(SceneNotifications.Position.BOTTOM_END));
                content = domain.signal("最初正文");
                toast = SceneNotifications.createToast(ctx, theme, 2,
                        () -> new SceneNotifications.Options(SceneNotifications.Position.TOP_START, 15, 0, .5f, false));
                notices = SceneNotifications.create(ctx, theme, SceneNotifications.Mode.NOTIFICATION, 2,
                        options, () -> "关闭");
                button = SceneButton.create(ctx, () -> "后台按钮", theme, () -> clicks++)
                        .size(Size.fixed(200), Size.fixed(80));
                root = new SceneNode().size(Size.fill(1), Size.fill(1)).onScroll(delta -> scrolls++)
                        .add(button).add(toast.node()).add(notices.node());
                mount = ctx.mount(root);
            });
            page.frame();
        }
        SceneNotifications.Handle show(SceneNotifications target, String title, long duration) {
            return target.show(() -> new SceneNotice.Message(SceneNotice.Kind.INFO, title, content.get()), duration);
        }
        boolean text(String expected) {
            return host.last.commands().stream().anyMatch(c -> c.kind == PaintCommand.Kind.TEXT
                    && c.text.lines().stream().map(line -> line.text).collect(java.util.stream.Collectors.joining()).equals(expected));
        }
        int alpha(String title) {
            return host.last.commands().stream().filter(c -> c.kind == PaintCommand.Kind.TEXT && title.equals(c.text.logicalText))
                    .mapToInt(c -> c.text.style.color >>> 24).max().orElse(0);
        }
        LayoutBox box(SceneNode node) {
            LayoutBox[] result = {null};
            domain.execute(() -> result[0] = page.pipeline().current().geometry.get(node).box);
            return result[0];
        }
        void clickText(String name) {
            SceneNode[] target = {null};
            domain.execute(() -> page.pipeline().current().geometry.ordered().stream()
                    .filter(e -> e.node.control() != null && e.node.control().semantics(e.node) != null
                            && name.equals(e.node.control().semantics(e.node).values.name))
                    .findFirst().ifPresent(e -> target[0] = e.node));
            require(target[0] != null, "缺少真实按钮：" + name);
            LayoutBox b = box(target[0]);
            page.pointer(ScenePointerEvent.down(0, b.x + b.width / 2, b.y + b.height / 2));
            page.pointer(ScenePointerEvent.up(0, b.x + b.width / 2, b.y + b.height / 2));
            page.frame();
        }
        public void close() { try { page.close(); } finally { text.close(); } }
    }

    @Override protected void runTests() throws Exception {
        scenario("新通知真实入场与退场透明度沿主题有限推进", () -> {
            try (Fixture f = new Fixture()) {
                SceneNotifications.Handle[] handle = {null};
                f.domain.execute(() -> handle[0] = f.show(f.toast, "动效消息", 0)); f.page.frame();
                require(f.alpha("动效消息") == 0, "新通知没有从透明状态入场");
                f.host.frameTime += 60_000_000L; f.page.frame();
                require(f.alpha("动效消息") > 0 && f.alpha("动效消息") < 255, "通知入场没有实际中间帧");
                f.host.frameTime += 200_000_000L; f.page.frame();
                require(f.alpha("动效消息") == 255, "通知入场没有落到完整不透明");
                f.domain.execute(handle[0]::dismiss); f.page.frame();
                f.host.frameTime += 60_000_000L; f.page.frame();
                require(f.alpha("动效消息") > 0 && f.alpha("动效消息") < 255, "通知退场没有实际中间帧");
                f.host.frameTime += 200_000_000L; f.page.frame();
                require(f.alpha("动效消息") == 0, "通知退场没有结束");
            }
        });
        scenario("连续追加保序、有界容量与独立关闭", () -> {
            try (Fixture f = new Fixture()) {
                SceneNotifications.Handle[] handles = new SceneNotifications.Handle[3];
                f.domain.execute(() -> {
                    handles[0] = f.show(f.toast, "第一条", 0);
                    handles[1] = f.show(f.toast, "第二条", 0);
                    handles[2] = f.show(f.toast, "第三条", 0);
                    require(!handles[0].isOpen() && handles[1].isOpen() && handles[2].isOpen(), "容量未移出最早条目或同批追加丢失");
                });
                f.page.frame();
                f.host.frameTime += 200_000_000L; f.page.frame();
                require(f.text("第二条") && f.text("第三条") && !f.text("第一条"), "正式绘制没有呈现当前有限消息");
                f.domain.execute(handles[1]::dismiss); f.page.frame();
                f.domain.execute(() -> require(!handles[1].isOpen() && handles[2].isOpen(), "独立关闭误清其他消息"));
                f.domain.execute(f.toast::clear); f.page.frame();
            }
        });
        scenario("纯提示指针透明、无焦点抢占且不消费ESC", () -> {
            try (Fixture f = new Fixture()) {
                Object[] focus = {null};
                f.domain.execute(() -> { focus[0] = f.page.runtime().focusRouter().focused().get(); f.show(f.toast, "覆盖背景", 0); });
                f.page.frame();
                f.domain.execute(() -> require(f.page.runtime().focusRouter().focused().get() == focus[0], "纯提示夺取焦点"));
                LayoutBox b = f.box(f.button);
                f.page.pointer(ScenePointerEvent.down(0, b.x + 10, b.y + 10));
                f.page.pointer(ScenePointerEvent.up(0, b.x + 10, b.y + 10)); f.page.frame();
                require(f.clicks == 1, "纯提示拦截了后台点击");
                int[] fallback = {0}; f.page.pipeline().escape(() -> fallback[0]++); f.page.frame();
                require(fallback[0] == 1, "纯提示消费ESC");
            }
        });
        scenario("通知正文与留白滚轮透传且关闭按钮仍可用", () -> {
            try (Fixture f = new Fixture()) {
                SceneNotifications.Handle[] handle = {null};
                f.domain.execute(() -> handle[0] = f.show(f.notices, "滚轮通知", 0)); f.page.frame();
                f.host.frameTime += 200_000_000L; f.page.frame();
                LayoutBox[] body = {null};
                f.domain.execute(() -> f.page.pipeline().current().geometry.ordered().stream()
                        .filter(e -> "最初正文".equals(e.node.text())).findFirst().ifPresent(e -> body[0] = e.box));
                require(body[0] != null, "通知正文没有进入正式几何");
                f.page.pointer(ScenePointerEvent.scroll(body[0].x + 1, body[0].y + 1, 32)); f.page.frame();
                require(f.scrolls == 1, "通知正文吞掉后台滚轮");
                LayoutBox layer = f.box(f.notices.node());
                f.page.pointer(ScenePointerEvent.scroll(layer.x + 2, layer.y + 2, 32)); f.page.frame();
                require(f.scrolls == 2, "通知留白吞掉后台滚轮");
                f.clickText("关闭");
                f.domain.execute(() -> require(!handle[0].isOpen(), "局部参与使关闭按钮失去输入资格"));
            }
        });
        scenario("期限独立、正文主题更新不重启且归零后停止", () -> {
            try (Fixture f = new Fixture()) {
                SceneNotifications.Handle[] timed = new SceneNotifications.Handle[2];
                f.domain.execute(() -> timed[0] = f.show(f.toast, "短消息", 1_000_000_000L)); f.page.frame();
                f.host.frameTime += 600_000_000L;
                f.domain.execute(() -> { timed[1] = f.show(f.toast, "后续消息", 1_000_000_000L);
                    f.content.set("更新正文"); f.theme.set(SceneAppearance.theme(true)); }); f.page.frame();
                require(f.text("更新正文"), "通知未响应内容更新");
                f.host.frameTime += 500_000_000L; f.page.frame();
                f.domain.execute(() -> require(!timed[0].isOpen() && timed[1].isOpen(), "消息共享期限或内容变化延长旧期限"));
                f.host.frameTime += 600_000_000L; f.page.frame();
                f.domain.execute(() -> require(!timed[1].isOpen(), "后续期限未结束"));
                f.host.frameTime += 3_000_000_000L; f.page.frame();
                require(!f.text("短消息") && !f.text("后续消息"), "有限退场结束后仍保留消息画面");
            }
        });
        scenario("真实按钮关闭和附属动作不产生模态或抢焦点", () -> {
            try (Fixture f = new Fixture()) {
                SceneNotifications.Handle[] handle = {null}; Object[] focus = {null};
                f.domain.execute(() -> { focus[0] = f.page.runtime().focusRouter().focused().get();
                    handle[0] = f.notices.show(() -> new SceneNotice.Message(SceneNotice.Kind.SUCCESS, "归档完成", "可以撤销"), 0,
                            // 关闭会使 guard 失效；获准执行后仍须先撤下再执行一次。
                            () -> "撤销", SceneCommand.of(() -> handle[0] != null && handle[0].isOpen(), () -> {
                                require(!handle[0].isOpen(), "业务动作执行时通知仍活跃"); f.calls++;
                            })); }); f.page.frame();
                f.domain.execute(() -> require(f.page.runtime().focusRouter().focused().get() == focus[0]
                        && f.page.pipeline().current().geometry.activeModal() == null, "通知抢焦点或形成模态"));
                f.clickText("撤销"); require(f.calls == 1, "附属动作未从正式输入执行");
                f.domain.execute(() -> handle[0] = f.show(f.notices, "持续消息", 0)); f.page.frame();
                f.clickText("关闭");
                f.domain.execute(() -> require(!handle[0].isOpen(), "关闭按钮未撤下当前消息"));
            }
        });
        scenario("卸载结束短暂通知、持续通知重挂与页面释放", () -> {
            try (Fixture f = new Fixture()) {
                SceneNotifications.Handle[] handles = new SceneNotifications.Handle[2];
                int[] reads = {0};
                f.domain.execute(() -> {
                    handles[0] = f.toast.show(() -> { reads[0]++; return new SceneNotice.Message(SceneNotice.Kind.INFO, "短暂挂载", f.content.get()); }, 1_000_000_000L);
                    handles[1] = f.show(f.notices, "持续挂载", 0);
                }); f.page.frame();
                f.domain.execute(f.mount::detach);
                int detachedReads = reads[0];
                f.domain.execute(() -> { require(!handles[0].isOpen() && handles[1].isOpen(), "卸载未结束短暂通知或错误撤销持续状态"); f.content.set("卸载后更新"); });
                f.host.frameTime += 2_000_000_000L; f.page.frame();
                require(reads[0] == detachedReads, "卸载后仍读取通知内容");
                f.domain.execute(() -> f.mount = f.page.runtime().attach(null, f.root)); f.page.frame();
                f.host.frameTime += 200_000_000L; f.page.frame();
                require(!f.text("短暂挂载") && f.text("持续挂载"), "重挂回放旧短暂消息或丢失持续通知");
                f.page.close();
                f.domain.execute(() -> require(!handles[1].isOpen(), "页面结束仍保留活跃通知身份"));
            }
        });
        scenario("同批追加后立即卸载不在重挂时重放旧短暂通知", () -> {
            try (Fixture f = new Fixture()) {
                SceneNotifications.Handle[] handles = new SceneNotifications.Handle[2];
                f.domain.execute(() -> {
                    handles[0] = f.show(f.toast, "未物化短暂消息", 1_000_000_000L);
                    handles[1] = f.show(f.notices, "未物化持续消息", 0);
                    f.mount.detach();
                    require(!handles[0].isOpen() && handles[1].isOpen(), "未物化条目没有沿通知区挂载结束");
                });
                f.domain.execute(() -> f.mount = f.page.runtime().attach(null, f.root)); f.page.frame();
                f.host.frameTime += 200_000_000L; f.page.frame();
                require(!f.text("未物化短暂消息") && f.text("未物化持续消息"), "同批离开后重挂回放旧短暂通知");
            }
        });
        scenario("附属业务失败仍撤下当前通知", () -> {
            try (Fixture f = new Fixture()) {
                SceneNotifications.Handle[] handle = {null};
                IllegalStateException failure = new IllegalStateException("业务失败探针");
                f.domain.execute(() -> handle[0] = f.notices.show(
                        () -> new SceneNotice.Message(SceneNotice.Kind.ERROR, "操作失败", "点击以触发业务异常"), 0,
                        () -> "失败动作", SceneCommand.of(() -> true, () -> { throw failure; })));
                f.page.frame();
                boolean thrown = false;
                try { f.clickText("失败动作"); } catch (IllegalStateException error) { require(error == failure, "原业务异常被替换"); thrown = true; }
                require(thrown, "真实按钮没有执行业务异常路径");
                f.domain.execute(() -> require(!handle[0].isOpen(), "业务异常留下活跃通知"));
            }
        });
        scenario("当前字号、视口和RTL位置参与正式布局", () -> {
            try (Fixture f = new Fixture()) {
                f.domain.execute(() -> f.show(f.notices, "动态布局", 0)); f.page.frame();
                LayoutBox first = f.box(f.notices.node());
                f.domain.execute(() -> f.options.set(new SceneNotifications.Options(SceneNotifications.Position.TOP_START, 18, 1, .5f, true)));
                f.page.frame(); LayoutBox rtl = f.box(f.notices.node());
                require(rtl.x > f.host.width / 2 && rtl.y < first.y && rtl.width < first.width, "方向或宽度来源未使布局更新");
                f.host.width = 220; f.host.height = 300; f.page.frame();
                LayoutBox narrow = f.box(f.notices.node());
                require(narrow.x >= 0 && narrow.x + narrow.width <= 220, "窄视口未约束通知区");
            }
        });
    }
}
