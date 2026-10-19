package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.*;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.*;
import java.util.concurrent.CompletableFuture;

/** 真正页面切主题后仍使用原字段、弹层和有限数据视图。 */
public final class DensityPresentationScenario extends TestApplication {
    @Override protected void runTests()throws Exception{
        scenario("共享密度页的尺寸角色与表单保存沿原状态更新",this::page);
        scenario("项目详情在密度切换中保留选区弹层与活动面板",this::project);
        scenario("真实任务表切密度保留锚点备注和固定列",this::table);
    }
    final class Fixture implements AutoCloseable{
        final ReactiveDomain domain=ReactiveDomain.direct();
        final VectorPresentationScenario.Host host=new VectorPresentationScenario.Host();
        final ScenePageSession session;Signal<SceneDensity> density;SceneNode root;
        Fixture(int kind){host.width=1280;host.height=kind==2?600:1800;
            session=ScenePageSession.open(domain,host,host,page->{
                page.runtime().motionPolicy(()->club.heiqi.uilib.ui.scene.motion.MotionPolicy.OFF);
                density=domain.signal(SceneDensity.COMFORTABLE);page.density(density);
                ReadableSignal<SceneTheme> theme=domain.computed(page.scope(),()->SceneAppearance.theme(false).withDensity(density.get()));
                root=kind==0?DensityPage.create(page,theme,density,()->{}):kind==1?ProjectDetailsPage.create(page,theme,()->{}):DataTablePage.create(page,theme,()->{});
                page.mount(root);
            });session.frame();
        }
        SceneSemanticSnapshot.Entry find(SceneSemantics.Role role,String name){
            for(SceneSemanticSnapshot.Entry entry:host.semantics.entries)if(entry.values.role==role&&name.equals(entry.values.name))return entry;
            throw new AssertionError("缺少页面控件 "+role+" "+name);
        }
        void request(SceneSemanticRequest request){CompletableFuture<SceneSemanticRequest.Result> result=session.semantic(request);session.frame();require(result.join()==SceneSemanticRequest.Result.APPLIED,"页面动作未接受 "+request);}
        void action(SceneSemantics.Role role,String name,SceneSemantics.Action action){request(SceneSemanticRequest.of(find(role,name).id,action));}
        void density(SceneDensity value){domain.execute(()->density.set(value));session.frame();}
        boolean message(String text){for(SceneSemanticSnapshot.Entry entry:host.semantics.entries)if(text.equals(entry.values.name))return true;return false;}
        SceneNode scroller(){final SceneNode[] target={null};final int[] maximum={0};domain.execute(()->scan(root,target,maximum));require(target[0]!=null,"真实表格没有滚动容器");return target[0];}
        void scan(SceneNode node,SceneNode[] target,int[] maximum){SceneGeometryView.Entry entry=session.pipeline().current().geometry.get(node);
            if(entry!=null&&node.axis()==SceneNode.Axis.COLUMN&&entry.scrollMaximum>maximum[0]){maximum[0]=entry.scrollMaximum;target[0]=node;}
            for(SceneNode child:node.children())scan(child,target,maximum);
        }
        void scroll(SceneNode node,int offset){domain.execute(()->session.runtime().scrollToPixels(node,SceneNode.Axis.COLUMN,offset));session.frame();}
        int firstVisibleRow(){int first=Integer.MAX_VALUE;for(SceneSemanticSnapshot.Entry entry:host.semantics.entries)
            if(entry.values.role==SceneSemantics.Role.ROW&&entry.values.index!=null&&entry.values.index>1&&!entry.offscreen)first=Math.min(first,entry.values.index);
            require(first!=Integer.MAX_VALUE,"当前没有可见数据行");return first;
        }
        public void close(){session.close();}
    }
    private void page(){try(Fixture f=new Fixture(0)){
        SceneSemanticSnapshot.Entry small=f.find(SceneSemantics.Role.BUTTON,"小号操作"),regular=f.find(SceneSemantics.Role.BUTTON,"常规操作"),large=f.find(SceneSemantics.Role.BUTTON,"大号操作");
        require(small.box.height<regular.box.height&&regular.box.height<large.box.height,"局部尺寸角色没有实际自然高度区别");
        SceneSemanticId draft=f.find(SceneSemantics.Role.TEXT_FIELD,"任务名称").id;
        f.request(SceneSemanticRequest.text(draft,"密度保持编辑"));
        int comfortableHeight=regular.box.height;
        f.action(SceneSemantics.Role.RADIO,"紧凑",SceneSemantics.Action.SELECT);
        require(f.host.semantics.get(regular.id)!=null&&f.host.semantics.get(regular.id).box.height<comfortableHeight,"密度没有改变同一操作的自然高度");
        require("密度保持编辑".equals(f.host.semantics.get(draft).values.value)&&f.message("当前密度：紧凑"),"密度选择没有沿同一表单来源更新");
        f.request(SceneSemanticRequest.value(f.find(SceneSemantics.Role.SLIDER,"任务进度").id,65));
        f.action(SceneSemantics.Role.SELECT,"优先级",SceneSemantics.Action.EXPAND);
        SceneSemanticId popup=f.find(SceneSemantics.Role.LISTBOX,"优先级").id;
        f.density(SceneDensity.COMFORTABLE);
        require(f.host.semantics.get(popup)!=null&&f.host.semantics.get(draft)!=null,"密度恢复重建打开的弹层或字段");
        f.action(SceneSemantics.Role.OPTION,"紧急",SceneSemantics.Action.SELECT);
        f.action(SceneSemantics.Role.BUTTON,"保存设置",SceneSemantics.Action.ACTIVATE);
        require(f.message("已保存：密度保持编辑 · 紧急 · 65% · 完成后通知"),"真实保存没有提交切密度后的原表单值");
        require(f.host.semantics.get(regular.id).box.height==comfortableHeight,"密度往返累计缩小");
    }}
    private void project(){try(Fixture f=new Fixture(1)){
        f.action(SceneSemantics.Role.TAB,"名称与备注",SceneSemantics.Action.SELECT);
        SceneSemanticId name=f.find(SceneSemantics.Role.TEXT_FIELD,"项目名称").id;
        SceneSemanticId panel=f.find(SceneSemantics.Role.TAB_PANEL,"名称与备注").id;
        f.request(SceneSemanticRequest.text(name,"保留项目草稿"));
        f.request(SceneSemanticRequest.of(name,SceneSemantics.Action.FOCUS));
        f.session.edit(SceneEditEvent.command(SceneEditEvent.Command.SELECT_ALL,false,null));f.session.frame();
        f.density(SceneDensity.COMPACT);
        final String[] selected={null};SceneEditEvent.Clipboard clipboard=new SceneEditEvent.Clipboard(){public String read(){return selected[0];}public void write(String value){selected[0]=value;}};
        f.session.edit(SceneEditEvent.command(SceneEditEvent.Command.COPY,false,clipboard));f.session.frame();
        require("保留项目草稿".equals(selected[0])&&name.equals(f.host.semantics.focused),"密度切换丢失实际选区或焦点");
        SceneSemanticId select=f.find(SceneSemantics.Role.SELECT,"负责人").id;f.request(SceneSemanticRequest.of(select,SceneSemantics.Action.EXPAND));
        SceneSemanticId popup=f.find(SceneSemantics.Role.LISTBOX,"负责人").id;
        f.density(SceneDensity.COMFORTABLE);
        require(f.host.semantics.get(popup)!=null&&f.host.semantics.get(panel)!=null&&f.host.semantics.get(select).box.width==180,"密度重建活动面板/弹层或改写明确选择器宽度");
        f.action(SceneSemantics.Role.OPTION,"周宁",SceneSemantics.Action.SELECT);
        f.action(SceneSemantics.Role.BUTTON,"保存项目",SceneSemantics.Action.ACTIVATE);
        require("保留项目草稿".equals(f.host.semantics.get(name).values.value)&&"周宁".equals(f.host.semantics.get(select).values.value),"项目保存丢失原业务字段或选择");
    }}
    private void table(){try(Fixture f=new Fixture(2)){
        SceneSemanticSnapshot.Entry table=f.find(SceneSemantics.Role.TABLE,"团队任务");
        require(table.values.count==1000,"真实页面任务数据规模变化");
        SceneSemanticSnapshot.Entry note=null;for(SceneSemanticSnapshot.Entry entry:f.host.semantics.entries)
            if(entry.values.role==SceneSemantics.Role.TEXT_FIELD&&!entry.offscreen&&entry.values.name.startsWith("我的备注：")){note=entry;break;}
        require(note!=null,"真实任务表首屏缺少备注字段");String noteName=note.values.name;f.request(SceneSemanticRequest.text(note.id,"密度切换后仍保留"));
        SceneNode scroller=f.scroller();f.scroll(scroller,Integer.MAX_VALUE);f.scroll(scroller,12000);int anchor=f.firstVisibleRow();
        f.density(SceneDensity.COMPACT);
        require(f.firstVisibleRow()==anchor,"密度更新未保留当前真实逻辑锚点");
        f.scroll(scroller,Integer.MAX_VALUE);boolean last=false;
        for(SceneSemanticSnapshot.Entry entry:f.host.semantics.entries)if(entry.values.role==SceneSemantics.Role.ROW&&entry.values.index!=null&&entry.values.index.equals(entry.values.count)&&!entry.offscreen)last=true;
        require(last,"密度后的估算校正未能显露真实末行");
        f.scroll(scroller,0);SceneSemanticSnapshot.Entry restored=f.find(SceneSemantics.Role.TEXT_FIELD,noteName);
        require("密度切换后仍保留".equals(restored.values.value),"实际离屏返回丢失独立备注");
        require(f.find(SceneSemantics.Role.COLUMN_HEADER,"状态").box.width==100&&f.find(SceneSemantics.Role.COLUMN_HEADER,"操作").box.width==120,"密度改写表格明确列宽");
        long mountedRows=f.host.semantics.entries.stream().filter(entry->entry.values.role==SceneSemantics.Role.ROW).count();
        require(mountedRows<100,"切密度实例化整份数据表");
    }}
}
