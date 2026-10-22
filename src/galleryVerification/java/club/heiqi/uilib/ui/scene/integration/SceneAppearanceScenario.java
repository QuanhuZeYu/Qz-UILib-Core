package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/** 实际卡片回放颜色沿主题和自定义背景同批更新，不以节点声明值代替已绘结果。 */
public final class SceneAppearanceScenario extends TestApplication {
    protected void runTests() throws Exception {
        scenario("半透明卡片主题与独立背景变化进入同一绘制目标", this::cardBackground);
    }

    private void cardBackground() {
        ReactiveDomain domain = ReactiveDomain.direct(); SettingsHudHost host = new SettingsHudHost();
        host.width = 400; host.height = 160;
        Signal<SceneTheme>[] theme = new Signal[1]; Signal<Integer>[] alpha = new Signal[1];
        SceneAppearance[] style = new SceneAppearance[1]; SceneNode[] card = new SceneNode[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            context.motionPolicy(() -> MotionPolicy.OFF);
            theme[0] = domain.signal(SceneTheme.light()); alpha[0] = domain.signal(0xd0);
            style[0] = new SceneAppearance(context, theme[0]);
            ReadableSignal<Integer> background = () -> alpha[0].get() << 24 | style[0].colors.get().background & 0x00ffffff;
            card[0] = style[0].card(style[0].label(() -> "HUD 内容", 1, false), background)
                    .size(Size.fill(1), Size.fill(1)).own(style[0].scope);
            context.mountOwned(card[0]);
        })) {
            page.frame();
            require(host.rectanglePixel(200, 80) == 0xd0ffffff, "初始卡片背景未进入实际绘制");
            domain.execute(() -> theme[0].set(SceneTheme.dark())); page.frame();
            System.out.println("CARD_BACKGROUND darkPainted=" + Integer.toHexString(host.rectanglePixel(200, 80)));
            require(host.rectanglePixel(200, 80) == 0xd009090b, "主题变更仍绘制旧卡片背景");
            boolean lightText = false;
            for (PaintCommand command : host.plan.commands())
                if (command.kind == PaintCommand.Kind.TEXT && "HUD 内容".equals(command.text.logicalText))
                    lightText = command.text.style.color == 0xfffafafa;
            require(lightText, "卡片文字没有随主题进入同批绘制");
            domain.execute(() -> alpha[0].set(0x90)); page.frame();
            require(host.rectanglePixel(200, 80) == 0x9009090b, "独立背景变化没有驱动实际填充");
            domain.execute(() -> theme[0].set(SceneTheme.light())); page.frame();
            require(host.rectanglePixel(200, 80) == 0x90ffffff, "主题恢复浅色后仍绘制旧深色背景");
            System.out.println("CARD_BACKGROUND restoredLight=" + Integer.toHexString(host.rectanglePixel(200, 80)));
        }
    }
}
