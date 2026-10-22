package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.hud.*;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 位置数学以独立期望核对；拖拽、取消和像素沿正式 Session 运行。 */
public final class SceneHudScenario extends TestApplication {
    protected void runTests() throws Exception {
        scenario("九锚点缩放与逻辑偏移共用绘制盒和被动输入", this::anchors);
        scenario("吸附放下更新位置且 ESC 失焦卸载取消不提交", this::drag);
        scenario("视口内容及 GUI Scale 动态变化重新约束同一节点", this::dynamic);
    }

    private final class Fixture implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SettingsHudHost host = new SettingsHudHost();
        Signal<SceneHudPlacement> placement;
        Signal<Float> guiScale;
        Signal<Integer> snap, width, height;
        Signal<Boolean> visible;
        SceneNode content, hud;
        final ScenePageSession page;
        Fixture(boolean editable) {
            host.width = 400; host.height = 300;
            page = ScenePageSession.open(domain, host, host, context -> {
                context.motionPolicy(() -> MotionPolicy.OFF);
                placement = domain.signal(new SceneHudPlacement(SceneHudPlacement.Anchor.TOP_LEFT, 10, 15, 1));
                guiScale = domain.signal(1f); snap = domain.signal(10); width = domain.signal(80); height = domain.signal(40);
                visible = domain.signal(true);
                content = new SceneNode().background(0xff28a745)
                        .bind(NodeBinding.value(width, (n, w) -> n.size(Size.fixed(w), Size.fixed(height.get()))))
                        .bind(NodeBinding.value(height, (n, h) -> n.size(Size.fixed(width.get()), Size.fixed(h))));
                hud = (editable ? SceneHud.edit(context, content, placement, guiScale, snap)
                        : SceneHud.create(context, content, placement, guiScale))
                        .bind(NodeBinding.value(visible, SceneNode::visible));
                context.mountOwned(hud);
            });
            page.frame();
        }
        LayoutBox box() {
            LayoutBox[] box = new LayoutBox[1];
            domain.execute(() -> box[0] = page.pipeline().current().geometry.get(content).box); return box[0];
        }
        int pointerX, pointerY;
        void begin() { LayoutBox b = box(); pointerX = b.x + 5; pointerY = b.y + 5; page.pointer(ScenePointerEvent.down(0, pointerX, pointerY)); page.frame(); }
        void move(int dx, int dy) { pointerX += dx; pointerY += dy; page.pointer(ScenePointerEvent.move(pointerX, pointerY)); page.frame(); }
        void release() { page.pointer(ScenePointerEvent.up(0, pointerX, pointerY)); page.frame(); }
        SceneHudPlacement value() { SceneHudPlacement[] p = new SceneHudPlacement[1]; domain.execute(() -> p[0] = placement.get()); return p[0]; }
        public void close() { page.close(); }
    }

    private void anchors() {
        try (Fixture f = new Fixture(false)) {
            SceneHudPlacement.Anchor[] anchors = SceneHudPlacement.Anchor.values();
            for (int i = 0; i < anchors.length; i++) {
                SceneHudPlacement.Anchor anchor = anchors[i];
                f.domain.execute(() -> { f.guiScale.set(2f); f.placement.set(new SceneHudPlacement(anchor, 10, 15, 1)); });
                f.page.frame(); LayoutBox box = f.box();
                int baseX = i % 3 == 0 ? 0 : i % 3 == 1 ? 120 : 240;
                int baseY = i / 3 == 0 ? 0 : i / 3 == 1 ? 110 : 220;
                int x = Math.min(240, baseX + 20), y = Math.min(220, baseY + 30);
                require(box.x == x && box.y == y && box.width == 160 && box.height == 80,
                        "锚点几何错误 " + anchor + " 实际=" + box.x + "," + box.y + "," + box.width + "," + box.height);
                require(f.host.rectanglePixel(x + 1, y + 1) == 0xff28a745, "缩放绘制未覆盖正式盒");
                require(f.host.rectanglePixel(x - 1, y - 1) == 0, "缩放绘制越出正式盒");
            }
            LayoutBox still = f.box(); f.begin(); f.move(-30, -20); f.release();
            require(SceneGeometryView.sameBox(still, f.box()), "正常 HUD 附加了拖动输入");
        }
    }

    private void drag() {
        try (Fixture f = new Fixture(true)) {
            f.begin(); f.move(37, 26);
            require(f.box().x == 50 && f.box().y == 45, "拖动未按原网格吸附");
            require(f.value().offsetX == 10 && f.value().offsetY == 15, "临时拖动提前提交位置");
            f.release(); require(f.value().offsetX == 50 && f.value().offsetY == 45, "正常放下未写位置草稿");
            require(f.box().x == 50 && f.box().y == 45, "放下后位置跳回旧槽");
            SceneHudPlacement committed = f.value();
            f.begin(); f.move(30, 20); f.page.escape(() -> { throw new AssertionError("活动拖拽未消费 ESC"); }); f.page.frame(); f.release();
            require(committed.equals(f.value()) && f.box().x == 50, "ESC 取消结果：offset=" + f.value().offsetX + "," + f.value().offsetY + " box=" + f.box().x + "," + f.box().y);
            f.begin(); f.move(30, 20); f.page.focusLost(); f.page.frame(); f.release();
            require(committed.equals(f.value()), "失焦取消提交了位置");
            f.begin(); f.move(30, 20); f.domain.execute(() -> f.visible.set(false)); f.page.frame(); f.release();
            require(committed.equals(f.value()), "隐藏取消提交了位置");
            f.domain.execute(() -> f.visible.set(true)); f.page.frame(); f.begin(); f.move(30, 20); f.page.close();
            require(committed.equals(f.value()), "卸载取消提交了位置");
        }
    }

    private void dynamic() {
        try (Fixture f = new Fixture(true)) {
            f.domain.execute(() -> { f.guiScale.set(2f); f.placement.set(new SceneHudPlacement(SceneHudPlacement.Anchor.BOTTOM_RIGHT, -5, -7, 1.5f)); });
            f.page.frame(); LayoutBox b = f.box();
            require(b.x == 150 && b.y == 166 && b.width == 240 && b.height == 120, "GUI Scale/内容缩放未同步");
            f.host.width = 200; f.host.height = 140; f.page.frame(); b = f.box();
            require(b.x == 0 && b.y == 6, "窗口收窄未约束当前内容");
            require(f.host.rectanglePixel(199, 10) == 0xff28a745 && f.host.rectanglePixel(201, 10) == 0,
                    "过大 HUD 未按同一视口裁剪软件像素");
            f.domain.execute(() -> { f.width.set(40); f.height.set(20); f.guiScale.set(1f); }); f.page.frame(); b = f.box();
            require(b.x == 135 && b.y == 103 && b.width == 60 && b.height == 30, "内容和缩放变化未重新锚定");
            f.begin(); f.move(-30, -20); f.host.width = 300; f.page.frame(); f.release();
            b = f.box(); require(b.x >= 0 && b.y >= 0 && b.x + b.width <= f.host.width && b.y + b.height <= f.host.height,
                    "拖中窗口变化后落点越界");
        }
    }
}
