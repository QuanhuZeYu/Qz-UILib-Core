package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 用真实目录与编辑提交防住混排、跨页漏项及旧确认覆盖新草稿。 */
public final class FilePickerScenario extends TestApplication {
    @Override
    protected void runTests() throws Exception {
        scenario(
                "共享字符前缀保留文件窗口并支持重复首字母",
                () -> {
                    List<String> names = Arrays.asList("Café", "Cava", "Bravo");
                    SceneTypeahead files = new SceneTypeahead(800_000_000L, 128),
                            other = new SceneTypeahead();
                    require(
                            files.find(names, value -> value, 2, "c", 0) == 0
                                    && other.find(names, value -> value, 2, "c", 0) == 0,
                            "首次字符跳转未消费共同名称匹配");
                    require(
                            files.find(names, value -> value, 0, "b", 900_000_000L) == 2,
                            "文件选择器失去原有较短窗口");
                    require(
                            other.find(names, value -> value, 0, "b", 900_000_000L) == -1,
                            "普通集合窗口被文件策略改写");
                    files.reset();
                    require(
                            files.find(names, value -> value, 2, "c", 0) == 0
                                    && files.find(names, value -> value, 0, "c", 100_000_000L) == 1,
                            "重复首字母未循环同前缀项");
                    files.reset();
                    require(files.find(names, value -> value, 2, "caf", 0) == 0, "名称重音未沿共同规则归一化");
                    files.reset();
                    List<String> spaced = Arrays.asList("分页文件 001", "分页文件 050");
                    int[] focused = {0};
                    "分页文件 05"
                            .codePoints()
                            .forEach(
                                    cp -> {
                                        int found =
                                                files.find(
                                                        spaced,
                                                        value -> value,
                                                        focused[0],
                                                        new String(Character.toChars(cp)),
                                                        100_000_000L);
                                        if (found >= 0) focused[0] = found;
                                    });
                    require(focused[0] == 1, "逐字符输入丢失文件名前缀中的空格");
                });
        scenario(
                "目录优先自然名称及超长数字段",
                () -> {
                    List<FilePickerDirectory.Entry> entries = new ArrayList<>();
                    for (String name : Arrays.asList("片10", "片02", "片2", "片1", "目录10", "目录2"))
                        entries.add(
                                new FilePickerDirectory.Entry(
                                        Paths.get(name), name.startsWith("目录")));
                    entries.sort(FilePickerOrder.NAME);
                    require(
                            entries.stream()
                                    .map(FilePickerDirectory.Entry::name)
                                    .collect(Collectors.toList())
                                    .equals(Arrays.asList("目录2", "目录10", "片1", "片2", "片02", "片10")),
                            "目录优先或自然数字顺序错误");
                    require(
                            FilePickerOrder.natural(
                                            "片999999999999999999999999",
                                            "片1000000000000000000000000")
                                    < 0,
                            "数字段溢出或按字典序比较");
                    require(FilePickerOrder.natural("VIDEO2", "video10") < 0, "大小写改变自然名称语义");
                });
        scenario(
                "真实全目录有序前后页与定位刷新",
                () -> {
                    Path root = Files.createTempDirectory("uilib 有序目录 +");
                    try (FilePickerDirectory reader = new FilePickerDirectory()) {
                        for (int i = 79; i >= 0; i--) {
                            Files.createFile(root.resolve("视频 " + i + ".ViDeO"));
                            Files.createDirectory(root.resolve("目录 " + i));
                        }
                        List<String> expected = new ArrayList<>();
                        for (int i = 0; i < 80; i++) expected.add("目录 " + i);
                        for (int i = 0; i < 80; i++) expected.add("视频 " + i + ".ViDeO");
                        long request = 1;
                        reader.browse(request, root, FilePickerQuery.first());
                        FilePickerDirectory.Result result = await(reader);
                        List<String> actual = new ArrayList<>();
                        while (true) {
                            require(
                                    result.error == null
                                            && result.entries.size()
                                                    <= FilePickerDirectory.PAGE_SIZE,
                                    "真实目录读取失败或页预算无界");
                            for (FilePickerDirectory.Entry e : result.entries) actual.add(e.name());
                            if (!result.next) break;
                            reader.browse(
                                    ++request,
                                    root,
                                    FilePickerQuery.after(
                                            result.page + 1,
                                            result.entries.get(result.entries.size() - 1)));
                            result = await(reader);
                        }
                        require(actual.equals(expected), "跨页顺序不一致、漏项或重复：" + actual);
                        while (result.previous) {
                            reader.browse(
                                    ++request,
                                    root,
                                    FilePickerQuery.before(result.page - 1, result.entries.get(0)));
                            result = await(reader);
                        }
                        require(
                                result.entries.stream()
                                        .map(FilePickerDirectory.Entry::name)
                                        .collect(Collectors.toList())
                                        .equals(expected.subList(0, FilePickerDirectory.PAGE_SIZE)),
                                "返回第一页没有恢复同一有序内容");
                        Path chosen = root.resolve("视频 70.ViDeO");
                        reader.locate(++request, chosen);
                        result = await(reader);
                        require(
                                chosen.equals(result.selected)
                                        && result.entries.stream()
                                                .anyMatch(e -> e.path.equals(chosen)),
                                "直接定位文件没有显露当前目标");
                        require(
                                result.page == 2
                                        && result.entries.stream()
                                                .map(FilePickerDirectory.Entry::name)
                                                .collect(Collectors.toList())
                                                .equals(expected.subList(128, 160)),
                                "定位文件没有落在同一全局有序页");
                        reader.browse(
                                ++request, root, FilePickerQuery.before(1, result.entries.get(0)));
                        result = await(reader);
                        require(
                                result.page == 1
                                        && result.entries.stream()
                                                .map(FilePickerDirectory.Entry::name)
                                                .collect(Collectors.toList())
                                                .equals(expected.subList(64, 128)),
                                "定位后上一页与正常分页边界不同");
                        reader.browse(
                                ++request, root, FilePickerQuery.before(0, result.entries.get(0)));
                        result = await(reader);
                        require(
                                result.page == 0
                                        && !result.previous
                                        && result.entries.stream()
                                                .map(FilePickerDirectory.Entry::name)
                                                .collect(Collectors.toList())
                                                .equals(expected.subList(0, 64)),
                                "定位后出现不同内容的重复第一页");
                        reader.browse(
                                ++request,
                                root,
                                FilePickerQuery.after(
                                        1, result.entries.get(result.entries.size() - 1)));
                        result = await(reader);
                        require(
                                result.page == 1
                                        && result.entries.stream()
                                                .map(FilePickerDirectory.Entry::name)
                                                .collect(Collectors.toList())
                                                .equals(expected.subList(64, 128)),
                                "定位后的前后翻页没有回到同一内容");
                        reader.browse(
                                ++request,
                                root,
                                FilePickerQuery.after(
                                        2, result.entries.get(result.entries.size() - 1)));
                        result = await(reader);
                        require(
                                result.page == 2
                                        && !result.next
                                        && result.entries.stream()
                                                .map(FilePickerDirectory.Entry::name)
                                                .collect(Collectors.toList())
                                                .equals(expected.subList(128, 160)),
                                "前后往返没有回到原定位文件所属页");
                        Files.createDirectory(root.resolve("目录 -1"));
                        reader.browse(++request, root, FilePickerQuery.first());
                        result = await(reader);
                        require(result.entries.get(0).name().equals("目录 -1"), "刷新沿用了过期目录顺序");
                        System.out.println(
                                "FILE_PICKER_ORDER actualDirectory=true total="
                                        + actual.size()
                                        + " globalDirectoryFirst=true natural=true backward=true"
                                        + " locate=true refresh=true pageBudget="
                                        + FilePickerDirectory.PAGE_SIZE);
                    } finally {
                        deleteTree(root);
                    }
                });
        scenario(
                "全目录名称查询、扩展名筛选与元数据顺序",
                () -> {
                    Path root = Files.createTempDirectory("uilib 全局搜索");
                    try (FilePickerDirectory reader = new FilePickerDirectory()) {
                        for (int i = 0; i < 140; i++)
                            Files.write(root.resolve("font " + i + ".ttf"), new byte[i]);
                        Files.write(root.resolve("font 200.otf"), new byte[200]);
                        Files.createFile(root.resolve("other.any"));
                        Files.createDirectory(root.resolve("font folder"));
                        reader.browse(
                                1,
                                root,
                                FilePickerQuery.first()
                                        .filter("font 13", "")
                                        .order(FilePickerOrder.SIZE_DESC));
                        FilePickerDirectory.Result result = await(reader);
                        require(
                                result.entries.size() == 11
                                        && result.entries.get(0).name().equals("font 139.ttf")
                                        && result.entries
                                                .get(result.entries.size() - 1)
                                                .name()
                                                .equals("font 13.ttf"),
                                "查询只覆盖可见页或大小排序失效");
                        reader.browse(
                                2,
                                root,
                                FilePickerQuery.first()
                                        .filter("font", "otf")
                                        .order(FilePickerOrder.NAME));
                        result = await(reader);
                        require(
                                result.entries.size() == 2
                                        && result.entries.get(0).directory
                                        && result.entries.get(1).size == 200,
                                "扩展名过滤隐藏目录或元数据未在worker取得");
                        reader.browse(3, root, FilePickerQuery.first());
                        result = await(reader);
                        require(result.next, "恢复所有文件丢失分页");
                    } finally {
                        deleteTree(root);
                    }
                });
        scenario(
                "前后历史保留目录、选择与滚动位置",
                () -> {
                    Path root = Files.createTempDirectory("uilib 导航历史");
                    Path child = Files.createDirectory(root.resolve("child")),
                            file = Files.createFile(root.resolve("selected.any"));
                    ReactiveDomain domain = ReactiveDomain.direct();
                    FilePickerSelection[] state = {null};
                    try {
                        domain.execute(
                                () -> {
                                    state[0] =
                                            new FilePickerSelection(
                                                    domain, root, p -> {}, () -> {});
                                    state[0].attach();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].activate(new FilePickerDirectory.Entry(file, false));
                                    state[0].scrollPosition = () -> 123;
                                    state[0].navigate(child);
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(state[0].backCount.get() == 1, "进入目录没有前历史");
                                    state[0].history(-1);
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(
                                            state[0].directory.get().equals(root)
                                                    && file.equals(state[0].currentSelection())
                                                    && state[0].scroll.get() == 123,
                                            "返回没有恢复位置或选择");
                                    state[0].history(1);
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> require(state[0].directory.get().equals(child), "前进没有恢复目录"));
                    } finally {
                        domain.execute(
                                () -> {
                                    if (state[0] != null) state[0].detach();
                                });
                        deleteTree(root);
                    }
                });
        scenario(
                "直接定位后页文件再导航返回保留有序边界",
                () -> {
                    Path root = Files.createTempDirectory("uilib 后页定位");
                    Path child = Files.createDirectory(root.resolve("child"));
                    for (int i = 0; i < 150; i++)
                        Files.createFile(root.resolve("file " + i + ".any"));
                    Path chosen = root.resolve("file 149.any");
                    ReactiveDomain domain = ReactiveDomain.direct();
                    FilePickerSelection[] state = {null};
                    try {
                        domain.execute(
                                () -> {
                                    state[0] =
                                            new FilePickerSelection(
                                                    domain, root, p -> {}, () -> {});
                                    state[0].attach();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].path.reset(chosen.toString());
                                    state[0].go(state[0].path.request(true));
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(
                                            chosen.equals(state[0].currentSelection())
                                                    && chosen.equals(state[0].revealTarget.get())
                                                    && state[0].page.get() > 0,
                                            "反例没有定位到后页文件");
                                    state[0].navigate(child);
                                });
                        settle(domain, state[0]);
                        domain.execute(() -> state[0].history(-1));
                        settle(domain, state[0]);
                        domain.execute(
                                () ->
                                        require(
                                                chosen.equals(state[0].currentSelection())
                                                        && state[0].entries.get().stream()
                                                                .anyMatch(
                                                                        e -> chosen.equals(e.path))
                                                        && state[0].revealTarget.get() == null,
                                                "返回后页定位丢失原边界/文件选择或重新追随目标"));
                        domain.execute(() -> state[0].refresh());
                        settle(domain, state[0]);
                        domain.execute(
                                () -> require(
                                        chosen.equals(state[0].currentSelection())
                                                && state[0].revealTarget.get() == null,
                                        "刷新未保留合法选择或重新发布定位显露"));
                    } finally {
                        domain.execute(
                                () -> {
                                    if (state[0] != null) state[0].detach();
                                });
                        deleteTree(root);
                    }
                });
        scenario(
                "根范围清除文件目标并在前后历史中保持",
                () -> {
                    Path root = Files.createTempDirectory("uilib 根范围");
                    Path file = Files.createFile(root.resolve("selected.any"));
                    ReactiveDomain domain = ReactiveDomain.direct();
                    FilePickerSelection[] state = {null};
                    List<Path> chosen = new ArrayList<>();
                    try {
                        domain.execute(
                                () -> {
                                    state[0] = new FilePickerSelection(domain, root, chosen::add, () -> {});
                                    state[0].attach();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].activate(new FilePickerDirectory.Entry(file, false));
                                    state[0].showRoots(0);
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(
                                            state[0].roots.get()
                                                    && state[0].currentSelection() == null
                                                    && state[0].path.text().get().isEmpty(),
                                            "根范围继续把旧文件路径作为待确认目标");
                                    state[0].confirm();
                                    require(chosen.isEmpty() && state[0].showing.get(), "根范围确认提交了旧文件");
                                    state[0].history(-1);
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(file.equals(state[0].currentSelection()), "返回目录未恢复合法文件选择");
                                    state[0].history(1);
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> require(
                                        state[0].roots.get()
                                                && state[0].path.text().get().isEmpty()
                                                && state[0].currentSelection() == null,
                                        "前进到根范围复活了旧文件目标"));
                    } finally {
                        domain.execute(() -> { if (state[0] != null) state[0].detach(); });
                        deleteTree(root);
                    }
                });
        scenario(
                "根范围名称查询、类型筛选与历史使用同一顺序",
                () -> {
                    Path root = Files.createTempDirectory("uilib 根查询");
                    List<Path> roots = new ArrayList<>();
                    root.getFileSystem().getRootDirectories().forEach(
                            path -> roots.add(path.toAbsolutePath().normalize()));
                    roots.sort((left, right) -> right.toString().compareToIgnoreCase(left.toString()));
                    require(!roots.isEmpty(), "实际文件系统没有根目录");
                    String absent = "__uilib_no_such_root__";
                    require(roots.stream().noneMatch(path -> path.toString().contains(absent)), "根查询反例不独立");
                    String rootName = roots.get(0).toString();
                    ReactiveDomain domain = ReactiveDomain.direct();
                    FilePickerSelection[] state = {null};
                    try {
                        domain.execute(
                                () -> {
                                    state[0] = new FilePickerSelection(domain, root, p -> {}, () -> {});
                                    state[0].attach();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].extension.set("ttf");
                                    state[0].order.set(FilePickerOrder.NAME_DESC);
                                    state[0].search.reset(absent);
                                    state[0].query(state[0].search.request(true));
                                });
                        settle(domain, state[0]);
                        domain.execute(() -> state[0].showRoots(0));
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(
                                            state[0].roots.get()
                                                    && state[0].entries.get().isEmpty()
                                                    && state[0].searchTerm.get().equals(absent)
                                                    && state[0].order.get() == FilePickerOrder.NAME_DESC,
                                            "进入根范围丢失查询或名称过滤被旁路");
                                    state[0].clearQuery();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(
                                            state[0].entries.get().stream().map(entry -> entry.path)
                                                    .collect(Collectors.toList()).equals(
                                                            roots.subList(0, Math.min(FilePickerDirectory.PAGE_SIZE, roots.size())))
                                                    && state[0].entries.get().stream().allMatch(entry -> entry.directory)
                                                    && state[0].extension.get().equals("ttf"),
                                            "清除根查询没有恢复降序根目录或类型过滤隐藏了导航入口");
                                    state[0].search.reset(rootName);
                                    state[0].query(state[0].search.request(true));
                                });
                        settle(domain, state[0]);
                        domain.execute(() -> state[0].refresh());
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(
                                            state[0].entries.get().stream().map(entry -> entry.path)
                                                    .collect(Collectors.toList()).equals(Collections.singletonList(roots.get(0))),
                                            "根查询刷新没有保留当前名称条件");
                                    state[0].navigate(root);
                                });
                        settle(domain, state[0]);
                        domain.execute(() -> state[0].history(-1));
                        settle(domain, state[0]);
                        domain.execute(
                                () -> require(
                                        state[0].roots.get()
                                                && state[0].search.text().get().equals(rootName.toLowerCase(java.util.Locale.ROOT))
                                                && state[0].extension.get().equals("ttf")
                                                && state[0].order.get() == FilePickerOrder.NAME_DESC
                                                && state[0].entries.get().stream().map(entry -> entry.path)
                                                        .collect(Collectors.toList()).equals(Collections.singletonList(roots.get(0)))
                                                && state[0].path.text().get().isEmpty(),
                                        "历史根查询与显示字段、排序或结果不一致"));
                    } finally {
                        domain.execute(() -> { if (state[0] != null) state[0].detach(); });
                        deleteTree(root);
                    }
                });
        scenario(
                "未领取目录结果时刷新、搜索与上级沿当前请求范围",
                () -> {
                    Path root = Files.createTempDirectory("uilib 请求范围");
                    Path a = Files.createDirectory(root.resolve("A"));
                    Path b = Files.createDirectory(root.resolve("B"));
                    Path aChild = Files.createDirectory(a.resolve("child"));
                    Path bChild = Files.createDirectory(b.resolve("child"));
                    Path aFile = Files.createFile(a.resolve("a-only.any"));
                    Path bFile = Files.createFile(b.resolve("b-only.any"));
                    ReactiveDomain domain = ReactiveDomain.direct();
                    FilePickerSelection[] state = {null};
                    try {
                        domain.execute(
                                () -> {
                                    state[0] = new FilePickerSelection(domain, a, p -> {}, () -> {});
                                    state[0].attach();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].navigate(b);
                                    require(state[0].directory.get().equals(a), "反例提前领取了B结果");
                                    state[0].refresh();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(state[0].directory.get().equals(b)
                                                    && state[0].entries.get().stream().anyMatch(entry -> entry.path.equals(bFile)),
                                            "在途目录刷新退回上次完成的A");
                                    state[0].navigate(a);
                                    state[0].search.reset("a-only");
                                    state[0].query(state[0].search.request(true));
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(state[0].directory.get().equals(a)
                                                    && state[0].entries.get().size() == 1
                                                    && state[0].entries.get().get(0).path.equals(aFile),
                                            "在途目录搜索退回上次完成范围");
                                    state[0].clearQuery();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].navigate(b);
                                    state[0].cycleOrder();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(state[0].directory.get().equals(b)
                                                    && state[0].order.get() == FilePickerOrder.NAME_DESC,
                                            "在途目录排序没有留在B");
                                    state[0].navigate(bChild);
                                    state[0].up();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(state[0].directory.get().equals(b), "在途目录上级使用了旧目录");
                                    state[0].navigate(a);
                                    state[0].path.reset("child");
                                    state[0].go(state[0].path.request(true));
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> require(state[0].directory.get().equals(aChild),
                                        "在途目录的相对路径使用了旧目录B"));
                    } finally {
                        domain.execute(() -> { if (state[0] != null) state[0].detach(); });
                        deleteTree(root);
                    }
                });
        scenario(
                "未领取前往与根结果时查询仍保留目标范围",
                () -> {
                    Path root = Files.createTempDirectory("uilib 未决前往");
                    Path a = Files.createDirectory(root.resolve("A"));
                    Path b = Files.createDirectory(root.resolve("B"));
                    Path bFile = Files.createFile(b.resolve("b-only.any"));
                    ReactiveDomain domain = ReactiveDomain.direct();
                    FilePickerSelection[] state = {null};
                    try {
                        domain.execute(
                                () -> {
                                    state[0] = new FilePickerSelection(domain, a, p -> {}, () -> {});
                                    state[0].attach();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].path.reset(b.toString());
                                    state[0].go(state[0].path.request(true));
                                    state[0].refresh();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(state[0].directory.get().equals(b)
                                                    && state[0].entries.get().stream().anyMatch(entry -> entry.path.equals(bFile)),
                                            "未决前往刷新取消B并退回A");
                                    state[0].path.reset(bFile.toString());
                                    state[0].go(state[0].path.request(true));
                                    state[0].search.reset("b-only");
                                    state[0].query(state[0].search.request(true));
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(state[0].directory.get().equals(b)
                                                    && state[0].entries.get().size() == 1
                                                    && state[0].entries.get().get(0).path.equals(bFile)
                                                    && state[0].currentSelection() == null
                                                    && state[0].revealTarget.get() == null,
                                            "未决文件定位后的搜索没有查询父范围或重新选择了旧文件");
                                    state[0].showRoots(0);
                                    state[0].search.reset("__uilib_no_such_root__");
                                    state[0].query(state[0].search.request(true));
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(state[0].roots.get() && state[0].entries.get().isEmpty(),
                                            "未决根范围查询退回上次完成目录");
                                    state[0].navigate(a);
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].history(-1);
                                    state[0].clearQuery();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> require(state[0].roots.get() && !state[0].entries.get().isEmpty(),
                                        "未决根历史的查询退回目录或历史转移丢失"));
                    } finally {
                        domain.execute(() -> { if (state[0] != null) state[0].detach(); });
                        deleteTree(root);
                    }
                });
        scenario(
                "未决父级与相对草稿突发输入保持有界",
                () -> {
                    Path root = Files.createTempDirectory("uilib 有界请求");
                    Path child = Files.createDirectory(root.resolve("child"));
                    Files.createFile(child.resolve("marker.any"));
                    try (FilePickerDirectory reader = new FilePickerDirectory()) {
                        FilePickerDirectory.Scope scope = FilePickerDirectory.Scope.path(
                                null, child);
                        for (int i = 0; i < 20000; i++) scope = FilePickerDirectory.Scope.parent(scope);
                        reader.readScope(1, scope, FilePickerQuery.first(), false);
                        require(await(reader).roots, "连续上一级没有在根范围结束");
                        scope = FilePickerDirectory.Scope.path(
                                FilePickerDirectory.Scope.directory(root), root.getFileSystem().getPath("child"));
                        for (int i = 0; i < 20000; i++) {
                            scope = FilePickerDirectory.Scope.parent(scope);
                            scope = FilePickerDirectory.Scope.path(scope, root.getFileSystem().getPath("child"));
                        }
                        reader.readScope(2, scope, FilePickerQuery.first(), false);
                        FilePickerDirectory.Result result = await(reader);
                        require(child.equals(result.directory) && result.entries.size() == 1,
                                "取消的相对草稿被累积为导航历史或请求无法完成");
                    } finally {
                        deleteTree(root);
                    }
                });
        scenario(
                "新路径核验进度与失败原因不被旧选择提示覆盖",
                () -> {
                    Path root = Files.createTempDirectory("uilib 核验反馈");
                    Path file = Files.createFile(root.resolve("selected.any"));
                    Path missing = root.resolve("missing.any");
                    ReactiveDomain domain = ReactiveDomain.direct();
                    FilePickerSelection[] state = {null};
                    try {
                        domain.execute(
                                () -> {
                                    state[0] = new FilePickerSelection(domain, root, p -> {}, () -> {});
                                    state[0].attach();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].activate(new FilePickerDirectory.Entry(file, false));
                                    state[0].path.replaceAll(missing.toString());
                                    state[0].confirm();
                                    require(state[0].feedback().contains("核验"), "核验进度被旧选择提示覆盖");
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> require(
                                        state[0].feedback().contains("无法访问路径")
                                                && state[0].feedback().contains("missing.any")
                                                && state[0].path.text().get().equals(missing.toString()),
                                        "失败原因不可见或错误清除了当前路径"));
                    } finally {
                        domain.execute(() -> { if (state[0] != null) state[0].detach(); });
                        deleteTree(root);
                    }
                });
        scenario(
                "路径草稿变更后旧核验不提交与选择提示一致",
                () -> {
                    Path root = Files.createTempDirectory("uilib 选择目标 +");
                    Path a = Files.createFile(root.resolve("A.any")),
                            b = Files.createFile(root.resolve("B.any"));
                    ReactiveDomain domain = ReactiveDomain.direct();
                    FilePickerSelection[] state = {null};
                    List<Path> chosen = new ArrayList<>();
                    try {
                        domain.execute(
                                () -> {
                                    state[0] =
                                            new FilePickerSelection(
                                                    domain, root, chosen::add, () -> {});
                                    state[0].attach();
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    state[0].activate(new FilePickerDirectory.Entry(a, false));
                                    state[0].confirm();
                                    state[0].path.select(
                                            0, state[0].path.snapshot().get().text.length());
                                    state[0].path.replaceSelection(b.toString());
                                });
                        settle(domain, state[0]);
                        domain.execute(
                                () -> {
                                    require(
                                            chosen.isEmpty() && state[0].showing.get(),
                                            "旧A核验提交并关闭了新草稿");
                                    require(
                                            state[0].path
                                                    .snapshot()
                                                    .get()
                                                    .text
                                                    .equals(b.toString()),
                                            "旧核验覆盖了B路径");
                                    require(
                                            state[0].currentSelection() == null
                                                    && state[0].targetLabel().contains("B.any"),
                                            "选择提示仍冒充旧A");
                                    state[0].confirm();
                                });
                        settle(domain, state[0]);
                        require(chosen.equals(Collections.singletonList(b)), "当前确认目标不是B或重复回调");
                        System.out.println(
                                "FILE_PICKER_CONFIRM staleVerifyIgnored=true"
                                    + " currentDraftPreserved=true exactTarget=true"
                                    + " callbackOnce=true");
                    } finally {
                        domain.execute(
                                () -> {
                                    if (state[0] != null) state[0].detach();
                                });
                        deleteTree(root);
                    }
                });
    }

    private static FilePickerDirectory.Result await(FilePickerDirectory reader) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        FilePickerDirectory.Result result;
        while ((result = reader.take()) == null) {
            if (System.nanoTime() > deadline) throw new AssertionError("真实目录读取没有完成");
            Thread.sleep(2);
        }
        return result;
    }

    private static void settle(ReactiveDomain domain, FilePickerSelection selection)
            throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        boolean[] busy = {true};
        do {
            domain.execute(
                    () -> {
                        selection.receiveCompletion();
                        busy[0] = selection.busy.get();
                    });
            if (busy[0]) Thread.sleep(2);
        } while (busy[0] && System.nanoTime() < deadline);
        require(!busy[0], "真实文件选择状态没有完成");
    }

    private static void deleteTree(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Collections.reverseOrder()).collect(Collectors.toList()))
                Files.deleteIfExists(path);
        }
    }
}
