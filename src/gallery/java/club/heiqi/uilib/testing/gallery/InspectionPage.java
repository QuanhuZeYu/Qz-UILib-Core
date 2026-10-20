package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.*;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.geometry.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.*;
import club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot;
import club.heiqi.uilib.ui.scene.inspection.SceneInspectionSnapshot.Entry;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.*;
import club.heiqi.uilib.ui.scene.runtime.*;
import club.heiqi.uilib.ui.scene.semantics.*;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.*;

/** 原场景的只读检查消费者；手动或F8更新，不观察面板自身触发连续准备。 */
public final class InspectionPage {
    private InspectionPage() {}

    public static SceneNode create(
            ScenePageContext page, ReadableSignal<SceneTheme> theme, Runnable back) {
        return new View(page, theme).build(back);
    }

    private static final class View {
        final ScenePageContext page;
        final ReadableSignal<SceneTheme> theme;
        final Signal<SceneInspectionSnapshot> snapshot;
        final Signal<SceneSemanticId> selected;
        final Signal<Boolean> recording, busy;
        final Signal<String> message;
        final SceneEditState draft;
        final Signal<Integer> changes;
        final SceneCommand refresh;
        SceneNode sample, field;
        boolean active;

        View(ScenePageContext page, ReadableSignal<SceneTheme> theme) {
            this.page = page;
            this.theme = theme;
            snapshot = page.domain().signal(null);
            selected = page.domain().signal(null);
            recording = page.domain().signal(false);
            busy = page.domain().signal(false);
            message = page.domain().signal("正在读取当前场景");
            changes = page.domain().signal(0);
            draft = new SceneEditState(page.domain(), "检查不会提交这份备注");
            refresh = SceneCommand.of(() -> active && !busy.get(), this::update);
        }

        SceneNode build(Runnable back) {
            SceneNode root = new SceneNode().size(Size.fill(1), Size.natural()).padding(28).gap(24);
            root.add(
                    GalleryStyle.header(
                            page, theme, "场景检查", "操作左侧样例，再按 F8 更新；当前焦点和拖动会继续保留。", back));
            SceneNode grid = GalleryStyle.grid(page, theme);
            grid.add(sample()).add(panel());
            root.add(grid);
            SceneNode scroll =
                    SceneScrollArea.create(page, root, () -> new SceneScrollArea.Options(), theme)
                            .node()
                            .size(Size.fill(1), Size.fill(1));
            scroll.bind(
                    NodeBinding.shortcuts(
                            SceneShortcut.Scope.PAGE, SceneShortcut.of(SceneKey.F8, refresh)));
            scroll.bind(
                    (runtime, node, owner) -> {
                        active = true;
                        page.inspectionRecording(recording);
                        update();
                        return new BindingActivation() {
                            public void refresh() {}

                            public void close() {
                                active = false;
                                busy.set(false);
                                page.inspectionRecording(() -> false);
                            }
                        };
                    });
            return scroll;
        }

        SceneNode sample() {
            sample =
                    GalleryStyle.card(theme)
                            .gap(16)
                            .bind(
                                    NodeBinding.semantics(
                                            () ->
                                                    SceneSemantics.builder(
                                                                    SceneSemantics.Role.GROUP)
                                                            .name("可操作场景")
                                                            .build()));
            sample.add(GalleryStyle.label(page, () -> "可操作场景", theme, 20, false));
            SceneNode label = GalleryStyle.label(page, () -> "任务备注", theme, 14, true);
            field =
                    SceneTextField.create(
                                    page, draft, theme, () -> false, s -> draft.accept(s, s.text))
                            .padding(0)
                            .bind(
                                    NodeBinding.semantics(
                                            () ->
                                                    SceneSemantics.builder()
                                                            .labelledBy(label)
                                                            .build()));
            sample.add(label).add(field);
            sample.add(
                    GalleryStyle.button(
                            page, () -> "更新样例文案", theme, () -> changes.set(changes.get() + 1)));
            sample.add(
                    SceneLabel.create(
                            page,
                            () -> changes.get() == 0 ? "样例尚未更新" : "文案已更新 " + changes.get() + " 次",
                            theme));
            SceneNode rotated =
                    new SceneNode()
                            .size(Size.fixed(280), Size.natural())
                            .padding(16)
                            .gap(12)
                            .transform(SceneTransform.rotationDegrees(6))
                            .bind(
                                    NodeBinding.semantics(
                                            () ->
                                                    SceneSemantics.builder(
                                                                    SceneSemantics.Role.GROUP)
                                                            .name("旋转内容")
                                                            .build()));
            for (int i = 1; i <= 8; i++) {
                final int item = i;
                rotated.add(SceneLabel.create(page, () -> "资源 " + item + " · 旋转与圆角裁剪", theme));
            }
            SceneNode clipped =
                    new SceneNode()
                            .size(Size.fill(1), Size.fixed(190))
                            .padding(12)
                            .scrollable(true)
                            .clip(SceneShape.roundRect(24))
                            .add(rotated)
                            .bind(NodeBinding.value(theme, (n, t) -> n.background(t.button)))
                            .bind(
                                    NodeBinding.semantics(
                                            () ->
                                                    SceneSemantics.builder(
                                                                    SceneSemantics.Role.GROUP)
                                                            .name("形状视口")
                                                            .build()));
            sample.add(clipped);
            SceneDraggable.State drag = SceneDraggable.state(page.domain());
            SceneNode badge =
                    GalleryStyle.frame(
                            page,
                            GalleryStyle.centeredRow()
                                    .size(Size.fixed(200), Size.fixed(64))
                                    .padding(12)
                                    .gap(12)
                                    .add(GalleryStyle.dragGrip(theme))
                                    .add(
                                            SceneLabel.create(page, () -> "拖动标记", theme)
                                                    .size(Size.fill(1), Size.natural())),
                            theme);
            sample.add(
                    SceneDraggable.create(
                                    page,
                                    badge,
                                    drag,
                                    new SceneDraggable.Options()
                                            .bounds(SceneDraggable.Bounds.PARENT))
                            .size(Size.fill(1), Size.fixed(100))
                            .text("拖动标记")
                            .bind(
                                    NodeBinding.semantics(
                                            () ->
                                                    SceneSemantics.builder(
                                                                    SceneSemantics.Role.GROUP)
                                                            .name("拖动标记")
                                                            .build())));
            sample.add(GalleryStyle.label(page, () -> "拖动时按 F8，可查看实际捕获与按键占用。", theme, 14, true));
            return GalleryStyle.frame(page, sample, theme);
        }

        SceneNode panel() {
            SceneNode panel = GalleryStyle.card(theme).gap(14);
            SceneNode actions =
                    GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(12);
            actions.add(
                    GalleryStyle.label(page, () -> "当前检查", theme, 20, false)
                            .size(Size.fill(1), Size.natural()));
            actions.add(
                    GalleryStyle.primary(
                            page, () -> busy.get() ? "读取中" : "更新检查 · F8", theme, refresh));
            panel.add(actions)
                    .add(
                            SceneSwitch.create(
                                    page, recording, () -> "记录准备原因", theme, recording::set));
            panel.add(GalleryStyle.label(page, message, theme, 14, true));
            SceneNode label = GalleryStyle.label(page, () -> "检查对象", theme, 14, true);
            panel.add(label)
                    .add(
                            SceneSelect.create(
                                            page,
                                            this::options,
                                            selected,
                                            selected::set,
                                            () -> "请选择当前对象",
                                            theme)
                                    .bind(
                                            NodeBinding.semantics(
                                                    () ->
                                                            SceneSemantics.builder()
                                                                    .labelledBy(label)
                                                                    .build())));
            panel.add(
                    new SceneNode()
                            .size(Size.fill(1), Size.fixed(170))
                            .control(new GeometryPreview(this))
                            .bind(
                                    NodeBinding.semantics(
                                            () ->
                                                    SceneSemantics.builder(
                                                                    SceneSemantics.Role.IMAGE)
                                                            .name("对象可见区域")
                                                            .description("浅色为呈现包络，强调色为同批精确可见区域。")
                                                            .build())));
            panel.add(GalleryStyle.label(page, () -> "布局与有效值", theme, 16, false));
            panel.add(SceneLabel.create(page, this::properties, theme));
            panel.add(GalleryStyle.label(page, () -> "输入归属", theme, 16, false));
            panel.add(SceneLabel.create(page, this::input, theme));
            panel.add(GalleryStyle.label(page, () -> "最近准备", theme, 16, false));
            panel.add(SceneLabel.create(page, this::preparation, theme));
            return GalleryStyle.frame(page, panel, theme);
        }

        void update() {
            if (!active || busy.get()) return;
            busy.set(true);
            page.inspect()
                    .whenComplete(
                            (value, error) ->
                                    page.domain()
                                            .execute(
                                                    () -> {
                                                        if (!active) return;
                                                        busy.set(false);
                                                        if (error != null) {
                                                            message.set(
                                                                    "读取失败：" + error.getMessage());
                                                            return;
                                                        }
                                                        snapshot.set(value);
                                                        if (value.status
                                                                == SceneInspectionSnapshot.Status
                                                                        .CLOSED) {
                                                            message.set("页面已结束");
                                                            return;
                                                        }
                                                        if (selected.get() == null)
                                                            for (Entry entry : value.entries)
                                                                if (entry.nodeId.equals(
                                                                        field.id())) {
                                                                    selected.set(entry.id);
                                                                    break;
                                                                }
                                                        message.set(
                                                                "已更新 · 第 "
                                                                        + value.generation
                                                                        + " 批 · "
                                                                        + value.entries.size()
                                                                        + " 个当前挂载");
                                                    }));
        }

        List<SceneSelect.Option<SceneSemanticId>> options() {
            SceneInspectionSnapshot value = snapshot.get();
            if (value == null) return Collections.emptyList();
            List<SceneSelect.Option<SceneSemanticId>> result = new ArrayList<>();
            for (Entry entry : value.entries)
                if (belongs(value, entry) && entry.name != null && !entry.name.isEmpty())
                    result.add(
                            new SceneSelect.Option<>(
                                    entry.id,
                                    () ->
                                            entry.name
                                                    + (entry.role == null
                                                            ? ""
                                                            : " · " + entry.role)));
            return result;
        }

        boolean belongs(SceneInspectionSnapshot value, Entry entry) {
            for (Entry at = entry; at != null; at = value.get(at.parent))
                if (at.nodeId.equals(sample.id())) return true;
            return false;
        }

        Entry current() {
            SceneInspectionSnapshot value = snapshot.get();
            return value == null ? null : value.get(selected.get());
        }

        String properties() {
            Entry e = current();
            if (e == null) return selected.get() == null ? "选择一个当前对象。" : "原挂载已结束，请选择当前对象。";
            String clip =
                    e.effectiveClip.unbounded()
                            ? "无界"
                            : e.effectiveClip.empty()
                                    ? "空"
                                    : e.effectiveClip.rectangular() ? "矩形" : "精确形状";
            SceneTransform t = e.transform;
            return e.name
                    + "\n布局盒 "
                    + box(e.layoutBox)
                    + "\n呈现包络 "
                    + box(e.worldBounds)
                    + "\n裁剪 "
                    + clip
                    + " · 可见面积 "
                    + number(e.visibleMesh.area())
                    + " px²"
                    + "\n变换 ["
                    + number(t.a)
                    + ", "
                    + number(t.c)
                    + ", "
                    + number(t.tx)
                    + "; "
                    + number(t.b)
                    + ", "
                    + number(t.d)
                    + ", "
                    + number(t.ty)
                    + "]"
                    + "\n字号 "
                    + number(e.style.logicalEm)
                    + " px · "
                    + source(e.styleSource)
                    + "\n内距 "
                    + e.padding
                    + " px · "
                    + source(e.paddingSource)
                    + "；间距 "
                    + e.gap
                    + " px · "
                    + source(e.gapSource)
                    + "\n滚动 "
                    + e.scrollOffset
                    + " / "
                    + e.scrollMaximum
                    + " px"
                    + (e.logicalItemCount < 0
                            ? ""
                            : "\n集合 "
                                    + e.instantiatedItemCount
                                    + " 个当前视图 / "
                                    + e.logicalItemCount
                                    + " 个逻辑项")
                    + "\n"
                    + (e.visible ? "显示" : "隐藏")
                    + " · "
                    + (e.enabled ? "启用" : "禁用")
                    + " · "
                    + (e.interactive ? "参与交互" : "不参与当前范围");
        }

        String input() {
            SceneInspectionSnapshot s = snapshot.get();
            if (s == null) return "等待当前场景";
            return "焦点 "
                    + name(s, s.focused)
                    + "\n悬停 "
                    + name(s, s.hovered)
                    + "\n按压 "
                    + name(s, s.pressed)
                    + "\n捕获 "
                    + name(s, s.captured)
                    + " · 按钮 "
                    + s.buttons
                    + "\n模态 "
                    + name(s, s.currentModal);
        }

        String preparation() {
            SceneInspectionSnapshot s = snapshot.get();
            if (s == null || !s.recording) return "开启记录，操作样例后按 F8 更新。";
            if (s.latest == null) return "尚无准备记录。";
            StringBuilder text =
                    new StringBuilder("第 ")
                            .append(s.latest.generation)
                            .append(" 批：测量 ")
                            .append(s.latest.measureCount)
                            .append("，排列 ")
                            .append(s.latest.arrangeCount)
                            .append("，绘制收集 ")
                            .append(s.latest.paintCollectCount);
            for (SceneInspectionSnapshot.Invalidation reason : s.latest.reasons)
                text.append('\n')
                        .append(reason.category)
                        .append(" · ")
                        .append(reason.id == null ? "页面" : name(s, reason.id));
            if (!s.pending.isEmpty()) text.append("\n待准备原因 ").append(s.pending.size());
            return text.toString();
        }
    }

    private static String name(SceneInspectionSnapshot value, SceneSemanticId id) {
        if (id == null) return "无";
        Entry e = value.get(id);
        return e == null ? "已结束挂载" : e.name == null || e.name.isEmpty() ? e.controlType : e.name;
    }

    private static String source(SceneInspectionSnapshot.Source source) {
        switch (source) {
            case AUTHOR:
                return "作者声明";
            case CONTROL_DEFAULT:
                return "控件默认";
            default:
                return "初始值";
        }
    }

    private static String box(LayoutBox b) {
        return b == null ? "未定位" : b.x + ", " + b.y + " · " + b.width + " × " + b.height + " px";
    }

    private static String number(double n) {
        return String.format(Locale.ROOT, "%.2f", n);
    }

    /** 独立图示只投影快照mesh；不重新解析裁剪或参与被检查对象的命中。 */
    private static final class GeometryPreview implements SceneControl {
        final View view;

        GeometryPreview(View view) {
            this.view = view;
        }

        public MeasuredSize measure(SceneNode node, Constraints c, SceneTextMeasurer text) {
            return new MeasuredSize(c.availableWidth, 170);
        }

        public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(
                SceneNode node,
                LayoutBox box,
                club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children) {
            return club.heiqi.uilib.ui.scene.layout.SceneFlowLayout.arrange(node, box, children);
        }

        public List<ReadableSignal<?>> paintDependencies(SceneNode node) {
            return Arrays.asList(view.snapshot, view.selected, view.theme);
        }

        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry own = geometry.get(node);
            if (own == null) return;
            paint.add(PaintCommand.rect(own.box, own.clip, view.theme.get().button));
            Entry selected = view.current();
            if (selected == null || selected.worldBounds == null) return;
            LayoutBox world = selected.worldBounds;
            if (world.width <= 0 || world.height <= 0) return;
            double scale =
                    Math.min(
                            (own.box.width - 24d) / world.width,
                            (own.box.height - 24d) / world.height);
            double x = own.box.x + (own.box.width - world.width * scale) / 2,
                    y = own.box.y + (own.box.height - world.height * scale) / 2;
            SceneTransform map =
                    SceneTransform.translation(x, y)
                            .multiply(SceneTransform.scale(scale, scale))
                            .multiply(SceneTransform.translation(-world.x, -world.y));
            paint.add(
                    PaintCommand.path(
                            SceneMesh.rectangle(world.x, world.y, world.width, world.height)
                                    .transformed(map),
                            own.clip,
                            view.theme.get().disabledText));
            paint.add(
                    PaintCommand.path(
                            selected.visibleMesh.transformed(map),
                            own.clip,
                            view.theme.get().focus));
        }
    }
}
