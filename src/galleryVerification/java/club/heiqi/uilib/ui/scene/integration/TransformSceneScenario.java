package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.geometry.*;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.image.*;
import club.heiqi.uilib.ui.scene.motion.*;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** 真实Session验证普通子树变换、局部旧控件输入、精确clip及原所有权。 */
public final class TransformSceneScenario extends TestApplication {
    protected void runTests() throws Exception {
        scenario("旋转按钮与字段沿旧caret入口且计划只变换一次", this::buttonAndField);
        scenario("形状孔洞拒绝点击和静止悬停随同批裁剪更新", this::shapeHole);
        scenario("旋转滑杆提交及零缩放取消吞尾", this::sliderAndSingular);
        scenario("旋转滚动容器按局部轴最小显露", this::scrollReveal);
        scenario("Layer逃离来源仿射与clip且fullclip取消来源", this::layerSource);
        scenario("持续拖动使用稳定父空间并保原位移单位", this::drag);
        scenario("图像可见和离屏备用捕获均只乘一次原矩阵", this::imageCapture);
        scenario("退场片段跟随当前父矩阵及形状裁剪且无输入身份", this::presenceCapture);
        scenario("默认滚动条继承原host矩阵并沿局部轨道拖动", this::scrollbar);
        scenario("旋转有限集合沿世界方向准备下一逻辑行", this::virtualDirection);
        scenario("旋转排序按目的容器局部主轴发布原Move", this::sortable);
        scenario("焦点在形状洞内沿原滚动轴显露真实交集", this::holeReveal);
        scenario("identity原geometry及事件对象入口保持", this::identity);
    }
    private static MultilineEditingScenario.PlainHost host() {
        MultilineEditingScenario.PlainHost host = new MultilineEditingScenario.PlainHost(); host.width = 640; host.height = 480; return host;
    }
    private static SceneTransform rotated() { return SceneTransform.translation(300, 60).multiply(SceneTransform.rotationDegrees(90)); }
    private static SceneGeometryView geometry(ScenePageSession page) {
        SceneGeometryView[] value = new SceneGeometryView[1]; page.domain().execute(() -> value[0] = page.pipeline().current().geometry); return value[0];
    }
    private static int[] point(ScenePageSession page, SceneNode node, double x, double y) {
        SceneGeometryView.Entry entry = geometry(page).get(node);
        return new int[] {(int) Math.round(entry.transform.x(entry.layoutBox.x + x, entry.layoutBox.y + y)),
                (int) Math.round(entry.transform.y(entry.layoutBox.x + x, entry.layoutBox.y + y))};
    }
    private static void click(ScenePageSession page, int[] p) { page.pointer(ScenePointerEvent.down(0,p[0],p[1])); page.pointer(ScenePointerEvent.up(0,p[0],p[1])); page.frame(); }
    private void buttonAndField() {
        ReactiveDomain domain = ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host = host();
        SceneNode[] nodes = new SceneNode[2]; SceneEditState[] state = new SceneEditState[1]; int[] clicks = {0};
        try (ScenePageSession page = ScenePageSession.open(domain, host, host, ctx -> {
            ctx.runtime().motionPolicy(() -> MotionPolicy.OFF); state[0] = new SceneEditState(domain,"abcd");
            nodes[0] = SceneButton.create(ctx, () -> "按钮", ctx.theme(), () -> clicks[0]++).size(Size.fixed(140),Size.fixed(36));
            nodes[1] = SceneTextField.create(ctx,state[0],ctx.theme(),()->false,s->{}).size(Size.fixed(140),Size.fixed(36));
            ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(new SceneNode().size(Size.fixed(160),Size.fixed(100))
                    .gap(8).transform(rotated()).add(nodes[0]).add(nodes[1])));
        })) {
            page.frame(); click(page,point(page,nodes[0],50,18)); require(clicks[0]==1,"旋转普通按钮未激活");
            SceneGeometryView geometry = geometry(page);
            require(geometry.asControl(nodes[1])==geometry.asControl(nodes[1]),"本批consumer view未缓存身份");
            require(geometry.asControl(nodes[1]).world()==geometry,"局部view未保唯一world");
            click(page,point(page,nodes[1],nodes[1].padding()+nodes[1].style().size,18));
            page.edit(SceneEditEvent.text("X")); page.frame();
            domain.execute(() -> require("abXcd".equals(state[0].text().get()),"旋转字段未按原局部caret插入："+state[0].text().get()));
            boolean transformedText=false; for(PaintCommand command:host.lastPlan.commands())
                if(command.kind==PaintCommand.Kind.TEXT&&!command.transform.identity()) transformedText=true;
            require(transformedText,"普通文字计划未携带累计矩阵");
        }
    }
    private static SceneShape hole() {
        ScenePath path = ScenePath.builder().rule(ScenePath.FillRule.EVEN_ODD)
                .moveTo(0,0).lineTo(100,0).lineTo(100,100).lineTo(0,100).close()
                .moveTo(25,25).lineTo(75,25).lineTo(75,75).lineTo(25,75).close().build();
        return SceneShape.path(path,100,100);
    }
    private void shapeHole() {
        ReactiveDomain domain=ReactiveDomain.direct(); MultilineEditingScenario.PlainHost host=host();
        SceneNode[] node=new SceneNode[2]; Signal<SceneShape>[] clip=new Signal[1]; int[] clicks={0};
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            ctx.runtime().motionPolicy(()->MotionPolicy.OFF); clip[0]=domain.signal(hole());
            node[0]=SceneButton.create(ctx,()->"",ctx.theme(),()->clicks[0]++).size(Size.fixed(100),Size.fixed(100));
            node[1]=new SceneNode().size(Size.fixed(100),Size.fixed(100)).transform(SceneTransform.translation(180,80).multiply(SceneTransform.rotationDegrees(35)))
                    .bind(NodeBinding.value(clip[0],(n,s)->n.clip(s))).add(node[0]); ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(node[1]));
        })) {
            page.frame();int[] center=point(page,node[0],50,50);click(page,center);require(clicks[0]==0,"clip孔洞退回矩形click");
            click(page,point(page,node[0],10,10));require(clicks[0]==1,"clip有效填充没有旧按钮命中");
            page.pointer(ScenePointerEvent.move(center[0],center[1]));page.frame();
            domain.execute(()->require(page.runtime().pointerRouter().hovered(geometry(page))==null,"孔洞发布hover"));
            domain.execute(()->clip[0].set(null));page.frame();
            domain.execute(()->require(page.runtime().pointerRouter().hovered(geometry(page))==node[0],"裁剪变化后静止指针没有按同批事实重算"));
        }
    }
    private void sliderAndSingular() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] node=new SceneNode[2];
        Signal<SceneTransform>[] transform=new Signal[1];List<Double> commits=new ArrayList<>();int[] neighbor={0};
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            ctx.runtime().motionPolicy(()->MotionPolicy.OFF); transform[0]=domain.signal(rotated());
            node[0]=SceneSlider.create(ctx,()->new SceneSlider.Model(0,100,1,0,true),v->{},commits::add,ctx.theme()).size(Size.fixed(160),Size.fixed(40));
            node[1]=new SceneNode().size(Size.fixed(180),Size.fixed(80)).bind(NodeBinding.value(transform[0],SceneNode::transform)).add(node[0]);
            ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(node[1]).add(new SceneNode().size(Size.fixed(160),Size.fixed(40)).onClick(()->neighbor[0]++)));
        })) {
            page.frame();int[] a=point(page,node[0],12,20),b=point(page,node[0],120,20);
            page.pointer(ScenePointerEvent.down(0,a[0],a[1]));page.pointer(ScenePointerEvent.move(b[0],b[1]));page.pointer(ScenePointerEvent.up(0,b[0],b[1]));page.frame();
            require(commits.size()==1&&commits.get(0)>60,"旋转滑杆未按局部轨道提交："+commits);
            page.pointer(ScenePointerEvent.down(0,a[0],a[1]));page.frame();domain.execute(()->transform[0].set(SceneTransform.scale(0,1)));page.frame();
            page.pointer(ScenePointerEvent.up(0,20,100));page.frame();require(commits.size()==1&&neighbor[0]==0,"失去逆映射的旧UP投给新目标或提交");
        }
    }
    private void scrollReveal() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] scroll=new SceneNode[1];List<SceneNode> buttons=new ArrayList<>();
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            ctx.runtime().motionPolicy(()->MotionPolicy.OFF);scroll[0]=new SceneNode().size(Size.fixed(120),Size.fixed(80)).scrollable(true).transform(rotated());
            for(int i=0;i<7;i++){SceneNode button=SceneButton.create(ctx,()->"",ctx.theme(),()->{}).size(Size.fixed(120),Size.fixed(30));buttons.add(button);scroll[0].add(button);}
            ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(scroll[0]));
        })) {
            page.frame();click(page,point(page,buttons.get(0),40,15));for(int i=0;i<6;i++)page.navigation(SceneNavigationEvent.NEXT);page.frame();
            domain.execute(()->require(page.runtime().scrollOffset(scroll[0])>0,"旋转scroll按world y误判而未显露"));
            SceneGeometryView view=geometry(page).asControl(scroll[0]);LayoutBox box=view.get(buttons.get(6)).box,window=view.get(scroll[0]).box;
            require(box.y>=window.y&&(long)box.y+box.height<=(long)window.y+window.height,"主动导航未在局部轴最小显露");
            domain.execute(()->require(page.runtime().focusRouter().focused().get()==buttons.get(6),"显露改变原逻辑focus"));
        }
    }
    private void layerSource() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] nodes=new SceneNode[3];Signal<SceneShape>[] sourceClip=new Signal[1];
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            sourceClip[0]=domain.signal(hole());
            nodes[1]=new SceneNode().size(Size.fixed(30),Size.fixed(30)).translation(35,35);
            nodes[2]=SceneLayer.create(ctx,()->new SceneLayer.Policy(0,SceneLayer.Participation.PASSIVE)).size(Size.fixed(60),Size.fixed(20));
            nodes[2].add(new SceneNode().size(Size.fixed(60),Size.fixed(20)).background(0xff123456));nodes[1].add(nodes[2]);
            nodes[0]=new SceneNode().size(Size.fixed(100),Size.fixed(100)).transform(SceneTransform.translation(220,100).multiply(SceneTransform.rotationDegrees(30))).bind(NodeBinding.value(sourceClip[0],(n,s)->n.clip(s))).add(nodes[1]);
            ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(nodes[0]));
        })) {
            page.frame();require(!geometry(page).layerSourceAvailable(nodes[2]),"完全在shape洞中的来源仍发布layer");
            domain.execute(()->sourceClip[0].set(null));page.frame();SceneGeometryView geometry=geometry(page);
            require(geometry.layerSourceAvailable(nodes[2])&&geometry.get(nodes[2])!=null,"来源重新有真实交集后layer未呈现");
            require(geometry.get(nodes[2]).transform.identity(),"页面锚定layer重复继承来源仿射");
        }
    }
    private void drag() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] node=new SceneNode[1];SceneDraggable.State[] state=new SceneDraggable.State[1];List<SceneDraggable.Snapshot> drops=new ArrayList<>();
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            ctx.runtime().motionPolicy(()->MotionPolicy.OFF);state[0]=SceneDraggable.state(domain);
            node[0]=SceneDraggable.create(ctx,new SceneNode().size(Size.fixed(40),Size.fixed(40)).background(0xff445566),state[0],new SceneDraggable.Options().onDrop(drops::add)).size(Size.fixed(40),Size.fixed(40)).transform(SceneTransform.rotationDegrees(90));
            ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(new SceneNode().size(Size.fixed(160),Size.fixed(100))
                    .transform(rotated().multiply(SceneTransform.scale(1.5,1.5))).add(node[0])));
        })) {
            page.frame();int[] a=point(page,node[0],10,10),b=point(page,node[0],30,22);page.pointer(ScenePointerEvent.down(0,a[0],a[1]));page.pointer(ScenePointerEvent.move(b[0],b[1]));page.frame();
            domain.execute(()->{SceneDraggable.Snapshot s=state[0].snapshot().get();require(s.active&&s.deltaX==20&&s.deltaY==12,"拖动没有按稳定父布局单位推进："+s.deltaX+","+s.deltaY);});
            page.frame();page.frame();domain.execute(()->require(state[0].snapshot().get().deltaX==20,"静止指针被已移动frame回授"));
            page.pointer(ScenePointerEvent.up(0,b[0],b[1]));page.frame();require(drops.size()==1&&drops.get(0).deltaX==20&&drops.get(0).deltaY==12,"拖动释放未沿原合法位移单次结算");
        }
    }
    private static PaintCommand colored(PaintPlan plan, int rgb) {
        for (PaintCommand command : plan.commands()) if ((command.color & 0xffffff) == rgb) return command;
        return null;
    }
    private static void samePosition(PaintCommand a, PaintCommand b) {
        require(Math.abs(a.transform.x(a.box.x,a.box.y)-b.transform.x(b.box.x,b.box.y))<1e-6
                && Math.abs(a.transform.y(a.box.x,a.box.y)-b.transform.y(b.box.x,b.box.y))<1e-6,"捕获改变局部图元的世界位置");
    }
    private void imageCapture() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();Signal<ImageState>[] status=new Signal[1];SceneNode[] image=new SceneNode[1];
        ImageSource source=ImageSource.resource("transform-test");ImageContent bitmap=new ImageContent(1,1,new byte[]{1,2,3,(byte)255});
        ImageService service=new ImageService(){public Lease acquire(ImageSource s,ReactiveDomain d){return new Lease(){public club.heiqi.uilib.ui.reactive.ReadableSignal<ImageState> state(){return status[0];}public void close(){}};}public void reload(ImageSource s){}};
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            status[0]=domain.signal(ImageState.loading(source,1));
            SceneNode fallback=new SceneNode().size(Size.fixed(80),Size.fixed(60)).transform(SceneTransform.translation(4,5)).control(new SceneControl(){
                public MeasuredSize measure(SceneNode node,Constraints body,club.heiqi.uilib.ui.scene.text.SceneTextMeasurer text){return new MeasuredSize(80,60);}
                public ChildPlacements arrange(SceneNode node,LayoutBox box,SceneChildMeasurer children){return SceneFlowLayout.arrange(node,box,children);}
                public void paint(SceneNode node,SceneGeometryView geometry,club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder paint){
                    SceneGeometryView.Entry own=geometry.get(node),parent=geometry.get(node.parent());
                    require(parent!=null&&parent.box.width==80,"离屏备用的旧parent盒查询丢失");
                    paint.add(PaintCommand.rect(own.box,own.clip,0xff3456ab));
                }
            });
            image[0]=SceneImage.create(ctx,service,()->source,()->new ImageOptions(Size.fixed(80),Size.fixed(60),ImageOptions.Fit.STRETCH,0,0,80,60,"",false),ctx.theme(),fallback);
            ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(new SceneNode().size(Size.fixed(100),Size.fixed(80)).transform(rotated()).clip(SceneShape.ellipse()).add(image[0])));
        })) {
            page.frame();PaintCommand before=colored(host.lastPlan,0x3456ab);require(before!=null,"loading未回放正规备用子树");
            domain.execute(()->status[0].set(ImageState.ready(source,1,bitmap)));page.frame();PaintCommand primary=null;
            for(PaintCommand command:host.lastPlan.commands())if(command.kind==PaintCommand.Kind.IMAGE)primary=command;
            require(primary!=null&&primary.pendingPaint!=null,"READY未携带离屏备用计划");PaintCommand captured=colored(primary.pendingPaint,0x3456ab);
            require(captured!=null,"READY丢失备用内容");samePosition(before,captured);
            SceneMesh a=before.effectiveClip().mesh(),b=captured.effectiveClip().mesh();
            double overlap=a.intersection(b).area();
            require(Math.abs(a.area()-overlap)<1e-5&&Math.abs(b.area()-overlap)<1e-5,"离屏备用没有保留同批有效shape clip："+a.area()+","+b.area()+","+overlap);
        }
    }
    private void presenceCapture() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();Signal<Boolean>[] shown=new Signal[1];Signal<SceneTransform>[] transform=new Signal[1];SceneNode[] nodes=new SceneNode[2];
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            shown[0]=domain.signal(true);transform[0]=domain.signal(SceneTransform.translation(160,70).multiply(SceneTransform.rotationDegrees(15)));
            nodes[1]=new SceneNode().size(Size.fixed(80),Size.fixed(60)).background(0xff6543ab).onClick(()->{throw new AssertionError("退场画面接收输入");})
                    .bind(SceneMotion.presence(shown[0],()->new PresenceOptions(MotionSpec.tween(1_000_000_000L,MotionSpec.LINEAR))));
            nodes[0]=new SceneNode().size(Size.fixed(80),Size.fixed(60)).clip(SceneShape.ellipse()).bind(NodeBinding.value(transform[0],SceneNode::transform)).add(nodes[1]);
            ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(nodes[0]));
        })) {
            page.frame();domain.execute(()->shown[0].set(false));page.frame();
            domain.execute(()->transform[0].set(SceneTransform.translation(260,100).multiply(SceneTransform.rotationDegrees(55))));page.frame();
            PaintCommand ghost=colored(host.lastPlan,0x6543ab);require(ghost!=null&&geometry(page).get(nodes[1])==null,"退场未保画面或恢复输入Entry");
            SceneGeometryView.Entry parent=geometry(page).get(nodes[0]);
            require(Math.abs(ghost.transform.x(ghost.box.x,ghost.box.y)-parent.transform.x(ghost.box.x,ghost.box.y))<1e-6
                    &&Math.abs(ghost.transform.y(ghost.box.x,ghost.box.y)-parent.transform.y(ghost.box.x,ghost.box.y))<1e-6,"退场只平移未跟随当前父仿射");
            require(!ghost.effectiveClip().contains(parent.transform.x(1,1),parent.transform.y(1,1)),"退场丢失当前祖先shape clip");
        }
    }
    private void scrollbar() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] viewport=new SceneNode[1];
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            ctx.runtime().motionPolicy(()->MotionPolicy.OFF);viewport[0]=new SceneNode().size(Size.fixed(120),Size.fixed(100)).scrollable(true).transform(rotated());
            viewport[0].add(new SceneNode().size(Size.fixed(100),Size.fixed(400)));ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(viewport[0]));
        })) {
            page.frame();SceneGeometryView geometry=geometry(page);SceneNode bar=null;
            List<SceneNode> bars=new ArrayList<>();domain.execute(()->bars.addAll(page.runtime().scrollDecorations(viewport[0])));
            for(SceneNode candidate:bars)if(geometry.get(candidate).layoutBox.height>geometry.get(candidate).layoutBox.width)bar=candidate;
            require(bar!=null&&geometry.get(bar).transform.equals(geometry.get(viewport[0]).transform),"默认条未继承host矩阵");
            int[] a=point(page,bar,5,6),b=point(page,bar,5,60);page.pointer(ScenePointerEvent.down(0,a[0],a[1]));page.pointer(ScenePointerEvent.move(b[0],b[1]));page.pointer(ScenePointerEvent.up(0,b[0],b[1]));page.frame();
            domain.execute(()->require(page.runtime().scrollOffset(viewport[0])>0,"旋转默认条没有沿局部thumb轨道更新原位置"));
        }
    }
    private void virtualDirection() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();Map<Integer,SceneNode> rows=new HashMap<>();SceneVirtualList<Integer,Integer>[] list=new SceneVirtualList[1];
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            ctx.runtime().motionPolicy(()->MotionPolicy.OFF);List<Integer> values=new ArrayList<>();for(int i=0;i<30;i++)values.add(i);
            list[0]=SceneVirtualList.create(ctx,()->values,i->i,value->{int key=value.get();SceneNode row=SceneButton.create(ctx,()->"",ctx.theme(),()->{}).size(Size.fill(1),Size.fixed(30));rows.put(key,row);return row;},()->new SceneVirtualList.Options().estimatedRowHeight(30).overscan(0));
            list[0].node().size(Size.fixed(120),Size.fixed(90)).transform(rotated());ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(list[0].node()));
        })) {
            page.frame();click(page,point(page,rows.get(0),20,15));for(int i=0;i<5;i++)page.navigation(SceneNavigationEvent.LEFT);page.frame();
            domain.execute(()->require(page.runtime().focusRouter().focused().get()==rows.get(5),"旋转有限行仍用未变换UP/DOWN跨行或view身份不稳定"));
        }
    }
    private void sortable() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();Map<String,SceneNode> rows=new HashMap<>();List<SceneSortable.Move<String,String>> moves=new ArrayList<>();
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            ctx.runtime().motionPolicy(()->MotionPolicy.OFF);
            SceneNode board=SceneSortable.create(ctx,()->Arrays.asList(new SceneSortable.Group<>("group",Arrays.asList("a","b","c"))),s->s,
                    key->{SceneNode items=new SceneNode().size(Size.fixed(100),Size.fixed(120));return new SceneSortable.Container(items,items,SceneSortable.Layout.VERTICAL);},
                    value->{SceneNode content=new SceneNode().size(Size.fill(1),Size.fixed(30)).background(0xff567812);rows.put(value.get(),content);return new SceneSortable.Row(content);},
                    value->new SceneNode().size(Size.fill(1),Size.fill(1)).background(0xff123456),moves::add);
            board.size(Size.fixed(100),Size.fixed(120)).transform(rotated());ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(board));
        })) {
            page.frame();int[] a=point(page,rows.get("a"),20,15),b=point(page,rows.get("c"),20,25);
            page.pointer(ScenePointerEvent.down(0,a[0],a[1]));page.pointer(ScenePointerEvent.move(b[0],b[1]));page.frame();page.pointer(ScenePointerEvent.up(0,b[0],b[1]));page.frame();
            require(moves.size()==1&&"a".equals(moves.get(0).key)&&moves.get(0).beforeKey==null,"旋转纵向排序未发布末尾原Move");
        }
    }
    private void holeReveal() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] nodes=new SceneNode[3];
        ScenePath mask=ScenePath.builder().rule(ScenePath.FillRule.EVEN_ODD)
                .moveTo(0,0).lineTo(100,0).lineTo(100,100).lineTo(0,100).close()
                .moveTo(50,20).lineTo(80,50).lineTo(50,80).lineTo(20,50).close().build();
        ScenePath diamond=ScenePath.builder().moveTo(50,30).lineTo(70,50).lineTo(50,70).lineTo(30,50).close().build();
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{
            ctx.runtime().motionPolicy(()->MotionPolicy.OFF);
            nodes[0]=new SceneNode().size(Size.fixed(100),Size.fixed(100)).scrollable(true).clip(SceneShape.path(mask,100,100)).transform(rotated());
            nodes[1]=SceneButton.create(ctx,()->"",ctx.theme(),()->{}).size(Size.fixed(20),Size.fixed(20));
            nodes[2]=SceneVector.create(ctx,()->new SceneVector.Model(diamond,100,100,0xff123456),()->{}).size(Size.fixed(100),Size.fixed(100));
            nodes[0].add(nodes[2]).add(new SceneNode().size(Size.fixed(100),Size.fixed(180)));
            ctx.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(nodes[1]).add(nodes[0]));
        })) {
            page.frame();SceneGeometryView before=geometry(page);SceneGeometryView.Entry target=before.get(nodes[2]),viewport=before.get(nodes[0]);
            require(viewport.scrollOffset==0&&viewport.scrollMaximum>=11&&before.visibleRegion(nodes[2]).area()==0,
                    "菱形目标应完全在菱形洞内且原滚动范围可显露");
            SceneMesh envelope=SceneMesh.rectangle(target.worldBounds.x,target.worldBounds.y,target.worldBounds.width,target.worldBounds.height);
            require(target.worldClip.clip(envelope).area()>0,"反例必须区分目标真实几何与其包络");
            click(page,point(page,nodes[1],10,10));page.navigation(SceneNavigationEvent.NEXT);page.frame();
            domain.execute(()->require(page.runtime().focusRouter().focused().get()==nodes[2],"Tab未选择原矢量目标"));
            SceneGeometryView after=geometry(page);
            require(after.get(nodes[0]).scrollOffset==11&&after.visibleRegion(nodes[2]).area()>0,
                    "菱形目标须沿原轴滚动11产生真实交集，实际="+after.get(nodes[0]).scrollOffset);
        }
    }
    private void identity() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] node=new SceneNode[1];
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,ctx->{node[0]=SceneButton.create(ctx,()->"",ctx.theme(),()->{});ctx.mount(node[0]);})) {
            page.frame();SceneGeometryView geometry=geometry(page);require(geometry.asControl(node[0])==geometry,"identity平白新增坐标view");
            ScenePointerEvent event=ScenePointerEvent.down(0,3,4);require(event.in(geometry)==event&&event.world()==event,"identity原事件入口对象被改变");
        }
    }
}
