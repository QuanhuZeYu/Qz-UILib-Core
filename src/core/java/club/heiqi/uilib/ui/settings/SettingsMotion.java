package club.heiqi.uilib.ui.settings;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.PresenceOptions;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 设置页只选择运动范围；当前值、速度、退场输入资格与减少动效均归正式运行时。
 * 视觉参照 Shadcn Admin，空间与颜色分工参照 https://motion.dev/docs/react-transitions 。 */
final class SettingsMotion {
    final ReadableSignal<MotionSpec> spatial;
    final ReadableSignal<MotionSpec> feedback;
    private final ReadableSignal<PresenceOptions> presence;

    SettingsMotion(ReadableSignal<club.heiqi.uilib.ui.scene.control.SceneAppearance.Palette> palette) {
        spatial = () -> palette.get().source.spatialMotion;
        feedback = () -> palette.get().source.feedbackMotion;
        presence = () -> new PresenceOptions(feedback.get()).budget(4);
    }

    SceneNode reveal(SceneNode node, ReadableSignal<Boolean> present) {
        return node.bind(SceneMotion.presence(present, presence));
    }

    SceneNode dialog(SceneNode layer, SceneNode window, ReadableSignal<Boolean> present) {
        // 退场快照跟随当前父几何；定位层持续存在，隐藏子树和重新进入共享同一空间轨迹。
        window.bind(SceneMotion.presence(present, presence));
        return layer.bind(SceneMotion.translateY(() -> present.get() ? 0f : 16f, spatial)).add(window);
    }

    SceneNode scroll(SceneNode viewport) {
        return viewport.bind(SceneMotion.scroll(() -> new ScrollMotionOptions(spatial.get()).maximumDisplacement(24)));
    }
}
