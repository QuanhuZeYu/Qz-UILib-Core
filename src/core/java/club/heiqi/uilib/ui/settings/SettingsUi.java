package club.heiqi.uilib.ui.settings;

import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

/** 设置专用文字与度量；按钮、字段、表面和反馈均复用正式现代外观。 */
final class SettingsUi extends SceneAppearance {
    final ScenePageContext context;
    final SettingsConfiguration configuration;
    final SettingsModel model;
    final ReadableSignal<Float> scale;
    final ReadableSignal<Boolean> compact,shortWindow;
    final SettingsMotion motion;

    SettingsUi(ScenePageContext context,SettingsConfiguration configuration,SettingsModel model) {
        super(context,() -> model.theme().get().sceneTheme().withDensity(context.density().get())
                .withTextStyle(SettingsTypography.style(Math.round(context.theme().get().textStyle.logicalEm*14/18),
                        model.theme().get().sceneTheme().textStyle.color,400))
                .withMotion(context.theme().get().feedbackMotion,context.theme().get().spatialMotion));
        this.context=context;this.configuration=configuration;this.model=model;
        scale=context.domain().computed(scope,() -> context.theme().get().textStyle.logicalEm/18f);
        compact=context.domain().computed(scope,() -> context.viewportWidth().get()<px(760));
        shortWindow=context.domain().computed(scope,() -> context.viewportHeight().get()<px(560));
        motion=new SettingsMotion(colors);
    }
    int px(int value) {return Math.max(0,Math.round(value*scale.get()));}
    SceneNode stack(int gap) {return stack(gap,gap);}
    SceneNode stack(int gap,int shortGap) {
        return column(0).bind(NodeBinding.value(()->px(shortWindow.get()?shortGap:gap),SceneNode::gap));
    }
    SceneNode inset(SceneNode node,int padding) {return inset(node,padding,padding);}
    SceneNode inset(SceneNode node,int padding,int shortPadding) {
        return node.bind(NodeBinding.value(()->px(shortWindow.get()?shortPadding:padding),SceneNode::padding));
    }
    SceneNode text(String value,int em,boolean muted) {return text(() -> value,em,muted);}
    SceneNode text(ReadableSignal<String> value,int em,boolean muted) {return label(value,em/14f,muted);}
    SceneNode heading(String value,int em) {
        return SceneLabel.create(context,() -> value,() -> colors.get().source.withTextStyle(
                SettingsTypography.style(px(em),colors.get().text,600)));
    }
    SceneNode path(ReadableSignal<String> value,int em) {
        return SceneLabel.create(context,value,() -> colors.get().source.withTextStyle(SettingsTypography.pathStyle(px(em),colors.get().muted)))
                .textPolicy(new TextPolicy(TextPolicy.Direction.LTR,TextPolicy.Wrap.EMERGENCY,"und",0,0));
    }
    SceneNode actions(SceneNode... children) {
        SceneNode row=stack(0).control(new SettingsLayout.WrapRow(scale,8));
        for(SceneNode child:children)row.add(child);return row;
    }
    SceneNode button(String text,Runnable action,Role role) {return action(text,null,action,role);}
    SceneNode button(String text,SceneCommand command,Role role) {return action(text,null,command,role);}
    SceneNode scroll(SceneNode content) {
        return motion.scroll(content.size(Size.fill(1),Size.fill(1)).clip(true).scrollable(true));
    }
    /** 正文与添加页持续显示溢出范围，低窗不依赖悬停才发现滚动。 */
    SceneScrollArea.Area scrollArea(SceneNode content) {
        return SceneScrollArea.create(context,content.size(Size.fill(1),Size.natural()),
                ()->new SceneScrollArea.Options().autoHide(false).motion(
                        new club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions(motion.spatial.get()).maximumDisplacement(24)),
                ()->colors.get().source);
    }
    SceneNode errorText(ReadableSignal<String> message) {return motion.reveal(error(message),()->!message.get().isEmpty());}
    String sourceName(String declaration) {
        if(declaration.equals("builtin:unifont"))return "GNU Unifont";
        if(declaration.equals("builtin:noto-color-emoji"))return "Noto Color Emoji";
        if(declaration.equals("builtin:noto-emoji-text"))return "Noto Emoji";
        if(declaration.startsWith("builtin:noto-")) {String id=declaration.substring(13);return "Noto Sans "+Character.toUpperCase(id.charAt(0))+id.substring(1);}
        if(declaration.startsWith("builtin:"))return declaration.substring(8);
        try {java.nio.file.Path path=java.nio.file.Paths.get(declaration.startsWith("file:")?declaration.substring(5):declaration);return path.getFileName()==null?declaration:path.getFileName().toString();}
        catch(IllegalArgumentException invalid) {return declaration;}
    }
    String sourceCounts() {
        List<SettingsModel.Source> sources=model.sources().get();int builtin=0;
        for(SettingsModel.Source source:sources)if(source.declaration().startsWith("builtin:"))builtin++;
        int file=sources.size()-builtin;
        return "共 "+sources.size()+" 个来源"+(builtin>0?" · 内置 "+builtin:"")+(file>0?" · 文件 "+file:"");
    }
    String changes() {
        SettingsConfiguration.Snapshot live=configuration.current();
        java.util.ArrayList<String> fields=new java.util.ArrayList<>();
        if(!declarations().equals(live.candidates()))fields.add("字体来源");
        if(model.theme().get()!=live.theme())fields.add("界面主题");
        if(configuration.hasMinecraft()&&model.replace().get()!=live.replace())fields.add("Minecraft 接入");
        return String.join("、",fields);
    }
    List<String> declarations() {
        java.util.ArrayList<String> values=new java.util.ArrayList<>();
        for(SettingsModel.Source source:model.sources().get())values.add(source.declaration());return values;
    }
}
