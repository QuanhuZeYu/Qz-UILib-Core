package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 以普通 PaintPlan 绘制标准图形，共用实际尺寸 coverage 与完整名称。 */
public final class SceneIconButton {
    public enum Symbol { STAR, STAR_FILLED, ARCHIVE, MAIL, MAIL_OPEN, SEARCH, CLOSE,
        CHEVRON_LEFT, CHEVRON_RIGHT, INBOX, ALL_MAIL, THEME, SORT, CHECK, MINUS, CHECK_BOX, TAG, PEOPLE, MENU, SLIDERS, FILE, MONITOR, FOLDER, CHEVRON_DOWN }
    private SceneIconButton() {}
    public static SceneNode create(ScenePageContext page, Symbol symbol, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Runnable action) {
        Objects.requireNonNull(symbol);return create(page,() -> symbol,name,theme,action);
    }
    /** 在同一动作节点上更新标记，延续命中与焦点身份。 */
    public static SceneNode create(ScenePageContext page, ReadableSignal<Symbol> symbol, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, Runnable action) {
        return SceneSymbolControl.node(page,name,new Control(theme,symbol),action);
    }
    /** 共用命令的可用条件与执行入口。 */
    public static SceneNode create(ScenePageContext page, Symbol symbol, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, SceneCommand command) {
        Objects.requireNonNull(symbol); return create(page, () -> symbol, name, theme, command);
    }
    public static SceneNode create(ScenePageContext page, ReadableSignal<Symbol> symbol, ReadableSignal<String> name,
            ReadableSignal<SceneTheme> theme, SceneCommand command) {
        Objects.requireNonNull(command, "命令");
        return SceneControlBindings.command(create(page, symbol, name, theme, command::tryExecute), command);
    }
    /** 为复合动作提供同源标记装饰，输入归所属动作根。 */
    public static SceneNode graphic(ScenePageContext page, ReadableSignal<Symbol> symbol, ReadableSignal<SceneTheme> theme) {
        return SceneSymbolControl.graphic(page,new Control(theme,symbol));
    }
    private static final class Control extends SceneSymbolControl {
        private final ReadableSignal<Symbol> shape;
        Control(ReadableSignal<SceneTheme> theme, ReadableSignal<Symbol> shape) {super(theme,false);this.shape=Objects.requireNonNull(shape);}
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) {return Arrays.asList(theme,shape);}
        @Override void symbol(LayoutBox box, PaintPlanBuilder target, LayoutBox clip, int color, boolean enabled) {
            SceneIconGlyph.paint(Objects.requireNonNull(shape.get(),"图标标记"),box,target,clip,color);
        }
    }
}
