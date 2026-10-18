package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneScrollArea;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneVector;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.geometry.ScenePath;
import club.heiqi.uilib.ui.scene.geometry.SceneShape;
import club.heiqi.uilib.ui.scene.geometry.SceneStroke;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 路径与形状的真实页面；图示只排列原节点，曲线、裁剪和输入继续正式Scene管线。 */
public final class VectorPresentationPage {
    private static final double FULL_TURN = Math.PI * 2;
    private VectorPresentationPage() { }

    public static SceneNode create(ScenePageContext page, ReadableSignal<SceneTheme> theme, Runnable back) {
        Owner owner = page.scope().child();
        Signal<Integer> progress = page.domain().signal(25);
        Signal<String> task = page.domain().signal("待开始");
        SceneNode content = new SceneNode().size(Size.fill(1), Size.natural()).padding(28).gap(24).own(owner);
        content.add(GalleryStyle.header(page, theme, "路径与形状", "推进任务、选择扇区，再试试旋转与形状中的编辑。", back));
        SceneNode examples = GalleryStyle.grid(page, theme);
        examples.add(progress(page, theme, owner, progress, task));
        examples.add(radial(page, theme, progress, task));
        examples.add(rotation(page, theme, owner));
        examples.add(viewport(page, theme, owner));
        content.add(examples);
        return SceneScrollArea.create(page, content, () -> new SceneScrollArea.Options(), theme).node()
                .size(Size.fill(1), Size.fill(1));
    }

    private static SceneNode progress(ScenePageContext page, ReadableSignal<SceneTheme> theme, Owner owner, Signal<Integer> progress, Signal<String> task) {
        SceneNode card = section(page, theme, "环形进度");
        Diagram diagram = new Diagram(240, 240);
        ScenePath ring = ScenePath.builder().rule(ScenePath.FillRule.EVEN_ODD)
                .arc(120, 120, 106, 0, FULL_TURN).close().moveTo(202, 120)
                .arc(120, 120, 82, 0, FULL_TURN).close().build();
        diagram.add(SceneVector.create(page, () -> new SceneVector.Model(ring, 240, 240, theme.get().button)).size(Size.fill(1), Size.fill(1))
                .bind(NodeBinding.semantics(() -> SceneSemantics.builder().excluded(true).build())), 0, 0, 240, 240);
        ReadableSignal<ScenePath> arc = page.domain().computed(owner, () -> progress.get() == 0 ? ScenePath.builder().build()
                : ScenePath.builder().arc(120, 120, 94, -Math.PI / 2, FULL_TURN * progress.get() / 100).build());
        diagram.add(SceneVector.create(page, () -> new SceneVector.Model(arc.get(), 240, 240, null, theme.get().focus,
                new SceneStroke(24, SceneStroke.Cap.ROUND, SceneStroke.Join.ROUND, 10))).text("任务进度").size(Size.fill(1), Size.fill(1))
                .bind(NodeBinding.semantics(() -> SceneSemantics.builder().value(progress.get() + "%").build())), 0, 0, 240, 240);
        diagram.add(GalleryStyle.label(page, () -> progress.get() + "%", theme, 36, false), 48, 90, 144, 48);
        diagram.add(GalleryStyle.label(page, () -> "已完成", theme, 16, true), 60, 140, 120, 28);
        card.add(diagram.node(theme, 15));
        SceneCommand advance = SceneCommand.of(() -> progress.get() < 100, () -> {
            progress.set(Math.min(100, progress.get() + 25)); task.set(progress.get() == 100 ? "已完成" : "进行中");
        });
        card.add(actions(theme).add(GalleryStyle.primary(page, () -> "推进进度", theme, advance::tryExecute)
                        .size(Size.fill(1), Size.natural()).bind(NodeBinding.value(advance.executable(), SceneNode::enabled)))
                .add(GalleryStyle.button(page, () -> "重置进度", theme, () -> { progress.set(0); task.set("待开始"); })
                        .size(Size.fill(1), Size.natural())));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode radial(ScenePageContext page, ReadableSignal<SceneTheme> theme, Signal<Integer> progress, Signal<String> task) {
        SceneNode card = section(page, theme, "扇区动作");
        Signal<Integer> active = page.domain().signal(-1);
        String[] labels = {"开始", "暂停", "完成", "重置"};
        String[] names = {"开始任务", "暂停任务", "完成任务", "重置任务"};
        Runnable[] changes = {
                () -> { task.set("进行中"); if (progress.get() == 0) progress.set(25); },
                () -> task.set("已暂停"),
                () -> { task.set("已完成"); progress.set(100); },
                () -> { task.set("待开始"); progress.set(0); }
        };
        Diagram diagram = new Diagram(240, 240);
        for (int index = 0; index < labels.length; index++) {
            final int choice = index;
            double angle = -Math.PI / 2 + index * Math.PI / 2;
            ScenePath path = sector(angle - Math.PI / 4 + .045, Math.PI / 2 - .09);
            Signal<Boolean> hovered = page.domain().signal(false);
            SceneNode[] target = new SceneNode[1];
            SceneCommand command = SceneCommand.of(() -> true, () -> { active.set(choice); changes[choice].run(); });
            target[0] = SceneVector.create(page, () -> {
                SceneTheme value = theme.get();
                SceneAppearance.Palette colors = new SceneAppearance.Palette(value);
                boolean focused = page.runtime().focusRouter().focused().get() == target[0];
                int fill = active.get() == choice ? colors.selected : hovered.get() ? value.hover : value.button;
                return new SceneVector.Model(path, 240, 240, fill, focused ? value.focus : colors.border,
                        new SceneStroke(focused ? 3 : 1, SceneStroke.Cap.BUTT, SceneStroke.Join.ROUND, 10));
            }, command::tryExecute).text(names[choice]).size(Size.fill(1), Size.fill(1))
                    .bind(NodeBinding.hoverWithin(0, (self, value) -> hovered.set(value)));
            diagram.add(target[0], 0, 0, 240, 240);
            double x = 120 + Math.cos(angle) * 78, y = 120 + Math.sin(angle) * 78;
            diagram.add(GalleryStyle.label(page, () -> labels[choice], theme, 18, false), x - 30, y - 16, 60, 32);
        }
        diagram.add(GalleryStyle.label(page, () -> "任务", theme, 18, true), 84, 100, 72, 40);
        card.add(diagram.node(theme, 15));
        card.add(GalleryStyle.label(page, () -> "任务状态：" + task.get(), theme, 18, false));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode rotation(ScenePageContext page, ReadableSignal<SceneTheme> theme, Owner owner) {
        SceneNode card = section(page, theme, "旋转标记");
        Signal<Double> angle = page.domain().signal(0d);
        ReadableSignal<Integer> extent = page.domain().computed(owner, () -> Math.round(theme.get().textStyle.logicalEm * 11));
        Diagram diagram = new Diagram(240, 240);
        ScenePath orbit = ScenePath.ellipse(120, 120, 94, 94);
        diagram.add(SceneVector.create(page, () -> new SceneVector.Model(orbit, 240, 240, null, theme.get().button, new SceneStroke(2)))
                .size(Size.fill(1), Size.fill(1))
                .bind(NodeBinding.semantics(() -> SceneSemantics.builder().excluded(true).build())), 0, 0, 240, 240);
        ScenePath pointer = ScenePath.builder().moveTo(100, 18).lineTo(153, 165)
                .quadTo(100, 138, 47, 165).close().build();
        SceneNode marker = SceneVector.create(page, () -> new SceneVector.Model(pointer, 200, 200,
                theme.get().focus, theme.get().focus, new SceneStroke(2, SceneStroke.Cap.ROUND, SceneStroke.Join.ROUND, 10)))
                .text("旋转标记");
        marker.bind(NodeBinding.value(extent, (self, pixels) -> self.size(Size.fixed(pixels), Size.fixed(pixels))));
        marker.bind(NodeBinding.value(() -> SceneTransform.around(extent.get() / 2d, extent.get() / 2d,
                SceneTransform.rotationDegrees(angle.get())), SceneNode::transform));
        marker.bind(NodeBinding.semantics(() -> SceneSemantics.builder().value(Math.round(angle.get()) + "°").build()));
        diagram.add(marker, 0, 0, 240, 240);
        card.add(diagram.node(theme, 15));
        SceneNode label = GalleryStyle.label(page, () -> "标记角度", theme, 16, false);
        card.add(label).add(GalleryStyle.label(page, () -> Math.round(angle.get()) + "°", theme, 22, false));
        card.add(SceneSlider.create(page, () -> new SceneSlider.Model(0, 360, 15, angle.get(), true), angle::set, angle::set, theme)
                .bind(NodeBinding.semantics(() -> SceneSemantics.builder().labelledBy(label).build())));
        card.add(actions(theme).add(GalleryStyle.button(page, () -> "旋转30°", theme, () -> angle.set((angle.get() + 30) % 360))
                        .size(Size.fill(1), Size.natural()))
                .add(GalleryStyle.button(page, () -> "标记归零", theme, () -> angle.set(0d)).size(Size.fill(1), Size.natural())));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode viewport(ScenePageContext page, ReadableSignal<SceneTheme> theme, Owner owner) {
        SceneNode card = section(page, theme, "形状视口");
        Signal<Double> angle = page.domain().signal(0d), scale = page.domain().signal(1d);
        Signal<Boolean> ellipse = page.domain().signal(true);
        Signal<String> saved = page.domain().signal("尚未保存备注");
        SceneEditState draft = new SceneEditState(page.domain(), "在形状里编辑");
        Consumer<SceneEditState.Submission> save = request -> {
            String value = request.text.trim();
            if (value.isEmpty()) { draft.reject(request, "请输入备注"); saved.set("请输入备注"); return; }
            draft.accept(request, value); saved.set("已保存：" + value);
        };
        SceneCommand saveCommand = SceneCommand.of(() -> !draft.snapshot().get().text.trim().isEmpty(), () -> save.accept(draft.request(true)));
        ReadableSignal<Integer> width = page.domain().computed(owner, () -> Math.round(theme.get().textStyle.logicalEm * 18));
        ReadableSignal<Integer> height = page.domain().computed(owner, () -> Math.round(theme.get().textStyle.logicalEm * 13));
        SceneNode form = new SceneNode().size(Size.natural(), Size.natural()).gap(8);
        form.bind(NodeBinding.value(() -> theme.get().textStyle.logicalEm, (self, em) -> {
            self.size(Size.fixed(width.get()), Size.fixed(height.get())).padding(Math.round(em * 2.4f));
        }));
        form.bind(NodeBinding.value(() -> new SceneAppearance.Palette(theme.get()).selected, SceneNode::background));
        form.bind(NodeBinding.value(() -> ellipse.get() ? SceneShape.ellipse() : SceneShape.roundRect(theme.get().textStyle.logicalEm * 1.5), SceneNode::clip));
        form.bind(NodeBinding.value(() -> SceneTransform.around(width.get() / 2d, height.get() / 2d,
                SceneTransform.rotationDegrees(angle.get()).multiply(SceneTransform.scale(scale.get(), scale.get()))), SceneNode::transform));
        SceneNode label = GalleryStyle.label(page, () -> "视口备注", theme, 16, false);
        form.add(label).add(SceneTextField.create(page, draft, theme, () -> false, save)
                .bind(NodeBinding.semantics(() -> SceneSemantics.builder().labelledBy(label).build())));
        form.add(GalleryStyle.primary(page, () -> "保存备注", theme, saveCommand::tryExecute)
                .bind(NodeBinding.value(saveCommand.executable(), SceneNode::enabled)));
        Diagram diagram = new Diagram(360, 300);
        diagram.add(form, 0, 0, 360, 300);
        card.add(diagram.node(theme, 18));
        card.add(GalleryStyle.label(page, () -> Math.round(angle.get()) + "° · " + Math.round(scale.get() * 100)
                + "% · " + (ellipse.get() ? "椭圆裁剪" : "圆角裁剪"), theme, 16, true));
        card.add(actions(theme).add(GalleryStyle.button(page, () -> "旋转视口", theme,
                        () -> angle.set(angle.get() == 0 ? 15d : angle.get() > 0 ? -15d : 0d)).size(Size.fill(1), Size.natural()))
                .add(GalleryStyle.button(page, () -> "缩放视口", theme, () -> scale.set(scale.get() == 1 ? .8 : 1d))
                        .size(Size.fill(1), Size.natural())));
        card.add(actions(theme).add(GalleryStyle.button(page, () -> "切换裁剪", theme, () -> ellipse.set(!ellipse.get()))
                        .size(Size.fill(1), Size.natural()))
                .add(GalleryStyle.button(page, () -> "恢复视口", theme, () -> { angle.set(0d); scale.set(1d); ellipse.set(true); })
                        .size(Size.fill(1), Size.natural())));
        card.add(GalleryStyle.label(page, saved, theme, 16, false));
        return GalleryStyle.frame(card, theme);
    }

    private static ScenePath sector(double start, double sweep) {
        double end = start + sweep;
        return ScenePath.builder().arc(120, 120, 112, start, sweep)
                .lineTo(120 + Math.cos(end) * 46, 120 + Math.sin(end) * 46)
                .arc(120, 120, 46, end, -sweep).close().build();
    }
    private static SceneNode section(ScenePageContext page, ReadableSignal<SceneTheme> theme, String title) {
        return GalleryStyle.card(theme).gap(16).add(GalleryStyle.label(page, () -> title, theme, 22, false));
    }
    private static SceneNode actions(ReadableSignal<SceneTheme> theme) {
        return GalleryStyle.centeredRow().size(Size.fill(1), Size.natural())
                .bind(NodeBinding.value(() -> theme.get().gap, SceneNode::gap));
    }

    /** 图示的每个形状、标签和字段仍是直属节点；这里只按本批分配居中叠放，不绘制或处理输入。 */
    private static final class Diagram implements SceneControl {
        private static final class Part {
            final SceneNode node; final double x, y, width, height;
            Part(SceneNode node, double x, double y, double width, double height) {
                this.node = node; this.x = x; this.y = y; this.width = width; this.height = height;
            }
        }
        private final double width, height;
        private final List<Part> parts = new ArrayList<>();
        Diagram(double width, double height) { this.width = width; this.height = height; }
        void add(SceneNode node, double x, double y, double width, double height) {
            parts.add(new Part(node, x, y, width, height));
        }
        SceneNode node(ReadableSignal<SceneTheme> theme, int heightEm) {
            SceneNode result = new SceneNode().size(Size.fill(1), Size.natural()).control(this);
            result.bind(NodeBinding.value(() -> Math.round(theme.get().textStyle.logicalEm * heightEm),
                    (self, pixels) -> self.size(Size.fill(1), Size.fixed(pixels))));
            for (Part part : parts) result.add(part.node);
            return result;
        }
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            return new MeasuredSize((int) Math.ceil(width), (int) Math.ceil(height));
        }
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            for (Part part : parts) children.measureChild(part.node, limits);
            return measure(node, limits, text);
        }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            int padding = node.padding(), availableWidth = Math.max(0, box.width - padding * 2), availableHeight = Math.max(0, box.height - padding * 2);
            double scale = Math.min(availableWidth / width, availableHeight / height);
            double left = padding + (availableWidth - width * scale) / 2, top = padding + (availableHeight - height * scale) / 2;
            List<ChildPlacements.Entry> entries = new ArrayList<>();
            for (Part part : parts) {
                int slotWidth = (int) Math.round(part.width * scale), slotHeight = (int) Math.round(part.height * scale);
                MeasuredSize measured = children.measureChild(part.node, new Constraints(slotWidth, slotHeight));
                int w = resolve(children.sizeIntent(part.node, SceneNode.Axis.ROW), slotWidth, measured.width);
                int h = resolve(children.sizeIntent(part.node, SceneNode.Axis.COLUMN), slotHeight, measured.height);
                entries.add(new ChildPlacements.Entry(part.node, new LayoutBox((int) Math.round(left + part.x * scale + (slotWidth - w) / 2d),
                        (int) Math.round(top + part.y * scale + (slotHeight - h) / 2d), w, h)));
            }
            return new ChildPlacements(entries);
        }
        private static int resolve(Size intent, int available, int natural) {
            return intent.constrain(intent.mode == Size.Mode.FIXED ? intent.value : intent.mode == Size.Mode.FILL ? available : natural);
        }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) { }
    }
}
