package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.paint.*;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
import club.heiqi.uilib.ui.scene.text.*;

/** 无窗口回放边界；正式 Session、输入和绘制计划保持原生产路径。 */
final class SettingsHudHost implements SceneHost, SceneTextMeasurer {
    private final MultilineEditingScenario.PlainHost text = new MultilineEditingScenario.PlainHost();
    int width = 1200, height = 900, replays;
    ThemeConfiguration themes;
    PaintPlan plan;
    SceneSemanticSnapshot semantics = SceneSemanticSnapshot.EMPTY;
    public int width() { return width; }
    public int height() { return height; }
    public long metricsRevision() { return 0; }
    public long frameNanos() { return text.frameNanos(); }
    public ThemeConfiguration themeConfiguration() { return themes; }
    public TextLayout layout(String value, TextStyle style, int width) { return text.layout(value, style, width); }
    public TextLayout layout(String value, TextStyle style, int width, TextPolicy policy) { return text.layout(value, style, width, policy); }
    public void replay(PaintPlan value) { plan = value; replays++; }
    public void semanticsChanged(SceneSemanticSnapshot value) { semantics = value; }

    /** 采样矩形命令，独立核验缩放与裁剪后的实际软件像素。 */
    int rectanglePixel(double x, double y) {
        int color = 0;
        for (PaintCommand command : plan.commands()) {
            if (command.kind != PaintCommand.Kind.RECT || !command.effectiveClip().contains(x, y)) continue;
            club.heiqi.uilib.ui.scene.geometry.SceneTransform inverse = command.transform.inverse();
            if (inverse == null) continue;
            double px = inverse.x(x, y), py = inverse.y(x, y);
            if (px >= command.box.x && py >= command.box.y
                    && px < (double) command.box.x + command.box.width && py < (double) command.box.y + command.box.height)
                color = command.color;
        }
        return color;
    }
}
