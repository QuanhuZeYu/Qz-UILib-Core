package club.heiqi.uilib.ui.scene.semantics;

import java.util.Objects;

/** 宿主向原页面输入序列递交的定向动作；结果异步完成，业务是否接受仍由原状态表达。 */
public final class SceneSemanticRequest {
    public enum Result { APPLIED, REJECTED, CLOSED }
    public final SceneSemanticId target;
    public final SceneSemantics.Action action;
    public final String text;
    public final double value;
    private SceneSemanticRequest(SceneSemanticId target,SceneSemantics.Action action,String text,double value){
        this.target=Objects.requireNonNull(target,"语义目标");this.action=Objects.requireNonNull(action,"语义动作");this.text=text;this.value=value;
    }
    public static SceneSemanticRequest of(SceneSemanticId target,SceneSemantics.Action action){return new SceneSemanticRequest(target,action,null,0);}
    public static SceneSemanticRequest text(SceneSemanticId target,String value){return new SceneSemanticRequest(target,SceneSemantics.Action.SET_TEXT,Objects.requireNonNull(value,"文本"),0);}
    public static SceneSemanticRequest value(SceneSemanticId target,double value){return new SceneSemanticRequest(target,SceneSemantics.Action.SET_VALUE,null,value);}
}
