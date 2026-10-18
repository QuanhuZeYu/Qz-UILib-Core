package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.geometry.*;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot;
import club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.*;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.*;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** 检查只读原Session：当前几何、实际Router、声明来源及有界工作量。 */
public final class SceneInspectionScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new SceneInspectionScenario().run(args); }
    protected void runTests() throws Exception {
        scenario("精确clip同批字段状态及稳定读取不重新准备", this::geometryAndEditing);
        scenario("原按压捕获物理键和模态范围分别读取", this::pointerAndModal);
        scenario("作者同值与零覆盖保来源且只绘制计数不同于量排", this::recording);
        scenario("视口与资源原因只归页面且返回原版本", this::environment);
        scenario("有限集合不构造屏外行且重挂沿原opaque身份", this::mountAndVirtual);
        scenario("关闭与初始化或批失败结束等待请求", this::closedAndFailed);
    }
    private static MultilineEditingScenario.PlainHost host() {
        MultilineEditingScenario.PlainHost host=new MultilineEditingScenario.PlainHost();host.width=640;host.height=480;return host;
    }
    private static Entry entry(SceneInspectionSnapshot snapshot,SceneNode node) {
        for(Entry entry:snapshot.entries)if(entry.nodeId.equals(node.id()))return entry;
        throw new AssertionError("检查没有实际挂载节点："+node.id());
    }
    private static ScenePresentation presentation(ScenePageSession page) {
        ScenePresentation[] result=new ScenePresentation[1];page.domain().execute(()->result[0]=page.pipeline().current());return result[0];
    }
    private static int[] center(Entry entry) {
        double x=entry.layoutBox.x+entry.layoutBox.width*.5,y=entry.layoutBox.y+entry.layoutBox.height*.5;
        return new int[]{(int)Math.round(entry.transform.x(x,y)),(int)Math.round(entry.transform.y(x,y))};
    }
    private static void click(ScenePageSession page,Entry entry) {
        int[] p=center(entry);page.pointer(ScenePointerEvent.down(0,p[0],p[1]));page.pointer(ScenePointerEvent.up(0,p[0],p[1]));
    }
    private static boolean reason(List<Invalidation> reasons,SceneSemanticId id,Category category) {
        for(Invalidation reason:reasons)if(java.util.Objects.equals(reason.id,id)&&reason.category==category)return true;
        return false;
    }
    private void geometryAndEditing() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] nodes=new SceneNode[3];SceneEditState[] draft=new SceneEditState[1];ScenePageContext[] context=new ScenePageContext[1];
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,p->{
            context[0]=p;p.motionPolicy(()->MotionPolicy.OFF);p.inspectionRecording(()->true);draft[0]=new SceneEditState(domain,"草稿");
            nodes[1]=SceneTextField.create(p,draft[0],p.theme(),()->false,s->{}).size(Size.fixed(160),Size.fixed(32));
            nodes[2]=SceneButton.create(p,()->"动作",p.theme(),()->{}).size(Size.fixed(160),Size.fixed(32));
            nodes[0]=new SceneNode().size(Size.fixed(180),Size.fixed(100)).clip(SceneShape.ellipse())
                    .transform(SceneTransform.translation(200,50).multiply(SceneTransform.rotationDegrees(15))).add(nodes[1]).add(nodes[2]);
            p.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(nodes[0]));
        })) {
            SceneInspectionSnapshot first=page.inspect().join();require(first.status==Status.READY&&first.recording,"检查未沿正式准备完成");
            Entry parent=entry(first,nodes[0]),field=entry(first,nodes[1]);
            require(field.parent.equals(parent.id)&&first.get(field.id)==field,"层级与opaque索引不一致");
            require(!parent.effectiveClip.contains(parent.transform.x(1,1),parent.transform.y(1,1)),"精确椭圆clip被退化成包络");
            ScenePresentation ready=presentation(page);SceneGeometryView.Entry actual=ready.geometry.get(nodes[1]);
            require(field.layoutBox==actual.layoutBox&&field.worldBounds==actual.worldBounds&&field.effectiveClip==actual.worldClip,"快照几何没有消费同一批事实");
            require(Math.abs(field.visibleMesh.area()-ready.geometry.visibleRegion(nodes[1]).area())<1e-7,"可见区域偏离正式mesh");
            click(page,field);SceneInspectionSnapshot focused=page.inspect().join();require(field.id.equals(focused.focused),"此前输入没有在检查前准备");
            SceneCompositionSource source=new SceneCompositionSource();page.edit(SceneEditEvent.compositionBegin(source,"候选"));page.inspect().join();
            SceneEditState.Snapshot[] before=new SceneEditState.Snapshot[1];domain.execute(()->before[0]=draft[0].snapshot().get());
            ScenePresentation stable=presentation(page);CompletableFuture<SceneInspectionSnapshot>[] future=new CompletableFuture[1];
            domain.execute(()->future[0]=context[0].inspect());SceneInspectionSnapshot next=future[0].join();
            domain.execute(()->require(before[0]==draft[0].snapshot().get()&&before[0].composing,"检查改变了草稿选区或组字联系"));
            require(presentation(page)==stable&&next.generation==stable.generation,"稳定检查强制重建几何或计划");
            SceneInspectionSnapshot repeated=page.inspect().join();require(next.latest==repeated.latest,"稳定检查追加了空执行批");
            require(next.captured==null&&next.buttons.isEmpty()&&next.focused.equals(field.id),"检查扰动原输入联系");
            try{next.entries.clear();throw new AssertionError("检查列表可写");}catch(UnsupportedOperationException expected){}
            page.edit(SceneEditEvent.compositionCommit(source,"已确认"));page.inspect().join();
            domain.execute(()->require(!draft[0].snapshot().get().composing&&draft[0].text().get().contains("已确认"),"检查结束了原可继续组字联系"));
        }
    }
    private void pointerAndModal() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] nodes=new SceneNode[3];Signal<Boolean>[] modal=new Signal[1];
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,p->{
            p.motionPolicy(()->MotionPolicy.OFF);modal[0]=domain.signal(false);
            nodes[0]=SceneButton.create(p,()->"按钮",p.theme(),()->{}).size(Size.fixed(180),Size.fixed(32));
            nodes[1]=SceneSlider.create(p,()->new SceneSlider.Model(0,100,1,20,true),v->{},v->{},p.theme()).size(Size.fixed(180),Size.fixed(40));
            nodes[2]=new SceneNode().size(Size.fixed(180),Size.fixed(60)).modal(true).bind(NodeBinding.value(modal[0],SceneNode::visible));
            p.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(nodes[0]).add(nodes[1]).add(nodes[2]));
        })) {
            SceneInspectionSnapshot start=page.inspect().join();Entry button=entry(start,nodes[0]),slider=entry(start,nodes[1]);int[] p=center(button);
            page.pointer(ScenePointerEvent.down(0,p[0],p[1]));SceneInspectionSnapshot down=page.inspect().join();
            require(button.id.equals(down.pressed)&&down.captured==null&&down.buttons.equals(Collections.singletonList(0)),"普通按钮按压被误报为捕获");
            page.pointer(ScenePointerEvent.up(0,p[0],p[1]));page.inspect().join();p=center(slider);
            page.pointer(ScenePointerEvent.down(0,p[0],p[1]));page.pointer(ScenePointerEvent.down(2,p[0],p[1]));SceneInspectionSnapshot captured=page.inspect().join();
            require(slider.id.equals(captured.pressed)&&slider.id.equals(captured.captured)&&captured.buttons.equals(Arrays.asList(0,2)),"未读取真实capture与物理占用");
            SceneInspectionSnapshot stable=page.inspect().join();require(stable.captured.equals(captured.captured)&&stable.buttons.equals(captured.buttons),"稳定检查取消捕获");
            page.pointer(ScenePointerEvent.up(0,p[0],p[1]));SceneInspectionSnapshot released=page.inspect().join();require(released.captured==null&&released.pressed==null&&released.buttons.equals(Collections.singletonList(2)),"释放后检查沿旧捕获或漏物理尾");
            page.pointer(ScenePointerEvent.allButtonsReleased());page.inspect().join();domain.execute(()->modal[0].set(true));SceneInspectionSnapshot scope=page.inspect().join();
            require(entry(scope,nodes[2]).id.equals(scope.currentModal)&&!entry(scope,nodes[0]).interactive&&scope.buttons.isEmpty(),"检查范围不是原当前模态");
        }
    }
    private void recording() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] nodes=new SceneNode[3];Signal<Integer>[] color=new Signal[1];Signal<String>[] text=new Signal[1];Signal<Boolean>[] record=new Signal[1];Signal<SceneDensity>[] density=new Signal[1];
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,p->{
            p.motionPolicy(()->MotionPolicy.OFF);record[0]=domain.signal(true);p.inspectionRecording(record[0]);density[0]=domain.signal(SceneDensity.COMFORTABLE);p.density(density[0]);
            color[0]=domain.signal(0xff123456);text[0]=domain.signal("自然文字");
            nodes[0]=new SceneNode().bind(NodeBinding.value(color[0],SceneNode::background)).bind(NodeBinding.value(text[0],SceneNode::text));
            nodes[1]=SceneButton.create(p,()->"默认",p.theme(),()->{});
            nodes[2]=SceneButton.create(p,()->"作者",p.theme(),()->{}).padding(0).gap(0).style(nodes[1].style());
            p.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(nodes[0]).add(nodes[1]).add(nodes[2]));
        })) {
            SceneInspectionSnapshot initial=page.inspect().join();Entry author=entry(initial,nodes[2]),defaults=entry(initial,nodes[1]),label=entry(initial,nodes[0]);
            require(author.padding==0&&author.gap==0&&author.paddingSource==Source.AUTHOR&&author.gapSource==Source.AUTHOR&&author.styleSource==Source.AUTHOR,"作者0/同值样式来源丢失");
            require(defaults.paddingSource==Source.CONTROL_DEFAULT&&defaults.styleSource==Source.CONTROL_DEFAULT,"主题默认被误报作者");
            require(initial.latest.measureCount>0&&initial.latest.arrangeCount>0&&initial.latest.paintCollectCount>0,"真实量排执行未计数");
            domain.execute(()->{color[0].set(0xff222222);color[0].set(0xff333333);});SceneInspectionSnapshot paint=page.inspect().join();
            require(paint.latest.measureCount==0&&paint.latest.arrangeCount==0&&paint.latest.paintCollectCount==1,"颜色变化被当作重测或计数冒充revision");
            require(reason(paint.latest.reasons,label.id,Category.BINDING)&&reason(paint.latest.reasons,label.id,Category.PAINT),"只绘制未保真实节点和类别");
            domain.execute(()->text[0].set("改变自然测量宽度的文字"));SceneInspectionSnapshot measured=page.inspect().join();
            require(measured.latest.measureCount>0&&measured.latest.arrangeCount>0&&reason(measured.latest.reasons,label.id,Category.MEASURE),"文字变化没有真实测量来源或执行计数");
            domain.execute(()->density[0].set(SceneDensity.COMPACT));SceneInspectionSnapshot compact=page.inspect().join();
            require(entry(compact,nodes[2]).padding==0&&entry(compact,nodes[2]).style.equals(author.style)&&entry(compact,nodes[1]).padding<defaults.padding,"检查丢失密度默认与作者覆盖条件");
            domain.execute(()->record[0].set(false));domain.execute(()->text[0].set("关闭后仍可正常变化"));SceneInspectionSnapshot off=page.inspect().join();
            require(!off.recording&&off.latest==Preparation.EMPTY&&off.pending.isEmpty(),"停用保留历史或继续记执行");
        }
    }
    private void environment() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost text=host();int[] width={300};long[] metrics={7};
        SceneHost host=new SceneHost(){public int width(){return width[0];}public int height(){return 200;}public long metricsRevision(){return metrics[0];}public void replay(club.heiqi.uilib.ui.scene.paint.PaintPlan plan){}};
        try(ScenePageSession page=ScenePageSession.open(domain,host,text,p->{p.inspectionRecording(()->true);p.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).text("环境"));})) {
            SceneInspectionSnapshot first=page.inspect().join();require(first.metricsRevision==7,"检查未返回原资源版本");
            width[0]=420;SceneInspectionSnapshot resized=page.inspect().join();
            require(reason(resized.latest.reasons,null,Category.VIEWPORT)&&resized.latest.arrangeCount>0,"视口变化未沿真实页面原因记录");
            metrics[0]=8;SceneInspectionSnapshot refreshed=page.inspect().join();
            require(refreshed.metricsRevision==8&&reason(refreshed.latest.reasons,null,Category.RESOURCE)&&refreshed.latest.measureCount>0,"资源刷新未返回同批版本及实际测量");
            for(Invalidation cause:refreshed.latest.reasons)if(cause.category==Category.RESOURCE)require(cause.id==null&&cause.nodeId==null,"资源变化伪造节点来源");
        }
    }
    private void mountAndVirtual() {
        ReactiveDomain domain=ReactiveDomain.direct();MultilineEditingScenario.PlainHost host=host();SceneNode[] nodes=new SceneNode[3];Signal<List<SceneNode>>[] children=new Signal[1];int[] built={0};
        try(ScenePageSession page=ScenePageSession.open(domain,host,host,p->{
            p.motionPolicy(()->MotionPolicy.OFF);nodes[0]=new SceneNode().size(Size.fixed(100),Size.fixed(30)).onClick(()->{});children[0]=domain.signal(Collections.singletonList(nodes[0]));
            nodes[1]=new SceneNode().size(Size.fixed(100),Size.fixed(40)).bind(NodeBinding.children(children[0]));List<Integer> values=new ArrayList<>();for(int i=0;i<1000;i++)values.add(i);
            SceneVirtualList<Integer,Integer> list=SceneVirtualList.create(p,()->values,i->i,value->{built[0]++;return SceneButton.create(p,()->"行",p.theme(),()->{}).size(Size.fill(1),Size.fixed(30));},()->new SceneVirtualList.Options().estimatedRowHeight(30).overscan(0));
            nodes[2]=list.node().size(Size.fixed(120),Size.fixed(90));p.mount(new SceneNode().size(Size.fill(1),Size.fill(1)).add(nodes[1]).add(nodes[2]));
        })) {
            SceneInspectionSnapshot before=page.inspect().join();Entry virtual=entry(before,nodes[2]),old=entry(before,nodes[0]);int count=built[0];
            require(virtual.logicalItemCount==1000&&virtual.instantiatedItemCount<1000&&virtual.instantiatedItemCount>0,"有限集合没有读原逻辑/当前实例计数");
            page.inspect().join();require(count==built[0],"稳定检查为屏外条目构造节点");
            domain.execute(()->children[0].set(Collections.emptyList()));SceneInspectionSnapshot detached=page.inspect().join();require(detached.get(old.id)==null,"卸载后的旧挂载ID仍在当前树");
            domain.execute(()->children[0].set(Collections.singletonList(nodes[0])));SceneInspectionSnapshot after=page.inspect().join();Entry current=entry(after,nodes[0]);
            require(!current.id.equals(old.id)&&current.nodeId.equals(old.nodeId)&&after.get(old.id)==null,"重挂复用旧联系或改变原NodeUUID");
            require(before.get(old.id)==old,"旧纯值快照随新挂载变化");
        }
    }
    private static final class Queue {
        final ArrayDeque<Runnable> work=new ArrayDeque<>();
        final ReactiveDomain domain=new ReactiveDomain(work::add);
        void drain(){while(!work.isEmpty())work.remove().run();}
    }
    private void closedAndFailed() {
        Queue queue=new Queue();MultilineEditingScenario.PlainHost host=host();ScenePageSession unopened=ScenePageSession.open(queue.domain,host,host,p->p.mount(new SceneNode()));
        CompletableFuture<SceneInspectionSnapshot> pending=unopened.inspect();unopened.close();require(pending.join().status==Status.CLOSED&&pending.join().entries.isEmpty(),"初始化前关闭未结束检查");queue.drain();
        require(unopened.inspect().join().status==Status.CLOSED,"关闭后的检查没有明确CLOSED");
        Queue failing=new Queue();RuntimeException expected=new RuntimeException("原handler失败");SceneNode[] button=new SceneNode[1];
        ScenePageSession page=ScenePageSession.open(failing.domain,host,host,p->{p.motionPolicy(()->MotionPolicy.OFF);button[0]=SceneButton.create(p,()->"失败",p.theme(),()->{throw expected;});p.mount(button[0]);});
        failing.drain();page.frame();failing.drain();
        page.pointer(ScenePointerEvent.syntheticActivation(10,10));CompletableFuture<SceneInspectionSnapshot> failed=page.inspect();
        try{failing.drain();throw new AssertionError("原失败未传播");}catch(RuntimeException actual){require(actual==expected,"原失败身份改变");}
        require(failed.isCompletedExceptionally(),"批失败遗留检查Future");try{failed.join();throw new AssertionError("失败Future没有cause");}catch(CompletionException error){require(error.getCause()==expected,"失败Future未保原cause");}
        page.close();failing.drain();
        Queue initializing=new Queue();RuntimeException buildFailure=new RuntimeException("原初始化失败");ScenePageSession bad=ScenePageSession.open(initializing.domain,host,host,p->{throw buildFailure;});
        CompletableFuture<SceneInspectionSnapshot> waiting=bad.inspect();try{initializing.drain();throw new AssertionError("初始化错误未传播");}catch(RuntimeException actual){require(actual==buildFailure,"初始化错误身份改变");}
        require(waiting.isCompletedExceptionally(),"初始化失败没有结束请求");bad.close();initializing.drain();
    }
}
