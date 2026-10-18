package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.geometry.*;
import club.heiqi.uilib.ui.scene.image.*;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.*;

/** 新绘制状态保持原资源和分支值身份，旧plan被保留时不发生隐式修改。 */
public final class VectorPaintScenario extends TestApplication {
    public static void main(String[] args)throws Exception{new VectorPaintScenario().run(args);}
    @Override protected void runTests()throws Exception{
        scenario("局部clip和外层矩阵组合保持曲线网格与同代覆盖",this::states);
        scenario("备用图像与延后overlay共用一次分支映射",this::branches);
        scenario("旧呈现片段和新仿射片段平移保持各自坐标合同",this::presentation);
    }
    private static PaintPlan plan(PaintCommand...commands){PaintPlanBuilder b=new PaintPlanBuilder();for(PaintCommand c:commands)b.add(c);return b.build();}
    private void states(){
        SceneMesh mesh=ScenePath.rectangle(0,0,10,10).fillMesh(.1);PaintCommand original=PaintCommand.path(mesh,new LayoutBox(0,0,5,10),0xFF123456);
        SceneTransform a=SceneTransform.translation(20,30).multiply(SceneTransform.rotationDegrees(90));
        PaintCommand one=original.withGeometry(a,SceneClipRegion.UNBOUNDED),two=one.withGeometry(SceneTransform.translation(7,9),SceneClipRegion.UNBOUNDED);
        require(one.mesh==original.mesh&&one.box==original.box&&original.transform.identity(),"映射修改原网格或局部盒");
        require(one.effectiveClip().contains(a.x(2,4),a.y(2,4))&&!one.effectiveClip().contains(a.x(8,4),a.y(8,4)),"局部clip被当成世界AABB");
        require(two.transform.equals(SceneTransform.translation(7,9).multiply(a))&&two.effectiveClip().contains(two.transform.x(2,4),two.transform.y(2,4)),"外层矩阵顺序错误");
        require(two.kind==PaintCommand.Kind.PATH&&two.color==original.color&&two.mesh==mesh,"PATH载荷丢失");
        PaintCommand ordinary=PaintCommand.rect(new LayoutBox(1,2,10,10),null,0xFFFFFFFF).withGeometry(SceneTransform.IDENTITY,SceneClipRegion.rectangle(new LayoutBox(0,0,8,8)));
        require(ordinary.sceneGeometry&&ordinary.transform.identity()&&ordinary.shapeClip.unbounded()&&ordinary.clip.width==8,"普通矩形用法被强制改成新shape载荷");
        PaintCommand legacy=PaintCommand.rect(new LayoutBox(0,0,10,10),null,0xFFFFFFFF);
        require(!legacy.sceneGeometry&&plan(legacy).transformed(SceneTransform.IDENTITY,SceneClipRegion.UNBOUNDED).commands().get(0).sceneGeometry,"明确使用正式几何的单位值被旧优化丢弃");
        PaintCommand moved=plan(ordinary).presented(3,4,1,null).commands().get(0);require(moved.sceneGeometry&&moved.box.x==4&&moved.box.y==6&&moved.clip.x==3,"普通片段平移不再保持原box/clip合同");
    }
    private void branches(){
        ImageContent content=new ImageContent(1,1,new byte[]{-1,-1,-1,-1});LayoutBox box=new LayoutBox(0,0,10,10);
        PaintPlan fallback=plan(PaintCommand.rect(box,null,0xFF00FF00));
        PaintPlan ready=plan(PaintCommand.rect(new LayoutBox(1,1,8,8),null,0xFF0000FF));
        PaintCommand image=PaintCommand.image(box,null,ImagePlacement.fit(content,box,ImageOptions.defaults()),fallback,fallback,ready);
        PaintPlan main=plan(image),overlay=plan(PaintCommand.imageBranchOverlay(image,fallback,ready));SceneTransform transform=SceneTransform.translation(30,40);
        PaintPlan.Transformation mapping=PaintPlan.transformation(transform,SceneClipRegion.rectangle(new LayoutBox(0,0,100,100)));
        PaintCommand mapped=mapping.apply(main).commands().get(0),deferred=mapping.apply(overlay).commands().get(0);
        require(mapped.sceneGeometry&&deferred.sceneGeometry&&mapped.pendingPaint.commands().get(0).sceneGeometry&&deferred.branchSource==mapped&&mapped.pendingPaint==mapped.failedPaint,"备用/overlay丢失原主图分支身份");
        require(mapped.pendingPaint.commands().get(0).transform.equals(transform)&&mapped.readyOverlay.commands().get(0).transform.equals(transform),"备用或readyOverlay未受同一外层变换");
        require(image.pendingPaint.commands().get(0).transform.identity()&&image.image==mapped.image,"外层变换烘坏旧image或原计划");
    }
    private void presentation(){
        SceneMesh mesh=ScenePath.rectangle(0,0,10,10).fillMesh(.1);PaintPlan old=plan(PaintCommand.path(mesh,null,0xFFFFFFFF));
        PaintCommand translated=old.presented(7,9,1,null).commands().get(0);
        require(translated.transform.identity()&&translated.box.x==7&&translated.box.y==9&&translated.mesh.contains(8,10)&&!translated.mesh.contains(1,1),"旧片段没有沿原像素位置平移网格");
        SceneTransform rotation=SceneTransform.rotationDegrees(30);PaintCommand geometric=old.transformed(rotation,SceneClipRegion.UNBOUNDED).presented(7,9,.5f,new LayoutBox(-100,-100,200,200)).commands().get(0);
        require(geometric.mesh==mesh&&geometric.box.x==0&&geometric.transform.equals(SceneTransform.translation(7,9).multiply(rotation)),"新片段重复平移局部网格");
        require((geometric.color>>>24)==128,"呈现透明度丢失");
    }
}
