package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;

/** 宿主提供环境事实与绘制回放，布局和声明协调归场景运行时。 */
public interface SceneHost {
    /** 借用应用级主题配置；缺省时页面使用浅色主题。 */
    default ThemeConfiguration themeConfiguration() { return null; }

    /** 应用唯一滚轮配置；未装配时使用Core声明默认值。 */
    default club.heiqi.uilib.ui.scene.control.ScrollConfiguration scrollConfiguration() { return null; }

    /** 帧开始在宿主调用线程推进有界资源入口，由服务向受控 domain 递交变化。 */
    default void prepareResources() {}

    /** 媒体宿主提供挂载 consumer 泵；缺省入口保持按需装配。 */
    default club.heiqi.uilib.ui.scene.video.MediaViewPump mediaViews() { return null; }

    /** 每个宿主帧采样一次的单调纳秒时间，默认与System.nanoTime同域；供输入周期和呈现共同消费。 */
    default long frameNanos() { return System.nanoTime(); }

    int width();

    int height();

    /** 字体、主题或度量环境改变时递增；尺寸变化会单独被框架感知。 */
    long metricsRevision();

    /** 发布同批语义的实际变化，宿主按需接入原生无障碍桥。 */
    default void semanticsChanged(club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot snapshot) {}

    void replay(PaintPlan plan);
}
