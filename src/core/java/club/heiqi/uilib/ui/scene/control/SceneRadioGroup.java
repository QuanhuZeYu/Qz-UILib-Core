package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** 单一选择来源和单Tab入口；方向只改变原路由焦点并发布业务选择意图。 */
public final class SceneRadioGroup<K> {
    public static final class Option<K> {
        public final K key;
        public final ReadableSignal<String> name;
        public final ReadableSignal<Boolean> enabled;
        public Option(K key, ReadableSignal<String> name, ReadableSignal<Boolean> enabled) {
            this.key = Objects.requireNonNull(key, "选项键"); this.name = Objects.requireNonNull(name, "名称");
            this.enabled = Objects.requireNonNull(enabled, "可用来源");
        }
        public Option(K key, ReadableSignal<String> name) { this(key, name, () -> true); }
    }
    private final Map<K, SceneNode> nodes = new HashMap<>();
    private final SceneNode node;

    private SceneRadioGroup(ScenePageContext page, ReadableSignal<List<Option<K>>> options, ReadableSignal<K> selected,
            Consumer<K> change, ReadableSignal<SceneTheme> theme, SceneTextPolicy policy) {
        SceneChoiceNavigation<Option<K>, K> navigation = new SceneChoiceNavigation<>(page, options, option -> option.key,
                option -> option.enabled.get(), nodes, selected, change, true);
        node = new SceneNode().size(Size.fill(1), Size.natural()).control(navigation);
        node.bind(NodeBinding.value(() -> theme.get().metrics(SceneControlMetrics.Kind.SYMBOL).gap, SceneNode::defaultGap));
        node.bind(NodeBinding.semantics(() -> SceneSemantics.builder(SceneSemantics.Role.RADIO_GROUP)
                .count(options.get().size()).actions().build()));
        node.bind(NodeBinding.repeat(options, option -> option.key, current -> {
            K key = current.get().key;
            ReadableSignal<Boolean> checked = () -> Objects.equals(selected.get(), current.get().key);
            SceneNode item = SceneSymbolControl.node(page, () -> current.get().name.get(), new Mark(theme, checked, policy), () -> navigation.select(key));
            item.size(Size.fill(1), Size.natural()).bind(NodeBinding.value(() -> current.get().enabled.get(), SceneNode::enabled));
            nodes.put(key, item); item.own(() -> nodes.remove(key, item)); return item;
        }));
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, ReadableSignal<SceneTheme> theme) {
        return create(page, options, selected, change, theme, SceneTextPolicy.WRAP);
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, ReadableSignal<SceneTheme> theme, SceneTextPolicy policy) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneRadioGroup<>(page, Objects.requireNonNull(options, "选项来源"), Objects.requireNonNull(selected, "选中来源"),
                Objects.requireNonNull(change, "选择意图"), Objects.requireNonNull(theme, "主题"), Objects.requireNonNull(policy, "文案策略"));
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change) { return create(page, options, selected, change, page.theme()); }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, SceneTextPolicy policy) {
        return create(page, options, selected, change, page.theme(), policy);
    }
    public SceneNode node() { return node; }

    private static final class Mark extends SceneSymbolControl {
        private final ReadableSignal<Boolean> checked;
        private ReadableSignal<Float> selected;
        Mark(ReadableSignal<SceneTheme> theme, ReadableSignal<Boolean> checked, SceneTextPolicy policy) {
            super(theme, true, policy); this.checked = checked;
        }
        @Override void installPresentation(SceneNode node) {
            selected = SceneControlMotion.progress(node, theme,
                    () -> Objects.requireNonNull(checked.get(), "单选状态") ? 1f : 0f, false);
        }
        @Override public SceneSemantics semantics(SceneNode node) {
            boolean selected = Objects.requireNonNull(checked.get(), "单选状态");
            SceneSemantics.Builder result = super.semantics(node).toBuilder().role(SceneSemantics.Role.RADIO)
                    .checked(selected).selected(selected);
            if (node.focusable()) result.actions(SceneSemantics.Action.FOCUS, SceneSemantics.Action.ACTIVATE, SceneSemantics.Action.SELECT);
            else result.actions(SceneSemantics.Action.ACTIVATE, SceneSemantics.Action.SELECT);
            return result.build();
        }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(theme, checked); }
        @Override void symbol(LayoutBox box, PaintPlanBuilder paint, LayoutBox clip, int color, boolean enabled) {
            ScenePaintShapes.rounded(paint, box, clip, color, box.width / 2);
            int edge = Math.max(1, box.width / 9);
            LayoutBox inside = new LayoutBox(box.x + edge, box.y + edge, Math.max(0, box.width - edge * 2), Math.max(0, box.height - edge * 2));
            ScenePaintShapes.rounded(paint, inside, clip, theme.get().panel, inside.width / 2);
            int inset = Math.max(edge + 1, box.width / 4);
            LayoutBox dot = new LayoutBox(box.x + inset, box.y + inset, Math.max(0, box.width - inset * 2), Math.max(0, box.height - inset * 2));
            ScenePaintShapes.rounded(paint, dot, clip,
                    SceneMotion.alpha(color, Math.max(0, Math.min(1, selected.get()))), dot.width / 2);
        }
    }
}
