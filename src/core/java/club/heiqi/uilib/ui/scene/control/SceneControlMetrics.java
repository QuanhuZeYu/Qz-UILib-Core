package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.text.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 控件度量的一次纯解析；布局条件只包含实际占位，不包含配色、动效和圆角。 */
public final class SceneControlMetrics {
    public enum Kind { LABEL, BUTTON, MODERN_BUTTON, FIELD, SYMBOL, SLIDER, SCROLLBAR, TOOLTIP,
        TABS, POPUP, TREE, TABLE, VIRTUAL, CARD, PANEL }
    public final TextStyle textStyle;
    public final int padding, gap, radius, iconSize, iconGap;
    public final int sliderWidth, sliderHeight, sliderInset, sliderThickness, sliderThumbRadius;
    public final int scrollbarThickness, scrollbarMinimumThumb, scrollbarInset;
    public final int treeIndent, treeMarkerSlot, rowHeightEstimate, popupWidth;
    private final Kind kind;
    SceneControlMetrics(SceneTheme theme, Kind kind, TextStyle style) {
        this.kind = kind; textStyle = style;
        float em = style.logicalEm;
        padding = kind == Kind.LABEL ? 0 : kind == Kind.SYMBOL ? theme.symbolPadding()
                : kind == Kind.TOOLTIP ? Math.max(theme.spacing(4), Math.round(em * .4f)) : theme.padding;
        gap = kind == Kind.TOOLTIP ? Math.max(theme.spacing(2), padding / 2) : theme.gap;
        iconGap = gap;
        iconSize = kind == Kind.SYMBOL ? Math.max(1, (int)Math.ceil(em * 1.15f)) : Math.max(1, Math.round(em * 1.2f));
        radius = kind == Kind.CARD || kind == Kind.PANEL ? theme.spacing(12)
                : kind == Kind.MODERN_BUTTON || kind == Kind.TOOLTIP ? theme.spacing(8)
                : kind == Kind.POPUP ? Math.max(1, padding) : kind == Kind.SCROLLBAR ? theme.spacing(3) : 0;
        sliderWidth = Math.round(em * 10);
        sliderHeight = Math.max(theme.spacing(28), Math.round(em * 2));
        sliderInset = Math.max(theme.spacing(5), Math.round(em * .45f));
        sliderThickness = Math.max(theme.spacing(3), Math.round(em * .3f));
        sliderThumbRadius = Math.max(theme.spacing(5), Math.round(em * .4f));
        scrollbarThickness = Math.max(1, theme.spacing(11));
        scrollbarMinimumThumb = Math.max(1, theme.spacing(25));
        scrollbarInset = theme.spacing(2);
        treeIndent = Math.max(1, Math.round(em * 1.25f));
        treeMarkerSlot = Math.max(1, Math.round(em * 2));
        rowHeightEstimate = kind == Kind.POPUP ? Math.max(1, Math.round(em * 1.4f) + padding * 2)
                : Math.max(1, Math.round(em * 3));
        popupWidth = Math.max(1, Math.round(em * 18));
    }
    public Object layoutKey() {
        List<Object> values = new ArrayList<>(Arrays.asList(kind, textStyle.layoutStyle(), padding, gap));
        switch (kind) {
            case BUTTON: case MODERN_BUTTON: case SYMBOL: case TABS: case POPUP:
                values.add(iconSize); values.add(iconGap); break;
            case SLIDER: values.addAll(Arrays.asList(sliderWidth, sliderHeight, sliderInset, sliderThickness, sliderThumbRadius)); break;
            case SCROLLBAR: values.addAll(Arrays.asList(scrollbarThickness, scrollbarMinimumThumb, scrollbarInset)); break;
            default: break;
        }
        if (kind == Kind.TREE) values.addAll(Arrays.asList(treeIndent, treeMarkerSlot));
        if (kind == Kind.POPUP) values.add(popupWidth);
        return Collections.unmodifiableList(values);
    }
}
