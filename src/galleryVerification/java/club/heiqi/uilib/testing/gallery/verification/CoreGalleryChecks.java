package club.heiqi.uilib.testing.gallery.verification;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.GalleryChecks;
import club.heiqi.uilib.testing.media.*;
import club.heiqi.uilib.ui.scene.control.DocumentArrangementScenario;
import club.heiqi.uilib.ui.scene.control.FilePickerScenario;
import club.heiqi.uilib.ui.scene.control.VirtualCollectionIndexScenario;
import club.heiqi.uilib.ui.scene.integration.*;
import club.heiqi.uilib.ui.scene.text.math.MathStructureScenario;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** 无平台检查的唯一目录；桌面、Minecraft 与命令行消费相同的模块和断言。 */
public final class CoreGalleryChecks implements GalleryChecks {
    private static final class Module {
        final Check check;
        final List<Supplier<TestApplication>> applications;
        @SafeVarargs Module(String id, String title, String description, Supplier<TestApplication>... applications) {
            check = new Check(id, title, description); this.applications = Arrays.asList(applications);
        }
    }
    private final List<Module> modules = Arrays.asList(
        new Module("gallery", "广场与页面", "真实目录进入全部页面、三尺寸切换与编辑结果",
                GalleryNavigationScenario::new, SettingsWorkspaceScenario::new, VectorPresentationScenario::new,
                DensityPresentationScenario::new, InspectionPresentationScenario::new),
        new Module("state", "状态与生命周期", "派生读取、动态结构、资源归属与动效时序",
                SceneCoreLoopTest::new, PageSessionScenario::new, DynamicStructureScenario::new, MotionScenario::new),
        new Module("input", "指针与焦点", "物理按压、焦点范围、方向导航、滚动显露与退出",
                PointerInputScenario::new, FocusInputScenario::new, FocusScopeScenario::new,
                FocusNavigationScenario::new, FocusRevealScenario::new, DirectionalNavigationScenario::new,
                EscapeInputScenario::new, HoverProjectionScenario::new),
        new Module("editing", "编辑与命令", "草稿、撤销、组字身份、快捷键与同批输入隔离",
                EditingScenario::new, MultilineEditingScenario::new, CompositionIdentityScenario::new, CommandScenario::new),
        new Module("controls", "标准控件", "按钮、状态选择、标签页、弹层、滑杆与提示的正式输入",
                StandardControlsScenario::new, StateControlsScenario::new, TabsScenario::new,
                PopupControlsScenario::new, SliderScenario::new, TooltipScenario::new),
        new Module("collections", "有限集合", "索引与选择、可变行高、表格列宽、树投影及屏外导航",
                VirtualCollectionIndexScenario::new, VirtualCollectionScenario::new, DataTableScenario::new, TreeViewScenario::new),
        new Module("presentation", "呈现与检查", "仿射与精确裁剪、绘制载荷、密度、场景检查和语义动作",
                VectorGeometryScenario::new, VectorPaintScenario::new, TransformSceneScenario::new,
                OrderedQuadScenario::new, DensityControlsScenario::new, DensityCollectionScenario::new,
                SceneInspectionScenario::new, SemanticScenario::new),
        new Module("configuration", "配置与持久化", "真实文件往返、对象校验、草稿应用、外部发布及资源释放",
                ConfigScenario::new, YamlObjectScenario::new, ThemeConfigurationScenario::new,
                FontConfigurationScenario::new, SettingsConfigurationScenario::new),
        new Module("content", "文本与资源", "语言重载、字体资源生命周期、共同内容与最终文档排列",
                LocalizationScenario::new, LocalizationResourceScenario::new, CoreFontEngineScenario::new,
                TypedParagraphScenario::new, DocumentArrangementScenario::new),
        new Module("math", "数学内容", "真实宏展开、结构语料、模式资格、来源范围及资源界限",
                MathMacroScenario::new, MathStructureScenario::new),
        new Module("media", "媒体核心", "无设备会话、背压、换代、帧租约、视频布局与卸载",
                MediaCoreScenario::new, MediaSourceScenario::new, SceneVideoLayoutScenario::new),
        new Module("files", "文件选择", "真实目录分页、排序、查询、历史与过期选择",
                FilePickerScenario::new),
        new Module("samples", "性能样本口径", "有界采样、分位数与阈值统计",
                PerformanceSamplesScenario::new),
        new Module("artifacts", "正式产物隔离", "正式 Core Jar 的字节码、Unicode 资源和无平台媒体消费",
                ArtifactChecks::new)
    );
    private final List<Check> directory;

    public CoreGalleryChecks() {
        List<Check> entries = new ArrayList<>();
        for (Module module : modules) entries.add(module.check);
        directory = Collections.unmodifiableList(entries);
    }
    @Override public List<Check> checks() { return directory; }

    @Override public Result run(String id) {
        Module selected = null;
        for (Module module : modules) if (module.check.id.equals(id)) selected = module;
        if (selected == null) throw new IllegalArgumentException("未知核心检查：" + id);
        long begin = System.nanoTime();
        StringBuilder details = new StringBuilder(selected.check.title).append('\n');
        Throwable failure = null;
        for (Supplier<TestApplication> application : selected.applications) {
            TestApplication instance = application.get();
            try {
                instance.runReporting(result -> details.append(result.failure == null ? "通过 " : "失败 ")
                        .append(result.application).append('/').append(result.name)
                        .append(" · ").append(result.elapsedNanos / 1_000_000).append(" ms\n"));
            } catch (Throwable error) {
                StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
                details.append(instance.getClass().getSimpleName()).append('\n').append(trace);
                if (failure == null) failure = error; else if (failure != error) failure.addSuppressed(error);
            }
        }
        return new Result(id, System.nanoTime() - begin, details.toString(), failure);
    }
}
