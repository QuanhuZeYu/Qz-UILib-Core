package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 正式按钮入口；激活和焦点权威仍由核心router负责。 */
public final class SceneButton {
    private SceneButton() {}

    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            Runnable action) {
        return create(context, text, theme, action, SceneTextPolicy.WRAP);
    }

    public static SceneNode create(
            ScenePageContext context,
            ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme,
            Runnable action,
            SceneTextPolicy policy) {
        return createWithIcon(context,text,theme,action,policy,null);
    }
    public static SceneNode create(ScenePageContext context, ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme, SceneCommand command) {
        return create(context, text, theme, command, SceneTextPolicy.WRAP);
    }
    public static SceneNode create(ScenePageContext context, ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme, SceneCommand command, SceneTextPolicy policy) {
        Objects.requireNonNull(command, "command");
        return SceneControlBindings.command(create(context, text, theme, command::tryExecute, policy), command);
    }
    static SceneNode createModern(ScenePageContext context, ReadableSignal<String> text, ReadableSignal<SceneAppearance.Palette> colors,
            Runnable action, SceneAppearance.Role role, ReadableSignal<SceneIconButton.Symbol> icon, SceneTextPolicy policy) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return action(SceneTextControl.createModern(text, colors, role, icon, policy)
                .size(Size.natural(), Size.natural()), action);
    }
    static SceneNode createModern(ScenePageContext context, ReadableSignal<String> name, SceneNode content,
            ReadableSignal<SceneAppearance.Palette> colors, Runnable action, SceneAppearance.Role role, ReadableSignal<Boolean> selected) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return action(SceneTextControl.createModern(name, content, colors, role, selected)
                .size(Size.natural(), Size.natural()), action);
    }
    private static SceneNode action(SceneNode node, Runnable action) {
        return node.focusable(true).clickFocus(true).onClick(Objects.requireNonNull(action, "action"));
    }
    /** 触发器尾部图标复用原按钮的度量预算、省略及唯一焦点。 */
    static SceneNode createWithTrailingIcon(ScenePageContext context, ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme, Runnable action, SceneTextPolicy policy, ReadableSignal<SceneIconButton.Symbol> icon) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return action(SceneTextControl.createTrailing(text, theme, policy, icon)
                .size(Size.fill(1), Size.natural()), action);
    }
    /** 包内组合仍使用同一文字、省略与提示控制器；图标只占用声明的文字前缀区域。 */
    static SceneNode createWithIcon(ScenePageContext context,ReadableSignal<String> text,ReadableSignal<SceneTheme> theme,
            Runnable action,SceneTextPolicy policy,ReadableSignal<SceneIconButton.Symbol> icon) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return action(SceneTextControl.create(text, theme, policy, true, icon)
                .size(Size.fill(1), Size.natural()), action);
    }
}
