package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.settings.SettingsFields;
import java.util.*;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/** 按实际卡片回放颜色核验主题与自定义背景同批更新。 */
public final class SceneAppearanceScenario extends TestApplication {
    protected void runTests() throws Exception {
        scenario("半透明卡片主题与独立背景变化进入同一绘制目标", this::cardBackground);
        scenario("字段绘制复用挂载语义焦点且动态目标与重挂载不复活旧缓存", this::fieldFocusPreparation);
    }

    private void fieldFocusPreparation() {
        ReactiveDomain domain = ReactiveDomain.direct(); SettingsHudHost host = new SettingsHudHost();
        int[] descriptions = {0}, selectionReads = {0};
        Signal<SceneNode>[] declared = new Signal[1], override = new Signal[1];
        Signal<Boolean>[] attached = new Signal[1];
        SceneNode[] first = new SceneNode[1], second = new SceneNode[1], composite = new SceneNode[1];
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, context -> {
            context.motionPolicy(() -> MotionPolicy.OFF);
            SceneAppearance style = new SceneAppearance(context, context.theme());
            first[0] = SceneButton.create(context, () -> "第一焦点", context.theme(), () -> {});
            second[0] = SceneButton.create(context, () -> "第二焦点", context.theme(), () -> {});
            context.own(second[0]::destroy);
            declared[0] = domain.signal(first[0]); override[0] = domain.signal(first[0]); attached[0] = domain.signal(true);
            composite[0] = new SceneNode().size(Size.fill(1), Size.natural()).padding(4).control(new SceneControl() {
                public SceneSemantics semantics(SceneNode node) {
                    descriptions[0]++;
                    return SceneSemantics.builder(SceneSemantics.Role.GROUP).focusTarget(declared[0].get()).build();
                }
                public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
                public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
                    return SceneFlowLayout.measure(node, limits, children);
                }
                public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
                    return SceneFlowLayout.arrange(node, box, children);
                }
                public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
            }).add(first[0]).bind(NodeBinding.children(() -> attached[0].get()
                    ? Arrays.asList(first[0], second[0]) : Collections.singletonList(first[0])))
                    .bind(NodeBinding.semantics(() -> SceneSemantics.builder().focusTarget(override[0].get()).build()));
            SceneNode field = style.field(composite[0]);
            SceneNode choice = new SettingsFields(context).choice(() -> "真实选择字段", () -> "",
                    () -> Arrays.asList(new SceneSelect.Option<>("first", () -> "第一项"), new SceneSelect.Option<>("last", () -> "末项")),
                    () -> { selectionReads[0]++; return "last"; }, ignored -> {}, () -> true, () -> "");
            context.mountOwned(SceneAppearance.column(8).add(field).add(choice).own(style.scope));
        })) {
            page.frame();
            int preparedDescriptions = descriptions[0], preparedReads = selectionReads[0];
            for (int frame = 0; frame < 5; frame++) {
                domain.execute(() -> page.runtime().invalidatePaint()); page.frame();
            }
            System.out.println("FIELD_FOCUS_PREPARATION descriptions=" + preparedDescriptions + "->" + descriptions[0]
                    + " selectionReads=" + preparedReads + "->" + selectionReads[0]);
            require(descriptions[0] == preparedDescriptions && selectionReads[0] == preparedReads,
                    "字段纯绘制重新执行控件语义或扫描真实选择来源");
            page.navigation(SceneNavigationEvent.NEXT); page.navigation(SceneNavigationEvent.PREVIOUS); page.frame();
            assertFieldFocus(page, host, domain, composite[0], true);
            domain.execute(() -> override[0].set(second[0])); page.frame();
            assertFieldFocus(page, host, domain, composite[0], false);
            page.navigation(SceneNavigationEvent.NEXT); page.frame();
            assertFieldFocus(page, host, domain, composite[0], true);
            domain.execute(() -> attached[0].set(false)); page.frame();
            assertFieldFocus(page, host, domain, composite[0], false);
            domain.execute(() -> attached[0].set(true)); page.frame();
            assertFieldFocus(page, host, domain, composite[0], false);
            page.navigation(SceneNavigationEvent.NEXT); page.navigation(SceneNavigationEvent.NEXT); page.frame();
            assertFieldFocus(page, host, domain, composite[0], true);
        }
    }

    private void assertFieldFocus(ScenePageSession page, SettingsHudHost host, ReactiveDomain domain, SceneNode node, boolean expected) {
        LayoutBox[] box = new LayoutBox[1];
        domain.execute(() -> {
            box[0] = page.pipeline().current().geometry.get(node).box;
            SceneNode focused = page.runtime().focusRouter().focused().get(), actual = page.runtime().semanticFocusTarget(node);
            System.out.println("FIELD_FOCUS_TARGET expected=" + expected + " focus=" + (focused == null ? "null" : focused.text())
                    + " declared=" + (actual == null ? "null" : actual.text()));
        });
        SceneAppearance.Palette palette = new SceneAppearance.Palette(false);
        int edge = host.rectanglePixel(box[0].x + box[0].width / 2.0, box[0].y + .5);
        require(edge == (expected ? palette.muted : palette.border), "字段外表面未消费当前声明焦点或复活旧挂载：" + expected);
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
