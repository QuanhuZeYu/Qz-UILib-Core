package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.List;
import java.util.Objects;

/** 共享命令的动作菜单；菜单不复制可执行资格或动作。 */
public final class SceneMenu {
    private SceneMenu() { }
    public static final class Item<K> {
        public final K key;
        public final ReadableSignal<String> name;
        public final SceneCommand command;
        public Item(K key, ReadableSignal<String> name, SceneCommand command) {
            this.key = Objects.requireNonNull(key, "菜单键"); this.name = Objects.requireNonNull(name, "名称");
            this.command = Objects.requireNonNull(command, "共享命令");
        }
    }
    public static <K> SceneNode create(ScenePageContext page, ReadableSignal<String> name, ReadableSignal<List<Item<K>>> source,
            ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(page, "page").domain().checkWrite(); Objects.requireNonNull(name, "触发名称"); Objects.requireNonNull(theme, "主题");
        Owner owner = page.scope().child();
        ReadableSignal<List<Item<K>>> checked = ScenePopupList.checked(page, owner, Objects.requireNonNull(source, "菜单来源"), item -> item.key);
        return new ScenePopupList<>(page, owner, checked, item -> item.key, item -> item.name.get(), item -> item.command.executable().get(),
                () -> null, name, item -> item.command.tryExecute(), theme, true).node();
    }
    public static <K> SceneNode create(ScenePageContext page, ReadableSignal<String> name, ReadableSignal<List<Item<K>>> source) {
        return create(page, name, source, page.theme());
    }
}
