package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.*;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneShortcut;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

final class FormComponentsPage {
    private FormComponentsPage() {}

    static SceneNode create(ScenePageContext p, ReadableSignal<SceneTheme> theme, Runnable back, GalleryCompositeLabels labels) {
        ComponentExamples e =
                new ComponentExamples(p, theme, "表单与搜索", "标签、说明、校验结果与附属动作使用同一公共字段组合。", back);
        SceneEditState name = new SceneEditState(p.domain(), "");
        Signal<String> accepted = p.domain().signal("尚未提交");
        SceneNode editor =
                SceneTextField.create(
                        p,
                        name,
                        theme,
                        () -> false,
                        submission -> {
                            String value = submission.text.trim();
                            if (value.isEmpty()) name.reject(submission, "请填写名称后再提交。");
                            else if (name.accept(submission, value)) accepted.set(value);
                        },
                        SceneTextField.SubmitPolicy.CONFIRM,
                        () -> "输入名称后按 Enter",
                        false);
        SceneNode reset =
                e.style.action(
                        "恢复示例", null, () -> name.reset("示例项目"), SceneAppearance.Role.OUTLINE);
        e.section(
                "表单字段",
                "提交空白名称会显示错误，错误保留在原编辑状态中。",
                SceneField.create(
                        p,
                        theme,
                        () ->
                                new SceneField.Labels(
                                        "项目名称",
                                        "名称会在确认时去掉首尾空白。",
                                        name.snapshot().get().error == null
                                                ? ""
                                                : name.snapshot().get().error),
                        new SceneField.Content(e.style.field(editor), editor, reset)),
                e.style.label(() -> "已接受：" + accepted.get(), 1, true));
        Signal<Boolean> automatic = p.domain().signal(true);
        SceneNode toggle = SceneSwitch.create(p, automatic, () -> "自动保存", theme, automatic::set);
        e.section(
                "自适应设置行",
                "宽窗口并列说明与控件，空间不足时自然堆叠。",
                SceneField.create(
                        p,
                        theme,
                        () -> new SceneField.Labels("自动保存", "保存策略由业务选择；组件只组合说明与开关。", "", true),
                        toggle));
        SceneEditState query = new SceneEditState(p.domain(), "");
        Signal<String> filter = p.domain().signal("");
        SceneSearchField.View search =
                SceneSearchField.create(
                        p,
                        query,
                        theme,
                        s -> {
                            if (query.accept(s, s.text)) filter.set(s.text);
                        },
                        SceneTextField.SubmitPolicy.INPUT,
                        () -> "搜索示例内容",
                        labels.clearSearch,
                        () -> {
                            query.reset("");
                            filter.set("");
                        });
        e.section(
                "搜索框",
                "输入立即更新结果；清空发布同一查询意图。使用 Primary+F 聚焦搜索。",
                search.node,
                e.style.label(
                        () -> filter.get().isEmpty() ? "全部示例内容" : "搜索结果：" + filter.get(),
                        1,
                        false));
        e.root.bind(
                NodeBinding.shortcuts(
                        SceneShortcut.Scope.PAGE, SceneShortcut.primary(SceneKey.F, search.focus)));
        return e.root;
    }
}
