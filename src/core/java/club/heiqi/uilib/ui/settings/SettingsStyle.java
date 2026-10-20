package club.heiqi.uilib.ui.settings;

import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.*;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/** 仅保留主题缩略图；实际选中、焦点、按压和按钮绘制由 SceneAppearance 承担。 */
final class SettingsStyle {
    private SettingsStyle() {}
    static SceneNode themeChoice(SettingsUi ui,String label,boolean dark,Runnable choose) {
        ReadableSignal<Boolean> selected=()->ui.model.theme().get().isDark()==dark;
        SceneNode preview=ui.stack(0).control(new ThemePreview(dark,selected,ui.scale))
                .bind(NodeBinding.value(()->ui.px(ui.shortWindow.get()?54:78),
                        (n,height)->n.size(Size.fill(1),Size.fixed(height))));
        SceneNode mark=ui.icon(SceneIconButton.Symbol.CHECK).bind(NodeBinding.value(selected,SceneNode::visible));
        SceneNode content=ui.stack(10).add(preview).add(ui.actions(ui.text(label,14,false),mark));
        return ui.action(()->label,content,choose,SceneAppearance.Role.ENTRY,selected).size(Size.fill(1),Size.natural())
                .bind(SceneMotion.translateY(()->selected.get()?-(float)ui.px(2):0f,ui.motion.spatial));
    }
    private static final class ThemePreview implements SceneControl {
        private final SceneAppearance.Palette colors;
        private final ReadableSignal<Boolean> selected;private final ReadableSignal<Float> scale;
        ThemePreview(boolean dark,ReadableSignal<Boolean> selected,ReadableSignal<Float> scale) {
            colors=new SceneAppearance.Palette(dark);this.selected=selected;this.scale=scale;
        }
        public List<ReadableSignal<?>> paintDependencies(SceneNode n) {return java.util.Arrays.asList(selected,scale);}
        public MeasuredSize measure(SceneNode n,Constraints c,SceneTextMeasurer text) {return new MeasuredSize(c.availableWidth,c.availableHeight);}
        public ChildPlacements arrange(SceneNode n,LayoutBox b,SceneChildMeasurer children) {return new ChildPlacements(Collections.emptyList());}
        public void paint(SceneNode n,SceneGeometryView geometry,PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry=geometry.get(n);LayoutBox b=entry.box;
            int edge=selected.get()?Math.max(1,Math.round(2*scale.get())):1;
            ScenePaintShapes.rounded(paint,b,entry.clip,selected.get()?colors.text:colors.border,6);
            rect(paint,entry,b.x+edge,b.y+edge,b.width-edge*2,b.height-edge*2,colors.background);
            int inset=Math.max(4,b.height/9),rail=Math.max(8,b.width/5),start=b.x+rail+inset*2;
            rect(paint,entry,b.x+inset,b.y+inset,rail,b.height-inset*2,colors.surface);
            rect(paint,entry,start,b.y+inset,Math.max(0,b.width-rail-inset*4),Math.max(2,b.height/10),colors.muted);
            for(int row=0;row<2;row++)rect(paint,entry,start,b.y+b.height/3+row*b.height/4,
                    Math.max(0,b.width-rail-inset*4),Math.max(3,b.height/6),colors.surface);
        }
        private static void rect(PaintPlanBuilder p,SceneGeometryView.Entry e,int x,int y,int w,int h,int color) {
            ScenePaintShapes.rounded(p,new LayoutBox(x,y,Math.max(0,w),Math.max(0,h)),e.clip,color,3);
        }
    }
}
