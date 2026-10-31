package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Consumer;

/** 单表的标准网格外观；业务提供列内容与选择意图，滚动和虚拟化仍归 SceneDataTable。 */
public final class SceneTableAppearance<T> extends SceneAppearance {
    private final ReadableSignal<List<T>> items;
    private final Predicate<T> selected;
    private final Consumer<T> select;

    public SceneTableAppearance(ScenePageContext page, ReadableSignal<List<T>> items, ReadableSignal<SceneTheme> theme) {
        this(page, items, theme, item -> false, item -> { });
    }

    public SceneTableAppearance(ScenePageContext page, ReadableSignal<List<T>> items, ReadableSignal<SceneTheme> theme,
            Predicate<T> selected, Consumer<T> select) {
        super(page, theme);
        this.items = Objects.requireNonNull(items, "数据行");
        this.selected = Objects.requireNonNull(selected, "选中状态");
        this.select = Objects.requireNonNull(select, "选择意图");
    }

    public <C> SceneDataTable.Column<T, C> column(C key, ReadableSignal<Size> width, ReadableSignal<String> title,
            Function<ReadableSignal<T>, SceneNode> content) {
        return new SceneDataTable.Column<>(key, width, () -> heading(title, false), row -> cell(row, content.apply(row)));
    }

    /** 按当前数据的完整格式化值保守预留字符空间；不创建屏外单元格或读取字体资源。 */
    public <C> SceneDataTable.Column<T, C> number(C key, ReadableSignal<String> title, Function<T, String> value) {
        ReadableSignal<Size> width = page.domain().computed(scope, () -> {
            int characters = Math.max(4, title.get().length());
            for (T item : items.get()) characters = Math.max(characters, value.apply(item).length());
            return Size.fixed(Math.round(em() * (characters + 2)));
        });
        return new SceneDataTable.Column<>(key, width, () -> heading(title, true),
                row -> cell(row, numeric(() -> value.apply(row.get()))));
    }

    /** 自定义排序表头可直接组合原 SceneCommand 控件。 */
    public SceneNode header(SceneNode content) { return frame(content, () -> false, true); }

    /** 自定义单元格复用同一外框；内部按钮及字段保留自身的输入与焦点。 */
    public SceneNode cell(ReadableSignal<T> row, SceneNode content) {
        return frame(content, () -> selected.test(row.get()), false).onClick(() -> select.accept(row.get()));
    }

    /** 一份外观归一张表拥有，销毁表时释放所属列宽派生和主题观察。 */
    public <K, C> SceneDataTable<T, K, C> create(Function<T, K> key,
            ReadableSignal<List<SceneDataTable.Column<T, C>>> columns) {
        SceneDataTable<T, K, C> table = SceneDataTable.create(page, items, key, columns, SceneDataTable.Options::new,
                page.domain().computed(scope, () -> colors.get().editorTheme()));
        table.node().own(scope);
        return table;
    }

    private SceneNode heading(ReadableSignal<String> title, boolean number) {
        SceneNode content = number ? numeric(title) : ellipsis(title, 1, false).size(Size.fill(1), Size.natural());
        SceneNode header = header(content);
        SceneTooltip.attach(page, header, () -> SceneTooltip.Content.text(title.get()));
        return header;
    }

    private SceneNode numeric(ReadableSignal<String> value) {
        SceneNode line = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural());
        line.add(new SceneNode().size(Size.fill(1), Size.fixed(1)));
        SceneNode label = ellipsis(value, 1, false).size(Size.natural(), Size.natural());
        line.add(label);
        SceneTooltip.attach(page, label, () -> SceneTooltip.Content.text(value.get()));
        return line;
    }

    private float em() { return colors.get().source.metrics(SceneControlMetrics.Kind.TABLE).textStyle.logicalEm; }

    private SceneNode frame(SceneNode content, ReadableSignal<Boolean> selected, boolean header) {
        SceneNode inside = SceneLayouts.center(content)
                .bind(NodeBinding.value(() -> Math.max(2, Math.round(em() / 4)), SceneNode::padding))
                .bind(NodeBinding.value(() -> header ? colors.get().hover
                        : selected.get() ? colors.get().selected : colors.get().background, SceneNode::background));
        return new SceneNode().gap(0).padding(1).add(inside)
                .bind(NodeBinding.value(() -> colors.get().source.metrics(SceneControlMetrics.Kind.TABLE).rowHeightEstimate,
                        (self, height) -> self.size(Size.fill(1), Size.fill(1).bounds(height, Integer.MAX_VALUE))))
                .bind(NodeBinding.value(() -> colors.get().border, SceneNode::background));
    }
}
