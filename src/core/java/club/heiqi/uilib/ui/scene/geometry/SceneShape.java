package club.heiqi.uilib.ui.scene.geometry;

import java.util.Objects;

/** 按当前节点外盒解析的纯值形状；可适应分配尺寸，不读回布局或保留绘制回调。 */
public final class SceneShape {
    private enum Kind { ELLIPSE, ROUND_RECT, PATH, FIXED, VECTOR }
    private final Kind kind;private final ScenePath path;private final double width,height,radius;
    private final SceneStroke stroke;private final boolean filled;private final double viewX,viewY;
    private volatile Resolved prepared;
    private static final class Resolved {
        final double width,height,error;final SceneMesh mesh;
        Resolved(double width,double height,double error,SceneMesh mesh){this.width=width;this.height=height;this.error=error;this.mesh=mesh;}
    }
    private SceneShape(Kind kind,ScenePath path,double width,double height,double radius){this(kind,path,width,height,radius,true,null);}
    private SceneShape(Kind kind,ScenePath path,double width,double height,double radius,boolean filled,SceneStroke stroke){this(kind,path,0,0,width,height,radius,filled,stroke);}
    private SceneShape(Kind kind,ScenePath path,double viewX,double viewY,double width,double height,double radius,boolean filled,SceneStroke stroke){this.viewX=viewX;this.viewY=viewY;this.kind=kind;this.path=path;this.width=width;this.height=height;this.radius=radius;this.filled=filled;this.stroke=stroke;}
    public static SceneShape ellipse(){return new SceneShape(Kind.ELLIPSE,null,0,0,0);}
    public static SceneShape roundRect(double radius){if(!Double.isFinite(radius)||radius<0)throw new IllegalArgumentException("圆角须非负且有限");return new SceneShape(Kind.ROUND_RECT,null,0,0,radius);}
    public static SceneShape path(ScenePath path,double viewWidth,double viewHeight){
        if(!Double.isFinite(viewWidth)||!Double.isFinite(viewHeight)||viewWidth<=0||viewHeight<=0)throw new IllegalArgumentException("路径视图尺寸必须为正且有限");
        return new SceneShape(Kind.PATH,Objects.requireNonNull(path),viewWidth,viewHeight,0);
    }
    public static SceneShape vector(ScenePath path,double viewWidth,double viewHeight,boolean filled,SceneStroke stroke){
        SceneShape view=path(path,viewWidth,viewHeight);return new SceneShape(Kind.VECTOR,view.path,viewWidth,viewHeight,0,filled,stroke);
    }
    public static SceneShape vector(ScenePath path,double viewX,double viewY,double viewWidth,double viewHeight,boolean filled,SceneStroke stroke){
        SceneShape value=vector(path,viewWidth,viewHeight,filled,stroke);if(!Double.isFinite(viewX)||!Double.isFinite(viewY))throw new IllegalArgumentException("路径视图原点必须有限");
        return new SceneShape(value.kind,value.path,viewX,viewY,viewWidth,viewHeight,0,filled,stroke);
    }
    public static SceneShape fixed(ScenePath path){return new SceneShape(Kind.FIXED,Objects.requireNonNull(path),0,0,0);}
    /** 结果位于当前外盒局部0..width/0..height；error同属这个局部frame。 */
    public SceneMesh resolve(double width,double height,double error){
        Resolved value=prepared;if(value!=null&&value.width==width&&value.height==height&&value.error==error)return value.mesh;
        SceneMesh mesh=prepare(width,height,error);prepared=new Resolved(width,height,error,mesh);return mesh;
    }
    private SceneMesh prepare(double width,double height,double error){
        if(width<=0||height<=0)return SceneMesh.EMPTY;
        switch(kind){
            case ELLIPSE:return ScenePath.ellipse(width/2,height/2,width/2,height/2).fillMesh(error);
            case ROUND_RECT:
                double r=Math.min(radius,Math.min(width,height)/2);if(r==0)return SceneMesh.rectangle(0,0,width,height);
                return ScenePath.builder().moveTo(r,0).lineTo(width-r,0).arc(width-r,r,r,-Math.PI/2,Math.PI/2)
                        .lineTo(width,height-r).arc(width-r,height-r,r,0,Math.PI/2).lineTo(r,height)
                        .arc(r,height-r,r,Math.PI/2,Math.PI/2).lineTo(0,r).arc(r,r,r,Math.PI,Math.PI/2).close().build().fillMesh(error);
            case VECTOR:
                SceneTransform transform=SceneTransform.scale(width/this.width,height/this.height).multiply(SceneTransform.translation(-viewX,-viewY));double localError=error/transform.maximumScale();
                SceneMesh fill=filled?path.fillMesh(localError):SceneMesh.EMPTY;
                return (stroke==null?fill:fill.union(path.strokeMesh(stroke,localError))).transformed(transform);
            case PATH:return path.fillMesh(error,SceneTransform.scale(width/this.width,height/this.height));
            default:return path.fillMesh(error);
        }
    }
    @Override public boolean equals(Object other){if(!(other instanceof SceneShape))return false;SceneShape s=(SceneShape)other;
        return kind==s.kind&&Objects.equals(path,s.path)&&width==s.width&&height==s.height&&radius==s.radius&&filled==s.filled&&Objects.equals(stroke,s.stroke)&&viewX==s.viewX&&viewY==s.viewY;}
    @Override public int hashCode(){return Objects.hash(kind,path,width,height,radius==0?0:radius,filled,stroke,viewX==0?0:viewX,viewY==0?0:viewY);}
}
