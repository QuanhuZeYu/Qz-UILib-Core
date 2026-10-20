package club.heiqi.uilib.ui.settings;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 共享设置入口；视图组合归工作区，配置与草稿仍由原有持有者维护。 */
public final class SettingsPage implements ScenePage {
    private final SettingsConfiguration configuration;
    private final Runnable leave;
    private SettingsWorkspace workspace;

    public SettingsPage(SettingsConfiguration configuration, Runnable leave) {
        this.configuration=Objects.requireNonNull(configuration,"configuration");
        this.leave=Objects.requireNonNull(leave,"leave");
    }
    @Override public void build(ScenePageContext context) { context.mountOwned(create(context)); }
    /** 组合宿主沿同一页面上下文拥有与释放此声明。 */
    public SceneNode create(ScenePageContext context) {
        if(context.domain()!=configuration.domain())throw new IllegalArgumentException("配置页面与配置必须同域");
        if(workspace!=null)throw new IllegalStateException("配置页面已经打开");
        workspace=new SettingsWorkspace(context,configuration,leave);
        context.own(() -> workspace=null);
        return workspace.build();
    }
    public void requestClose() { requestClose(leave); }
    /** 确认后仅退出，不隐含保存或撤销已应用值。 */
    public void requestClose(Runnable afterConfirmed) {
        Objects.requireNonNull(afterConfirmed,"afterConfirmed");
        configuration.domain().execute(() -> {if(workspace!=null)workspace.closeRequested(afterConfirmed);});
    }
    public void requestReload() {
        configuration.domain().execute(() -> {if(workspace!=null)workspace.reloadRequested();});
    }
}
