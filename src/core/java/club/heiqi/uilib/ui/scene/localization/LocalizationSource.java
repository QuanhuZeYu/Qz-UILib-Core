package club.heiqi.uilib.ui.scene.localization;

import java.util.function.Consumer;

/** 独立已加载资料入口。订阅建立时同步交付当前资料，随后递交的事实按发布顺序单调推进；
 * 重入发布按最新代际合并待递交事实，current 始终返回最新资料。
 * 初次交付与订阅登记在同一受控操作中完成，完整衔接后续换代。
 * 通知可能来自资料提供者线程；调用方经LocalizationBinding递交自己的状态域。 */
public interface LocalizationSource {
    LocalizationSnapshot current();

    Subscription subscribe(Consumer<LocalizationSnapshot> listener);

    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
