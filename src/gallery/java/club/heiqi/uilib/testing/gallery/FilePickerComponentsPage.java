package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

import java.nio.file.Path;
import java.util.Collections;

final class FilePickerComponentsPage {
    private FilePickerComponentsPage() {}

    static SceneNode create(
            ScenePageContext p,
            ReadableSignal<SceneTheme> theme,
            GalleryEnvironment environment,
            Runnable back) {
        ComponentExamples e =
                new ComponentExamples(p, theme, "本地文件选择", "目录浏览、路径导航、搜索、过滤、历史与分页使用正式文件选择器。", back);
        Signal<Boolean> open = p.domain().signal(false);
        Signal<Path> directory = p.domain().signal(environment.initialMediaDirectory());
        Signal<String> selected = p.domain().signal("尚未选择文件");
        e.section(
                "打开单个文件",
                "选择器异步读取目录并核验普通文件；取消保留上次选择。",
                e.style.action("打开文件选择器", null, () -> open.set(true), SceneAppearance.Role.PRIMARY),
                e.style.label(selected, 1, true));
        SceneNode layer =
                SceneLayouts.overlay()
                        .bind(
                                NodeBinding.repeat(
                                        () ->
                                                open.get()
                                                        ? Collections.singletonList(Boolean.TRUE)
                                                        : Collections.emptyList(),
                                        value -> value,
                                        value ->
                                                SceneFilePicker.create(
                                                        p,
                                                        directory.get(),
                                                        theme,
                                                        path -> {
                                                            directory.set(path.getParent());
                                                            selected.set("已选择文件：" + path);
                                                            open.set(false);
                                                        },
                                                        () -> open.set(false))));
        return SceneLayouts.overlay().add(e.root).add(layer);
    }
}
