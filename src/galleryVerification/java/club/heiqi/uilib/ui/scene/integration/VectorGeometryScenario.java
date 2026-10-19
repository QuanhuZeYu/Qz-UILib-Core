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
