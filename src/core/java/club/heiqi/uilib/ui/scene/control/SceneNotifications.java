package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.PresenceOptions;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 页面拥有的有限通知区；接到持续来源的直属孩子，不维护业务历史。
 * Toast 与 Notification 的输入合同见通用界面组合规格第4.1节。 */
public final class SceneNotifications {
    public enum Mode { TOAST, NOTIFICATION }
    public enum Position { TOP_START, TOP_CENTER, TOP_END, BOTTOM_START, BOTTOM_CENTER, BOTTOM_END }

    /** 尺寸按当前主题字号求值；位置、方向与度量均可动态替换。 */
    public static final class Options {
        public final Position position;
        public final float widthEm, insetEm, gapEm;
        public final boolean rightToLeft;

        public Options(Position position) { this(position, 24, 1, .6f, false); }

        public Options(Position position, float widthEm, float insetEm, float gapEm, boolean rightToLeft) {
            this.position = Objects.requireNonNull(position, "通知位置");
            if (!Float.isFinite(widthEm) || widthEm <= 0 || !Float.isFinite(insetEm) || insetEm < 0
                    || !Float.isFinite(gapEm) || gapEm < 0)
                throw new IllegalArgumentException("通知宽度须为正，边距与间距须非负且有限");
            this.widthEm = widthEm; this.insetEm = insetEm; this.gapEm = gapEm;
            this.rightToLeft = rightToLeft;
        }
    }

    /** 独立通知身份；关闭幂等，不影响同一批中的其他条目。 */
    public final class Handle {
        private final ReadableSignal<SceneNotice.Message> message;
        private final ReadableSignal<String> actionText;
        private final SceneCommand action;
        private final long durationNanos;
        private final Signal<Boolean> open;

        private Handle(ReadableSignal<SceneNotice.Message> message, long durationNanos,
                ReadableSignal<String> actionText, SceneCommand action) {
            this.message = message; this.durationNanos = durationNanos;
            this.actionText = actionText; this.action = action;
            open = page.domain().signal(true);
        }

        public boolean isOpen() { return !scope.isDisposed() && open.get(); }
        public void dismiss() {
            page.domain().checkWrite();
            if (!scope.isDisposed()) open.set(false);
        }
    }

    private final ScenePageContext page;
    private final ReadableSignal<SceneTheme> theme;
    private final ReadableSignal<Options> options;
    private final ReadableSignal<String> closeText;
    private final Mode mode;
    private final int capacity;
    private final Owner scope;
    private final Signal<List<Handle>> entries;
    private final SceneNode node;

    private SceneNotifications(ScenePageContext page, ReadableSignal<SceneTheme> theme, Mode mode,
            int capacity, ReadableSignal<Options> options, ReadableSignal<String> closeText) {
        this.page = Objects.requireNonNull(page);
        page.domain().checkWrite();
        this.theme = Objects.requireNonNull(theme);
        this.mode = Objects.requireNonNull(mode);
        if (capacity < 1) throw new IllegalArgumentException("通知容量须为正");
        this.capacity = capacity;
        this.options = Objects.requireNonNull(options);
        this.closeText = Objects.requireNonNull(closeText);
        scope = page.scope().child();
        entries = page.domain().signal(Collections.emptyList());
        ReadableSignal<PresenceOptions> presence = () ->
                new PresenceOptions(theme.get().feedbackMotion).budget(capacity);
        node = SceneLayer.create(page,
                        () -> new SceneLayer.Policy(0, mode == Mode.TOAST
                                ? SceneLayer.Participation.PASSIVE : SceneLayer.Participation.TARGETS_ONLY),
                        (self, source, viewport, size) -> position(viewport, size.width, size.height),
                        Collections.singletonList(options))
                .bind(NodeBinding.value(() -> Math.round(options.get().widthEm * theme.get().textStyle.logicalEm),
                        (self, width) -> self.size(Size.fill(1).bounds(0, width), Size.natural())))
                .bind(NodeBinding.value(() -> Math.round(options.get().gapEm * theme.get().textStyle.logicalEm),
                        SceneNode::gap))
                .bind((runtime, self, owner) -> new club.heiqi.uilib.ui.scene.runtime.BindingActivation() {
                    public void refresh() {}
                    public void close() {
                        // 同批追加后立即卸载时，卡片尚未物化；期限归通知区挂载共同结束。
                        for (Handle entry : entries.get()) if (entry.durationNanos > 0) entry.dismiss();
                    }
                })
                .bind(NodeBinding.repeat(this::visible, entry -> entry,
                        entry -> card(entry.get()), presence))
                .bind(SceneMotion.layoutPositions(() -> theme.get().spatialMotion))
                .own(scope);
        // 释放回调只撤销本区引用；外部消息、命令和其状态仍归调用方。
        scope.own(() -> entries.set(Collections.emptyList()));
    }

    /** 创建一个页面通知区；capacity 是可见条目与有限退场各自的上限。
     * @param closeText Notification 关闭按钮文案，Toast 不显示按钮。
     * @return 页面持有的通知区，node 只能声明在一个持续来源中。 */
    public static SceneNotifications create(ScenePageContext page, ReadableSignal<SceneTheme> theme,
            Mode mode, int capacity, ReadableSignal<Options> options, ReadableSignal<String> closeText) {
        return new SceneNotifications(page, theme, mode, capacity, options, closeText);
    }

    /** 创建纯提示区，无关闭按钮；布局、容量与期限复用同一通知实现。 */
    public static SceneNotifications createToast(ScenePageContext page, ReadableSignal<SceneTheme> theme,
            int capacity, ReadableSignal<Options> options) {
        return create(page, theme, Mode.TOAST, capacity, options, () -> "");
    }

    public SceneNode node() { return node; }

    /** 追加独立消息；零时长保持，正时长沿挂载期统一时间结束。
     * 内容变化不重启期限；连续追加不要求页面手动推进帧。 */
    public Handle show(ReadableSignal<SceneNotice.Message> message, long durationNanos) {
        return show(message, durationNanos, null, null);
    }

    /** 附属操作复用标准命令；执行前撤下当前通知，业务异常不恢复通知。
     * @param actionText 与 action 同时提供；Toast 不接受操作。
     * @return 可独立关闭的消息身份。 */
    public Handle show(ReadableSignal<SceneNotice.Message> message, long durationNanos,
            ReadableSignal<String> actionText, SceneCommand action) {
        page.domain().checkWrite();
        if (scope.isDisposed()) throw new IllegalStateException("通知区已销毁");
        Objects.requireNonNull(message, "通知内容");
        if (durationNanos < 0) throw new IllegalArgumentException("通知时长不能为负");
        if ((actionText == null) != (action == null)) throw new IllegalArgumentException("操作与文案须同时提供");
        if (mode == Mode.TOAST && action != null) throw new IllegalArgumentException("纯提示不能附带操作");
        Handle next = new Handle(message, durationNanos, actionText, action);
        List<Handle> active = visible();
        if (active.size() == capacity) active.remove(0).dismiss();
        active.add(next);
        // 已结束条目在下次追加时一并释放；保留列表始终不超过容量。
        entries.set(Collections.unmodifiableList(active));
        return next;
    }

    public void clear() {
        page.domain().checkWrite();
        if (scope.isDisposed()) return;
        for (Handle entry : entries.get()) entry.dismiss();
        entries.set(Collections.emptyList());
    }

    private List<Handle> visible() {
        List<Handle> active = new ArrayList<>();
        for (Handle entry : entries.get()) if (entry.open.get()) active.add(entry);
        return active;
    }

    private SceneNode card(Handle entry) {
        SceneAppearance style = new SceneAppearance(page, theme);
        SceneNode actions = SceneLayouts.wrap(() -> Math.round(theme.get().gap * .5f));
        if (entry.action != null) {
            SceneCommand command = entry.action.beforeExecute(entry::dismiss);
            actions.add(style.action(entry.actionText, null, command, SceneAppearance.Role.OUTLINE));
        }
        if (mode == Mode.NOTIFICATION)
            actions.add(style.action(closeText, null, entry::dismiss, SceneAppearance.Role.GHOST));
        SceneNode content = SceneNotice.create(page, theme, entry.message,
                mode == Mode.NOTIFICATION ? actions : null)
                .bind(NodeBinding.value(() -> theme.get().padding, SceneNode::padding));
        SceneNode card = style.card(content)
                .bind(NodeBinding.presence(() -> true, () ->
                        new PresenceOptions(theme.get().feedbackMotion).initialEnter(true)))
                .own(style.scope);
        if (entry.durationNanos > 0)
            card.bind(NodeBinding.transientValue(() -> entry, entry.durationNanos, (self, shown) -> {
                if (shown == null) entry.dismiss();
            }));
        return card;
    }

    private LayoutBox position(LayoutBox viewport, int width, int height) {
        Options policy = options.get();
        int inset = Math.round(policy.insetEm * theme.get().textStyle.logicalEm);
        boolean bottom = policy.position == Position.BOTTOM_START || policy.position == Position.BOTTOM_CENTER
                || policy.position == Position.BOTTOM_END;
        boolean center = policy.position == Position.TOP_CENTER || policy.position == Position.BOTTOM_CENTER;
        boolean end = policy.position == Position.TOP_END || policy.position == Position.BOTTOM_END;
        int x = center ? viewport.x + (viewport.width - width) / 2
                : end != policy.rightToLeft ? viewport.x + viewport.width - width - inset : viewport.x + inset;
        int y = bottom ? viewport.y + viewport.height - height - inset : viewport.y + inset;
        return new LayoutBox(x, y, width, height);
    }
}
