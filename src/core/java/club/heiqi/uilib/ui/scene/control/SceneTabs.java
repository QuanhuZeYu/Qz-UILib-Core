package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 只创建当前面板，标签条共享选择导航；业务状态不归属于面板挂载。 */
public final class SceneTabs<K> {
    public static final class Tab<K> {
        public final K key;
        public final ReadableSignal<String> title;
        public final ReadableSignal<Boolean> enabled;
        public final Supplier<SceneNode> content;
        public Tab(K key, ReadableSignal<String> title, ReadableSignal<Boolean> enabled, Supplier<SceneNode> content) {
            this.key = Objects.requireNonNull(key, "标签键"); this.title = Objects.requireNonNull(title, "标题来源");
            this.enabled = Objects.requireNonNull(enabled, "可用来源"); this.content = Objects.requireNonNull(content, "内容工厂");
        }
        public Tab(K key, ReadableSignal<String> title, Supplier<SceneNode> content) { this(key, title, () -> true, content); }
    }
    private final SceneNode node;
    private final SceneScrollViewport headerViewport;
    private SceneTabs(ScenePageContext page, ReadableSignal<List<Tab<K>>> source, ReadableSignal<K> selected,
            Consumer<K> change, ReadableSignal<SceneTheme> theme) {
        Owner owner = page.scope().child();
        ReadableSignal<SceneControlMetrics> metrics = page.domain().computed(owner, () -> theme.get().metrics(SceneControlMetrics.Kind.TABS));
        ReadableSignal<List<Tab<K>>> tabs = page.domain().computed(owner, () -> {
            List<Tab<K>> result = new ArrayList<>(Objects.requireNonNull(source.get(), "标签来源")); Set<K> keys = new HashSet<>();
            for (Tab<K> tab : result) if (!keys.add(Objects.requireNonNull(tab, "标签声明").key)) throw new IllegalArgumentException("标签键重复: " + tab.key);
            return Collections.unmodifiableList(result);
        });
        Map<K, SceneNode> targets = new HashMap<>();
        SceneChoiceNavigation<Tab<K>, K> navigation = new SceneChoiceNavigation<>(page, tabs, tab -> tab.key,
                tab -> tab.enabled.get(), targets, selected, change, false);
        SceneNode labels = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.natural(), Size.natural());
        labels.bind(NodeBinding.value(() -> metrics.get().gap, SceneNode::defaultGap));
        labels.bind(NodeBinding.repeat(tabs, tab -> tab.key, current -> {
            K key = current.get().key;
            SceneNode button = SceneButton.create(page, () -> current.get().title.get(), theme, () -> navigation.select(key), SceneTextPolicy.ELLIPSIS_SINGLE_LINE)
                    .size(Size.natural(), Size.natural());
            button.bind(NodeBinding.value(metrics, (self, value) -> self.defaultStyle(value.textStyle)
                    .defaultPadding(value.padding).defaultGap(value.iconGap)));
            button.bind(NodeBinding.value(() -> current.get().enabled.get(), SceneNode::enabled));
            button.bind(NodeBinding.semantics(() -> SceneSemantics.builder(SceneSemantics.Role.TAB)
                    .name(current.get().title.get()).selected(Objects.equals(selected.get(), key))
                    .position(tabs.get().indexOf(current.get()) + 1, tabs.get().size())
                    .actions(SceneSemantics.Action.FOCUS, SceneSemantics.Action.ACTIVATE, SceneSemantics.Action.SELECT).build()));
            targets.put(key, button); button.own(() -> targets.remove(key, button));
            return new SceneNode().size(Size.natural(), Size.natural())
                    .control(new Label(button, theme, () -> Objects.equals(selected.get(), key))).add(button);
        }));
        SceneScrollArea.Area scroll = SceneScrollArea.create(page, labels,
                () -> new SceneScrollArea.Options().axes(SceneScrollArea.Axes.HORIZONTAL), theme);
        scroll.node().size(Size.fill(1), Size.natural());
        headerViewport = SceneScrollViewport.of(page, scroll.viewport(SceneNode.Axis.ROW), SceneNode.Axis.ROW);
        // 显式横条也属于这个逻辑停靠边界；不能在条与内容之间插入新的Tab目标。
        SceneNode bar = new SceneNode().size(Size.fill(1), Size.natural()).control(navigation).add(scroll.node());
        bar.bind(NodeBinding.semantics(() -> SceneSemantics.builder(SceneSemantics.Role.TAB_LIST)
                .count(tabs.get().size()).actions().build()));
        ReadableSignal<List<Tab<K>>> active = page.domain().computed(owner, () -> {
            K key = selected.get();
            for (Tab<K> tab : tabs.get()) if (Objects.equals(key, tab.key)) return Collections.singletonList(tab);
            return Collections.emptyList();
        });
        SceneNode panel = new SceneNode().size(Size.fill(1), Size.fill(1));
        // 与前端key一致：新的声明对象和普通数据更新不重置同一活动面板，重置由新key明确表达。
        panel.bind(NodeBinding.repeat(active, tab -> tab.key, current -> Objects.requireNonNull(current.get().content.get(), "标签内容产物")));
        panel.bind(NodeBinding.semantics(() -> {
            page.runtime().interactionRevision().get();
            if (active.get().isEmpty()) return null;
            SceneSemantics.Builder description = SceneSemantics.builder(SceneSemantics.Role.TAB_PANEL);
            SceneNode label = targets.get(active.get().get(0).key);
            if (label != null) description.labelledBy(label);
            return description.build();
        }));
        panel.bind(NodeBinding.value(() -> {
            page.runtime().interactionRevision().get();
            return !active.get().isEmpty() && !hasFocusableChild(panel);
        }, (self, focusable) -> { if (self.focusable() != focusable) self.focusable(focusable); }));
        node = new SceneNode().size(Size.fill(1), Size.fill(1)).own(owner).add(bar).add(panel);
        node.bind(NodeBinding.value(() -> metrics.get().gap, SceneNode::defaultGap));
    }
    public static <K> SceneTabs<K> create(ScenePageContext page, ReadableSignal<List<Tab<K>>> tabs, ReadableSignal<K> selected,
            Consumer<K> change, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneTabs<>(page, Objects.requireNonNull(tabs, "标签来源"), Objects.requireNonNull(selected, "活动来源"),
                Objects.requireNonNull(change, "切换意图"), Objects.requireNonNull(theme, "主题"));
    }
    public static <K> SceneTabs<K> create(ScenePageContext page, ReadableSignal<List<Tab<K>>> tabs, ReadableSignal<K> selected,
            Consumer<K> change) { return create(page, tabs, selected, change, page.theme()); }
    private static boolean hasFocusableChild(SceneNode parent) {
        for (SceneNode child : parent.children()) {
            if (!child.visible() || !child.enabled() || child.destroyed()) continue;
            if (child.focusable() || hasFocusableChild(child)) return true;
        }
        return false;
    }
    /** 下划线跟随已分配按钮宽度，不能用自然宽容器中的FILL子项反过来撑宽标题。 */
    private static final class Label implements SceneControl {
        private final SceneNode button;
        private final ReadableSignal<SceneTheme> theme;
        private final ReadableSignal<Boolean> selected;
        Label(SceneNode button, ReadableSignal<SceneTheme> theme, ReadableSignal<Boolean> selected) {
            this.button = button; this.theme = theme; this.selected = selected;
        }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(theme, selected); }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            MeasuredSize actual = children.measureChild(button, limits);
            return new MeasuredSize(actual.width, actual.height + 2);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(button,
                    new LayoutBox(node.padding(), node.padding(), Math.max(0, box.width - node.padding() * 2), Math.max(0, box.height - node.padding() * 2 - 2)))));
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (selected.get() && entry != null) paint.add(PaintCommand.rect(new LayoutBox(entry.box.x, entry.box.y + Math.max(0, entry.box.height - 2),
                    entry.box.width, Math.min(2, entry.box.height)), entry.clip, theme.get().focus));
        }
    }
    public SceneNode node() { return node; }
    public SceneScrollViewport headerViewport() { return headerViewport; }
}
