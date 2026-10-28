package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** 单一选择来源和单Tab入口；方向只改变原路由焦点并发布业务选择意图。 */
public final class SceneRadioGroup<K> {
    public enum Variant { RADIO, SEGMENTED }

    /** 声明单选外观，选择与导航继续使用共同来源。 */
    public static final class Options {
        final Variant variant;
        public Options() { this(Variant.RADIO); }
        private Options(Variant variant) { this.variant = variant; }
        public Options variant(Variant value) { return new Options(Objects.requireNonNull(value, "单选组外观")); }
    }

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
            Consumer<K> change, ReadableSignal<Options> appearance, ReadableSignal<SceneTheme> theme, SceneTextPolicy policy) {
        Owner owner = page.scope().child();
        ReadableSignal<Variant> variant = page.domain().computed(owner, () -> Objects.requireNonNull(appearance.get(), "单选组选项").variant);
        ReadableSignal<SceneAppearance.Palette> colors = page.domain().computed(owner, () -> new SceneAppearance.Palette(theme.get()));
        SceneChoiceNavigation<Option<K>, K> navigation = new SceneChoiceNavigation<>(page, options, option -> option.key,
                option -> option.enabled.get(), nodes, selected, change, true);
        node = new SceneNode().size(Size.fill(1), Size.natural()).own(owner);
        SceneRadioGroupPresentation.install(node, navigation, variant, colors);
        node.bind(NodeBinding.semantics(() -> SceneSemantics.builder(SceneSemantics.Role.RADIO_GROUP)
                .count(options.get().size()).actions().build()));
        node.bind(NodeBinding.repeat(options, option -> option.key, current -> {
            K key = current.get().key;
            ReadableSignal<Boolean> checked = () -> Objects.equals(selected.get(), current.get().key);
            SceneNode item = SceneRadioOptionControl.create(page, () -> current.get().name.get(), theme, colors, variant, checked, policy, () -> navigation.select(key));
            item.bind(NodeBinding.value(() -> variant.get() == Variant.SEGMENTED, (self, segmented) ->
                    self.size(segmented ? Size.natural() : Size.fill(1), Size.natural())));
            item.bind(NodeBinding.value(() -> current.get().enabled.get(), SceneNode::enabled));
            nodes.put(key, item); item.own(() -> nodes.remove(key, item)); return item;
        }));
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, ReadableSignal<SceneTheme> theme) {
        return create(page, options, selected, change, theme, SceneTextPolicy.WRAP);
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, ReadableSignal<SceneTheme> theme, SceneTextPolicy policy) {
        return create(page, options, selected, change, () -> new Options(), theme, policy);
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change) { return create(page, options, selected, change, page.theme()); }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, SceneTextPolicy policy) {
        return create(page, options, selected, change, page.theme(), policy);
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, Options appearance) {
        Objects.requireNonNull(appearance, "单选组选项");
        return create(page, options, selected, change, () -> appearance, page.theme());
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, ReadableSignal<Options> appearance, ReadableSignal<SceneTheme> theme) {
        return create(page, options, selected, change, appearance, theme, SceneTextPolicy.WRAP);
    }
    public static <K> SceneRadioGroup<K> create(ScenePageContext page, ReadableSignal<List<Option<K>>> options,
            ReadableSignal<K> selected, Consumer<K> change, ReadableSignal<Options> appearance,
            ReadableSignal<SceneTheme> theme, SceneTextPolicy policy) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneRadioGroup<>(page, Objects.requireNonNull(options, "选项来源"), Objects.requireNonNull(selected, "选中来源"),
                Objects.requireNonNull(change, "选择意图"), Objects.requireNonNull(appearance, "单选组选项"),
                Objects.requireNonNull(theme, "主题"), Objects.requireNonNull(policy, "文案策略"));
    }
    public SceneNode node() { return node; }

}
