package club.heiqi.uilib.ui.scene.geometry;

import java.util.Arrays;

/** 已准备的平面三角形纯值；坐标不含资源和节点，可同时消费于绘制与精确覆盖判断。 */
public final class SceneMesh {
    public static final SceneMesh EMPTY=new SceneMesh(new double[0]);
    private final double[] positions;
    private final boolean axisRectangle;
    public final double minX,minY,maxX,maxY;
    SceneMesh(double[] positions){this(positions,false);}
    private SceneMesh(double[] positions,boolean rectangle){
        for(double value:positions)if(!Double.isFinite(value))throw new IllegalArgumentException("网格坐标必须有限");
        this.positions=positions.clone();double left=Double.POSITIVE_INFINITY,top=left,right=Double.NEGATIVE_INFINITY,bottom=right;
        for(int i=0;i<positions.length;i+=2){left=Math.min(left,positions[i]);top=Math.min(top,positions[i+1]);right=Math.max(right,positions[i]);bottom=Math.max(bottom,positions[i+1]);}
        minX=positions.length==0?0:left;minY=positions.length==0?0:top;maxX=positions.length==0?0:right;maxY=positions.length==0?0:bottom;
        axisRectangle=rectangle&&minX<maxX&&minY<maxY;
    }
    public static SceneMesh rectangle(double x,double y,double width,double height){
        if(width<=0||height<=0)return EMPTY;
        return rectangleBounds(x,y,x+width,y+height);
    }
    private static SceneMesh rectangleBounds(double left,double top,double right,double bottom){
        return new SceneMesh(new double[]{left,top,right,top,right,bottom,left,top,right,bottom,left,bottom},true);
    }
    public SceneMesh union(SceneMesh other){
        if(this==other||other.empty())return this;if(empty())return other;
        java.util.List<ScenePath.Contour> contours=new java.util.ArrayList<>();addContours(contours,positions);addContours(contours,other.positions);
        return ScenePathTessellator.fill(contours,ScenePath.FillRule.NON_ZERO);
    }
    private static void addContours(java.util.List<ScenePath.Contour> contours,double[] data){
        for(int i=0;i<data.length;i+=6){double[] triangle=Arrays.copyOfRange(data,i,i+6);
            if(cross(triangle[2]-triangle[0],triangle[3]-triangle[1],triangle[4]-triangle[0],triangle[5]-triangle[1])<0){
                double x=triangle[2],y=triangle[3];triangle[2]=triangle[4];triangle[3]=triangle[5];triangle[4]=x;triangle[5]=y;}
            contours.add(new ScenePath.Contour(triangle,true,true));
        }
    }
    public SceneMesh intersection(SceneMesh other){
        if(empty()||other.empty()||maxX<=other.minX||other.maxX<=minX||maxY<=other.minY||other.maxY<=minY)return EMPTY;
        // 保留精确小数边界，避免矩形的内部对角线在嵌套交集中反复切出碎片。
        if(axisRectangle&&other.axisRectangle)return rectangleBounds(Math.max(minX,other.minX),Math.max(minY,other.minY),Math.min(maxX,other.maxX),Math.min(maxY,other.maxY));
        ScenePathTessellator.Triangles output=new ScenePathTessellator.Triangles();
        for(int i=0;i<positions.length;i+=6){double[] triangle=Arrays.copyOfRange(positions,i,i+6);
            other.clipPolygon(triangle,2,polygon->{for(int p=4;p<polygon.length;p+=2)output.add(polygon[0],polygon[1],polygon[p-2],polygon[p-1],polygon[p],polygon[p+1]);});}
        return output.mesh();
    }
    public boolean intersects(SceneMesh other){return !intersection(other).empty();}
    /** 裁剪凸输入多边形并线性插值附加顶点属性；输出数组归消费者，不进入任何活场景状态。 */
    public void clipPolygon(double[] polygon,int stride,java.util.function.Consumer<double[]> output){
        if(stride<2||polygon.length%stride!=0)throw new IllegalArgumentException("多边形顶点格式无效");
        if(axisRectangle){
            double[] part=polygon;
            if(part.length>=stride*3)part=clipAxis(part,stride,0,minX,true);
            if(part.length>=stride*3)part=clipAxis(part,stride,0,maxX,false);
            if(part.length>=stride*3)part=clipAxis(part,stride,1,minY,true);
            if(part.length>=stride*3)part=clipAxis(part,stride,1,maxY,false);
            if(part.length>=stride*3)output.accept(part);
            return;
        }
        for(int i=0;i<positions.length;i+=6){double[] part=polygon;
            double direction=cross(positions[i+2]-positions[i],positions[i+3]-positions[i+1],positions[i+4]-positions[i],positions[i+5]-positions[i+1]);
            if(direction==0)continue;direction=Math.copySign(1,direction);
            for(int edge=0;edge<3&&part.length>=stride*3;edge++){int a=i+edge*2,b=i+(edge+1)%3*2;
                part=clipEdge(part,stride,positions[a],positions[a+1],positions[b],positions[b+1],direction);}
            if(part.length>=stride*3)output.accept(part);
        }
    }
    private static double[] clipAxis(double[] points,int stride,int axis,double boundary,boolean minimum){
        double[] output=new double[points.length*2];int size=0,previous=points.length-stride;
        double before=minimum?points[previous+axis]-boundary:boundary-points[previous+axis];
        for(int current=0;current<points.length;current+=stride){
            double after=minimum?points[current+axis]-boundary:boundary-points[current+axis];
            if((before>=0)!=(after>=0)){
                double ratio=before/(before-after);
                for(int v=0;v<stride;v++)output[size++]=v==axis?boundary:points[previous+v]+(points[current+v]-points[previous+v])*ratio;
            }
            if(after>=0){System.arraycopy(points,current,output,size,stride);size+=stride;}
            previous=current;before=after;
        }
        return Arrays.copyOf(output,size);
    }
    private static double[] clipEdge(double[] points,int stride,double ax,double ay,double bx,double by,double direction){
        // 每条输入边至多产生一个交点和当前端点；重复边界及浮点投影仍遵守2n上界。
        double[] output=new double[points.length*2];int size=0,previous=points.length-stride;
        double before=cross(bx-ax,by-ay,points[previous]-ax,points[previous+1]-ay)*direction;
        for(int current=0;current<points.length;current+=stride){double after=cross(bx-ax,by-ay,points[current]-ax,points[current+1]-ay)*direction;
            if((before>=0)!=(after>=0)){double ratio=before/(before-after);for(int v=0;v<stride;v++)output[size++]=points[previous+v]+(points[current+v]-points[previous+v])*ratio;}
            if(after>=0){System.arraycopy(points,current,output,size,stride);size+=stride;}
            previous=current;before=after;
        }
        return Arrays.copyOf(output,size);
    }
    public int vertexCount(){return positions.length/2;}
    public int triangleCount(){return positions.length/6;}
    public double x(int vertex){return positions[vertex*2];}
    public double y(int vertex){return positions[vertex*2+1];}
    public boolean empty(){return positions.length==0;}
    public SceneMesh transformed(SceneTransform transform){
        if(transform.identity()||empty())return this;double[] result=new double[positions.length];
        for(int i=0;i<positions.length;i+=2){result[i]=transform.x(positions[i],positions[i+1]);result[i+1]=transform.y(positions[i],positions[i+1]);}
        // 仅严格轴向矩阵延续矩形事实；旋转的浮点余弦和一般斜切仍走原网格。
        boolean axis=axisRectangle&&(transform.b==0&&transform.c==0||transform.a==0&&transform.d==0);
        return new SceneMesh(result,axis);
    }
    public boolean contains(double x,double y){
        if(empty()||x<minX||x>maxX||y<minY||y>maxY)return false;
        for(int i=0;i<positions.length;i+=6){
            double a=cross(positions[i+2]-positions[i],positions[i+3]-positions[i+1],x-positions[i],y-positions[i+1]);
            double b=cross(positions[i+4]-positions[i+2],positions[i+5]-positions[i+3],x-positions[i+2],y-positions[i+3]);
            double c=cross(positions[i]-positions[i+4],positions[i+1]-positions[i+5],x-positions[i+4],y-positions[i+5]);
            if((a>=0&&b>=0&&c>=0||a<=0&&b<=0&&c<=0)&&cross(positions[i+2]-positions[i],positions[i+3]-positions[i+1],positions[i+4]-positions[i],positions[i+5]-positions[i+1])!=0)return true;
        }
        return false;
    }
    public double area(){double area=0;for(int i=0;i<positions.length;i+=6)
        area+=Math.abs(cross(positions[i+2]-positions[i],positions[i+3]-positions[i+1],positions[i+4]-positions[i],positions[i+5]-positions[i+1]))*0.5;return area;}
    static double cross(double x,double y,double u,double v){return x*v-y*u;}
    @Override public boolean equals(Object other){return other instanceof SceneMesh&&Arrays.equals(positions,((SceneMesh)other).positions);}
    @Override public int hashCode(){return Arrays.hashCode(positions);}
}
