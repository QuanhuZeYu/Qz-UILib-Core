package club.heiqi.uilib.ui.scene.text;

/** 按组件声明的文本策略提供共享行布局。 */
public interface SceneTextMeasurer {
    TextLayout layout(String text, TextStyle style, int availableWidth);

    /** 字体相关样式意图在同一字体代际内解析，供共同文字与数学内容共用。 */
    default TextStyle resolveStyle(TextStyle style) { return style; }

    /** 当前样式的数学字体；无 MATH 能力时由数学呈现保留源码并诊断。 */
    default club.heiqi.uilib.ui.scene.text.math.MathFont mathFont(TextStyle style) {
        return null;
    }

    default TextLayout layout(AttributedText text, float availableWidth, TextPolicy policy) {
        throw new UnsupportedOperationException("此measurer尚不支持属性文本");
    }

    /** 默认适配只接受 NORMAL 策略，其余策略由实现显式支持。 */
    default TextLayout layout(String text, TextStyle style, int availableWidth, TextPolicy policy) {
        if (!TextPolicy.NORMAL.equals(policy))
            throw new UnsupportedOperationException("此 measurer 尚不支持组件文本策略");
        return layout(text, style, availableWidth);
    }
}
