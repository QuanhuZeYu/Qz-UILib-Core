package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.EntranceOptions;
import club.heiqi.uilib.ui.scene.motion.PresenceOptions;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** 键决定内容身份；来源为 null 时退出，同键更新保持节点。只组合正式 repeat、入场和有限退场。 */
public final class SceneAnimatedContent {
    private SceneAnimatedContent() { }

    public static <T, K> SceneNode create(ScenePageContext page, ReadableSignal<T> source,
            Function<T, K> key, Function<ReadableSignal<T>, SceneNode> content,
            ReadableSignal<EntranceOptions> options) {
        Objects.requireNonNull(page, "页面"); Objects.requireNonNull(source, "内容来源");
        Objects.requireNonNull(key, "内容键"); Objects.requireNonNull(content, "内容声明");
        Objects.requireNonNull(options, "内容动效");
        ReadableSignal<List<T>> values = () -> {
            T value = source.get();
            return value == null ? Collections.emptyList() : Collections.singletonList(value);
        };
        return SceneLayouts.overlay().size(Size.natural(), Size.natural())
                .bind(NodeBinding.repeat(values, key, value -> {
                    SceneNode node = SceneLayouts.overlay().size(Size.natural(), Size.natural())
                            .add(Objects.requireNonNull(content.apply(value), "内容节点"));
                    return node.bind(SceneMotion.enter(options));
                }, () -> new PresenceOptions(options.get().feedback).budget(2)));
    }
}
