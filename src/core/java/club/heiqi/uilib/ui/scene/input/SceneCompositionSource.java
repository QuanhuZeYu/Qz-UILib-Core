package club.heiqi.uilib.ui.scene.input;

/** 宿主为一次真实组字来源创建的身份；更新、提交与取消必须保留同一实例。
 * 联系由原 BEGIN 绑定目标，后续迟到事件继续使用原身份。 */
public final class SceneCompositionSource {
    private boolean begun;

    /** Core 在首次 BEGIN 路由时取得一次性联系，后续调用返回 false。 */
    public synchronized boolean beginOnce() {
        if (begun) return false;
        begun = true;
        return true;
    }
}
