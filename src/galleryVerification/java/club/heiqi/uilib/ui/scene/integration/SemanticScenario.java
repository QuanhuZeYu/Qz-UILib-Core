package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.*;
import club.heiqi.uilib.ui.scene.semantics.*;
import club.heiqi.uilib.ui.scene.text.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 语义读取和定向动作走正式Session、共同几何及真实控件，不替换路由。 */
public final class SemanticScenario extends TestApplication {
    @Override protected void runTests()throws Exception{
        scenario("名称关系与纯描述更新复用布局绘制且旧快照不可变",this::descriptions);
        scenario("同目标MOVE先发布纯语义变化后current仍取得同代快照",this::semanticDuringMove);
        scenario("定向字段替换保留撤销选区和原业务基线",this::targetedText);
        scenario("真实组字联系与只读仅保护当前字段写入",this::composition);
        scenario("旧挂载ID重建拒绝且关闭发布移除并结束请求",this::identityAndClose);
        scenario("语义动作Effect失败结束结果并隔离同批后续动作",this::failedAction);
        scenario("语义动作范围按同批打开模态的新事实核验",this::modalActions);
        scenario("动作主动关闭页面仍完成当前请求且结束未执行尾部",this::closingAction);
        scenario("真实项目表单的选择预览菜单及标签页共享同一语义",this::projectDetails);
        scenario("显式完成回调失败停止原批次并结束关闭尾部",this::failedCompletion);
    }
    static final class Host implements SceneHost,SceneTextMeasurer{
        final MultilineEditingScenario.PlainHost text=new MultilineEditingScenario.PlainHost();
        SceneSemanticSnapshot snapshot=SceneSemanticSnapshot.EMPTY;PaintPlan paint;int changes,measures;
        public int width(){return 600;}public int height(){return 400;}public long metricsRevision(){return 0;}
        public void replay(PaintPlan plan){paint=plan;}
        public void semanticsChanged(SceneSemanticSnapshot value){snapshot=value;changes++;}
        public TextLayout layout(String s,TextStyle style,int width){measures++;return text.layout(s,style,width);}
        public TextLayout layout(String s,TextStyle style,int width,TextPolicy policy){measures++;return text.layout(s,style,width,policy);}
    }
    final class Fixture implements AutoCloseable{
        final ReactiveDomain domain=ReactiveDomain.direct();final Host host=new Host();
        final SceneNode[] fields=new SceneNode[2];final SceneEditState[] drafts=new SceneEditState[2];
        ScenePageContext page;Signal<String> accessible;Signal<Boolean> readOnly,show,modal;
        SceneNode label,button;int actions,submissions;final ScenePageSession session;
        Fixture(){session=ScenePageSession.open(domain,host,host,p->{
            p.runtime().motionPolicy(()->club.heiqi.uilib.ui.scene.motion.MotionPolicy.OFF);
            page=p;accessible=domain.signal("电子邮箱");readOnly=domain.signal(false);show=domain.signal(true);modal=domain.signal(false);
            label=SceneLabel.create(p,()->"邮件地址",p.theme());
            label.bind(NodeBinding.semantics(()->SceneSemantics.builder().name(accessible.get()).build()));
            drafts[0]=new SceneEditState(domain,"seed");drafts[1]=new SceneEditState(domain,"other");
            SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).gap(4).add(label);
            SceneNode fieldRegion=new SceneNode().size(Size.fill(1),Size.natural());root.add(fieldRegion);
            fieldRegion.bind(NodeBinding.repeat(()->show.get()?Collections.singletonList("field"):Collections.emptyList(),key->key,current->{
                fields[0]=SceneTextField.create(p,drafts[0],p.theme(),readOnly,request->submissions++).size(Size.fill(1),Size.fixed(36));
                fields[0].bind(NodeBinding.semantics(()->SceneSemantics.builder().labelledBy(label).build()));
                return fields[0];
            }));
            fields[1]=SceneTextField.create(p,drafts[1],p.theme(),()->false,request->{}).size(Size.fill(1),Size.fixed(36));
            button=SceneButton.create(p,()->"执行",p.theme(),()->actions++);root.add(fields[1]).add(button);
            SceneNode modalRegion=new SceneNode().size(Size.fill(1),Size.natural());root.add(modalRegion);
            modalRegion.bind(NodeBinding.repeat(()->modal.get()?Collections.singletonList("modal"):Collections.emptyList(),key->key,current->new SceneNode().modal(true).size(Size.fixed(100),Size.fixed(60))
                    .add(SceneButton.create(p,()->"模态动作",p.theme(),()->actions+=10))));p.mount(root);
        });session.frame();}
        SceneSemanticSnapshot.Entry entry(SceneSemantics.Role role,String value){
            for(SceneSemanticSnapshot.Entry e:host.snapshot.entries)if(e.values.role==role&&(value==null||value.equals(e.values.value)||value.equals(e.values.name)))return e;
            throw new AssertionError("缺少语义 "+role+" "+value);
        }
        public void close(){session.close();}
    }
    private void descriptions(){try(Fixture f=new Fixture()){
        f.domain.execute(()->f.page.runtime().focusRouter().request(f.page.runtime().captureMount(f.button)));f.session.frame();
        SceneSemanticSnapshot old=f.host.snapshot;SceneSemanticSnapshot.Entry field=f.entry(SceneSemantics.Role.TEXT_FIELD,"seed");
        require("电子邮箱".equals(field.values.name)&&"seed".equals(field.values.value)&&field.labelledBy.size()==1,"字段名称和值或标签关系混淆 name="+field.values.name+" value="+field.values.value+" labels="+field.labelledBy.size());
        ScenePresentation[] before=new ScenePresentation[1];f.domain.execute(()->before[0]=f.session.pipeline().current());int measures=f.host.measures;
        f.domain.execute(()->f.accessible.set("接收通知的完整邮箱名称"));f.session.frame();
        ScenePresentation[] after=new ScenePresentation[1];f.domain.execute(()->after[0]=f.session.pipeline().current());
        require(before[0].geometry==after[0].geometry&&before[0].paint==after[0].paint&&f.host.measures==measures,"纯语义名称触发重测或重绘收集 geometry="+(before[0].geometry==after[0].geometry)+" paint="+(before[0].paint==after[0].paint)+" measures="+measures+"/"+f.host.measures);
        require("接收通知的完整邮箱名称".equals(f.host.snapshot.get(field.id).values.name)&&"电子邮箱".equals(old.get(field.id).values.name),"动态名称不更新或旧值被改写");
        SceneSemanticSnapshot idle=f.host.snapshot;int changes=f.host.changes;f.session.frame();
        require(f.host.snapshot==idle&&f.host.changes==changes,"静止帧重复发布语义");
    }}
    private void semanticDuringMove(){try(Fixture f=new Fixture()){
        f.session.pipeline().pointer(ScenePointerEvent.move(f.host.width()-1,f.host.height()-1));f.session.frame();
        ScenePresentation[] before=new ScenePresentation[1];f.domain.execute(()->before[0]=f.session.pipeline().current());
        SceneSemanticId field=f.entry(SceneSemantics.Role.TEXT_FIELD,"seed").id;int changes=f.host.changes,measures=f.host.measures;
        f.domain.execute(()->f.accessible.set("输入批次中的新邮箱名称"));
        // 输入准备已通知host不能替代正式呈现缓存更新；同目标MOVE本身不应强制重收绘制。
        f.session.pipeline().pointer(ScenePointerEvent.move(f.host.width()-2,f.host.height()-1));f.session.frame();
        ScenePresentation[] after=new ScenePresentation[1];f.domain.execute(()->after[0]=f.session.pipeline().current());
        require("输入批次中的新邮箱名称".equals(after[0].semantics.get(field).values.name)
                &&after[0].semantics==f.host.snapshot,"输入已发布新语义但current仍返回旧快照 host="+f.host.snapshot.get(field).values.name+" current="+after[0].semantics.get(field).values.name);
        require(f.host.changes==changes+1&&before[0].geometry==after[0].geometry&&before[0].paint==after[0].paint&&f.host.measures==measures,
                "同目标MOVE纯语义更新重复通知或触发重测重绘 changes="+changes+"/"+f.host.changes+" measures="+measures+"/"+f.host.measures);
    }}
    private void targetedText(){try(Fixture f=new Fixture()){
        f.domain.execute(()->{f.drafts[0].select(2,2);f.drafts[0].replaceSelection("X");f.drafts[0].select(1,3);f.page.runtime().focusRouter().request(f.page.runtime().captureMount(f.fields[1]));});
        f.session.frame();SceneSemanticId a=f.entry(SceneSemantics.Role.TEXT_FIELD,"seXed").id;Object[] baseline=new Object[1];
        f.domain.execute(()->baseline[0]=f.drafts[0].inputBaseline());
        CompletableFuture<SceneSemanticRequest.Result> result=f.session.semantic(SceneSemanticRequest.text(a,"新文本"));
        require(!result.isDone(),"入队被错报为动作完成");f.session.frame();require(result.join()==SceneSemanticRequest.Result.APPLIED,"定向替换未处理");
        f.domain.execute(()->{require("新文本".equals(f.drafts[0].snapshot().get().text)&&"other".equals(f.drafts[1].snapshot().get().text),"语义替换投到了实际焦点字段");
            require(f.drafts[0].inputBaseline()==baseline[0],"语义替换更换业务基线");f.drafts[0].undo();
            require("seXed".equals(f.drafts[0].snapshot().get().text)&&f.drafts[0].snapshot().get().anchor==1&&f.drafts[0].snapshot().get().caret==3,"整字段撤销没恢复原文本与选区");
            f.drafts[0].undo();require("seed".equals(f.drafts[0].snapshot().get().text),"语义替换清除了此前历史");});
    }}
    private void composition(){try(Fixture f=new Fixture()){
        SceneSemanticId id=f.entry(SceneSemantics.Role.TEXT_FIELD,"seed").id;
        f.session.semantic(SceneSemanticRequest.of(id,SceneSemantics.Action.FOCUS));f.session.frame();
        SceneCompositionSource source=new SceneCompositionSource();f.session.edit(SceneEditEvent.compositionBegin(source,"输入"));
        f.session.edit(SceneEditEvent.compositionUpdate(source,"",false));f.session.frame();
        CompletableFuture<SceneSemanticRequest.Result> text=f.session.semantic(SceneSemanticRequest.text(id,"不应覆盖"));
        CompletableFuture<SceneSemanticRequest.Result> submit=f.session.semantic(SceneSemanticRequest.of(id,SceneSemantics.Action.SUBMIT));f.session.frame();
        require(text.join()==SceneSemanticRequest.Result.REJECTED&&submit.join()==SceneSemanticRequest.Result.REJECTED&&f.submissions==0,"语义写入越过UPDATEfalse有效联系");
        f.session.edit(SceneEditEvent.compositionCancel(source));text=f.session.semantic(SceneSemanticRequest.text(id,"正常"));f.session.frame();
        require(text.join()==SceneSemanticRequest.Result.APPLIED,"明确取消后仍拒绝正常字段写入");
        f.domain.execute(()->f.readOnly.set(true));text=f.session.semantic(SceneSemanticRequest.text(id,"只读拒绝"));f.session.frame();
        require(text.join()==SceneSemanticRequest.Result.REJECTED&&Boolean.TRUE.equals(f.host.snapshot.get(id).values.readOnly),"只读事实或执行资格未更新");
    }}
    private void identityAndClose(){Fixture f=new Fixture();
        SceneSemanticId old=f.entry(SceneSemantics.Role.TEXT_FIELD,"seed").id;
        f.domain.execute(()->f.show.set(false));f.session.frame();require(f.host.snapshot.get(old)==null&&f.host.snapshot.removed.contains(old),"卸载未移除旧身份");
        f.domain.execute(()->f.show.set(true));f.session.frame();SceneSemanticId current=f.entry(SceneSemantics.Role.TEXT_FIELD,"seed").id;
        require(!old.equals(current),"真实新挂载复用旧语义身份");CompletableFuture<SceneSemanticRequest.Result> stale=f.session.semantic(SceneSemanticRequest.text(old,"迟到"));f.session.frame();
        require(stale.join()==SceneSemanticRequest.Result.REJECTED,"旧目标被重投新挂载");
        CompletableFuture<SceneSemanticRequest.Result> pending=f.session.semantic(SceneSemanticRequest.text(current,"关闭尾部"));f.close();
        require(pending.join()==SceneSemanticRequest.Result.CLOSED&&f.host.snapshot.entries.isEmpty(),"关闭未结束请求或未发布移除");
    }
    private void failedAction(){
        RuntimeException expected=new RuntimeException("语义动作Effect失败");
        SceneNode[] node=new SceneNode[1];Host host=new Host();ReactiveDomain domain=ReactiveDomain.direct();
        ScenePageSession session=ScenePageSession.open(domain,host,host,p->{Signal<Boolean> fired=domain.signal(false);
            domain.effect(p.scope(),()->{if(fired.get())throw expected;});node[0]=SceneButton.create(p,()->"失败",p.theme(),()->fired.set(true));p.mount(node[0]);});
        try{session.frame();SceneSemanticId id=host.snapshot.entries.get(0).id;
            CompletableFuture<SceneSemanticRequest.Result> first=session.semantic(SceneSemanticRequest.of(id,SceneSemantics.Action.ACTIVATE));
            CompletableFuture<SceneSemanticRequest.Result> tail=session.semantic(SceneSemanticRequest.of(id,SceneSemantics.Action.ACTIVATE));
            try{session.frame();throw new AssertionError("应传播原异常");}catch(RuntimeException actual){require(actual==expected,"丢失原异常 actual="+actual);}
            require(first.isCompletedExceptionally()&&tail.isCompletedExceptionally(),"失败动作或丢弃尾部请求悬挂");
        }finally{session.close();}
    }
    private static SceneSemanticSnapshot.Entry find(Host host,SceneSemantics.Role role,String name){
        for(SceneSemanticSnapshot.Entry e:host.snapshot.entries)if(e.values.role==role&&Objects.equals(name,e.values.name))return e;
        throw new AssertionError("缺少语义 "+role+" name="+name);
    }
    private static SceneSemanticRequest.Result perform(ScenePageSession session,SceneSemanticId id,SceneSemantics.Action action){
        CompletableFuture<SceneSemanticRequest.Result> result=session.semantic(SceneSemanticRequest.of(id,action));session.frame();return result.join();
    }
    private void projectDetails(){
        ReactiveDomain domain=ReactiveDomain.direct();Host host=new Host();
        ScenePageSession session=ScenePageSession.open(domain,host,host,p->{p.runtime().motionPolicy(()->club.heiqi.uilib.ui.scene.motion.MotionPolicy.OFF);
            p.mount(club.heiqi.uilib.testing.gallery.ProjectDetailsPage.create(p,p.theme(),()->{}));});
        try{session.frame();SceneSemanticSnapshot.Entry select=find(host,SceneSemantics.Role.SELECT,"负责人");
            require("林岚".equals(select.values.value),"选择器名称与值未分开");
            require(perform(session,select.id,SceneSemantics.Action.EXPAND)==SceneSemanticRequest.Result.APPLIED,"负责人不能展开");
            SceneSemanticSnapshot.Entry box=find(host,SceneSemantics.Role.LISTBOX,"负责人");
            SceneSemanticSnapshot.Entry zhou=find(host,SceneSemantics.Role.OPTION,"周宁");
            require(perform(session,zhou.id,SceneSemantics.Action.FOCUS)==SceneSemanticRequest.Result.APPLIED
                    &&Boolean.TRUE.equals(find(host,SceneSemantics.Role.OPTION,"林岚").values.selected)
                    &&!Boolean.TRUE.equals(find(host,SceneSemantics.Role.OPTION,"周宁").values.selected),"预览焦点改写了业务选中");
            require(find(host,SceneSemantics.Role.OPTION,"访客（不可分配）").actions.isEmpty(),"禁用选项仍可执行动作");
            require(perform(session,zhou.id,SceneSemantics.Action.SELECT)==SceneSemanticRequest.Result.APPLIED
                    &&"周宁".equals(host.snapshot.get(select.id).values.value)&&host.snapshot.get(box.id)==null,"选项确认未沿业务更新并关闭");
            require(perform(session,zhou.id,SceneSemantics.Action.SELECT)==SceneSemanticRequest.Result.REJECTED,"旧弹出项仍执行");
            SceneSemanticSnapshot.Entry menu=find(host,SceneSemantics.Role.BUTTON,"更多操作");
            require(perform(session,menu.id,SceneSemantics.Action.ACTIVATE)==SceneSemanticRequest.Result.APPLIED,"菜单触发没有执行");
            SceneSemanticSnapshot.Entry popup=find(host,SceneSemantics.Role.MENU,"更多操作");
            require(find(host,SceneSemantics.Role.MENU_ITEM,"撤销未保存编辑").actions.isEmpty(),"未改草稿的禁用命令可执行");
            require(perform(session,popup.id,SceneSemantics.Action.COLLAPSE)==SceneSemanticRequest.Result.APPLIED,"菜单不能关闭");
            SceneSemanticSnapshot.Entry tab=find(host,SceneSemantics.Role.TAB,"名称与备注");
            require(perform(session,tab.id,SceneSemantics.Action.SELECT)==SceneSemanticRequest.Result.APPLIED,"标签选择没有执行");
            SceneSemanticSnapshot.Entry panel=find(host,SceneSemantics.Role.TAB_PANEL,"名称与备注");
            require(panel.labelledBy.contains(tab.id)&&Boolean.TRUE.equals(host.snapshot.get(tab.id).values.selected),"面板未关联当前Tab");
            SceneSemanticSnapshot.Entry field=find(host,SceneSemantics.Role.TEXT_FIELD,"项目名称");
            SceneSemanticSnapshot.Entry notes=find(host,SceneSemantics.Role.TEXT_AREA,"项目备注");
            require("界面资源整理".equals(field.values.value)&&notes.values.value.contains("本周"),"真实草稿名称或值缺失");
            SceneSemanticId before=field.id;
            require(perform(session,find(host,SceneSemantics.Role.BUTTON,"反转标签顺序").id,SceneSemantics.Action.ACTIVATE)==SceneSemanticRequest.Result.APPLIED
                    &&host.snapshot.get(before)!=null,"重排Tab重建了原活动字段");
        }finally{session.close();}
    }
    private void failedCompletion(){try(Fixture f=new Fixture()){
        RuntimeException expected=new RuntimeException("完成回调失败");SceneSemanticId button=f.entry(SceneSemantics.Role.BUTTON,"执行").id;
        CompletableFuture<SceneSemanticRequest.Result> first=f.session.semantic(SceneSemanticRequest.of(button,SceneSemantics.Action.ACTIVATE),(result,error)->{throw expected;});
        CompletableFuture<SceneSemanticRequest.Result> tail=f.session.semantic(SceneSemanticRequest.of(button,SceneSemantics.Action.ACTIVATE));
        f.session.edit(SceneEditEvent.text("不应继续"));
        try{f.session.frame();throw new AssertionError("应传播完成回调错误");}catch(RuntimeException actual){require(actual==expected,"完成回调错误身份丢失");}
        require(f.actions==1&&first.isCompletedExceptionally()&&tail.isCompletedExceptionally(),"完成回调异常被Future吞掉或继续尾部动作");
    }
        Fixture closed=new Fixture();SceneSemanticId id=closed.entry(SceneSemantics.Role.BUTTON,"执行").id;
        RuntimeException expected=new RuntimeException("关闭回调失败");
        CompletableFuture<SceneSemanticRequest.Result> bad=closed.session.semantic(SceneSemanticRequest.of(id,SceneSemantics.Action.ACTIVATE),(result,error)->{throw expected;});
        CompletableFuture<SceneSemanticRequest.Result> rest=closed.session.semantic(SceneSemanticRequest.of(id,SceneSemantics.Action.ACTIVATE));
        try{closed.close();throw new AssertionError("应传播关闭完成回调错误");}catch(RuntimeException actual){require(actual==expected,"关闭时回调错误身份丢失");}
        require(bad.isCompletedExceptionally()&&rest.join()==SceneSemanticRequest.Result.CLOSED&&closed.host.snapshot.entries.isEmpty(),"关闭回调失败遗留尾部或语义资源");
    }
    private void closingAction(){
        ReactiveDomain domain=ReactiveDomain.direct();Host host=new Host();ScenePageSession[] session=new ScenePageSession[1];int[] calls={0};
        session[0]=ScenePageSession.open(domain,host,host,p->p.mount(SceneButton.create(p,()->"关闭页面",p.theme(),()->{calls[0]++;session[0].close();})));
        session[0].frame();SceneSemanticId id=host.snapshot.entries.get(0).id;
        CompletableFuture<SceneSemanticRequest.Result> active=session[0].semantic(SceneSemanticRequest.of(id,SceneSemantics.Action.ACTIVATE));
        CompletableFuture<SceneSemanticRequest.Result> tail=session[0].semantic(SceneSemanticRequest.of(id,SceneSemantics.Action.ACTIVATE));
        session[0].frame();require(calls[0]==1&&active.join()==SceneSemanticRequest.Result.APPLIED&&tail.join()==SceneSemanticRequest.Result.CLOSED
                &&host.snapshot.entries.isEmpty(),"动作关闭页面后当前请求或尾部结果不正确");
    }
    private void modalActions(){try(Fixture f=new Fixture()){
        SceneSemanticId button=f.entry(SceneSemantics.Role.BUTTON,"执行").id;
        f.domain.execute(()->f.modal.set(true));CompletableFuture<SceneSemanticRequest.Result> result=f.session.semantic(SceneSemanticRequest.of(button,SceneSemantics.Action.ACTIVATE));f.session.frame();
        require(result.join()==SceneSemanticRequest.Result.REJECTED&&f.actions==0&&f.host.snapshot.currentModal!=null,"语义动作越过同批当前模态");
        SceneSemanticId top=f.entry(SceneSemantics.Role.BUTTON,"模态动作").id;result=f.session.semantic(SceneSemanticRequest.of(top,SceneSemantics.Action.ACTIVATE));f.session.frame();
        require(result.join()==SceneSemanticRequest.Result.APPLIED&&f.actions==10,"当前模态合法动作没有执行");
    }}
}
