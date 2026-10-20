package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 只声明通知区与业务意图，不另建计时、定位或输入实现。 */
final class NotificationComponentsPage {
    private NotificationComponentsPage() {}

    static SceneNode create(ScenePageContext page, ReadableSignal<SceneTheme> theme, Runnable back) {
        ComponentExamples e = new ComponentExamples(page, theme, "浮动通知",
                "Toast 提供短暂操作反馈；Notification 展示详细内容和动作，出现时保留原焦点。", back);
        Signal<Integer> serial = page.domain().signal(0);
        Signal<Integer> actions = page.domain().signal(0);
        Signal<SceneNotifications.Position> position = page.domain().signal(SceneNotifications.Position.BOTTOM_END);
        SceneNotifications toasts = SceneNotifications.createToast(page, theme, 3,
                () -> new SceneNotifications.Options(SceneNotifications.Position.TOP_CENTER));
        SceneNotifications notifications = SceneNotifications.create(page, theme, SceneNotifications.Mode.NOTIFICATION, 3,
                () -> new SceneNotifications.Options(position.get()), () -> "关闭通知");
        Runnable toast = () -> {
            serial.set(serial.get() + 1);
            SceneNotice.Message message = new SceneNotice.Message(SceneNotice.Kind.SUCCESS,
                    "保存成功 " + serial.get(), "你的更改已保存。");
            toasts.show(() -> message, 3_000_000_000L);
        };
        e.section("轻提示 Toast", "顶部短暂浮现，多条依序堆叠；整区不接收指针、Tab 或 ESC。",
                SceneLayouts.wrap(8)
                        .add(e.style.action("显示轻提示", null, toast, SceneAppearance.Role.PRIMARY))
                        .add(e.style.action("连续三条", null, () -> { for (int i = 0; i < 3; i++) toast.run(); },
                                SceneAppearance.Role.OUTLINE)));
        e.section("通知 Notification", "右下角通知保持到关闭；可附带一个由业务提供的操作。最多保留三条。",
                SceneLayouts.wrap(8)
                        .add(e.style.action("显示持续通知", null, () -> {
                            serial.set(serial.get() + 1);
                            SceneNotice.Message message = new SceneNotice.Message(SceneNotice.Kind.INFO,
                                    "新的协作消息 " + serial.get(), "团队成员更新了项目说明。此通知会保持到你关闭。");
                            notifications.show(() -> message, 0);
                        }, SceneAppearance.Role.PRIMARY))
                        .add(e.style.action("显示带动作通知", null, () -> notifications.show(
                                () -> new SceneNotice.Message(SceneNotice.Kind.SUCCESS, "文件已归档", "可通过附属操作撤销本次示例归档。"),
                                0, () -> "撤销归档", SceneCommand.of(() -> true, () -> actions.set(actions.get() + 1))),
                                SceneAppearance.Role.OUTLINE)),
                e.style.label(() -> "已执行撤销：" + actions.get(), 1, true));
        e.section("位置与清理", "动态切换顶部/底部及起始/居中/末端位置，不重建通知身份。",
                SceneLayouts.wrap(8)
                        .add(e.style.action("切换通知位置", null, () -> {
                            SceneNotifications.Position[] positions = SceneNotifications.Position.values();
                            position.set(positions[(position.get().ordinal() + 1) % positions.length]);
                        }, SceneAppearance.Role.OUTLINE))
                        .add(e.style.action("清空通知", null, () -> { toasts.clear(); notifications.clear(); },
                                SceneAppearance.Role.GHOST)),
                e.style.label(() -> "当前位置：" + positionName(position.get()), 1, true));
        return SceneLayouts.overlay().add(e.root).add(toasts.node()).add(notifications.node());
    }

    private static String positionName(SceneNotifications.Position position) {
        switch (position) {
            case TOP_START: return "左上角";
            case TOP_CENTER: return "顶部居中";
            case TOP_END: return "右上角";
            case BOTTOM_START: return "左下角";
            case BOTTOM_CENTER: return "底部居中";
            default: return "右下角";
        }
    }
}
