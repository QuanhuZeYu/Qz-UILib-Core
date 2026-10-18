package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 非编辑单选来源；弹出焦点是预览，确认才发布业务选择意图。 */
public final class SceneSelect {
    private SceneSelect() { }
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
    public static <K> SceneNode create(ScenePageContext page, ReadableSignal<List<Option<K>>> options, ReadableSignal<K> selected,
            Consumer<K> change, ReadableSignal<SceneTheme> theme) { return create(page, options, selected, change, () -> "请选择", theme); }
    public static <K> SceneNode create(ScenePageContext page, ReadableSignal<List<Option<K>>> options, ReadableSignal<K> selected, Consumer<K> change) {
        return create(page, options, selected, change, page.theme());
    }
    public static <K> SceneNode create(ScenePageContext page, ReadableSignal<List<Option<K>>> options, ReadableSignal<K> selected,
            Consumer<K> change, ReadableSignal<String> placeholder, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(page, "page").domain().checkWrite(); Objects.requireNonNull(selected, "选中来源");
        Objects.requireNonNull(change, "选择意图"); Objects.requireNonNull(placeholder, "占位名称"); Objects.requireNonNull(theme, "主题");
        Owner owner = page.scope().child();
        ReadableSignal<List<Option<K>>> checked = ScenePopupList.checked(page, owner, Objects.requireNonNull(options, "选项来源"), option -> option.key);
        ReadableSignal<String> title = () -> {
            for (Option<K> option : checked.get()) if (Objects.equals(option.key, selected.get())) return option.name.get();
            return placeholder.get();
        };
        return new ScenePopupList<>(page, owner, checked, option -> option.key, option -> option.name.get(), option -> option.enabled.get(),
                selected, title, option -> { if (!Objects.equals(selected.get(), option.key)) change.accept(option.key); }, theme, false).node();
    }
}
