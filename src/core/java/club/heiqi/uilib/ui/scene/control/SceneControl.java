package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.Collections;
import java.util.List;

/** 声明控件内容与区域；测量、排列和绘制使用框架只读阶段，业务写入归输入与命令阶段。 */
public interface SceneControl {
    /** 读取现有有限集合计数；-1 表示普通内容，检查复用当前实例。 */
    default int inspectionLogicalItemCount(SceneNode node) { return -1; }
    default int inspectionInstantiatedItemCount(SceneNode node) { return -1; }
    /** 实际图形可声明自己的新命中轮廓；null继续普通分配盒。由共同几何按本批尺寸和像素精度解析。 */
    default club.heiqi.uilib.ui.scene.geometry.SceneShape hitShape(SceneNode node) { return null; }

    /** 只读语义随实际Signal依赖更新；null透传普通布局孩子。 */
    default club.heiqi.uilib.ui.scene.semantics.SceneSemantics semantics(SceneNode node) { return null; }

    /** 定向语义动作只交给已解析的当前控件；激活/焦点由原runtime统一处理。 */
    default boolean semanticAction(SceneNode node, club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest request) { return false; }

    /** 为按需复合内容提供逻辑遍历，null 使用普通声明树前序。 */
    default club.heiqi.uilib.ui.scene.input.SceneFocusTraversal focusTraversal(SceneNode node) { return null; }
    /** null 表示普通流；所属层策略不得返回 null。 */
    default SceneLayer.Policy layerPolicy(SceneNode node) { return null; }

    /** 所属层只声明位置；null 沿默认锚定，尺寸仍由正式测量决定，裁剪与范围仍归核心。 */
    default LayoutBox placeLayer(SceneNode node, LayoutBox sourceBox, LayoutBox viewport, MeasuredSize size) {
        return null;
    }
    /** 变换来源的正式当批事实；旧定位端口继续消费world包络。 */
    default LayoutBox placeLayer(SceneNode node, SceneGeometryView.Entry source, LayoutBox viewport, MeasuredSize size) {
        return placeLayer(node, source.box, viewport, size);
    }

    /** 当前有效 owner 在主键释放前更新位置意图，框架结算并准备新几何后再派 UP。
     * 默认保留原位置；放下与激活由随后 UP 处理。 */
    default void preparePointerRelease(SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event, SceneGeometryView geometry) {}

    /** 声明只读输入时间需求，MAX 表示静止；挂载订阅跟踪实际读取的 Signal。 */
    default long nextInputNanos(SceneNode node) { return Long.MAX_VALUE; }

    /** 在有效几何后、绘制前推进交互呈现，每宿主批次至多一次。 */
    default void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {}

    /** 本批活动输入使共同几何改变后同步交互呈现；可随重排结算多次。
     * 仅同步新几何，时间推进、激活等待与滚动由 inputFrame 负责。 */
    default void inputGeometryChanged(SceneNode node, SceneGeometryView geometry) {}

    /** 本批活动输入或后续 Effect 失败时，只结束本批实际参与控件的临时输入状态。
     * 此处清理活动输入临态，指针捕获由 captureLost 单独清理，业务提交仅归正常结束路径。 */
    default void inputFrameCancelled(SceneNode node) {}

    /** 捕获起手控件可明确把本段延续到自己打开的所属模态；null保持普通当前范围资格。 */
    default SceneNode capturedPointerScope(SceneNode node) { return null; }

    /** 只有显式声明的子 activator 可把其命中交给祖先；普通子按钮仍优先。 */
    default Object pointerActivator(SceneNode node, SceneNode hit, SceneGeometryView geometry, int x, int y) {
        return null;
    }

    /** 当前范围的显式NEXT/PREVIOUS可被消费；默认与返回选焦不调用，旧范围保持原导航。 */
    default boolean navigateScope(SceneNode node, boolean backward) { return false; }

    /** 将键盘命令委派给明确声明的子 activator。 */
    default boolean editActivator(SceneNode node, SceneNode focused,
            club.heiqi.uilib.ui.scene.input.SceneEditEvent event) { return false; }
    /** 声明测量使用的响应来源；框架在挂载期订阅，变更重新测量。 */
    default List<ReadableSignal<?>> measureDependencies(SceneNode node) {
        return Collections.emptyList();
    }

    /** 仅定位或视口变化：复用内容度量，在共同几何中更新排列与命中。 */
    default List<ReadableSignal<?>> arrangementDependencies(SceneNode node) {
        return Collections.emptyList();
    }

    /** 声明仅影响绘制的来源，内容度量与几何继续复用。 */
    default List<ReadableSignal<?>> paintDependencies(SceneNode node) {
        return Collections.emptyList();
    }

    /** 只读的下一次绘制时刻（System.nanoTime同一时基）；有限过渡结束返回MAX_VALUE。
     * 绘制阶段只读呈现，时刻到达仅触发绘制失效；默认复用静态计划。 */
    default long nextPaintNanos(SceneNode node) { return Long.MAX_VALUE; }

    /** 将控件文本请求交给共同字体服务。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout layoutText(
            SceneNode node, SceneTextMeasurer text, int width) {
        return text.layout(node.text(), node.style(), width, node.textPolicy());
    }

    /** 为复合文本提供同批直属子测量，文字服务继续处理纯文字请求。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout layoutText(
            SceneNode node, SceneTextMeasurer text, int width, SceneChildMeasurer children) {
        return layoutText(node, text, width);
    }

    /** 基于当前 Entry 查询内部目标身份，输入阶段消费已准备几何。 */
    default Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
        return null;
    }

    default boolean pointerTargetValid(SceneNode node, Object target) {
        return false;
    }

    /** 唯一 router 已确认同目标、同挂载后由 runtime 分发。 */
    default void activatePointer(SceneNode node, Object target) {}

    /** constraints 是框架已扣除 node.padding 的正文约束，返回正文自然尺寸。 */
    MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text);

    /** 布局引擎为组合控件提供子测量能力；既有纯文字控件沿原入口执行。 */
    default MeasuredSize measure(
            SceneNode node,
            Constraints constraints,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        return measure(node, constraints, text);
    }

    /**
     * allocated 是本次分配的外层盒，包含 node.padding；子盒位置相对此盒原点。
     * children 只测量直属逻辑子项，限本次调用使用。
     * 普通容器在实际正文空间中完成测量与排列。
     */
    ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children);

    /** 框架同批父分配与根视口，供明确的空间约束使用；既有控件沿原排列入口。
     * 分配、视口与 children 均限本次调用使用。 */
    default ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children,
            LayoutBox parentAllocation, LayoutBox viewport) { return arrange(node, allocated, children); }

    /** 本批分配盒原点处的只读呈现变换，与作者变换同序组合并进入共同几何。
     * 只读消费当前分配盒，动态来源沿 measure/arrangementDependencies 失效。 */
    default club.heiqi.uilib.ui.scene.geometry.SceneTransform presentationTransform(SceneNode node, LayoutBox allocated) {
        return club.heiqi.uilib.ui.scene.geometry.SceneTransform.IDENTITY;
    }

    /** 将完整内容投影为实际文字区域内的不可变定位，原文本与几何保持。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout presentText(
            SceneNode node,
            club.heiqi.uilib.ui.scene.text.TextLayout full,
            int width,
            int height,
            SceneTextMeasurer text) {
        return full;
    }

    /** 真实文字/文档/数学内容投影使用同批子测量；普通容器的排列由 arrange 承担。 */
    default club.heiqi.uilib.ui.scene.text.TextLayout presentText(
            SceneNode node,
            club.heiqi.uilib.ui.scene.text.TextLayout full,
            int width,
            int height,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        return presentText(node, full, width, height, text);
    }

    void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target);

    /** 只控制后代绘制，布局、挂载及输入事实保持；例如排序来源的不可见插槽。 */
    default boolean paintChildren(SceneNode node) { return true; }

    /** 本节点当前有效几何子树结束后、下一兄弟内容之前的局部装饰。 与paint共用同代几何和失效生命周期；全页tooltip仍由paintOverlay处理。 */
    default void paintAfterChildren(
            SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}

    /** 只读呈现准备；仅在现有paint失效路径调用，动态文字缓存消费当前主题/资源/viewport。 */
    default void preparePaint(
            SceneNode node,
            SceneGeometryView geometry,
            SceneTextMeasurer text,
            long metricsRevision) {}

    /** 返回已挂载的直属占位子树，框架沿当前挂载捕获同代 paint 作为资源备用分支。
     * 主输入几何继续服从原显隐声明。 */
    default SceneNode paintFallbackChild(SceneNode node) {
        return null;
    }

    /** fallbackPaint 为同一布局与 paint 能力捕获的不可变子片段，绘制仅消费其中值载荷。 */
    default void paint(
            SceneNode node,
            SceneGeometryView geometry,
            PaintPlanBuilder target,
            club.heiqi.uilib.ui.scene.paint.PaintPlan fallbackPaint) {
        paint(node, geometry, target);
    }

    /** 当前焦点接收中性编辑事件，返回本次消费结果。 */
    default boolean edit(SceneNode node, club.heiqi.uilib.ui.scene.input.SceneEditEvent event) {
        return false;
    }

    /** 键盘几何与指针消费同一事实；既有控件保持原 edit 接口。 */
    default boolean edit(SceneNode node, club.heiqi.uilib.ui.scene.input.SceneEditEvent event,
            SceneGeometryView geometry) { return edit(node, event); }

    /** 原目标与 activator 让渡普通命令后，由所属复合行为接续；带来源组字仍交原联系。 */
    default boolean editFallback(SceneNode node, SceneNode focused, club.heiqi.uilib.ui.scene.input.SceneEditEvent event,
            SceneGeometryView geometry) { return false; }

    /** 可选的本次命令续段许可；仅实际消费者取得，默认仍锁定原编辑焦点。 */
    default club.heiqi.uilib.ui.scene.input.SceneEditContinuation editContinuation(SceneNode node, SceneNode focused,
            club.heiqi.uilib.ui.scene.input.SceneEditEvent event) { return null; }

    /** 只有需要DOWN定位的控件申请；普通按钮仍在UP聚焦。 */
    default boolean capturesPointer() {
        return false;
    }

    /** 是否让普通滚轮推动节点的轴向滚动位置；复合双轴视口可指定主滚轮轴。
     * 像素滚动、导航显露及范围计算不受影响，未消费事件仍由唯一路由交给祖先。 */
    default boolean scrollsWithWheel(SceneNode node) { return true; }

    /** 声明内部内容视口参与滚轮命中，外层布局盒保留原容器语义。 */
    default boolean scrollableContent() {
        return false;
    }

    /** 专用内容公开的滚动轴；标准视口默认包含条，页面无需另加节点。 */
    default SceneScrollArea.Axes scrollbarAxes(SceneNode node) { return SceneScrollArea.Axes.NONE; }

    /** 在完整内容几何后定位框架装饰，宿主测量与范围沿用原内容。 */
    default LayoutBox placeScrollDecoration(SceneNode node, SceneGeometryView geometry) { return null; }

    /** 专用内容视口的同批事实；缺省null由普通Node滚动适配。位置仍归控件。 */
    default SceneScrollViewport.Snapshot scrollViewport(SceneNode node, SceneNode.Axis axis, SceneGeometryView geometry) { return null; }

    /** 专用内容像素移动；返回实际位移，runtime统一按变化失效共同几何。 */
    default int scrollByPixels(SceneNode node, SceneNode.Axis axis, int pixels, SceneGeometryView geometry) { return 0; }

    /** 绝对像素请求在原所有者生效后的通知；估算内容可保留本次末端显露意图。
     * 位置与输入仍由原所有者维护，默认保持原像素移动结果。 */
    default void scrollPositionRequested(SceneNode node, SceneNode.Axis axis, int requested, SceneScrollViewport.Snapshot previous) {}

    /** 代理滚动控件明确其实际视口，用于显式释放运动；输入所有权仍留原消费者。 */
    default SceneNode scrollMotionViewport(SceneNode node) { return node; }

    /** 正式结果区分消费与变化；旧扩展的boolean按既有“消费即变化”语义接入。 */
    default SceneScrollResult scrollResult(SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event, SceneGeometryView geometry) {
        return scroll(node, event, geometry) ? SceneScrollResult.CHANGED : SceneScrollResult.IGNORED;
    }

    /** 在同一受控输入中处理滚轮，消费后结束向外层传播。 */
    default boolean scroll(
            SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event,
            SceneGeometryView geometry) {
        return false;
    }

    /**
     * 主动 Tab 导航先调整控件内部显露，编辑提交由编辑合同处理。
     * 返回 true 时框架重新排列后查询 navigationRegion；默认保留内部视口。
     */
    default boolean prepareNavigation(SceneNode node, SceneGeometryView geometry) {
        return false;
    }

    /**
     * Tab显露的内部焦点/编辑区域，使用当前Entry对应的scene坐标；缺省null沿节点整盒。
     * 仅在节点大于祖先视口的轴优先使用，只读返回内部视口已协调的区域。
     * 祖先裁剪与必要滚动由框架统一处理。
     */
    default LayoutBox navigationRegion(SceneNode node, SceneGeometryView geometry) {
        return null;
    }

    /** 当前只读/可编辑事实由控件声明，宿主据此启停真实文本输入。 */
    default boolean acceptsTextInput(SceneNode node) {
        return false;
    }

    /**
     * 唯一 router 取消原 owner 时的内部清理；目标可能已经隐藏、禁用或卸载。
     * 按原联系独立于几何清理内部状态，业务激活与提交归各自正常输入路径。
     */
    default void captureLost(SceneNode node) {}

    /** 当前捕获明确参与 ESC 取消；只读查询，真正终止仍归唯一 router。 */
    default boolean cancelCaptureOnEscape(SceneNode node) { return false; }

    /** 指针定位/拖选沿已有owner，区域来自本次生产布局。 */
    default void pointer(
            SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event,
            SceneGeometryView geometry) {}

    /** 区分正常焦点离开与失活，提交策略仅在合法离开路径处理。 */
    default void focusChanged(SceneNode node, boolean focused, boolean eligible) {}

    /** 声明仅供被动提示使用的悬停资格。 */
    default boolean passiveHover() {
        return false;
    }

    /** 自动提示只声明当前事实；显式绑定优先，由页面协调统一触发与呈现。 */
    default SceneTooltip.Hint tooltip(SceneNode node,SceneGeometryView geometry){return null;}

    /** 在内容末端输出被动提示绘制值。 */
    default void paintOverlay(
            SceneNode node,
            SceneGeometryView geometry,
            PaintPlanBuilder target,
            SceneTextMeasurer text) {}
}
