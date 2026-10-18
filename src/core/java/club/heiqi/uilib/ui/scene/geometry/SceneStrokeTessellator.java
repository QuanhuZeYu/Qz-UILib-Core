package club.heiqi.uilib.ui.scene.geometry;

import java.util.ArrayList;
import java.util.List;

/** 描边先生成同向轮廓，再求一次非零填充，交叠笔段不会重复混合透明度。 */
final class SceneStrokeTessellator {
    static SceneMesh stroke(List<ScenePath.Contour> contours,SceneStroke style,double error){
        if(style.width==0)return SceneMesh.EMPTY;double radius=style.width*0.5;List<ScenePath.Contour> outlines=new ArrayList<>();
        for(ScenePath.Contour contour:contours){int count=contour.size();if(count==0||!contour.drawn)continue;
            if(count==1){if(!contour.closed&&style.cap==SceneStroke.Cap.ROUND)circle(outlines,contour.x(0),contour.y(0),radius,error);
                else if(!contour.closed&&style.cap==SceneStroke.Cap.SQUARE)polygon(outlines,contour.x(0)-radius,contour.y(0)-radius,contour.x(0)+radius,contour.y(0)-radius,contour.x(0)+radius,contour.y(0)+radius,contour.x(0)-radius,contour.y(0)+radius);continue;}
            int segments=contour.closed?count:count-1;double[] ux=new double[segments],uy=new double[segments];
            for(int i=0;i<segments;i++){int j=(i+1)%count;double ax=contour.x(i),ay=contour.y(i),bx=contour.x(j),by=contour.y(j),length=Math.hypot(bx-ax,by-ay);
                if(length==0)continue;ux[i]=(bx-ax)/length;uy[i]=(by-ay)/length;double nx=-uy[i]*radius,ny=ux[i]*radius;
                polygon(outlines,ax+nx,ay+ny,bx+nx,by+ny,bx-nx,by-ny,ax-nx,ay-ny);}
            int first=contour.closed?0:1,last=contour.closed?count:count-1;
            for(int i=first;i<last;i++){int before=(i-1+segments)%segments,after=i%segments;
                join(outlines,contour.x(i),contour.y(i),ux[before],uy[before],ux[after],uy[after],style,radius,error);}
            if(!contour.closed){cap(outlines,contour.x(0),contour.y(0),-ux[0],-uy[0],style.cap,radius,error);
                cap(outlines,contour.x(count-1),contour.y(count-1),ux[segments-1],uy[segments-1],style.cap,radius,error);}
        }
        return ScenePathTessellator.fill(outlines,ScenePath.FillRule.NON_ZERO);
    }
    private static void join(List<ScenePath.Contour> output,double x,double y,double ux,double uy,double vx,double vy,SceneStroke style,double r,double error){
        double cross=SceneMesh.cross(ux,uy,vx,vy),dot=ux*vx+uy*vy;
        if(cross==0){if(dot<0&&style.join==SceneStroke.Join.ROUND)circle(output,x,y,r,error);return;}
        double side=cross>0?-1:1,ax=x-uy*r*side,ay=y+ux*r*side,bx=x-vy*r*side,by=y+vx*r*side;
        if(style.join==SceneStroke.Join.ROUND){sector(output,x,y,r,Math.atan2(ay-y,ax-x),Math.atan2(cross,dot),error);return;}
        if(style.join==SceneStroke.Join.MITER){double t=SceneMesh.cross(bx-ax,by-ay,vx,vy)/cross,mx=ax+ux*t,my=ay+uy*t;
            if(Math.hypot(mx-x,my-y)<=style.miterLimit*r){polygon(output,x,y,ax,ay,mx,my,bx,by);return;}}
        polygon(output,x,y,ax,ay,bx,by);
    }
    private static void cap(List<ScenePath.Contour> output,double x,double y,double ux,double uy,SceneStroke.Cap cap,double r,double error){
        if(cap==SceneStroke.Cap.ROUND){circle(output,x,y,r,error);return;}
        if(cap==SceneStroke.Cap.SQUARE){double nx=-uy*r,ny=ux*r;polygon(output,x+nx,y+ny,x+nx+ux*r,y+ny+uy*r,x-nx+ux*r,y-ny+uy*r,x-nx,y-ny);}
    }
    private static void circle(List<ScenePath.Contour> output,double x,double y,double radius,double error){
        ScenePath.Contour circle=ScenePath.builder().arc(x,y,radius,0,Math.PI*2).close().build().contours(error).get(0);output.add(circle);
    }
    private static void sector(List<ScenePath.Contour> output,double x,double y,double radius,double start,double sweep,double error){
        ScenePath.Contour contour=ScenePath.builder().moveTo(x,y).arc(x,y,radius,start,sweep).close().build().contours(error).get(0);
        double[] points=new double[contour.size()*2];for(int i=0;i<contour.size();i++){points[i*2]=contour.x(i);points[i*2+1]=contour.y(i);}polygon(output,points);
    }
    private static void polygon(List<ScenePath.Contour> output,double...points){
        double signed=0;for(int i=0;i<points.length;i+=2){int j=(i+2)%points.length;signed+=points[i]*points[j+1]-points[j]*points[i+1];}
        if(signed==0)return;if(signed<0){for(int i=0,j=points.length-2;i<j;i+=2,j-=2){double x=points[i],y=points[i+1];points[i]=points[j];points[i+1]=points[j+1];points[j]=x;points[j+1]=y;}}
        output.add(new ScenePath.Contour(points,true,true));
    }
}
