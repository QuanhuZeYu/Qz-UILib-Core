package club.heiqi.uilib.ui.scene.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** 分割交点Y后的扫边带中顺序稳定；同一填充区域只生成一次，不叠半透明轮廓。 */
final class ScenePathTessellator {
    private static final class Edge {
        final double ax,ay,bx,by;final int winding;
        Edge(double ax,double ay,double bx,double by){this.ax=ax;this.ay=ay;this.bx=bx;this.by=by;winding=by>ay?1:-1;}
        double x(double y){return ax+(y-ay)*(bx-ax)/(by-ay);}
    }
    static SceneMesh fill(List<ScenePath.Contour> contours,ScenePath.FillRule rule){
        List<Edge> edges=new ArrayList<>();TreeSet<Double> levels=new TreeSet<>();
        for(ScenePath.Contour contour:contours)for(int i=0;i<contour.size();i++){
            int next=(i+1)%contour.size();double ax=contour.x(i),ay=contour.y(i),bx=contour.x(next),by=contour.y(next);
            if(ay==by)continue;edges.add(new Edge(ax,ay,bx,by));levels.add(ay==0?0:ay);levels.add(by==0?0:by);
        }
        for(int i=0;i<edges.size();i++)for(int j=i+1;j<edges.size();j++){
            Edge a=edges.get(i),b=edges.get(j);double dx=a.bx-a.ax,dy=a.by-a.ay,sx=b.bx-b.ax,sy=b.by-b.ay;
            double determinant=SceneMesh.cross(dx,dy,sx,sy);if(determinant==0)continue;
            double px=b.ax-a.ax,py=b.ay-a.ay,t=SceneMesh.cross(px,py,sx,sy)/determinant,u=SceneMesh.cross(px,py,dx,dy)/determinant;
            if(t>0&&t<1&&u>0&&u<1)levels.add(a.ay+t*dy);
        }
        Triangles triangles=new Triangles();Double previous=null;
        for(double high:levels){if(previous==null){previous=high;continue;}double low=previous;previous=high;double middle=low+(high-low)*0.5;
            List<Edge> active=new ArrayList<>();for(Edge edge:edges)if(middle>Math.min(edge.ay,edge.by)&&middle<Math.max(edge.ay,edge.by))active.add(edge);
            active.sort(Comparator.comparingDouble(edge->edge.x(middle)));int winding=0;Edge left=null;
            for(Edge edge:active){boolean before=inside(winding,rule);winding+=edge.winding;boolean after=inside(winding,rule);
                if(!before&&after)left=edge;
                if(before&&!after&&left!=null){double ax=left.x(low),bx=edge.x(low),cx=edge.x(high),dx=left.x(high);
                    triangles.add(ax,low,bx,low,cx,high);triangles.add(ax,low,cx,high,dx,high);}
            }
        }
        return triangles.mesh();
    }
    private static boolean inside(int winding,ScenePath.FillRule rule){return rule==ScenePath.FillRule.NON_ZERO?winding!=0:(winding&1)!=0;}
    static final class Triangles {
        double[] values=new double[48];int count;
        void add(double ax,double ay,double bx,double by,double cx,double cy){
            if(SceneMesh.cross(bx-ax,by-ay,cx-ax,cy-ay)==0)return;
            if(count+6>values.length)values=Arrays.copyOf(values,values.length*2);
            values[count++]=ax;values[count++]=ay;values[count++]=bx;values[count++]=by;values[count++]=cx;values[count++]=cy;
        }
        SceneMesh mesh(){return count==0?SceneMesh.EMPTY:new SceneMesh(Arrays.copyOf(values,count));}
    }
}
