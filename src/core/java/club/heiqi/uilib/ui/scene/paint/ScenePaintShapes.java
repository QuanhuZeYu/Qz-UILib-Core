package club.heiqi.uilib.ui.scene.paint;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** 将纯装饰展开为普通 RECT，角点 coverage 仅作用于绘制。 */
public final class ScenePaintShapes {
    private static final int SAMPLES=4;
    private ScenePaintShapes() {}

    /** 输出一逻辑像素圆角轮廓，每个像素独立计算一次 coverage。 */
    public static void roundedOutline(PaintPlanBuilder paint, LayoutBox box, LayoutBox clip, int color, int requested) {
        if (box.width <= 0 || box.height <= 0 || (color >>> 24) == 0) return;
        if (clip != null) {
            LayoutBox visible = box.intersect(clip);
            if (visible.width == 0 || visible.height == 0) return;
        }
        int radius = Math.max(0, Math.min(requested, Math.min(box.width, box.height) / 2));
        if (radius == 0) {
            paint.add(PaintCommand.rect(new LayoutBox(box.x, box.y, box.width, 1), clip, color));
            if (box.height > 1) paint.add(PaintCommand.rect(new LayoutBox(box.x, box.y + box.height - 1, box.width, 1), clip, color));
            if (box.height > 2) {
                paint.add(PaintCommand.rect(new LayoutBox(box.x, box.y + 1, 1, box.height - 2), clip, color));
                if (box.width > 1) paint.add(PaintCommand.rect(new LayoutBox(box.x + box.width - 1, box.y + 1, 1, box.height - 2), clip, color));
            }
            return;
        }
        if (box.width > radius * 2) {
            paint.add(PaintCommand.rect(new LayoutBox(box.x + radius, box.y, box.width - radius * 2, 1), clip, color));
            paint.add(PaintCommand.rect(new LayoutBox(box.x + radius, box.y + box.height - 1, box.width - radius * 2, 1), clip, color));
        }
        if (box.height > radius * 2) {
            paint.add(PaintCommand.rect(new LayoutBox(box.x, box.y + radius, 1, box.height - radius * 2), clip, color));
            paint.add(PaintCommand.rect(new LayoutBox(box.x + box.width - 1, box.y + radius, 1, box.height - radius * 2), clip, color));
        }
        double outer = (double) radius * radius, inner = (double) (radius - 1) * (radius - 1);
        for (int y = 0; y < radius; y++) for (int x = 0; x < radius; x++) {
            int coverage = 0;
            for (int sy = 0; sy < SAMPLES; sy++) for (int sx = 0; sx < SAMPLES; sx++) {
                double dx = radius - x - (sx + .5) / SAMPLES, dy = radius - y - (sy + .5) / SAMPLES;
                double distance = dx * dx + dy * dy;
                if (distance <= outer && distance >= inner) coverage++;
            }
            int alpha = ((color >>> 24) * coverage + SAMPLES * SAMPLES / 2) / (SAMPLES * SAMPLES);
            if (alpha == 0) continue;
            int edge = (color & 0xffffff) | (alpha << 24);
            paint.add(PaintCommand.rect(new LayoutBox(box.x + x, box.y + y, 1, 1), clip, edge));
            paint.add(PaintCommand.rect(new LayoutBox(box.x + box.width - x - 1, box.y + y, 1, 1), clip, edge));
            paint.add(PaintCommand.rect(new LayoutBox(box.x + x, box.y + box.height - y - 1, 1, 1), clip, edge));
            paint.add(PaintCommand.rect(new LayoutBox(box.x + box.width - x - 1, box.y + box.height - y - 1, 1, 1), clip, edge));
        }
    }
    public static void rounded(PaintPlanBuilder paint, LayoutBox box, LayoutBox clip, int color, int requested) {
        if(box.width<=0||box.height<=0||(color>>>24)==0)return;
        if(clip!=null) {LayoutBox visible=box.intersect(clip);if(visible.width==0||visible.height==0)return;}
        int radius=Math.max(0,Math.min(requested,Math.min(box.width,box.height)/2));
        paint.add(PaintCommand.rect(new LayoutBox(box.x,box.y+radius,box.width,box.height-radius*2),clip,color));
        for(int y=0;y<radius;y++) {
            double far=radius-y,near=radius-y-1;
            int full=(int)Math.ceil(radius-Math.sqrt(Math.max(0,(double)radius*radius-far*far)));
            int first=(int)Math.floor(radius-Math.sqrt(Math.max(0,(double)radius*radius-near*near)));
            if(box.width>full*2) {
                paint.add(PaintCommand.rect(new LayoutBox(box.x+full,box.y+y,box.width-full*2,1),clip,color));
                paint.add(PaintCommand.rect(new LayoutBox(box.x+full,box.y+box.height-y-1,box.width-full*2,1),clip,color));
            }
            for(int x=first;x<full;x++) {
                int coverage=0;
                for(int sy=0;sy<SAMPLES;sy++)for(int sx=0;sx<SAMPLES;sx++) {
                    double dx=radius-x-(sx+.5)/SAMPLES,dy=radius-y-(sy+.5)/SAMPLES;
                    if(dx*dx+dy*dy<=(double)radius*radius)coverage++;
                }
                int alpha=((color>>>24)*coverage+SAMPLES*SAMPLES/2)/(SAMPLES*SAMPLES);
                if(alpha==0)continue;int edge=(color&0xffffff)|(alpha<<24);
                paint.add(PaintCommand.rect(new LayoutBox(box.x+x,box.y+y,1,1),clip,edge));
                paint.add(PaintCommand.rect(new LayoutBox(box.x+box.width-x-1,box.y+y,1,1),clip,edge));
                paint.add(PaintCommand.rect(new LayoutBox(box.x+x,box.y+box.height-y-1,1,1),clip,edge));
                paint.add(PaintCommand.rect(new LayoutBox(box.x+box.width-x-1,box.y+box.height-y-1,1,1),clip,edge));
            }
        }
    }
}
