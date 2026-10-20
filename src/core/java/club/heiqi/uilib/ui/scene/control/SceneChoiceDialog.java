package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 有限内容选择界面；沿用下拉框选项合同，选中与关闭由业务决定。 */
public final class SceneChoiceDialog {
    private SceneChoiceDialog() {}

    public static <K> SceneNode create(
            ScenePageContext page,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Boolean> open,
            ReadableSignal<SceneDialog.Options> options,
            ReadableSignal<List<SceneSelect.Option<K>>> items,
            ReadableSignal<K> selected,
            Consumer<K> choose,
            Runnable cancel) {
        Objects.requireNonNull(page).domain().checkWrite();
        Objects.requireNonNull(choose);
        Objects.requireNonNull(selected);
        SceneAppearance style = new SceneAppearance(page, theme);
        // 一个业务代际只投影一次选项，避免布局重读临时列表不断刷新虚拟行身份。
        ReadableSignal<List<SceneSelect.Option<K>>> projected =
                page.domain().computed(style.scope, items::get);
        SceneNode list =
                SceneVirtualList.create(
                                page,
                                projected,
                                item -> item.key,
                                item -> {
                                    SceneNode label =
                                            style.label(() -> item.get().name.get(), 1, false)
                                                    .padding(12);
                                    return style.action(
                                                    () -> item.get().name.get(),
                                                    label,
                                                    () -> choose.accept(item.get().key),
                                                    SceneAppearance.Role.ENTRY,
                                                    () ->
                                                            Objects.equals(
                                                                    selected.get(), item.get().key))
                                            .size(Size.fill(1), Size.natural())
                                            .bind(
                                                    NodeBinding.value(
                                                            () -> item.get().enabled.get(),
                                                            SceneNode::enabled));
                                })
                        .node();
        SceneNode cancelButton = style.action("取消选择", null, cancel, SceneAppearance.Role.OUTLINE);
        SceneNode header =
                SceneAppearance.column(0)
                        .padding(16)
                        .add(style.label(() -> options.get().name, 1.2f, false));
        SceneNode footer = SceneAppearance.row(8).padding(12).add(cancelButton);
        return SceneDialog.create(
                        page,
                        theme,
                        open,
                        options,
                        new SceneDialog.Content(header, list, footer, cancelButton, cancel))
                .own(style.scope);
    }
}
