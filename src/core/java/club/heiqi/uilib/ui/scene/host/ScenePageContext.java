package club.heiqi.uilib.ui.scene.host;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import club.heiqi.uilib.ui.scene.localization.LocalizationSource;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDensity;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 页面作用域与声明接入；外部业务状态和宿主服务仅在明确own时归本次页面。 */
public final class ScenePageContext {
    private final ReactiveDomain domain;
    private final Owner scope;
    private final SceneRuntime runtime;
    private final club.heiqi.uilib.ui.scene.video.MediaViewPump mediaViews;
    private LocalizationSource localizationSource;
    private LocalizationBinding localization;
    private java.util.function.Supplier<java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot>> inspection;
    void inspection(java.util.function.Supplier<java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot>> request) { inspection = request; }
    public java.util.concurrent.CompletableFuture<club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot> inspect() {
        domain.checkAccess(); return inspection.get();
    }
    public void inspectionRecording(ReadableSignal<Boolean> enabled) { runtime.inspectionRecording(enabled); }
    private final ReadableSignal<ThemeMode> themeMode;
    private final ReadableSignal<SceneTheme> theme;
    private final Signal<ReadableSignal<SceneDensity>> densitySource;
    private final ReadableSignal<SceneDensity> density;
    private final ReadableSignal<Integer> scrollStep;

    ScenePageContext(ReactiveDomain domain, Owner scope, SceneRuntime runtime,
            ThemeConfiguration themes) {
        this(domain, scope, runtime, themes, null);
    }

    ScenePageContext(ReactiveDomain domain, Owner scope, SceneRuntime runtime, ThemeConfiguration themes,
            club.heiqi.uilib.ui.scene.video.MediaViewPump mediaViews) {
        this(domain, scope, runtime, themes, mediaViews, null);
    }

    ScenePageContext(ReactiveDomain domain, Owner scope, SceneRuntime runtime, ThemeConfiguration themes,
            club.heiqi.uilib.ui.scene.video.MediaViewPump mediaViews,
            club.heiqi.uilib.ui.scene.control.ScrollConfiguration scrolling) {
        this.mediaViews = mediaViews;
        this.domain = domain;
        this.scope = scope;
        this.runtime = runtime;
        themeMode = themes == null ? domain.signal(ThemeMode.LIGHT)
                : themes.bind(domain, scope);
        densitySource = domain.signal(() -> SceneDensity.COMFORTABLE);
        density = domain.computed(scope, () -> Objects.requireNonNull(densitySource.get().get(), "页面密度"));
        theme = domain.computed(scope, () -> themeMode.get().sceneTheme().withDensity(density.get()));
        runtime.scrollbarTheme(theme);
        scrollStep = scrolling == null ? () -> club.heiqi.uilib.ui.scene.control.ScrollConfiguration.KEY.defaultValue()
                : scrolling.bind(domain, scope);
        runtime.scrollStep(scrollStep);
    }

    /** 将应用主题配置投影到页面域，供局部配色派生使用。 */
    public ReadableSignal<ThemeMode> themeMode() { return themeMode; }
    public ReadableSignal<SceneTheme> theme() { return theme; }
    public ReadableSignal<SceneDensity> density() { return density; }
    /** 应用生效值；节点显式scrollStep继续覆盖此默认值。 */
    public ReadableSignal<Integer> scrollStep() { return scrollStep; }
    /** 页面默认主题与自动条共用此可追踪来源，订阅归原页面Owner。 */
    public void density(ReadableSignal<SceneDensity> source) {
        domain.checkWrite(); densitySource.set(Objects.requireNonNull(source, "密度来源"));
    }

    public ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionPolicy> motionPolicy() { return runtime.motionPolicy(); }
    public void motionPolicy(ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionPolicy> source) { runtime.motionPolicy(source); }

    /** 显式选择本页唯一来源，绑定归页面；共享流仅在明确 own 时才由页面关闭。 */
    public LocalizationBinding localization(LocalizationSource source) {
        domain.checkWrite();
        Objects.requireNonNull(source, "source");
        if (localization == null) {
            localization = new LocalizationBinding(domain, scope, source);
            localizationSource = source;
        } else if (localizationSource != source) {
            throw new IllegalStateException("页面语言绑定已选择其他来源");
        }
        return localization;
    }

    /** 页面响应式适配使用宿主根可用空间；普通子项仍由正式布局分配。 */
    public ReadableSignal<Integer> viewportWidth() { return runtime.viewportWidth(); }
    public ReadableSignal<Integer> viewportHeight() { return runtime.viewportHeight(); }

    /** 借用宿主媒体泵，视图绑定随 NodeBinding 挂载/卸载；会话仍归原持有者。 */
    public club.heiqi.uilib.ui.scene.video.MediaViewPump mediaViews() {
        domain.checkAccess();
        if (mediaViews == null) throw new IllegalStateException("宿主未装配媒体呈现服务");
        return mediaViews;
    }

    public ReactiveDomain domain() {
        return domain;
    }

    public Owner scope() {
        domain.checkAccess();
        return scope;
    }

    /** 扩展复用本页 runtime，普通内容通过 mount 声明根。 */
    public SceneRuntime runtime() {
        domain.checkAccess();
        return runtime;
    }

    public MountHandle mount(SceneNode root) {
        domain.checkWrite();
        return runtime.attach(null, root);
    }

    /** 页面在挂载结束后释放所拥有的根逻辑树；调用方保留的子树仍归调用方。 */
    public MountHandle mountOwned(SceneNode root) {
        domain.checkWrite();
        Objects.requireNonNull(root, "root");
        // 初始化失败同样沿页面已有 scope 清理，保留原错误及清理 suppressed。
        Object logicalScope = new Object();
        scope.own(() -> {
            // 明确移动会交接生命周期责任，此处只销毁仍归本页的根。
            if (root.logicalOwner() == logicalScope) root.destroy();
        });
        return runtime.attachOwned(root, logicalScope);
    }

    public void own(AutoCloseable resource) {
        domain.checkWrite();
        scope.own(resource);
    }
}
