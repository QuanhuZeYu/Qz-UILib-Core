package club.heiqi.uilib.ui.scene.text.font;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** 提供随包界面无衬线字族，应用 font.candidates 仍由配置持有。
 * 原始 Noto Sans SC 来自 google/fonts a85815a42757630ce188fdad368c2dfc444d4773，OFL 与资源一起发行。 */
public final class UiFontSources {
    private static final List<FontSource> SANS=load();
    private UiFontSources(){}
    public static List<FontSource> sans(){return SANS;}
    /** 保留显式字族与候选，默认 UI 声明采用随包界面字族。 */
    public static club.heiqi.uilib.ui.scene.text.TextStyle style(club.heiqi.uilib.ui.scene.text.TextStyle base){
        if(base.candidates()!=null||!"default".equals(base.font))return base;
        return new club.heiqi.uilib.ui.scene.text.TextStyle("uilib-ui",base.logicalEm,base.color,null,
                base.effects,base.decoration,base.fontStyle,base.baselineShift,base.letterSpacing,
                base.highlightColor,base.defaultHighlightColor,base.script);
    }
    private static List<FontSource> load(){
        try{
            java.util.ArrayList<FontSource> sources=new java.util.ArrayList<>();
            sources.add(FontSource.resource("uilib-settings-ui",1,UiFontSources.class.getClassLoader(),
                    "assets/qz_uilib/fonts/ui/NotoSansSC-VF.ttf",false,Collections.<String>emptySet()));
            // 界面字族之后沿用随包候选，覆盖其他文字系统。
            sources.addAll(FontTextService.defaultSources(UiFontSources.class.getClassLoader(),1));
            return Collections.unmodifiableList(sources);
        }catch(IOException failure){throw new ExceptionInInitializerError(failure);}
    }
}
