package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneRadioGroup;
import club.heiqi.uilib.ui.scene.control.SceneSwitch;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Arrays;

/** 通知偏好直接消费布尔及单选来源；拒绝策略和可用条件仍由表单决定。 */
public final class PreferencesPage {
    private PreferencesPage() { }
    public static SceneNode create(ScenePageContext page, ReadableSignal<SceneTheme> theme, Runnable back) {
        Signal<Boolean> enabled = page.domain().signal(true), network = page.domain().signal(false), automatic = page.domain().signal(true);
        Signal<String> frequency = page.domain().signal("daily"), feedback = page.domain().signal("通知已开启，每天汇总一次。");
        SceneRadioGroup<String> group = SceneRadioGroup.create(page, () -> Arrays.asList(
                new SceneRadioGroup.Option<>("instant", () -> "实时通知", network),
                new SceneRadioGroup.Option<>("daily", () -> "每天汇总"),
                new SceneRadioGroup.Option<>("weekly", () -> "每周汇总")), frequency, next -> {
                    frequency.set(next); feedback.set(next.equals("instant") ? "网络已连接，新的进展会立即通知。"
                            : next.equals("daily") ? "每天汇总一次工作进展。" : "每周整理工作进展。");
                }, theme);
        group.node().bind(NodeBinding.value(enabled, SceneNode::enabled));
        SceneNode card = GalleryStyle.card(theme).gap(18);
        card.add(SceneSwitch.create(page, enabled, () -> "接收项目通知", theme, next -> {
            enabled.set(next); feedback.set(next ? "通知已开启，选择适合的频率。" : "通知已关闭，保留已有频率。");
        }));
        card.add(GalleryStyle.label(page, () -> "通知频率", theme, 18, false));
        card.add(GalleryStyle.label(page, () -> network.get() ? "网络连接正常，可选择实时通知。" : "当前离线，实时通知暂不可用。", theme, 14, true));
        card.add(group.node());
        card.add(SceneSwitch.create(page, automatic, () -> "自动保存偏好", theme, next -> {
            if (network.get()) { automatic.set(next); feedback.set("自动保存已" + (next ? "开启。" : "关闭。")); }
            else feedback.set("连接网络后可修改自动保存；当前偏好已保留。");
        }));
        SceneNode tools = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(12);
        tools.add(GalleryStyle.button(page, () -> network.get() ? "模拟离线" : "连接网络", theme, () -> network.set(!network.get())));
        tools.add(GalleryStyle.button(page, () -> "恢复每天汇总", theme, () -> { frequency.set("daily"); feedback.set("已恢复为每天汇总。"); }));
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(28).gap(20);
        root.add(GalleryStyle.header(page, theme, "通知偏好", "调整通知与保存策略。Tab进入选项组，方向键切换频率。", back));
        root.add(GalleryStyle.frame(card, theme)).add(tools).add(GalleryStyle.label(page, feedback, theme, 15, false));
        return root;
    }
}
