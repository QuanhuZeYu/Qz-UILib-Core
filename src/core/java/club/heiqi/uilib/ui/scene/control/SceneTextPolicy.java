package club.heiqi.uilib.ui.scene.control;

/** 最终外盒内的文字呈现策略；SceneTextControl 统一投影共享布局，保留原文与字体事实。
 * 方向、语言和断行请求由 text.TextPolicy 表达。 */
public enum SceneTextPolicy {
    WRAP,
    ELLIPSIS_SINGLE_LINE,
    OVERFLOW
}
