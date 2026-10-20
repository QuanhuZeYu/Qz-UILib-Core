package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 操作与预期在共享编译单元，驱动只供给真实宿主输入和呈现事实，不操作控件回调。 */
public final class GalleryJourney {
    public interface Driver {
        void frame();

        SceneGeometryView geometry();

        PaintPlan paint();

        void click(int x, int y);

        void replaceText(String value);

        void scroll(int x, int y, int delta);

        void command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command command, boolean shift);

        void escape();

        void typeText(String value);

        club.heiqi.uilib.ui.scene.node.SceneNode focused();

        default void evidence(String name) {}

        default boolean resize(int width, int height) {
            return false;
        }

        default boolean pointer(
                club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind kind, int x, int y) {
            return false;
        }
    }

    public static void run(Driver driver) {
        driver.frame();
        require(text(driver, "组件广场"), "默认入口不是参考案例目录");
        require(
                actionTarget(driver, "打开收件箱") != null && actionTarget(driver, "打开配置") != null,
                "综合应用入口缺失");
        requireCategories(driver);
        require(
                !contains(driver, "打开知识库")
                        && !contains(driver, "打开项目设置")
                        && !contains(driver, "打开图像与公式"),
                "旧页面仍在目录中");
        settings(driver);
        mailbox(driver);
        components(driver);
        click(driver, "场景概览");
    }

    /** 从真实目录逐页进入并返回，覆盖嵌套 FILL、有限集合和保留页面的窗口变化。 */
    public static void visitAll(Driver driver) {
        for (int[] viewport : new int[][] {{1400, 900}, {2560, 1480}, {480, 900}}) {
            require(driver.resize(viewport[0], viewport[1]), "目录验证驱动必须支持无窗口视口变化");
            driver.frame();
            for (TestGallery.Page page : TestGallery.Page.values()) {
                if (page == TestGallery.Page.HOME) continue;
                click(driver, "打开" + page.title);
                driver.frame();
                require(!driver.paint().commands().isEmpty(), "页面没有实际绘制计划：" + page);
                for (SceneGeometryView.Entry entry : driver.geometry().ordered()) {
                    require(
                            entry.box.width >= 0 && entry.box.height >= 0,
                            "页面出现负尺寸：" + page + " " + entry.box);
                }
                String back =
                        page == TestGallery.Page.SETTINGS
                                ? "返回"
                                : page == TestGallery.Page.MAILBOX ? "场景概览" : "返回场景概览";
                click(driver, back);
                require(
                        actionTarget(driver, "打开收件箱") != null
                                && actionTarget(driver, "打开基础输入") != null,
                        "页面未返回同一分类目录：" + page);
            }
        }
    }

    private static void requireCategories(Driver driver) {
        java.util.EnumSet<TestGallery.Page> covered =
                java.util.EnumSet.noneOf(TestGallery.Page.class);
        for (GalleryDirectory.Category category : GalleryDirectory.Category.values()) {
            require(actionTarget(driver, category.title) != null, "分类标题缺失：" + category.title);
            for (TestGallery.Page page : category.pages) {
                require(covered.add(page), "组件重复归类：" + page);
                require(actionTarget(driver, "打开" + page.title) != null, "分类组件入口缺失：" + page);
            }
        }
        java.util.EnumSet<TestGallery.Page> expected =
                java.util.EnumSet.allOf(TestGallery.Page.class);
        expected.remove(TestGallery.Page.HOME);
        require(covered.equals(expected), "分类目录遗漏共享组件页面");
    }

    /** 同一输入旅程用于无窗口集成与真实 GLFW，避免示例成为只有外观的空壳。 */
    public static void components(Driver driver) {
        driver.resize(1400, 900);
        driver.frame();
        go(driver, TestGallery.Page.FORMS);
        SceneGeometryView.Entry field =
                driver.geometry().ordered().stream()
                        .filter(
                                e ->
                                        e.node.control() != null
                                                && e.node.control().acceptsTextInput(e.node))
                        .findFirst()
                        .get();
        SceneGeometryView.Entry label = actionTarget(driver, "项目名称");
        require(
                label != null
                        && label.box.width > 0
                        && field.box.y > label.box.y + label.box.height,
                "默认字段标签没有按竖排布置");
        clickField(driver, 0);
        driver.command(SceneEditEvent.Command.CONFIRM, false);
        driver.frame();
        require(text(driver, "请填写名称后再提交。"), "公共字段未展示正式编辑错误");
        driver.replaceText("组件项目");
        driver.command(SceneEditEvent.Command.CONFIRM, false);
        driver.frame();
        require(text(driver, "已接受：组件项目"), "字段提交未保持业务确认");
        clickField(driver, 1);
        driver.replaceText("弹窗");
        driver.frame();
        require(text(driver, "搜索结果：弹窗"), "公共搜索框没有提交查询");
        namedAction(driver, "清空搜索");
        require(text(driver, "全部示例内容"), "公共搜索清空没有更新查询");
        click(driver, "返回场景概览");

        go(driver, TestGallery.Page.DIALOGS);
        click(driver, "打开确认框");
        click(driver, "确认操作");
        require(text(driver, "已确认操作"), "公共确认弹窗未发布业务结果");
        click(driver, "打开输入框");
        click(driver, "选择预设名称");
        driver.escape();
        driver.frame();
        require(actionTarget(driver, "选择预设名称") != null, "嵌套 ESC 未只关闭顶层选择");
        click(driver, "选择预设名称");
        click(driver, "Beta 项目");
        require(actionTarget(driver, "选择预设名称") != null, "选择完成未返回输入弹窗");
        driver.escape();
        driver.frame();
        require(text(driver, "已选择：Beta 项目"), "内容选择没有发布结果");
        require(driver.geometry().activeModal() == null, "关闭弹窗仍拦截页面输入");
        click(driver, "返回场景概览");

        go(driver, TestGallery.Page.FILE_PICKER);
        click(driver, "打开文件选择器");
        require(driver.geometry().activeModal() != null, "独立文件选择入口没有打开正式模态");
        driver.escape();
        driver.frame();
        require(driver.geometry().activeModal() == null, "取消文件选择仍拦截页面输入");
        click(driver, "返回场景概览");

        go(driver, TestGallery.Page.MEDIA);
        SceneGeometryView.Entry mediaBefore =
                driver.geometry().ordered().stream()
                        .filter(
                                e ->
                                        e.node.control() != null
                                                && e.node.control().acceptsTextInput(e.node))
                        .findFirst()
                        .get();
        click(driver, "选择本地视频");
        SceneGeometryView.Entry dialog = driver.geometry().get(driver.geometry().activeModal());
        require(
                dialog != null
                        && dialog.box.width == driver.geometry().viewport().width
                        && dialog.box.height == driver.geometry().viewport().height,
                "媒体文件选择未覆盖完整视口");
        SceneGeometryView.Entry mediaDuring = driver.geometry().get(mediaBefore.node);
        require(
                mediaDuring != null
                        && mediaBefore.box.x == mediaDuring.box.x
                        && mediaBefore.box.y == mediaDuring.box.y
                        && mediaBefore.box.width == mediaDuring.box.width
                        && mediaBefore.box.height == mediaDuring.box.height,
                "媒体文件选择挤压原正文");
        driver.escape();
        driver.frame();
        click(driver, "返回场景概览");

        go(driver, TestGallery.Page.DOCUMENTS);
        require(text(driver, "普通文字与 粗体、斜体，以及 可操作链接。"), "富文本示例退化为源码");
        click(driver, "返回场景概览");

        go(driver, TestGallery.Page.COLLECTIONS);
        namedAction(driver, "下一页");
        require(text(driver, "第 2 页 / 共 4 页"), "公共分页未触发下一页");
        click(driver, "项目 7");
        require(text(driver, "项目 7 的详情"), "公共主从区域没有呈现选中详情");
        click(driver, "返回列表");
        require(actionTarget(driver, "项目 7") != null, "公共主从返回丢失当前列表页");
        click(driver, "返回场景概览");

        go(driver, TestGallery.Page.NOTICES);
        click(driver, "显示错误");
        require(text(driver, "操作失败"), "公共错误反馈未显示");
        click(driver, "清除错误");
        require(!text(driver, "操作失败"), "公共错误反馈未按业务状态撤销");
        click(driver, "显示成功");
        require(contains(driver, "已保存示例"), "公共短暂反馈没有显示");
        click(driver, "返回场景概览");
        go(driver, TestGallery.Page.NOTIFICATIONS);
        click(driver, "连续三条");
        require(contains(driver, "保存成功"), "通知广场未追加轻提示");
        click(driver, "显示持续通知");
        click(driver, "显示带动作通知");
        require(contains(driver, "新的协作消息") && actionTarget(driver, "撤销归档") != null,
                "通知广场未展示持续内容和真实操作");
        driver.evidence("notifications-stacked");
        click(driver, "撤销归档");
        require(text(driver, "已执行撤销：1"), "通知动作没有更新业务结果");
        click(driver, "关闭通知");
        require(actionTarget(driver, "关闭通知") == null, "关闭后仍保留通知输入资格");
        click(driver, "切换通知位置");
        click(driver, "清空通知");
        driver.evidence("notifications-cleared");
        click(driver, "返回场景概览");
        System.out.println(
                "GALLERY_COMPONENTS field=true search=true nestedDialog=true choice=true"
                        + " pagination=true masterDetail=true notice=true notifications=true");
    }

    private static void settings(Driver driver) {
        go(driver, TestGallery.Page.SETTINGS);
        require(
                text(driver, "外观")
                        && action(driver, "管理字体来源")
                        && !action(driver, "下移")
                        && text(driver, "已保存"),
                "配置案例未引用正式设置页面");
        click(driver, "外观");
        require(text(driver, "界面主题"), "外观分类缺失");
        click(driver, "深色外观");
        require(text(driver, "有未应用的更改"), "主题选择未保留草稿阶段");
        click(driver, "配置文件");
        require(text(driver, "有未应用的更改") && text(driver, "文件路径"), "切换分类丢失主题草稿");
        click(driver, "返回");
        require(text(driver, "离开配置工作区？"), "返回绕过正式草稿确认");
        click(driver, "继续编辑");
        require(text(driver, "有未应用的更改"), "取消离开丢失草稿");
        click(driver, "放弃编辑");
        require(text(driver, "已保存"), "放弃编辑未回到当前生效配置");
        click(driver, "返回");
        require(actionTarget(driver, "打开收件箱") != null, "配置返回没有回到同一工作台");
        click(driver, "打开配置");
        click(driver, "外观");
        click(driver, "深色外观");
        click(driver, "返回");
        click(driver, "不保存并离开");
        require(actionTarget(driver, "打开配置") != null, "确认离开没有回到目录");
        click(driver, "打开配置");
        require(!text(driver, "离开配置工作区？") && text(driver, "已保存"), "确认离开后重入残留旧模态或草稿");
        click(driver, "返回");
        driver.evidence("gallery-settings-return");
    }

    private static void mailbox(Driver driver) {
        go(driver, TestGallery.Page.MAILBOX);
        driver.resize(1400, 900);
        driver.frame();
        driver.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind.MOVE, 1100, 40);
        driver.frame();
        driver.evidence("mailbox-inbox");
        driver.evidence("mailbox-navigation-motion");
        click(driver, "折叠导航");
        driver.evidence("mailbox-collapsed");
        click(driver, "展开导航");
        driver.frame();
        click(driver, "分类：推广");
        require(contains(driver, "工坊活动与本周精选"), "推广分类没有真实消息");
        driver.evidence("mailbox-promotions");
        click(driver, "分类：社交");
        require(contains(driver, "你关注的讨论"), "社交分类没有真实消息");
        driver.evidence("mailbox-social");
        click(driver, "分类：主要");
        if (driver.resize(2504, 1469)) {
            driver.frame();
            click(driver, "每页 25");
            driver.click(1100, 40);
            driver.frame();
            driver.evidence("mailbox-reference-size");
            click(driver, "每页 50");
        }
        driver.resize(1400, 900);
        driver.frame();
        if (driver.resize(1000, 760)) {
            driver.frame();
            driver.evidence("mailbox-medium");
        }
        driver.resize(1400, 900);
        driver.frame();
        click(driver, "放大文字");
        driver.evidence("mailbox-large-text");
        click(driver, "标准文字");
        driver.frame();
        SceneGeometryView.Entry rowSubject =
                driver.geometry().ordered().stream()
                        .filter(
                                e ->
                                        e.text != null
                                                && e.text.logicalText.equals("周五的设计评审：收件箱与阅读体验"))
                        .findFirst()
                        .get();
        int stateX = rowSubject.box.x + 16, stateY = rowSubject.box.y + rowSubject.box.height / 2;
        if (driver.pointer(
                club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind.MOVE, stateX, stateY)) {
            driver.frame();
            driver.evidence("mailbox-hover");
            driver.pointer(
                    club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind.DOWN, stateX, stateY);
            driver.frame();
            driver.evidence("mailbox-pressed");
            driver.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind.MOVE, 180, 40);
            driver.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind.UP, 180, 40);
            driver.frame();
            driver.evidence("mailbox-cancelled");
            require(!contains(driver, "这封邮件记录"), "取消主体按压仍打开邮件");
            club.heiqi.uilib.ui.scene.node.SceneNode rowAction = rowSubject.node;
            while (rowAction != null && rowAction.clickAction() == null)
                rowAction = rowAction.parent();
            require(rowAction != null, "邮件内容没有复合动作来源");
            for (int step = 0; step < 32 && driver.focused() != rowAction; step++) {
                driver.command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.TAB, false);
                driver.frame();
            }
            require(driver.focused() == rowAction, "Tab未到达邮件主体合法焦点");
            driver.evidence("mailbox-focus");
        }
        namedAction(driver, "林晓 · 产品设计\n周五");
        require(contains(driver, "这封邮件记录"), "无查询的宽屏旁读没有正文");
        driver.evidence("mailbox-reading-unfiltered");
        if (driver.resize(1120, 900)) {
            driver.frame();
            driver.evidence("mailbox-reading-threshold");
        }
        driver.resize(1400, 900);
        driver.frame();
        click(driver, "返回列表");
        SceneGeometryView.Entry field =
                driver.geometry().ordered().stream()
                        .filter(
                                e ->
                                        e.node.control() != null
                                                && e.node.control().acceptsTextInput(e.node))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("收件箱没有正式搜索字段"));
        driver.click(field.box.x + 10, field.box.y + field.box.height / 2);
        driver.evidence("mailbox-search-focus");
        driver.replaceText("Café");
        driver.frame();
        SceneGeometryView.Entry message =
                driver.geometry().ordered().stream()
                        .filter(
                                e ->
                                        e.text != null
                                                && e.text.logicalText.contains("本周工坊精选")
                                                && visible(e))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("搜索没有当前消息"));
        club.heiqi.uilib.ui.scene.layout.LayoutBox messageHit =
                message.clip == null ? message.box : message.box.intersect(message.clip);
        driver.click(messageHit.x + messageHit.width / 2, messageHit.y + messageHit.height / 2);
        driver.frame();
        require(contains(driver, "这封邮件记录"), "收件箱阅读未显示正文");
        driver.evidence("mailbox-reading");
        click(driver, "返回列表");
        require(contains(driver, "本周工坊精选"), "阅读返回没有保留查询");
        click(driver, "清空搜索");
        SceneGeometryView.Entry emptyField =
                driver.geometry().ordered().stream()
                        .filter(
                                e ->
                                        e.node.control() != null
                                                && e.node.control().acceptsTextInput(e.node))
                        .findFirst()
                        .get();
        driver.click(emptyField.box.x + 12, emptyField.box.y + emptyField.box.height / 2);
        driver.replaceText("不存在的邮件主题");
        driver.frame();
        require(contains(driver, "没有匹配的邮件"), "空结果未显示真实说明");
        driver.evidence("mailbox-empty");
        click(driver, "清空搜索");
        namedAction(driver, "选择邮件：周五");
        namedAction(driver, "添加星标：周五");
        require(contains(driver, "已选择 1 封"), "行内选择没有批量目标");
        driver.evidence("mailbox-selection");
        namedAction(driver, "归档已选邮件");
        require(contains(driver, "已归档 1 封邮件"), "批量归档没有实际反馈");
        click(driver, "所有邮件");
        require(contains(driver, "周五的设计评审"), "所有邮件没有归档对象");
        driver.evidence("mailbox-all-mail");
        if (driver.resize(720, 900)) {
            driver.frame();
            driver.evidence("mailbox-narrow");
            click(driver, "收件箱");
            driver.frame();
            driver.evidence("mailbox-narrow-inbox");
            SceneGeometryView.Entry current =
                    driver.geometry().ordered().stream()
                            .filter(
                                    e ->
                                            e.text != null
                                                    && e.text.logicalText.contains("周五的设计评审")
                                                    && visible(e))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("窄窗口消息不可达"));
            club.heiqi.uilib.ui.scene.layout.LayoutBox hit =
                    current.clip == null ? current.box : current.box.intersect(current.clip);
            driver.click(hit.x + hit.width / 2, hit.y + hit.height / 2);
            driver.frame();
            require(contains(driver, "这封邮件记录"), "窄窗口不能进入阅读");
            driver.evidence("mailbox-narrow-reading");
            click(driver, "返回列表");
            if (action(driver, "预览深色")) click(driver, "预览深色");
            require(
                    driver.geometry().ordered().stream()
                            .anyMatch(e -> "预览浅色".equals(e.node.text())),
                    "邮箱主题图标点击未发布深色预览");
            driver.evidence("mailbox-dark");
            if (action(driver, "预览浅色")) click(driver, "预览浅色");
            driver.resize(1400, 1100);
            driver.frame();
        }
    }

    private static void namedAction(Driver driver, String name) {
        SceneGeometryView.Entry e =
                driver.geometry().ordered().stream()
                        .filter(
                                v ->
                                        v.node.focusable()
                                                && v.node.text() != null
                                                && v.node.text().startsWith(name)
                                                && visible(v))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("没有可见命名操作：" + name));
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit =
                e.clip == null ? e.box : e.box.intersect(e.clip);
        driver.click(hit.x + hit.width / 2, hit.y + hit.height / 2);
        driver.frame();
    }

    private static boolean visible(SceneGeometryView.Entry entry) {
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit =
                entry.clip == null ? entry.box : entry.box.intersect(entry.clip);
        return hit.width > 0 && hit.height > 0;
    }

    private static boolean contains(Driver driver, String value) {
        return driver.paint().commands().stream()
                .anyMatch(
                        c ->
                                c.kind == PaintCommand.Kind.TEXT
                                        && (c.text.originalText == null
                                                        ? c.text.logicalText
                                                        : c.text.originalText)
                                                .contains(value));
    }

    private static void go(Driver driver, TestGallery.Page page) {
        click(driver, "场景概览");
        click(driver, "打开" + page.title);
    }

    private static boolean action(Driver driver, String value) {
        return driver.geometry().ordered().stream()
                .anyMatch(
                        e ->
                                e.node.clickAction() != null
                                        && value.equals(e.node.text())
                                        && visible(e));
    }

    private static boolean text(Driver driver, String value) {
        return driver.paint().commands().stream()
                .anyMatch(
                        c -> c.kind == PaintCommand.Kind.TEXT && value.equals(c.text.logicalText));
    }

    private static SceneGeometryView.Entry actionTarget(Driver driver, String value) {
        SceneGeometryView.Entry action =
                driver.geometry().ordered().stream()
                        .filter(e -> e.node.clickAction() != null && value.equals(e.node.text()))
                        .findFirst()
                        .orElse(null);
        if (action != null) return action;
        return driver.geometry().ordered().stream()
                .filter(
                        e ->
                                e.node.clickAction() != null && value.equals(e.node.text())
                                        || e.text != null && value.equals(e.text.logicalText))
                .findFirst()
                .orElse(null);
    }

    private static void clickField(Driver driver, int index) {
        clickTarget(
                driver,
                () ->
                        driver.geometry().ordered().stream()
                                .filter(
                                        e ->
                                                e.node.control() != null
                                                        && e.node.control()
                                                                .acceptsTextInput(e.node))
                                .skip(index)
                                .findFirst()
                                .orElse(null),
                "编辑字段 " + index);
    }

    private static void click(Driver driver, String value) {
        clickTarget(driver, () -> actionTarget(driver, value), value);
    }

    private static void clickTarget(
            Driver driver,
            java.util.function.Supplier<SceneGeometryView.Entry> locate,
            String value) {
        SceneGeometryView.Entry target;
        for (int attempts = 0; ; attempts++) {
            require(attempts < 80, "目标反复滚动仍不可达：" + value);
            target = locate.get();
            require(target != null, "找不到输入目标：" + value);
            if (visible(target)) break;
            SceneGeometryView geometry = driver.geometry();
            club.heiqi.uilib.ui.scene.layout.LayoutBox limit =
                    target.clip == null ? geometry.viewport() : target.clip;
            boolean above = target.box.y < limit.y;
            SceneGeometryView.Entry scroller = null;
            for (club.heiqi.uilib.ui.scene.node.SceneNode parent = target.node.parent();
                    parent != null;
                    parent = parent.parent()) {
                SceneGeometryView.Entry candidate = geometry.get(parent);
                if (parent.scrollable()
                        && candidate != null
                        && visible(candidate)
                        && (above
                                ? candidate.scrollOffset > 0
                                : candidate.scrollOffset < candidate.scrollMaximum)) {
                    scroller = candidate;
                    break;
                }
                if (parent == geometry.layerBoundary(target.node)) break;
            }
            require(
                    scroller != null,
                    "目标被裁剪且没有可移动的滚动祖先："
                            + value
                            + " viewport="
                            + geometry.viewport()
                            + " target="
                            + target.box
                            + " clip="
                            + target.clip);
            club.heiqi.uilib.ui.scene.layout.LayoutBox area =
                    scroller.clip == null ? scroller.box : scroller.box.intersect(scroller.clip);
            long distance =
                    above
                            ? (long) area.y - target.box.y
                            : (long) target.box.y + target.box.height - area.y - area.height;
            int step = scroller.node.scrollStep();
            int ticks =
                    (int) Math.min(Integer.MAX_VALUE, Math.max(1, (distance + step - 1) / step));
            int previous = scroller.scrollOffset;
            club.heiqi.uilib.ui.scene.node.SceneNode owner = scroller.node;
            // 使用原滚动端口和当前共同几何显露目标，不修改offset或目标的Tab资格。
            driver.scroll(area.x + 1, area.y + area.height / 2, above ? ticks : -ticks);
            driver.frame();
            SceneGeometryView.Entry next = driver.geometry().get(owner);
            require(
                    next != null && next.scrollOffset != previous,
                    "滚轮未推进目标的实际滚动祖先："
                            + value
                            + " offset="
                            + previous
                            + " maximum="
                            + scroller.scrollMaximum
                            + " target="
                            + target.box
                            + " area="
                            + area);
        }
        club.heiqi.uilib.ui.scene.layout.LayoutBox hit =
                target.clip == null ? target.box : target.box.intersect(target.clip);
        driver.click(hit.x + hit.width / 2, hit.y + hit.height / 2);
        driver.frame();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
