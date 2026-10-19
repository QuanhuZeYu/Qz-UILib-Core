package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.VectorPresentationPage;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.semantics.*;
import club.heiqi.uilib.ui.scene.text.*;

/** 真实共享页面的同一业务状态、矢量命中与原表单编辑。 */
public final class VectorPresentationScenario extends TestApplication {
    @Override protected void runTests()throws Exception{
        scenario("真实扇区中心无命中且四动作共享环进度",this::radial);
        scenario("旋转缩放和形状切换保留原字段草稿与提交",this::viewport);
    }
    static final class Host implements SceneHost,SceneTextMeasurer{
        final MultilineEditingScenario.PlainHost text=new MultilineEditingScenario.PlainHost();int width=1280,height=1800;SceneSemanticSnapshot semantics=SceneSemanticSnapshot.EMPTY;
        public int width(){return width;}public int height(){return height;}public long metricsRevision(){return 0;}
        public void replay(PaintPlan plan){}public void semanticsChanged(SceneSemanticSnapshot snapshot){semantics=snapshot;}
        public TextLayout layout(String value,TextStyle style,int width){return text.layout(value,style,width);}
        public TextLayout layout(String value,TextStyle style,int width,TextPolicy policy){return text.layout(value,style,width,policy);}
    }
    final class Fixture implements AutoCloseable{
        final ReactiveDomain domain=ReactiveDomain.direct();final Host host=new Host();final ScenePageSession session;SceneNode root;
        Fixture(){session=ScenePageSession.open(domain,host,host,p->{p.runtime().motionPolicy(()->club.heiqi.uilib.ui.scene.motion.MotionPolicy.OFF);root=VectorPresentationPage.create(p,p.theme(),()->{});p.mount(root);});session.frame();}
        SceneSemanticSnapshot.Entry entry(SceneSemantics.Role role,String name){for(SceneSemanticSnapshot.Entry e:host.semantics.entries)if(e.values.role==role&&name.equals(e.values.name))return e;throw new AssertionError("缺少语义 "+role+" "+name);}
        SceneSemanticRequest.Result action(SceneSemantics.Role role,String name,SceneSemantics.Action action){java.util.concurrent.CompletableFuture<SceneSemanticRequest.Result> result=session.semantic(SceneSemanticRequest.of(entry(role,name).id,action));session.frame();require(result.isDone(),"语义请求未完成");return result.join();}
        SceneNode node(SceneNode n,String name){if(name.equals("#field")&&n.control()!=null){SceneSemantics s=n.control().semantics(n);if(s!=null&&s.values.role==SceneSemantics.Role.TEXT_FIELD)return n;}if(name.equals(n.text()))return n;for(SceneNode child:n.children()){SceneNode found=node(child,name);if(found!=null)return found;}return null;}
        void click(String name,double x,double y){int[] point=new int[2];domain.execute(()->{
            SceneNode target=node(root,name);require(target!=null,"缺少真实节点 "+name);SceneGeometryView.Entry e=session.pipeline().current().geometry.get(target);
            require(e!=null,"真实节点无几何 "+name);double localX=e.layoutBox.x+e.layoutBox.width*x,localY=e.layoutBox.y+e.layoutBox.height*y;
            point[0]=(int)Math.round(e.transform.x(localX,localY));point[1]=(int)Math.round(e.transform.y(localX,localY));});session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0,point[0],point[1]));session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0,point[0],point[1]));session.frame();}
        public void close(){session.close();}
    }
    private void radial(){try(Fixture f=new Fixture()){
        require("25%".equals(f.entry(SceneSemantics.Role.IMAGE,"任务进度").values.value),"初始任务进度不正确");
        f.click("完成任务",.5,.825);require("100%".equals(f.entry(SceneSemantics.Role.IMAGE,"任务进度").values.value),"扇区完成动作未命中");
        f.click("重置任务",.5,.5);require("100%".equals(f.entry(SceneSemantics.Role.IMAGE,"任务进度").values.value),"中心空洞误触重置");
        f.click("重置任务",.175,.5);require("0%".equals(f.entry(SceneSemantics.Role.IMAGE,"任务进度").values.value),"左扇区重置没有写同一状态");
        f.click("开始任务",.5,.175);require("25%".equals(f.entry(SceneSemantics.Role.IMAGE,"任务进度").values.value),"上扇区开始没有复用状态");
        for(int i=0;i<3;i++)require(f.action(SceneSemantics.Role.BUTTON,"推进进度",SceneSemantics.Action.ACTIVATE)==SceneSemanticRequest.Result.APPLIED,"推进动作拒绝");
        require("100%".equals(f.entry(SceneSemantics.Role.IMAGE,"任务进度").values.value)&&f.entry(SceneSemantics.Role.BUTTON,"推进进度").actions.isEmpty(),"进度上界及禁用事实不一致");
    }}
    private void viewport(){try(Fixture f=new Fixture()){
        SceneSemanticId note=f.entry(SceneSemantics.Role.TEXT_FIELD,"视口备注").id;
        java.util.concurrent.CompletableFuture<SceneSemanticRequest.Result> replace=f.session.semantic(SceneSemanticRequest.text(note,"变换保持原草稿"));f.session.frame();require(replace.join()==SceneSemanticRequest.Result.APPLIED,"原字段不能修改");
        f.action(SceneSemantics.Role.BUTTON,"旋转视口",SceneSemantics.Action.ACTIVATE);f.action(SceneSemantics.Role.BUTTON,"缩放视口",SceneSemantics.Action.ACTIVATE);f.action(SceneSemantics.Role.BUTTON,"切换裁剪",SceneSemantics.Action.ACTIVATE);
        require(f.host.semantics.get(note)!=null&&"变换保持原草稿".equals(f.host.semantics.get(note).values.value),"变换或裁剪重建了字段草稿");
        f.click("#field",.5,.5);
        require(note.equals(f.host.semantics.focused),"变换字段的真实指针没有进入原焦点");
        f.action(SceneSemantics.Role.BUTTON,"保存备注",SceneSemantics.Action.ACTIVATE);
        boolean saved=false;for(SceneSemanticSnapshot.Entry e:f.host.semantics.entries)if("已保存：变换保持原草稿".equals(e.values.name))saved=true;
        require(saved,"形状内原保存命令未提交原草稿");
        SceneSemanticId marker=f.entry(SceneSemantics.Role.IMAGE,"旋转标记").id;
        java.util.concurrent.CompletableFuture<SceneSemanticRequest.Result> angle=f.session.semantic(SceneSemanticRequest.value(f.entry(SceneSemantics.Role.SLIDER,"标记角度").id,75));f.session.frame();
        require(angle.join()==SceneSemanticRequest.Result.APPLIED&&"75°".equals(f.host.semantics.get(marker).values.value),"滑杆没有更新同一旋转标记");
        f.host.width=900;f.host.height=1400;f.session.frame();require(f.host.semantics.get(note)!=null&&f.host.semantics.get(marker)!=null,"窗口改变重建真实控件身份");
    }}
}
