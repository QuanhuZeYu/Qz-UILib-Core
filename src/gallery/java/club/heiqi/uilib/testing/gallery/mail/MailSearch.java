package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.testing.gallery.GalleryCompositeLabels;
import club.heiqi.uilib.ui.scene.control.SceneCommand;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 搜索表面只包正式单行字段；输入、组字、选区和提交不在案例中另实现。 */
final class MailSearch {
    private MailSearch() {}

    static final class View {
        final SceneNode node;
        final SceneCommand focus;

        View(SceneNode node, SceneCommand focus) {
            this.node = node;
            this.focus = focus;
        }
    }

    static View create(
            ScenePageContext p,
            ReadableSignal<SceneTheme> theme,
            ReadableSignal<MailPalette> palette,
            MailboxModel model, GalleryCompositeLabels labels) {
        SceneEditState query = new SceneEditState(p.domain(), model.query.get());
        Owner scope = p.domain().owner();
        String[] lastQuery = {model.query.get()};
        // 外部查询意图明确重置草稿；用户INPUT提交相同值不重置光标或组字。
        p.domain()
                .effect(
                        scope,
                        () -> {
                            String current = model.query.get();
                            if (!current.equals(lastQuery[0])) {
                                lastQuery[0] = current;
                                if (!current.equals(query.text().get())) query.reset(current);
                            }
                        });
        club.heiqi.uilib.ui.scene.control.SceneSearchField.View input =
                club.heiqi.uilib.ui.scene.control.SceneSearchField.content(
                        p,
                        query,
                        theme,
                        submission -> {
                            if (query.accept(submission, submission.text))
                                model.setQuery(submission.text);
                        },
                        SceneTextField.SubmitPolicy.INPUT,
                        () -> "搜索邮件",
                        labels.clearSearch,
                        () -> {
                            query.reset("");
                            model.setQuery("");
                        });
        SceneNode field = input.editor;
        ReadableSignal<Boolean> focused =
                () ->
                        field.runtime() != null
                                && field.runtime().focusRouter().focused().get() == field;
        SceneNode search =
                MailStyle.surface(
                                theme,
                                new MailRaisedSurface(
                                        theme,
                                        () ->
                                                focused.get()
                                                        ? palette.get().surface
                                                        : palette.get().search,
                                        club.heiqi.uilib.ui.scene.control.SceneAppearance
                                                        .CONTROL_RADIUS
                                                / 14f,
                                        focused,
                                        MailMotion.SEARCH_BACKGROUND,
                                        MotionSpec.instant()))
                        .axis(SceneNode.Axis.ROW)
                        .size(Size.fill(3).bounds(0, 720), Size.natural())
                        .gap(4)
                        .padding(4)
                        .own(scope);
        search.bind(
                NodeBinding.value(
                        theme,
                        (n, t) ->
                                n.padding(MailStyle.px(t, 4))
                                        .gap(MailStyle.px(t, 4))
                                        .size(
                                                Size.fill(3).bounds(0, MailStyle.px(t, 720)),
                                                Size.natural()
                                                        .bounds(
                                                                MailStyle.px(t, 48),
                                                                Integer.MAX_VALUE))));
        field.bind(
                NodeBinding.value(
                        theme,
                        (n, t) ->
                                n.padding(MailStyle.px(t, 4))
                                        .style(
                                                t.textStyle.withEm(
                                                        t.textStyle.logicalEm * 16f / 14f))));
        search.add(input.node);
        SceneCommand focus = input.focus;
        return new View(search, focus);
    }
}
