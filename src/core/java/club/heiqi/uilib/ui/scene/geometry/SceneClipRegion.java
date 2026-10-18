package club.heiqi.uilib.ui.scene.geometry;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import java.util.Objects;

/** 本批当前frame中的实际裁剪区域；普通矩形保持原快速路径，形状使用共享填充网格。 */
public final class SceneClipRegion {
    public static final SceneClipRegion UNBOUNDED=new SceneClipRegion(false,null,null);
    public static final SceneClipRegion EMPTY=new SceneClipRegion(true,new LayoutBox(0,0,0,0),SceneMesh.EMPTY);
    private final boolean bounded;
    private final LayoutBox bounds;
    private final SceneMesh shape;
    private volatile SceneMesh rectangleMesh;
    private SceneClipRegion(boolean bounded,LayoutBox bounds,SceneMesh shape){this.bounded=bounded;this.bounds=bounds;this.shape=shape;}
    public static SceneClipRegion rectangle(LayoutBox bounds){
        if(bounds==null)return UNBOUNDED;if(bounds.width<=0||bounds.height<=0)return EMPTY;
        return new SceneClipRegion(true,bounds,null);
    }
    public static SceneClipRegion of(SceneMesh mesh){Objects.requireNonNull(mesh,"裁剪网格");
        if(mesh.empty()||mesh.area()==0)return EMPTY;return new SceneClipRegion(true,outward(mesh),mesh);
    }
    /** 无界或超出整数盒可表达范围时为空，精确区域仍由mesh表达。 */
    public LayoutBox bounds(){return bounds;}
    public boolean unbounded(){return !bounded;}
    public boolean empty(){return this==EMPTY||shape!=null&&shape.empty();}
    public boolean rectangular(){return bounded&&shape==null;}
    public SceneMesh mesh(){
        if(!bounded)return null;if(shape!=null)return shape;SceneMesh value=rectangleMesh;
        if(value==null){value=SceneMesh.rectangle(bounds.x,bounds.y,bounds.width,bounds.height);rectangleMesh=value;}return value;
    }
    public boolean contains(double x,double y){
        if(!bounded)return true;if(empty())return false;
        if(bounds!=null&&(x<bounds.x||y<bounds.y||x>=(long)bounds.x+bounds.width||y>=(long)bounds.y+bounds.height))return false;
        return shape==null||shape.contains(x,y);
    }
    public SceneClipRegion intersect(SceneClipRegion other){
        Objects.requireNonNull(other);if(this==other||other.unbounded())return this;if(unbounded())return other;if(empty()||other.empty())return EMPTY;
        if(rectangular()&&other.rectangular()){
            long left=Math.max(bounds.x,other.bounds.x),top=Math.max(bounds.y,other.bounds.y),right=Math.min((long)bounds.x+bounds.width,(long)other.bounds.x+other.bounds.width),
                    bottom=Math.min((long)bounds.y+bounds.height,(long)other.bounds.y+other.bounds.height);
            return right<=left||bottom<=top?EMPTY:rectangle(new LayoutBox((int)left,(int)top,(int)(right-left),(int)(bottom-top)));
        }
        return of(mesh().intersection(other.mesh()));
    }
    public SceneClipRegion intersect(SceneMesh mesh){return intersect(of(mesh));}
    public SceneMesh clip(SceneMesh subject){if(unbounded())return subject;if(empty())return SceneMesh.EMPTY;return subject.intersection(mesh());}
    public boolean intersects(SceneMesh subject){return !clip(subject).empty();}
    public SceneClipRegion transformed(SceneTransform transform){if(unbounded()||empty()||transform.identity())return this;return of(mesh().transformed(transform));}
    private static LayoutBox outward(SceneMesh mesh){
        double left=Math.floor(mesh.minX),top=Math.floor(mesh.minY),right=Math.ceil(mesh.maxX),bottom=Math.ceil(mesh.maxY);
        if(left<Integer.MIN_VALUE||top<Integer.MIN_VALUE||right>Integer.MAX_VALUE||bottom>Integer.MAX_VALUE||right-left>Integer.MAX_VALUE||bottom-top>Integer.MAX_VALUE)return null;
        return new LayoutBox((int)left,(int)top,(int)(right-left),(int)(bottom-top));
    }
    @Override public boolean equals(Object other){if(!(other instanceof SceneClipRegion))return false;SceneClipRegion r=(SceneClipRegion)other;
        return bounded==r.bounded&&Objects.equals(shape,r.shape)&&same(bounds,r.bounds);}
    private static boolean same(LayoutBox a,LayoutBox b){return a==b||a!=null&&b!=null&&a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}
    @Override public int hashCode(){return Objects.hash(bounded,shape,bounds==null?0:Objects.hash(bounds.x,bounds.y,bounds.width,bounds.height));}
}
