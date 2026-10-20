package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;

/** 统一弹窗外框；只分配具名区域，模态范围与返回焦点交由正式核心。 */
final class SettingsDialogFrame {
    static SceneNode create(SettingsUi ui,String name,int width,int height,SceneNode header,SceneNode body,SceneNode footer,
            ReadableSignal<Boolean> visible,SceneNode initial,Runnable cancel,SceneNode... attached) {
        // 复合控件通过正式语义公开真实焦点目标；不依赖选择器内部子序或增加焦点权威。
        SceneSemantics semantics=initial.control()==null?null:initial.control().semantics(initial);
        SceneNode focus=semantics!=null&&semantics.focusTarget!=null?semantics.focusTarget:initial;
        SceneNode content=ui.stack(0).size(Size.fill(1),Size.fill(1)).control(new SettingsLayout.Dialog(header,body,footer))
                .add(header).add(body).add(footer);
        SceneNode card=ui.card(content).size(Size.fill(1),Size.fill(1));
        SceneNode position=ui.stack(0).size(Size.fill(1),Size.fill(1)).control(new SettingsLayout.Center(ui,card,width,height)).add(card);
        SceneNode scrim=ui.stack(0).size(Size.fill(1),Size.fill(1)).background(SceneAppearance.MODAL_SCRIM);
        SceneNode window=SceneLayer.create(ui.context,()->new SceneLayer.Policy(10,SceneLayer.Participation.INTERACTIVE))
                .size(Size.fill(1),Size.fill(1)).control(new SettingsLayout.Layers()).modal(true).initialFocus(focus)
                .onEscape(()->{cancel.run();return true;})
                .bind(NodeBinding.semantics(()->SceneSemantics.builder(SceneSemantics.Role.GROUP).name(name).build()))
                .add(scrim).add(position);
        for(SceneNode node:attached)window.add(node);
        SceneNode owner=ui.stack(0).size(Size.fill(1),Size.fill(1)).control(new SettingsLayout.Layers());
        return ui.motion.dialog(owner,window,visible);
    }
    private SettingsDialogFrame() {}
}
