package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;

/** 控件共同的内容区域与细边框，直接输出既有PaintCommand。 */
final class SceneControlPaint {
    private SceneControlPaint() {}

    static LayoutBox inner(LayoutBox box, int padding) {
        return new LayoutBox(
                box.x + padding,
                box.y + padding,
                Math.max(0, box.width - padding * 2),
                Math.max(0, box.height - padding * 2));
    }

    /** 共同动作的焦点只覆盖细轮廓，不以背景色遮盖内部，透明表面亦成立。 */
    static void focus(PaintPlanBuilder target, LayoutBox box, LayoutBox clip, int color, int radius) {
        club.heiqi.uilib.ui.scene.paint.ScenePaintShapes.roundedOutline(target, box, clip, color, radius);
    }

    static void border(PaintPlanBuilder target, LayoutBox box, LayoutBox clip, int color) {
        if (box.width == 0 || box.height == 0) return;
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y, box.width, 1), clip, color));
        target.add(
                PaintCommand.rect(
                        new LayoutBox(box.x, box.y + box.height - 1, box.width, 1), clip, color));
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y, 1, box.height), clip, color));
        target.add(
                PaintCommand.rect(
                        new LayoutBox(box.x + box.width - 1, box.y, 1, box.height), clip, color));
    }
}
