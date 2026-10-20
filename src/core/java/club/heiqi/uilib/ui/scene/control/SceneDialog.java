package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.PresenceOptions;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;

import java.util.Objects;

/** 同一模态合同下的标题、正文、页脚；业务只提供关闭意图。 返回全尺寸区域，与正文通过 SceneLayouts.overlay 组合；内部所属弹层继续沿正式范围声明。 */
public final class SceneDialog {
    private SceneDialog() {}

    public static final class Options {
        public final String name;
        public final int width, height, inset;

        public Options(String name, int width, int height) {
            this(name, width, height, 12);
        }

        public Options(String name, int width, int height, int inset) {
            this.name = Objects.requireNonNull(name);
            if (width < 0 || height < 0 || inset < 0)
                throw new IllegalArgumentException("弹窗尺寸与边距不能为负");
            this.width = width;
            this.height = height;
            this.inset = inset;
        }
    }

    public static final class Content {
        public final SceneNode header, body, footer, initialFocus;
        public final Runnable requestClose;
        private final SceneNode[] attached;

        public Content(
                SceneNode header,
                SceneNode body,
                SceneNode footer,
                SceneNode initialFocus,
                Runnable requestClose,
                SceneNode... attached) {
            this.header = Objects.requireNonNull(header);
            this.body = Objects.requireNonNull(body);
            this.footer = Objects.requireNonNull(footer);
            this.initialFocus = initialFocus;
            this.requestClose = Objects.requireNonNull(requestClose);
            this.attached = attached.clone();
        }
    }

    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<Boolean> open,
            ReadableSignal<Options> options,
            Content content) {
        Objects.requireNonNull(context).domain().checkWrite();
        Objects.requireNonNull(theme);
        Objects.requireNonNull(open);
        Objects.requireNonNull(options);
        Objects.requireNonNull(content);
        SceneAppearance style = new SceneAppearance(context, theme);
        SceneNode frame = SceneLayouts.frame(content.header, content.body, content.footer);
        SceneAppearance.Surface surface =
                new SceneAppearance.Surface(
                        style.colors, SceneAppearance.Role.PANEL, null, () -> false);
        SceneNode card =
                SceneAppearance.column(0)
                        .control(surface)
                        .add(frame)
                        .bind(
                                NodeBinding.value(
                                        options,
                                        (node, value) ->
                                                node.size(
                                                        Size.fill(1).bounds(0, value.width),
                                                        Size.fill(1).bounds(0, value.height))));
        surface.install(card);
        SceneNode position =
                SceneLayouts.center(card)
                        .bind(
                                NodeBinding.value(
                                        options, (node, value) -> node.padding(value.inset)));
        SceneNode backdrop =
                SceneAppearance.column(0)
                        .size(Size.fill(1), Size.fill(1))
                        .background(SceneAppearance.MODAL_SCRIM)
                        .bind(SceneMotion.presence(
                                open, () -> new PresenceOptions(theme.get().feedbackMotion).budget(4)));
        SceneNode body = SceneLayouts.overlay().add(position);
        // 页面级模态使用普通全屏区域；所属弹层由其内部控件沿原范围声明。
        SceneNode window =
                SceneLayouts.overlay()
                        .size(Size.fill(1), Size.fill(1))
                        .modal(true)
                        .onEscape(
                                () -> {
                                    content.requestClose.run();
                                    return true;
                                })
                        .bind(
                                NodeBinding.semantics(
                                        () ->
                                                SceneSemantics.builder(SceneSemantics.Role.GROUP)
                                                        .name(options.get().name)
                                                        .build()))
                        .add(body);
        if (content.initialFocus != null) {
            SceneSemantics semantics =
                    content.initialFocus.control() == null
                            ? null
                            : content.initialFocus.control().semantics(content.initialFocus);
            window.initialFocus(
                    semantics != null && semantics.focusTarget != null
                            ? semantics.focusTarget
                            : content.initialFocus);
        }
        for (SceneNode attached : content.attached) body.add(attached);
        window.bind(
                SceneMotion.presence(
                        open, () -> new PresenceOptions(theme.get().feedbackMotion).budget(4)));
        // 遮罩始终覆盖视口；只有面板所在的持续父层位移，退场快照也沿同一父层运动。
        SceneNode moving = SceneLayouts.overlay()
                .bind(SceneMotion.translateY(
                        () -> open.get() ? 0f : 16f, () -> theme.get().spatialMotion))
                .add(window);
        return SceneLayouts.overlay().add(backdrop).add(moving).own(style.scope);
    }
}
