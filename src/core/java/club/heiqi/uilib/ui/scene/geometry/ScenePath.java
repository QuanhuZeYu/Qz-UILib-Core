package club.heiqi.uilib.ui.scene.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 局部坐标中的不可变路径；曲线只在当前几何需要的像素精度下展平。 */
public final class ScenePath {
    public enum FillRule { NON_ZERO, EVEN_ODD }
    private enum Kind { MOVE, LINE, QUAD, CUBIC, ARC, CLOSE }
    private static final class Segment {
        final Kind kind;final double[] values;
        @Override public boolean equals(Object other){return other instanceof Segment&&kind==((Segment)other).kind&&Arrays.equals(values,((Segment)other).values);}
        @Override public int hashCode(){return Objects.hash(kind,Arrays.hashCode(values));}
        Segment(Kind kind,double...values){this.kind=kind;this.values=values;for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("路径坐标必须有限");}
    }
    /** 展平后的子路径，closed区分填充的隐式闭合和描边的显式连接。 */
    public static final class Contour {
        private final double[] points;
        public final boolean closed;
        final boolean drawn;
        Contour(double[] points,boolean closed,boolean drawn){this.points=points.clone();this.closed=closed;this.drawn=drawn;}
        public int size(){return points.length/2;}
        public double x(int index){return points[index*2];}
        public double y(int index){return points[index*2+1];}
    }
    public final FillRule rule;
    private final List<Segment> segments;
    private volatile Prepared prepared;
    private static final class Prepared {
        final double error;final List<Contour> contours;volatile SceneMesh fill;volatile StrokeResult stroke;
        Prepared(double error,List<Contour> contours){this.error=error;this.contours=contours;}
    }
    private static final class StrokeResult {
        final SceneStroke style;final SceneMesh mesh;StrokeResult(SceneStroke style,SceneMesh mesh){this.style=style;this.mesh=mesh;}
    }
    private ScenePath(Builder builder){rule=builder.rule;segments=Collections.unmodifiableList(new ArrayList<>(builder.segments));}
    public static Builder builder(){return new Builder();}
    public static ScenePath rectangle(double x,double y,double width,double height){return builder().moveTo(x,y).lineTo(x+width,y).lineTo(x+width,y+height).lineTo(x,y+height).close().build();}
    public static ScenePath ellipse(double x,double y,double rx,double ry){return builder().ellipse(x,y,rx,ry,0,0,Math.PI*2).close().build();}
    public SceneMesh fillMesh(double error){Prepared current=prepared(error);SceneMesh mesh=current.fill;if(mesh==null){mesh=ScenePathTessellator.fill(current.contours,rule);current.fill=mesh;}return mesh;}
    public SceneMesh strokeMesh(SceneStroke stroke,double error){Objects.requireNonNull(stroke,"描边");Prepared current=prepared(error);StrokeResult result=current.stroke;
        if(result==null||!result.style.equals(stroke)){result=new StrokeResult(stroke,SceneStrokeTessellator.stroke(current.contours,stroke,error));current.stroke=result;}return result.mesh;}
    public SceneMesh fillMesh(double pixelError,SceneTransform transform){
        double scale=transform.maximumScale();if(scale==0)return SceneMesh.EMPTY;return fillMesh(pixelError/scale).transformed(transform);
    }
    private Prepared prepared(double error){Prepared current=prepared;if(current!=null&&current.error==error)return current;
        current=new Prepared(error,flatten(error));prepared=current;return current;}
    public List<Contour> contours(double error){return prepared(error).contours;}
    private List<Contour> flatten(double error){
        if(!Double.isFinite(error)||error<=0)throw new IllegalArgumentException("路径精度必须为正且有限");
        List<Contour> result=new ArrayList<>();Points points=null;boolean drawn=false;double x=0,y=0,startX=0,startY=0;
        for(Segment segment:segments){double[] v=segment.values;
            switch(segment.kind){
                case MOVE:
                    if(points!=null)result.add(points.contour(false,drawn));points=new Points();points.add(v[0],v[1]);x=startX=v[0];y=startY=v[1];drawn=false;break;
                case LINE:points.add(v[0],v[1]);x=v[0];y=v[1];drawn=true;break;
                case QUAD:quad(points,x,y,v[0],v[1],v[2],v[3],error);x=v[2];y=v[3];drawn=true;break;
                case CUBIC:cubic(points,x,y,v[0],v[1],v[2],v[3],v[4],v[5],error);x=v[4];y=v[5];drawn=true;break;
                case ARC:
                    double sweep=v[6],radius=Math.max(v[2],v[3]);
                    if(radius>0&&sweep!=0){
                        double angle=Math.min(Math.PI/2,2*Math.acos(Math.max(-1,1-error/radius)));
                        if(angle==0)angle=Math.sqrt(8*error/radius);
                        int steps=Math.max(1,(int)Math.ceil(Math.abs(sweep)/angle));double cos=Math.cos(v[4]),sin=Math.sin(v[4]);
                        for(int i=1;i<=steps;i++){double t=i==steps&&Math.abs(sweep)==Math.PI*2?v[5]:v[5]+sweep*i/steps,px=Math.cos(t)*v[2],py=Math.sin(t)*v[3];points.add(v[0]+px*cos-py*sin,v[1]+px*sin+py*cos);}
                    }
                    double end=Math.abs(sweep)==Math.PI*2?v[5]:v[5]+sweep,px=Math.cos(end)*v[2],py=Math.sin(end)*v[3];x=v[0]+px*Math.cos(v[4])-py*Math.sin(v[4]);y=v[1]+px*Math.sin(v[4])+py*Math.cos(v[4]);points.add(x,y);drawn=true;break;
                case CLOSE:
                    result.add(points.contour(true,drawn));points=null;x=startX;y=startY;drawn=false;break;
            }
        }
        if(points!=null)result.add(points.contour(false,drawn));return Collections.unmodifiableList(result);
    }
    static final class Points {
        double[] values=new double[16];int size;
        void add(double x,double y){if(size>0&&values[size-2]==x&&values[size-1]==y)return;
            if(size+2>values.length)values=Arrays.copyOf(values,values.length*2);values[size++]=x;values[size++]=y;}
        Contour contour(boolean closed,boolean drawn){int count=size;
            if(closed&&count>2&&values[0]==values[count-2]&&values[1]==values[count-1])count-=2;
            return new Contour(Arrays.copyOf(values,count),closed,drawn);}
    }
    private static double distance(double x,double y,double ax,double ay,double bx,double by){
        double dx=bx-ax,dy=by-ay,length=Math.hypot(dx,dy);if(length==0)return Math.hypot(x-ax,y-ay);
        double along=(x-ax)*(dx/length)+(y-ay)*(dy/length);
        if(along<0)return Math.hypot(x-ax,y-ay);if(along>length)return Math.hypot(x-bx,y-by);
        return Math.abs((x-ax)*(dy/length)-(y-ay)*(dx/length));
    }
    private static void quad(Points out,double x,double y,double cx,double cy,double ex,double ey,double error){
        double a=(x*0.5+cx*0.5),b=(y*0.5+cy*0.5),c=(cx*0.5+ex*0.5),d=(cy*0.5+ey*0.5),mx=(a*0.5+c*0.5),my=(b*0.5+d*0.5);
        if(distance(cx,cy,x,y,ex,ey)<=error){out.add(ex,ey);return;}
        quad(out,x,y,a,b,mx,my,error);quad(out,mx,my,c,d,ex,ey,error);
    }
    private static void cubic(Points out,double x,double y,double a,double b,double c,double d,double ex,double ey,double error){
        double abx=(a*0.5+c*0.5),aby=(b*0.5+d*0.5),px=(x*0.5+a*0.5),py=(y*0.5+b*0.5),qx=(c*0.5+ex*0.5),qy=(d*0.5+ey*0.5);
        double lx=(px*0.5+abx*0.5),ly=(py*0.5+aby*0.5),rx=(abx*0.5+qx*0.5),ry=(aby*0.5+qy*0.5),mx=(lx*0.5+rx*0.5),my=(ly*0.5+ry*0.5);
        if(Math.max(distance(a,b,x,y,ex,ey),distance(c,d,x,y,ex,ey))<=error){out.add(ex,ey);return;}
        cubic(out,x,y,px,py,lx,ly,mx,my,error);cubic(out,mx,my,rx,ry,qx,qy,ex,ey,error);
    }
    @Override public boolean equals(Object other){return other instanceof ScenePath&&rule==((ScenePath)other).rule&&segments.equals(((ScenePath)other).segments);}
    @Override public int hashCode(){return Objects.hash(rule,segments);}
    public static final class Builder {
        private final List<Segment> segments=new ArrayList<>();private FillRule rule=FillRule.NON_ZERO;
        private double x,y,startX,startY;private boolean current,subpath;
        public Builder rule(FillRule rule){this.rule=Objects.requireNonNull(rule);return this;}
        public Builder moveTo(double x,double y){segments.add(new Segment(Kind.MOVE,x,y));this.x=startX=x;this.y=startY=y;current=subpath=true;return this;}
        private void continuePath(){if(!subpath)moveTo(x,y);}
        public Builder lineTo(double x,double y){if(!current)return moveTo(x,y);continuePath();segments.add(new Segment(Kind.LINE,x,y));this.x=x;this.y=y;return this;}
        public Builder quadTo(double cx,double cy,double x,double y){if(!current)moveTo(cx,cy);continuePath();segments.add(new Segment(Kind.QUAD,cx,cy,x,y));this.x=x;this.y=y;return this;}
        public Builder cubicTo(double ax,double ay,double bx,double by,double x,double y){if(!current)moveTo(ax,ay);continuePath();segments.add(new Segment(Kind.CUBIC,ax,ay,bx,by,x,y));this.x=x;this.y=y;return this;}
        /** 正sweep在y向下场景中顺时针，角度单位为弧度；达到一整圈时只绘制该整圈。 */
        public Builder arc(double cx,double cy,double radius,double start,double sweep){return ellipse(cx,cy,radius,radius,0,start,sweep);}
        public Builder ellipse(double cx,double cy,double rx,double ry,double rotation,double start,double sweep){
            if(rx<0||ry<0)throw new IllegalArgumentException("圆弧半径不能为负");
            if(!Double.isFinite(sweep))throw new IllegalArgumentException("圆弧角度必须有限");
            sweep=Math.copySign(Math.min(Math.abs(sweep),Math.PI*2),sweep);
            double cos=Math.cos(rotation),sin=Math.sin(rotation),px=Math.cos(start)*rx,py=Math.sin(start)*ry;
            double sx=cx+px*cos-py*sin,sy=cy+px*sin+py*cos;
            if(!current)moveTo(sx,sy);else lineTo(sx,sy);
            segments.add(new Segment(Kind.ARC,cx,cy,rx,ry,rotation,start,sweep));double end=Math.abs(sweep)==Math.PI*2?start:start+sweep;px=Math.cos(end)*rx;py=Math.sin(end)*ry;
            x=cx+px*cos-py*sin;y=cy+px*sin+py*cos;return this;
        }
        public Builder close(){if(subpath){segments.add(new Segment(Kind.CLOSE));x=startX;y=startY;subpath=false;}return this;}
        public ScenePath build(){return new ScenePath(this);}
    }
}
