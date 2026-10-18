package club.heiqi.uilib.ui.scene.geometry;

import java.util.Objects;

/** Canvas同序的二维仿射值：x'=a*x+c*y+tx，y'=b*x+d*y+ty。 */
public final class SceneTransform {
    public static final SceneTransform IDENTITY=new SceneTransform(1,0,0,1,0,0);
    public final double a,b,c,d,tx,ty;
    private SceneTransform(double a,double b,double c,double d,double tx,double ty){
        if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(c)||!Double.isFinite(d)||!Double.isFinite(tx)||!Double.isFinite(ty))
            throw new IllegalArgumentException("仿射坐标必须有限");
        this.a=a;this.b=b;this.c=c;this.d=d;this.tx=tx;this.ty=ty;
    }
    public static SceneTransform of(double a,double b,double c,double d,double tx,double ty){
        return a==1&&b==0&&c==0&&d==1&&tx==0&&ty==0?IDENTITY:new SceneTransform(a,b,c,d,tx,ty);
    }
    public static SceneTransform translation(double x,double y){return of(1,0,0,1,x,y);}
    public static SceneTransform scale(double x,double y){return of(x,0,0,y,0,0);}
    public static SceneTransform rotationRadians(double angle){double cos=Math.cos(angle),sin=Math.sin(angle);return of(cos,sin,-sin,cos,0,0);}
    public static SceneTransform rotationDegrees(double angle){return rotationRadians(Math.toRadians(angle));}
    public static SceneTransform skewRadians(double x,double y){return of(1,Math.tan(y),Math.tan(x),1,0,0);}
    public static SceneTransform around(double x,double y,SceneTransform effect){
        return translation(x,y).multiply(effect).multiply(translation(-x,-y));
    }
    public double x(double x,double y){return a*x+c*y+tx;}
    public double y(double x,double y){return b*x+d*y+ty;}
    /** this∘other：先other，再this；对应Canvas在当前矩阵右侧组合。 */
    public SceneTransform multiply(SceneTransform other){
        Objects.requireNonNull(other,"组合矩阵");if(this==IDENTITY)return other;if(other==IDENTITY)return this;
        return of(a*other.a+c*other.b,b*other.a+d*other.b,a*other.c+c*other.d,b*other.c+d*other.d,
                a*other.tx+c*other.ty+tx,b*other.tx+d*other.ty+ty);
    }
    /** 按调用顺序：先this，再next。 */
    public SceneTransform then(SceneTransform next){return next.multiply(this);}
    /** 缩放到零仍是合法呈现值；没有逆映射时返回null，输入联系沿原取消规则处理。 */
    public SceneTransform inverse(){
        double determinant=a*d-b*c;if(determinant==0)return null;
        return of(d/determinant,-b/determinant,-c/determinant,a/determinant,(c*ty-d*tx)/determinant,(b*tx-a*ty)/determinant);
    }
    /** 线性部分最大伸缩率，供曲线把局部误差换算为当前呈现像素。 */
    public double maximumScale(){
        double p=Math.hypot(a+d,b-c),q=Math.hypot(a-d,b+c);return (p+q)*0.5;
    }
    public boolean identity(){return a==1&&b==0&&c==0&&d==1&&tx==0&&ty==0;}
    @Override public boolean equals(Object other){if(!(other instanceof SceneTransform))return false;SceneTransform t=(SceneTransform)other;
        return a==t.a&&b==t.b&&c==t.c&&d==t.d&&tx==t.tx&&ty==t.ty;}
    @Override public int hashCode(){return Objects.hash(zero(a),zero(b),zero(c),zero(d),zero(tx),zero(ty));}
    private static double zero(double v){return v==0?0:v;}
}
