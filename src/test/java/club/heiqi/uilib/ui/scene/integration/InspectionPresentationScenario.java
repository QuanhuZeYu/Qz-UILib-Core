package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.InspectionPage;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.*;
import java.util.concurrent.CompletableFuture;

/** 检查页直接使用正式快照；F8读取保留原字段和实际拖动联系。 */
public final class InspectionPresentationScenario extends TestApplication {
    public static void main(String[] args)throws Exception{new InspectionPresentationScenario().run(args);}
    @Override protected void runTests()throws Exception{scenario("F8检查真实字段来源与形状且不提交草稿或转移焦点",this::inspect);}
    private void inspect(){
        ReactiveDomain domain=ReactiveDomain.direct();VectorPresentationScenario.Host host=new VectorPresentationScenario.Host();host.height=1800;
        final SceneNode[] root={null};
        ScenePageSession session=ScenePageSession.open(domain,host,host,p->{p.runtime().motionPolicy(()->club.heiqi.uilib.ui.scene.motion.MotionPolicy.OFF);root[0]=InspectionPage.create(p,p.theme(),()->{});p.mount(root[0]);});
        try{
            session.frame();session.frame();
            SceneSemanticSnapshot.Entry field=entry(host,SceneSemantics.Role.TEXT_FIELD,"任务备注");
            CompletableFuture<SceneSemanticRequest.Result> replace=session.semantic(SceneSemanticRequest.text(field.id,"读取后仍为原草稿"));session.frame();require(replace.join()==SceneSemanticRequest.Result.APPLIED,"真实字段无法编辑");
            CompletableFuture<SceneSemanticRequest.Result> focus=session.semantic(SceneSemanticRequest.of(field.id,SceneSemantics.Action.FOCUS));session.frame();require(focus.join()==SceneSemanticRequest.Result.APPLIED,"真实字段无法聚焦");
            SceneKeyEvent update=new SceneKeyEvent(SceneKey.F8,false,false,false,false,false,null);
            session.key(update);session.key(update.withPhase(SceneKeyEvent.Phase.UP));session.frame();session.frame();
            require(field.id.equals(host.semantics.focused)&&"读取后仍为原草稿".equals(host.semantics.get(field.id).values.value),"F8检查转移焦点或提交/重建草稿");
            require(labelContains(host,"内距 0 px · 作者声明")&&labelContains(host,"焦点 任务备注"),"检查页没有呈现实际作者0内距或当前焦点");
            SceneSemanticSnapshot.Entry grip=entry(host,SceneSemantics.Role.LABEL,"拖动标记");
            int x=grip.box.x+grip.box.width/2,y=grip.box.y+grip.box.height/2;
            session.pointer(ScenePointerEvent.down(0,x,y));session.pointer(ScenePointerEvent.move(x+20,y+8));session.frame();
            session.key(update);session.key(update.withPhase(SceneKeyEvent.Phase.UP));session.frame();session.frame();
            require(labelContains(host,"捕获 拖动标记 · 按钮 [0]"),"拖动中F8捕获反馈不符："+inputLabels(host));
            session.pointer(ScenePointerEvent.up(0,x+20,y+8));session.frame();
            SceneSemanticSnapshot.Entry selector=entry(host,SceneSemantics.Role.SELECT,"检查对象");
            CompletableFuture<SceneSemanticRequest.Result> expand=session.semantic(SceneSemanticRequest.of(selector.id,SceneSemantics.Action.EXPAND));session.frame();require(expand.join()==SceneSemanticRequest.Result.APPLIED,"检查对象列表无法打开");
            SceneSemanticSnapshot.Entry viewport=null;for(SceneSemanticSnapshot.Entry e:host.semantics.entries)if(e.values.role==SceneSemantics.Role.OPTION&&e.values.name.startsWith("形状视口")){viewport=e;break;}
            require(viewport!=null,"检查对象没有真实形状视口");CompletableFuture<SceneSemanticRequest.Result> select=session.semantic(SceneSemanticRequest.of(viewport.id,SceneSemantics.Action.SELECT));session.frame();require(select.join()==SceneSemanticRequest.Result.APPLIED,"不能选择形状对象");
            require(labelContains(host,"裁剪 精确形状"),"同批非矩形裁剪被页面降为矩形");
            SceneSemanticSnapshot.Entry recording=entry(host,SceneSemantics.Role.SWITCH,"记录准备原因");
            CompletableFuture<SceneSemanticRequest.Result> toggle=session.semantic(SceneSemanticRequest.of(recording.id,SceneSemantics.Action.ACTIVATE));session.frame();require(toggle.join()==SceneSemanticRequest.Result.APPLIED,"记录开关不能使用");
            CompletableFuture<SceneSemanticRequest.Result> change=session.semantic(SceneSemanticRequest.of(entry(host,SceneSemantics.Role.BUTTON,"更新样例文案").id,SceneSemantics.Action.ACTIVATE));session.frame();require(change.join()==SceneSemanticRequest.Result.APPLIED,"样例操作不能执行");
            session.key(update);session.key(update.withPhase(SceneKeyEvent.Phase.UP));session.frame();session.frame();require(labelContains(host,"绘制收集"),"已启用记录没有呈现真实准备结果");
            CompletableFuture<SceneInspectionSnapshot> snapshot=session.inspect();session.frame();require(snapshot.join().recording,"当前页未启用正式记录来源");
            domain.execute(()->session.runtime().captureMount(root[0]).detach());session.frame();
            CompletableFuture<SceneInspectionSnapshot> after=session.inspect();session.frame();require(!after.join().recording,"检查页卸载仍记录旧样例");
        }finally{session.close();}
    }
    static SceneSemanticSnapshot.Entry entry(VectorPresentationScenario.Host host,SceneSemantics.Role role,String name){for(SceneSemanticSnapshot.Entry e:host.semantics.entries)if(e.values.role==role&&name.equals(e.values.name))return e;throw new AssertionError("缺少检查页控件 "+role+" "+name);}
    static String inputLabels(VectorPresentationScenario.Host host){StringBuilder text=new StringBuilder();for(SceneSemanticSnapshot.Entry e:host.semantics.entries)if(e.values.role==SceneSemantics.Role.LABEL&&e.values.name!=null&&e.values.name.contains("捕获 "))text.append(e.values.name);return text.toString();}
    static boolean labelContains(VectorPresentationScenario.Host host,String text){for(SceneSemanticSnapshot.Entry e:host.semantics.entries)if(e.values.role==SceneSemantics.Role.LABEL&&e.values.name!=null&&e.values.name.contains(text))return true;return false;}
}
