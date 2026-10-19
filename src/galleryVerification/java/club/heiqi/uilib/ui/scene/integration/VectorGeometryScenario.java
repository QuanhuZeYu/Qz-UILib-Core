package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.geometry.*;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** 当前生产路径→网格的数学反馈；期望来自独立面积、点变换与顶点线性关系。 */
public final class VectorGeometryScenario extends TestApplication {
    @Override protected void runTests()throws Exception{
        scenario("矩阵组合逆映射和反射保持同一布局坐标",this::affine);
        scenario("填充规则凹形自交与旧路径不可变",this::fills);
        scenario("二次曲线椭圆和共线回转保留真实几何",this::curves);
        scenario("描边端帽连接与重叠笔段只贡献一次区域",this::strokes);
        scenario("形状洞和嵌套裁剪保留精确覆盖与线性采样坐标",this::clips);
        scenario("嵌套小数矩形保留精确区域且不拆出内部碎片",this::rectangleIntersections);
        scenario("轴向反射与直角变换保留矩形、非轴向仍按真实形状裁剪",this::rectangleTransforms);
        scenario("矩形裁剪一次发布凸多边形并保留全部采样属性",this::rectangleSamples);
    }
    private static void near(double got,double expected,double error,String message){require(Math.abs(got-expected)<=error,message+" got="+got+" expected="+expected);}
    private static ScenePath.Builder square(ScenePath.Builder b,double x,double y,double size,boolean reverse){
        b.moveTo(x,y);if(reverse)b.lineTo(x,y+size).lineTo(x+size,y+size).lineTo(x+size,y);else b.lineTo(x+size,y).lineTo(x+size,y+size).lineTo(x,y+size);return b.close();
    }
    private void affine(){
        SceneTransform m=SceneTransform.of(0,2,-1,0,200,50);near(m.x(35,25),175,0,"世界x");near(m.y(35,25),120,0,"世界y");
        SceneTransform inverse=m.inverse();near(inverse.x(175,120)-10,25,1e-10,"字段原布局横偏移");near(inverse.y(175,120),25,1e-10,"布局y");
        SceneTransform combined=SceneTransform.translation(100,50).multiply(SceneTransform.rotationDegrees(45));
        SceneTransform inv=combined.inverse();require(inv.x(90,55)<0,"旋转包围盒内的假命中未被逆映射排除");
        SceneTransform reflected=SceneTransform.scale(-3,2).then(SceneTransform.translation(7,9));
        near(reflected.x(2,4),1,0,"then的顺序");near(reflected.y(2,4),17,0,"then的平移");near(reflected.maximumScale(),3,1e-12,"最大伸缩率");
        require(SceneTransform.scale(0,1).inverse()==null,"不可逆变换被当成单位矩阵");
        SceneTransform pivot=SceneTransform.around(10,20,SceneTransform.rotationDegrees(90));near(pivot.x(10,20),10,1e-10,"旋转原点x");near(pivot.y(10,20),20,1e-10,"旋转原点y");
    }
    private void fills(){
        ScenePath.Builder builder=square(ScenePath.builder(),0,0,10,false);ScenePath old=builder.build();
        square(builder,3,3,4,false);ScenePath nonzero=builder.build();near(nonzero.fillMesh(.05).area(),10*10,1e-9,"同向非零填充");
        ScenePath evenodd=builder.rule(ScenePath.FillRule.EVEN_ODD).build();SceneMesh hole=evenodd.fillMesh(.05);
        near(hole.area(),10*10-4*4,1e-9,"奇偶孔面积");require(!hole.contains(5,5)&&hole.contains(1,1),"孔或填充区域命中不正确");
        ScenePath opposite=square(square(ScenePath.builder(),0,0,10,false),3,3,4,true).build();near(opposite.fillMesh(.05).area(),84,1e-9,"反向非零孔");
        near(old.fillMesh(.05).area(),100,1e-9,"builder后续修改改变旧路径");
        SceneMesh bow=ScenePath.builder().rule(ScenePath.FillRule.EVEN_ODD).moveTo(0,0).lineTo(10,10).lineTo(0,10).lineTo(10,0).close().build().fillMesh(.05);
        near(bow.area(),2*(10*5.0/2),1e-9,"自交蝴蝶");
        SceneMesh concave=ScenePath.builder().moveTo(0,0).lineTo(10,0).lineTo(10,3).lineTo(3,3).lineTo(3,10).lineTo(0,10).close().build().fillMesh(.05);
        near(concave.area(),10*3+3*7,1e-9,"凹形面积");require(!concave.contains(6,6),"凹角被凸包填满");
    }
    private void curves(){
        boolean rejected=false;try{ScenePath.builder().arc(0,0,10,0,Double.POSITIVE_INFINITY).build();}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"非有限圆弧sweep被一圈限幅静默改为合法声明");
        ScenePath parabola=ScenePath.builder().moveTo(0,0).quadTo(50,100,100,0).close().build();
        near(parabola.fillMesh(.02).area(),100*200.0/6,5,"二次曲线面积");
        near(ScenePath.ellipse(0,0,20,10).fillMesh(.01).area(),Math.PI*20*10,2,"椭圆面积");
        ScenePath loop=ScenePath.builder().moveTo(0,0).cubicTo(100,0,-100,0,0,0).build();
        SceneMesh stroke=loop.strokeMesh(new SceneStroke(2),.01);require(stroke.minX< -28&&stroke.maxX>28&&stroke.contains(25,0)&&stroke.contains(-25,0),"共线回转曲线被错误折成起终点");
        SceneMesh scaled=parabola.fillMesh(.05,SceneTransform.scale(-2,3));near(scaled.area(),100*200.0/6*6,30,"反射缩放面积");
        ScenePath.Contour circle=ScenePath.builder().arc(0,0,10,0,Math.PI*2).close().build().contours(.1).get(0);
        require(circle.closed&&!(circle.x(0)==circle.x(circle.size()-1)&&circle.y(0)==circle.y(circle.size()-1)),"圆闭合保留重复微小接缝");
    }
    private void strokes(){
        ScenePath line=ScenePath.builder().moveTo(0,0).lineTo(10,0).build();
        near(line.strokeMesh(new SceneStroke(2),.001).area(),20,1e-9,"平端帽");
        near(line.strokeMesh(new SceneStroke(2,SceneStroke.Cap.SQUARE,SceneStroke.Join.MITER,10),.001).area(),24,1e-9,"方端帽");
        near(line.strokeMesh(new SceneStroke(2,SceneStroke.Cap.ROUND,SceneStroke.Join.MITER,10),.001).area(),20+Math.PI,.03,"圆端帽");
        ScenePath elbow=ScenePath.builder().moveTo(0,0).lineTo(10,0).lineTo(10,10).build();
        near(elbow.strokeMesh(new SceneStroke(2),.001).area(),40,1e-9,"斜接");
        near(elbow.strokeMesh(new SceneStroke(2,SceneStroke.Cap.BUTT,SceneStroke.Join.BEVEL,10),.001).area(),39.5,1e-9,"斜角连接");
        near(elbow.strokeMesh(new SceneStroke(2,SceneStroke.Cap.BUTT,SceneStroke.Join.ROUND,10),.001).area(),39+Math.PI/4,.01,"圆连接");
        SceneMesh full=SceneShape.vector(ScenePath.rectangle(0,0,10,10),10,10,true,new SceneStroke(2)).resolve(10,10,.02);
        near(full.area(),12*12,1e-7,"填充和居中描边的命中union重复或丢外半");require(full.contains(-.5,5)&&full.contains(10.5,5),"居中描边外半缺失");
        ScenePath repeated=ScenePath.builder().moveTo(0,0).lineTo(10,0).lineTo(0,0).build();near(repeated.strokeMesh(new SceneStroke(2),.001).area(),20,1e-9,"重叠笔段重复贡献覆盖");
    }
    private void rectangleIntersections(){
        SceneClipRegion region=SceneClipRegion.rectangle(new LayoutBox(0,0,720,520))
                .transformed(SceneTransform.translation(0,.25));
        for(int i=1;i<=12;i++){
            double left=.2*i,top=.25+.17*i,width=720-.4*i,height=520-.34*i;
            region=region.intersect(SceneClipRegion.of(SceneMesh.rectangle(left,top,width,height)));
            SceneMesh mesh=region.mesh();
            require(mesh.triangleCount()==2,"矩形交集被内部对角线持续拆碎: depth="+i+" triangles="+mesh.triangleCount());
            near(mesh.minX,left,1e-10,"小数裁剪左边界");near(mesh.minY,top,1e-10,"小数裁剪上边界");
            near(mesh.maxX,left+width,1e-10,"小数裁剪右边界");near(mesh.maxY,top+height,1e-10,"小数裁剪下边界");
            near(mesh.area(),width*height,1e-7,"嵌套矩形交集面积");
            require(region.contains(left+width/2,top+height/2)&&!region.contains(left-.01,top+height/2),"矩形精确边界退化成整数包络");
        }
        club.heiqi.uilib.ui.scene.paint.PaintCommand command=club.heiqi.uilib.ui.scene.paint.PaintCommand
                .rect(new LayoutBox(0,0,720,520),new LayoutBox(0,0,720,520),0xffffffff)
                .withGeometry(SceneTransform.translation(0,.25),region);
        require(command.effectiveClip().mesh().triangleCount()==2,"正式绘制命令重建了矩形碎片");
        near(command.effectiveClip().mesh().area(),region.mesh().area(),1e-7,"绘制命令的小数裁剪区域");
    }
    private void rectangleTransforms(){
        SceneMesh rectangle=SceneMesh.rectangle(-7.5,1.25,20.5,13.5);
        SceneTransform[] transforms={SceneTransform.translation(.125,-.375),
                SceneTransform.scale(-1.25,2.5).then(SceneTransform.translation(4.125,-8.25)),
                SceneTransform.of(0,1,-1,0,10.125,3.5),SceneTransform.of(0,-2,-.5,0,7.25,-9.125)};
        for(SceneTransform transform:transforms){
            SceneMesh moved=rectangle.transformed(transform);
            double width=moved.maxX-moved.minX-1.5,height=moved.maxY-moved.minY-1;
            SceneMesh clipped=moved.intersection(SceneMesh.rectangle(moved.minX+.75,moved.minY+.5,width,height));
            require(clipped.triangleCount()==2,"轴向变换丢失精确矩形: "+transform.a+","+transform.b+","+transform.c+","+transform.d);
            near(clipped.area(),width*height,1e-9,"反射/直角矩形交集面积");
        }
        SceneMesh skewed=SceneMesh.rectangle(0,0,4,2).transformed(SceneTransform.of(1,0,1,1,0,0))
                .intersection(SceneMesh.rectangle(0,0,6,2));
        near(skewed.area(),4*2,1e-10,"非轴向矩形被包络填满");
        require(!skewed.contains(.25,1.75)&&skewed.contains(3,1),"斜切的精确区域被当作轴矩形");
        SceneMesh rotated=rectangle.transformed(SceneTransform.rotationDegrees(90));
        near(rotated.intersection(SceneMesh.rectangle(-100,-100,200,200)).area(),20.5*13.5,1e-9,"带浮点余弦的直角变换被近似");
        SceneMesh collapsed=rectangle.transformed(SceneTransform.scale(0,1));
        require(collapsed.intersection(rectangle).empty()&&!collapsed.contains(0,5),"零缩放产生了可见/可命中的矩形");
    }
    private void rectangleSamples(){
        double[] input={-2,-3,0,0,-6,17.25,10,-3,1,0,18,17.25,
                10,7,1,1,48,17.25,-2,7,0,1,24,17.25};
        java.util.List<double[]> polygons=new java.util.ArrayList<>();
        SceneMesh clip=SceneMesh.rectangle(.125,.375,4.5,3.75);
        clip.clipPolygon(input,6,polygons::add);
        require(polygons.size()==1&&polygons.get(0).length==4*6,"矩形把一个凸多边形重复切成三角分支");
        double[] polygon=polygons.get(0);double twiceArea=0;
        for(int i=0;i<polygon.length;i+=6){
            int next=(i+6)%polygon.length;twiceArea+=polygon[i]*polygon[next+1]-polygon[next]*polygon[i+1];
            near(polygon[i+2],(polygon[i]+2)/12,1e-12,"矩形裁剪u插值");
            near(polygon[i+3],(polygon[i+1]+3)/10,1e-12,"矩形裁剪v插值");
            near(polygon[i+4],2*polygon[i]+3*polygon[i+1]+7,1e-12,"额外顶点属性插值");
            near(polygon[i+5],17.25,1e-12,"常量顶点属性");
            require(polygon[i]>=clip.minX&&polygon[i]<=clip.maxX&&polygon[i+1]>=clip.minY&&polygon[i+1]<=clip.maxY,"插值顶点越过精确轴边界");
        }
        near(Math.abs(twiceArea)/2,4.5*3.75,1e-12,"裁剪后的多边形面积");
        require(polygon!=input,"输出顶点仍借用调用者输入数组");
        java.util.List<double[]> outside=new java.util.ArrayList<>();
        clip.clipPolygon(new double[]{-4,-4,0,0,-3,-4,1,0,-3,-3,1,1},4,outside::add);
        require(outside.isEmpty(),"完全在矩形外的三角形没有被裁掉");
    }
    private void clips(){
        SceneMesh donut=square(square(ScenePath.builder().rule(ScenePath.FillRule.EVEN_ODD),0,0,10,false),3,3,4,false).build().fillMesh(.05);
        SceneClipRegion region=SceneClipRegion.of(donut).intersect(SceneClipRegion.rectangle(new LayoutBox(2,2,6,6)));
        near(region.mesh().area(),6*6-4*4,1e-8,"孔区域交集面积");require(!region.contains(5,5)&&region.contains(2.5,2.5),"精确裁剪孔和外部区域");
        SceneMesh left=SceneMesh.rectangle(2,2,6,6);int[] vertices={0};left.clipPolygon(new double[]{0,0,0,0,10,0,1,0,10,10,1,1,0,10,0,1},4,polygon->{
            for(int i=0;i<polygon.length;i+=4){near(polygon[i+2],polygon[i]/10,1e-12,"裁剪u");near(polygon[i+3],polygon[i+1]/10,1e-12,"裁剪v");vertices[0]++;}});
        require(vertices[0]>0,"裁剪未生成真实顶点");
        ScenePath circles=ScenePath.builder().arc(200,120,50,0,Math.PI*2).close().arc(280,120,50,0,Math.PI*2).close().build();
        SceneMesh mask=circles.fillMesh(.05);require(!mask.contains(240,75)&&mask.contains(240,120),"Konva双圆clip退化为包围盒");
        SceneMesh responsive=SceneShape.ellipse().resolve(100,40,.05);near(responsive.maxX,100,.1,"椭圆外盒宽度");near(responsive.maxY,40,.1,"椭圆外盒高度");
    }
}
