package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.config.*;
import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.*;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.semantics.*;
import club.heiqi.uilib.ui.scene.text.font.*;
import club.heiqi.uilib.ui.settings.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 正式设置页面经原语义/快捷键入口消费真实配置文件，不以Gallery草稿替代应用保存。 */
public final class SettingsWorkspaceScenario extends TestApplication {
    public static void main(String[] args)throws Exception{new SettingsWorkspaceScenario().run(args);}
    @Override protected void runTests()throws Exception{
        scenario("正式设置单选字体仅改草稿且应用保存保留用户注释",this::fontSelection);
        scenario("路径拒绝保留原草稿与关联说明且模态返回按原确认",this::pathAndClose);
    }
    final class Fixture implements AutoCloseable{
        final ReactiveDomain domain=ReactiveDomain.direct();final VectorPresentationScenario.Host host=new VectorPresentationScenario.Host();
        final List<FontSource> builtins=FontTextService.defaultSources(getClass().getClassLoader(),1);
        final ConfigKey<List<String>> fonts=FontCandidateConfiguration.key(builtins);
        final Path file;ConfigSession config;SettingsConfiguration configuration;SettingsPage page;ScenePageSession session;
        int refreshes,leaves;
        Fixture()throws Exception{
            file=Files.createTempFile("uilib-workspace-",".yaml");onClose(()->Files.deleteIfExists(file));host.width=1280;host.height=1100;
            domain.execute(()->{
                config=new ConfigSession(domain,ConfigSchema.of(fonts,ThemeConfiguration.KEY));
                try{config.load(new StringReader("# 用户字体说明\nfont:\n  candidates: [builtin:"+builtins.get(0).id+"] # 字体候选注释\n"));config.save(file);}catch(IOException failure){throw new UncheckedIOException(failure);}
                configuration=new SettingsConfiguration(domain,config,fonts,null,file,FontCandidateConfiguration.defaults(builtins),values->{
                    try{FontCandidateConfiguration.resolve(values,builtins,(path,index)->FontSource.file("workspace-"+index,1,Paths.get(path),0,false,Collections.emptySet()));}
                    catch(IOException failure){throw new UncheckedIOException(failure);}
                },()->refreshes++);
                page=new SettingsPage(configuration,()->leaves++);
            });
            session=ScenePageSession.open(domain,host,host,page);session.frame();
        }
        SceneSemanticSnapshot.Entry entry(SceneSemantics.Role role,String name){
            for(SceneSemanticSnapshot.Entry e:host.semantics.entries)if(e.values.role==role&&name.equals(e.values.name))return e;
            throw new AssertionError("缺少正式工作区控件 "+role+" "+name);
        }
        SceneSemanticRequest.Result request(SceneSemanticRequest request){CompletableFuture<SceneSemanticRequest.Result> result=session.semantic(request);session.frame();return result.join();}
        void action(SceneSemantics.Role role,String name,SceneSemantics.Action action){require(request(SceneSemanticRequest.of(entry(role,name).id,action))==SceneSemanticRequest.Result.APPLIED,"正式动作拒绝 "+name);}
        String disk()throws IOException{return new String(Files.readAllBytes(file),StandardCharsets.UTF_8);}
        List<String> current(){final List<String>[] result=new List[1];domain.execute(()->result[0]=configuration.current().candidates());return result[0];}
        void openAdd(){action(SceneSemantics.Role.BUTTON,"管理字体来源",SceneSemantics.Action.ACTIVATE);action(SceneSemantics.Role.BUTTON,"+ 添加来源",SceneSemantics.Action.ACTIVATE);}
        public void close()throws Exception{try{session.close();}finally{Files.deleteIfExists(file);}}
    }
    private void fontSelection()throws Exception{try(Fixture f=new Fixture()){
        require(f.builtins.size()>1,"正式字体候选不足以检查选择");
        String original=f.disk();List<String> before=new ArrayList<>(f.current());
        SceneSemanticSnapshot.Entry sample=f.entry(SceneSemantics.Role.TEXT_AREA,"文字样本");
        require(!sample.labelledBy.isEmpty()&&sample.values.description.contains("当前生效字体"),"真实样本未关联标签或字体事实说明");
        f.openAdd();
        require(Boolean.TRUE.equals(f.entry(SceneSemantics.Role.RADIO,"内置来源").values.selected),"来源类型未表达原内置选择");
        f.action(SceneSemantics.Role.SELECT,"内置字体",SceneSemantics.Action.EXPAND);
        SceneSemanticSnapshot.Entry second=null;for(SceneSemanticSnapshot.Entry e:f.host.semantics.entries)
            if(e.values.role==SceneSemantics.Role.OPTION&&Integer.valueOf(2).equals(e.values.index)&&e.rangeInteractive){second=e;break;}
        require(second!=null,"公共单选未呈现真实第二字体来源");
        require(f.request(SceneSemanticRequest.of(second.id,SceneSemantics.Action.FOCUS))==SceneSemanticRequest.Result.APPLIED,"真实选项不能预览焦点");
        require(f.current().equals(before)&&f.disk().equals(original),"选项预览改写配置或文件");
        require(f.request(SceneSemanticRequest.of(second.id,SceneSemantics.Action.SELECT))==SceneSemanticRequest.Result.APPLIED,"选项确认拒绝");
        f.action(SceneSemantics.Role.BUTTON,"添加到末尾",SceneSemantics.Action.ACTIVATE);
        f.action(SceneSemantics.Role.BUTTON,"完成字体编辑",SceneSemantics.Action.ACTIVATE);
        require(f.current().equals(before)&&f.disk().equals(original),"来源编辑隐式应用或保存");
        require(f.entry(SceneSemantics.Role.BUTTON,"保存到文件").actions.isEmpty(),"未应用草稿仍可保存");
        f.action(SceneSemantics.Role.BUTTON,"应用全部更改",SceneSemantics.Action.ACTIVATE);
        require(f.current().equals(Arrays.asList(before.get(0),"builtin:"+f.builtins.get(1).id)),"应用未发布真实字体顺序："+f.current());
        require(f.refreshes==0,"应用错误地调用了仅属于显式文件加载的资源回调");
        require(f.disk().equals(original),"应用动作隐式落盘");
        SceneKeyEvent save=new SceneKeyEvent(SceneKey.S,true,false,false,false,false,null);
        f.session.key(save);f.session.key(save.withPhase(SceneKeyEvent.Phase.UP));f.session.frame();
        require(f.disk().contains(f.builtins.get(1).id)&&f.disk().contains("用户字体说明")&&f.disk().contains("字体候选注释"),"公共保存命令未保留真实候选或用户注释");
        require(f.entry(SceneSemantics.Role.BUTTON,"保存到文件").actions.isEmpty(),"保存后命令仍可执行");
        f.action(SceneSemantics.Role.BUTTON,"配置文件",SceneSemantics.Action.ACTIVATE);
        require(Boolean.TRUE.equals(f.entry(SceneSemantics.Role.BUTTON,"配置文件").values.selected),"当前分类未表达选中状态");
        f.action(SceneSemantics.Role.BUTTON,"从文件重新加载",SceneSemantics.Action.ACTIVATE);
        require(f.refreshes==1&&f.current().size()==2,"同值显式加载未沿真实资源通道刷新");
    }}
    private void pathAndClose()throws Exception{try(Fixture f=new Fixture()){
        String original=f.disk();f.openAdd();
        f.action(SceneSemantics.Role.RADIO,"本地字体文件",SceneSemantics.Action.SELECT);
        SceneSemanticSnapshot.Entry path=f.entry(SceneSemantics.Role.TEXT_FIELD,"字体文件路径");
        boolean described=false;for(SceneSemanticId id:path.describedBy){SceneSemanticSnapshot.Entry help=f.host.semantics.get(id);if(help!=null&&help.values.name.contains("应用前会检查字体来源"))described=true;}
        require(!path.labelledBy.isEmpty()&&described,"字体路径标签/说明关系不完整：labels="+path.labelledBy+", descriptions="+path.describedBy);
        require(f.request(SceneSemanticRequest.text(path.id,"   "))==SceneSemanticRequest.Result.APPLIED,"路径字段拒绝草稿输入");
        f.action(SceneSemantics.Role.BUTTON,"添加到末尾",SceneSemantics.Action.ACTIVATE);
        require(f.host.semantics.get(path.id)!=null&&"   ".equals(f.host.semantics.get(path.id).values.value),"路径校验拒绝丢失原草稿或字段");
        boolean error=false;for(SceneSemanticSnapshot.Entry e:f.host.semantics.entries)if("请输入字体文件路径。".equals(e.values.name))error=true;
        require(error&&f.disk().equals(original)&&f.current().size()==1,"拒绝没有定位错误或修改实际配置");
        f.action(SceneSemantics.Role.BUTTON,"取消",SceneSemantics.Action.ACTIVATE);
        f.action(SceneSemantics.Role.BUTTON,"完成字体编辑",SceneSemantics.Action.ACTIVATE);
        f.action(SceneSemantics.Role.BUTTON,"深色外观",SceneSemantics.Action.ACTIVATE);
        require(Boolean.TRUE.equals(f.entry(SceneSemantics.Role.BUTTON,"深色外观").values.selected),"外观选择缺少可读选中事实");
        f.action(SceneSemantics.Role.BUTTON,"返回",SceneSemantics.Action.ACTIVATE);
        require(f.leaves==0&&f.entry(SceneSemantics.Role.GROUP,"离开配置工作区")!=null,"有草稿的返回绕过原关闭确认");
        f.action(SceneSemantics.Role.BUTTON,"继续编辑",SceneSemantics.Action.ACTIVATE);
        require(f.leaves==0&&Boolean.TRUE.equals(f.entry(SceneSemantics.Role.BUTTON,"深色外观").values.selected),"继续编辑丢失主题草稿");
        f.action(SceneSemantics.Role.BUTTON,"返回",SceneSemantics.Action.ACTIVATE);
        f.action(SceneSemantics.Role.BUTTON,"不保存并离开",SceneSemantics.Action.ACTIVATE);
        require(f.leaves==1&&f.disk().equals(original),"确认离开没有沿原动作或隐式写文件");
    }}
}
