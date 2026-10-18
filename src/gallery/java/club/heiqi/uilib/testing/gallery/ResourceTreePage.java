package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneIconButton;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneTreeView;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 项目资源浏览；展开、选中与重命名草稿是独立业务来源。 */
public final class ResourceTreePage {
    private static final class Resource {
        final String key, parent, name; final boolean directory;
        Resource(String key, String parent, String name, boolean directory) { this.key = key; this.parent = parent; this.name = name; this.directory = directory; }
    }
    private ResourceTreePage() { }

    public static SceneNode create(ScenePageContext page, ReadableSignal<SceneTheme> theme, Runnable back) {
        List<Resource> initial = new ArrayList<>();
        initial.add(new Resource("project", null, "工作区资源", true));
        String[] folders = {"界面", "模型", "音效", "脚本", "配置"};
        Set<String> allDirectories = new LinkedHashSet<>(); allDirectories.add("project");
        for (int i = 0; i < folders.length; i++) {
            String folder = "folder-" + i; allDirectories.add(folder); initial.add(new Resource(folder, "project", folders[i], true));
            for (int j = 0; j < 200; j++) initial.add(new Resource(folder + "/" + j, folder, folders[i] + "资源 " + (j + 1), false));
        }
        Signal<List<Resource>> source = page.domain().signal(initial);
        Signal<Set<String>> expanded = page.domain().signal(new LinkedHashSet<>(Arrays.asList("project", "folder-0")));
        Signal<String> opened = page.domain().signal(null), feedback = page.domain().signal("打开一个资源，或切换名称编辑后按 Enter 应用。");
        Signal<Boolean> editing = page.domain().signal(false);
        Signal<Set<String>> disabled = page.domain().signal(Collections.singleton("folder-1/0"));
        Map<String, SceneEditState> drafts = new HashMap<>();
        SceneTreeView<Resource, String> tree = SceneTreeView.create(page, source, item -> item.key, item -> item.parent, expanded, request -> {
            Set<String> next = new LinkedHashSet<>(expanded.get());
            if (request.expanded) next.add(request.key); else next.remove(request.key);
            expanded.set(Collections.unmodifiableSet(next));
        }, disabled, item -> item.name, entry -> {
            String identity = entry.get().key;
            SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural()).padding(6).gap(10)
                    .focusable(true).clickFocus(true).onClick(() -> { opened.set(identity); feedback.set("已打开：" + entry.get().value.name); });
            row.bind(NodeBinding.value(() -> page.runtime().focusRouter().focusVisible(row) ? theme.get().hover
                    : Objects.equals(opened.get(), identity) ? theme.get().pressed : 0x00000000, SceneNode::background));
            row.add(SceneIconButton.graphic(page, () -> entry.get().value.directory ? SceneIconButton.Symbol.FOLDER : SceneIconButton.Symbol.FILE, theme)
                    .size(Size.fixed(24), Size.fixed(24)));
            row.add(GalleryStyle.label(page, () -> entry.get().value.name, theme, 15, false).size(Size.fill(1), Size.natural()));
            if (!entry.get().value.directory) {
                SceneEditState draft = drafts.computeIfAbsent(identity, ignored -> new SceneEditState(page.domain(), entry.get().value.name));
                SceneNode field = SceneTextField.create(page, draft, theme, () -> false, request -> {
                    String name = request.text.trim();
                    if (name.isEmpty()) { draft.reject(request, "名称不能为空"); return; }
                    if (draft.accept(request, name)) {
                        List<Resource> next = new ArrayList<>();
                        for (Resource item : source.get()) next.add(item.key.equals(identity) ? new Resource(item.key, item.parent, name, false) : item);
                        source.set(next); feedback.set("名称已更新：" + name);
                    }
                }).size(Size.fixed(220).bounds(100, 260), Size.natural());
                field.bind(NodeBinding.value(editing, SceneNode::visible));
                field.bind(NodeBinding.semantics(() -> SceneSemantics.builder().name("重命名：" + entry.get().value.name).build()));
                row.add(field);
            }
            return row;
        }, () -> new SceneTreeView.Options(), theme);
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(28).gap(18);
        root.add(GalleryStyle.header(page, theme, "项目资源", "方向键浏览与展开，Home/End 到首末，键入名称跳转；Tab 编辑当前条目。", back));
        SceneNode tools = GalleryStyle.centeredRow().size(Size.fill(1), Size.natural()).gap(12);
        tools.add(GalleryStyle.button(page, () -> "展开全部目录", theme, () -> expanded.set(Collections.unmodifiableSet(new LinkedHashSet<>(allDirectories)))));
        tools.add(GalleryStyle.button(page, () -> "折叠全部目录", theme, () -> expanded.set(Collections.emptySet())));
        tools.add(GalleryStyle.button(page, () -> editing.get() ? "收起名称编辑" : "编辑名称", theme, () -> editing.set(!editing.get())));
        tools.add(GalleryStyle.button(page, () -> disabled.get().contains("folder-1") ? "启用模型目录" : "禁用模型目录", theme, () -> {
            Set<String> next = new LinkedHashSet<>(disabled.get());
            if (!next.remove("folder-1")) next.add("folder-1"); disabled.set(Collections.unmodifiableSet(next));
        }));
        tools.add(GalleryStyle.button(page, () -> "反转同级顺序", theme, () -> { List<Resource> next = new ArrayList<>(source.get()); Collections.reverse(next); source.set(next); }));
        tree.node().bind(NodeBinding.semantics(() -> SceneSemantics.builder().name("项目资源").build()));
        root.add(tools).add(GalleryStyle.frame(tree.node(), theme).size(Size.fill(1), Size.fill(1)));
        root.add(GalleryStyle.label(page, feedback, theme, 14, true));
        return root;
    }
}
