package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import java.util.Arrays;

/** 同一份页面密度来源驱动真实控件；局部尺寸角色不改写业务状态或外层布局。 */
public final class DensityPage {
    private DensityPage(){}
    public static SceneNode create(ScenePageContext page, ReadableSignal<SceneTheme> theme,
                                   Signal<SceneDensity> density, Runnable back){
        Owner owner=page.scope().child();
        Signal<String> feedback=page.domain().signal("选择显示密度，再试试不同尺寸的操作。");
        SceneNode content=new SceneNode().size(Size.fill(1),Size.natural()).padding(28).gap(24).own(owner);
        content.add(GalleryStyle.header(page,theme,"尺寸与密度","选择舒适或紧凑排布，编辑内容会继续保留。",back));
        SceneNode densityLabel=GalleryStyle.label(page,()->"显示密度",theme,18,false);
        SceneRadioGroup<SceneDensity> choices=SceneRadioGroup.create(page,()->Arrays.asList(
                new SceneRadioGroup.Option<>(SceneDensity.COMFORTABLE,()->"舒适"),
                new SceneRadioGroup.Option<>(SceneDensity.COMPACT,()->"紧凑")),density,density::set,theme);
        choices.node().bind(NodeBinding.semantics(()->SceneSemantics.builder().labelledBy(densityLabel).build()));
        SceneNode settings=GalleryStyle.card(theme).gap(12).add(densityLabel).add(choices.node())
                .add(SceneLabel.create(page,()->"当前密度："+(density.get()==SceneDensity.COMPACT?"紧凑":"舒适"),theme));
        content.add(GalleryStyle.frame(settings,theme));
        SceneNode sizes=GalleryStyle.grid(page,theme);
        sizes.add(sample(page,theme,owner,SceneControlSize.SMALL,"小号",feedback));
        sizes.add(sample(page,theme,owner,SceneControlSize.REGULAR,"常规",feedback));
        sizes.add(sample(page,theme,owner,SceneControlSize.LARGE,"大号",feedback));
        content.add(sizes);
        content.add(editor(page,theme,feedback));
        content.add(SceneLabel.create(page,feedback,theme));
        return SceneScrollArea.create(page,content,()->new SceneScrollArea.Options(),theme).node()
                .size(Size.fill(1),Size.fill(1));
    }
    private static SceneNode sample(ScenePageContext page,ReadableSignal<SceneTheme> theme,Owner owner,
                                    SceneControlSize size,String name,Signal<String> feedback){
        ReadableSignal<SceneTheme> local=page.domain().computed(owner,()->theme.get().withControlSize(size));
        Signal<Boolean> selected=page.domain().signal(false);
        SceneEditState note=new SceneEditState(page.domain(),name+"备注");
        SceneNode card=GalleryStyle.card(theme).gap(14);
        card.add(GalleryStyle.label(page,()->name,theme,20,false));
        card.add(GalleryStyle.primary(page,()->name+"操作",local,()->feedback.set("已执行："+name+"操作")));
        card.add(SceneCheckBox.create(page,()->selected.get()?SceneCheckBox.State.CHECKED:SceneCheckBox.State.UNCHECKED,
                ()->name+"通知",local,selected::set));
        SceneNode label=GalleryStyle.label(page,()->name+"备注",theme,14,true);
        card.add(label).add(SceneTextField.create(page,note,local,()->false,request->{
            if(note.accept(request,request.text))feedback.set("已记录："+request.text);
        }).bind(NodeBinding.semantics(()->SceneSemantics.builder().labelledBy(label).build())));
        return GalleryStyle.frame(card,theme);
    }
    private static SceneNode editor(ScenePageContext page,ReadableSignal<SceneTheme> theme,Signal<String> feedback){
        SceneEditState title=new SceneEditState(page.domain(),"界面评审");
        Signal<String> priority=page.domain().signal("normal");
        Signal<Boolean> notify=page.domain().signal(true);
        Signal<Double> progress=page.domain().signal(50d);
        java.util.function.Consumer<SceneEditState.Submission> apply=request->{
            String text=request.text.trim();if(text.isEmpty()){title.reject(request,"请输入任务名称");return;}
            if(title.accept(request,text))feedback.set("已保存："+text+" · "+(priority.get().equals("urgent")?"紧急":"普通")
                    +" · "+Math.round(progress.get())+"%"+(notify.get()?" · 完成后通知":""));
        };
        SceneCommand save=SceneCommand.of(()->!title.snapshot().get().text.trim().isEmpty(),()->apply.accept(title.request(true)));
        SceneNode card=GalleryStyle.card(theme).gap(14);
        card.add(GalleryStyle.label(page,()->"任务设置",theme,22,false));
        SceneNode name=GalleryStyle.label(page,()->"任务名称",theme,14,true);
        card.add(name).add(SceneTextField.create(page,title,theme,()->false,apply)
                .bind(NodeBinding.semantics(()->SceneSemantics.builder().labelledBy(name).build())));
        SceneNode priorityLabel=GalleryStyle.label(page,()->"优先级",theme,14,true);
        card.add(priorityLabel).add(SceneSelect.create(page,()->Arrays.asList(
                new SceneSelect.Option<>("normal",()->"普通"),new SceneSelect.Option<>("urgent",()->"紧急")),
                priority,priority::set,theme).size(Size.natural(),Size.natural())
                .bind(NodeBinding.semantics(()->SceneSemantics.builder().labelledBy(priorityLabel).build())));
        SceneNode progressLabel=GalleryStyle.label(page,()->"任务进度 · "+Math.round(progress.get())+"%",theme,14,true);
        card.add(progressLabel).add(SceneSlider.create(page,()->new SceneSlider.Model(0,100,5,progress.get(),true),progress::set,value->progress.set(value),theme)
                .bind(NodeBinding.semantics(()->SceneSemantics.builder().name("任务进度").labelledBy(progressLabel).build())));
        card.add(SceneSwitch.create(page,notify,()->"完成后通知",theme,notify::set));
        SceneNode actions=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(12);
        actions.add(GalleryStyle.primary(page,()->"保存设置",theme,save::tryExecute)
                .bind(NodeBinding.value(save.executable(),SceneNode::enabled)));
        actions.add(GalleryStyle.button(page,()->"撤销编辑",theme,title::cancel));
        return GalleryStyle.frame(card.add(actions),theme);
    }
}
