package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.localization.LocalizationBinding;
import java.util.Objects;

/** 广场标准组合的四项名称；来源、语言选择和重载均归装配者。 */
public final class GalleryCompositeLabels {
    public final ReadableSignal<String> clearSearch, cancelChoice, previousPage, nextPage;

    public GalleryCompositeLabels(ReadableSignal<String> clearSearch, ReadableSignal<String> cancelChoice,
            ReadableSignal<String> previousPage, ReadableSignal<String> nextPage) {
        this.clearSearch = Objects.requireNonNull(clearSearch);
        this.cancelChoice = Objects.requireNonNull(cancelChoice);
        this.previousPage = Objects.requireNonNull(previousPage);
        this.nextPage = Objects.requireNonNull(nextPage);
    }

    /** 旧字面入口的明确默认；没有隐式语言源、文件读取或全局订阅。 */
    public static GalleryCompositeLabels defaults() {
        return new GalleryCompositeLabels(() -> "清空搜索", () -> "取消选择", () -> "上一页", () -> "下一页");
    }

    public static GalleryCompositeLabels from(LocalizationBinding language) {
        return new GalleryCompositeLabels(language.text("search.clear"), language.text("choice.cancel"),
                language.text("pagination.previous"), language.text("pagination.next"));
    }
}
