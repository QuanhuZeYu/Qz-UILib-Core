package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.geometry.*;
import club.heiqi.uilib.ui.scene.image.*;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.*;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 新绘制状态保持原资源和分支值身份，旧plan被保留时不发生隐式修改。 */
public final class VectorPaintScenario extends TestApplication {
    @Override protected void runTests()throws Exception{
        scenario("局部clip和外层矩阵组合保持曲线网格与同代覆盖",this::states);
        scenario("备用图像与延后overlay共用一次分支映射",this::branches);
        scenario("flat资源复用只读快照，图像分支保持主需求优先与片段去重",this::resources);
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
    private void resources() {
        LayoutBox box = new LayoutBox(0, 0, 120, 24);
        ImagePlacement placement = ImagePlacement.fit(
                new ImageContent(1, 1, new byte[]{-1, -1, -1, -1}), box, ImageOptions.defaults());
        PaintCommand label = PaintCommand.text(box, null, new MultilineEditingScenario.PlainHost()
                .layout("文字墙资源", new TextStyle("default", 16, -1), box.width));
        PaintCommand plainImage = PaintCommand.image(box, null, placement);
        PaintCommand sharedMark = PaintCommand.rect(box, null, 0xFF00FF00);
        PaintPlan shared = plan(sharedMark);

        resourceOrder(plan(), Collections.emptyList());
        PaintPlanBuilder builder = new PaintPlanBuilder().add(label).add(plainImage).add(label);
        PaintPlan flat = builder.build();
        builder.add(sharedMark);
        resourceOrder(flat, Collections.singletonList(Arrays.asList(label, plainImage, label)));
        require(flat.resourceLayers().get(0) == flat.commands() && flat.resourceCommands() == flat.commands(),
                "flat资源遍历重新复制只读命令快照");

        // 每个字段单独出现且位于后续命令，防止把含分支计划误判为flat。
        PaintCommand[] oneBranch = {
                PaintCommand.image(box, null, placement, null, null, shared),
                PaintCommand.image(box, null, placement, shared, null, null),
                PaintCommand.image(box, null, placement, null, shared, null)
        };
        for (int i = 0; i < oneBranch.length; i++) {
            PaintCommand branch = oneBranch[i];
            List<List<PaintCommand>> expected = i == 0
                    ? Collections.singletonList(Arrays.asList(label, branch, sharedMark))
                    : Arrays.asList(Arrays.asList(label, branch), Collections.singletonList(sharedMark));
            resourceOrder(plan(label, branch), expected);
        }

        PaintCommand pendingMark = PaintCommand.rect(box, null, 0xFFFFFF00);
        PaintCommand failedMark = PaintCommand.rect(box, null, 0xFFFF0000);
        PaintCommand readyMark = PaintCommand.rect(box, null, 0xFF0000FF);
        PaintPlan deep = plan(label);
        PaintCommand pendingImage = PaintCommand.image(box, null, placement, deep, deep, shared);
        PaintPlan pending = plan(pendingMark, pendingImage), failed = plan(failedMark);
        PaintCommand readyImage = PaintCommand.image(box, null, placement, null, null, shared);
        PaintPlan ready = plan(readyMark, readyImage);
        PaintCommand main = PaintCommand.image(box, null, placement, pending, failed, ready);
        PaintCommand deferred = PaintCommand.imageBranchOverlay(main, shared, shared);
        // shared先以备用可达，随后经ready闭包提升；同一片段只在最早层出现。
        resourceOrder(plan(main, label, deferred, main), Arrays.asList(
                Arrays.asList(main, label, deferred, main, readyMark, readyImage, sharedMark),
                Arrays.asList(pendingMark, pendingImage, failedMark),
                Collections.singletonList(label)));

        PaintCommand sourceOnly = PaintCommand.imageBranchOverlay(main, null, null);
        PaintPlan projection = plan(sourceOnly);
        resourceOrder(projection, Collections.singletonList(Collections.singletonList(sourceOnly)));
        require(projection.resourceLayers().get(0) == projection.commands(),
                "branchSource被误当成资源分支");
    }
    private void resourceOrder(PaintPlan plan, List<List<PaintCommand>> expected) {
        List<List<PaintCommand>> layers = plan.resourceLayers();
        require(layers.equals(expected), "资源分层改变主命令、ready闭包或备用的次序/片段身份");
        List<PaintCommand> flattened = new ArrayList<>();
        for (List<PaintCommand> layer : expected) flattened.addAll(layer);
        List<PaintCommand> commands = plan.resourceCommands(), visited = new ArrayList<>();
        plan.visitResources(visited::add);
        require(commands.equals(flattened) && visited.equals(flattened), "资源入口的顺序或去重结果不一致");
        readOnly(layers);
        for (List<PaintCommand> layer : layers) readOnly(layer);
        readOnly(commands);
        readOnly(plan.commands());
    }
    private void readOnly(List<?> values) {
        try { values.add(null); throw new AssertionError("资源列表允许追加"); }
        catch (UnsupportedOperationException expected) { }
        if (!values.isEmpty()) {
            try { values.set(0, null); throw new AssertionError("资源列表允许替换"); }
            catch (UnsupportedOperationException expected) { }
            try { values.remove(0); throw new AssertionError("资源列表允许删除"); }
            catch (UnsupportedOperationException expected) { }
        }
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
