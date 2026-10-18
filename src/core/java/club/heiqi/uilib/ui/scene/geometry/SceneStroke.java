package club.heiqi.uilib.ui.scene.geometry;

import java.util.Objects;

/** 以局部路径为中心线的不可变描边样式，默认值沿Canvas。 */
public final class SceneStroke {
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    public final double width,miterLimit;
    public final Cap cap;
    public final Join join;
    public SceneStroke(double width){this(width,Cap.BUTT,Join.MITER,10);}
    public SceneStroke(double width,Cap cap,Join join,double miterLimit){
        if(!Double.isFinite(width)||width<0||!Double.isFinite(miterLimit)||miterLimit<=0)throw new IllegalArgumentException("描边宽度须非负，斜接限制须为正且有限");
        this.width=width;this.cap=Objects.requireNonNull(cap);this.join=Objects.requireNonNull(join);this.miterLimit=miterLimit;
    }
    @Override public boolean equals(Object other){if(!(other instanceof SceneStroke))return false;SceneStroke s=(SceneStroke)other;return width==s.width&&cap==s.cap&&join==s.join&&miterLimit==s.miterLimit;}
    @Override public int hashCode(){return Objects.hash(width==0?0:width,cap,join,miterLimit);}
}
