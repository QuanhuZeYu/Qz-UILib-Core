package club.heiqi.uilib.ui.scene.hud;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Objects;

/** 通用 HUD 声明；输入、缩放与裁剪共用正式几何，不持久化位置。 */
public final class SceneHud {
    private SceneHud() {}

    public static SceneNode create(ScenePageContext context, SceneNode content,
            ReadableSignal<SceneHudPlacement> placement) {
        return create(context, content, placement, null, null);
    }

    public static SceneNode edit(ScenePageContext context, SceneNode content,
            Signal<SceneHudPlacement> placement, ReadableSignal<Integer> snap) {
        Objects.requireNonNull(snap, "snap");
        return create(context, content, placement, placement, snap);
    }

    private static SceneNode create(ScenePageContext context, SceneNode content,
            ReadableSignal<SceneHudPlacement> placement, Signal<SceneHudPlacement> writable,
            ReadableSignal<Integer> snap) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(placement, "placement");
        ReadableSignal<Float> scale = () -> {
            float value = Objects.requireNonNull(placement.get(), "HUD 位置").scale;
            if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("HUD 缩放须为有限正数");
            return value;
        };
        SceneNode transformed = new SceneNode().size(Size.natural(), Size.natural()).add(content)
                .bind(NodeBinding.value(scale, (n, s) -> n.transform(SceneTransform.scale(s, s))));
        SceneNode scaled = new SceneNode().control(new SceneHudLayout.Scaled(transformed, scale)).add(transformed);
        SceneHudLayout.Anchored layout = new SceneHudLayout.Anchored(placement);
        SceneNode target = scaled;
        if (writable != null) {
            SceneDraggable.State drag = SceneDraggable.state(context.domain());
            target = SceneDraggable.create(context, scaled, drag, () -> {
                int step = Objects.requireNonNull(snap.get(), "HUD 网格");
                if (step < 0) throw new IllegalArgumentException("HUD 网格不能为负");
                return new SceneDraggable.Options().activation(1, 0, 0)
                        .bounds(SceneDraggable.Bounds.PARENT).grid(step, step).autoScroll(false)
                        .onDrop(value -> layout.drop(writable, value.deltaX, value.deltaY));
            });
        }
        layout.content = target;
        return new SceneNode().size(Size.fill(1), Size.fill(1)).clip(true).control(layout).add(target);
    }
}
