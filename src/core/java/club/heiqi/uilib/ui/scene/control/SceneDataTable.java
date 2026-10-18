package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** 有限行共享列声明；排序、选择与草稿归业务，行为见交互数据表规格。 */
public final class SceneDataTable<T, K, C> {
    public static final class Column<T, C> {
        public final C key;
        private final ReadableSignal<Size> width;
        private final Supplier<SceneNode> header;
        private final Function<ReadableSignal<T>, SceneNode> cell;
        public Column(C key, ReadableSignal<Size> width, Supplier<SceneNode> header,
                Function<ReadableSignal<T>, SceneNode> cell) {
            this.key = Objects.requireNonNull(key, "列键"); this.width = Objects.requireNonNull(width, "列宽");
            this.header = Objects.requireNonNull(header, "表头工厂"); this.cell = Objects.requireNonNull(cell, "单元格工厂");
        }
    }

    public static final class Options {
        private final int gap;
        private final SceneVirtualList.Options rows;
        public Options() { this(0, new SceneVirtualList.Options()); }
        private Options(int gap, SceneVirtualList.Options rows) { this.gap = gap; this.rows = rows; }
        public Options columnGap(int gap) {
            if (gap < 0) throw new IllegalArgumentException("列间距不能为负");
            return new Options(gap, rows);
        }
        public Options rows(SceneVirtualList.Options rows) { return new Options(gap, Objects.requireNonNull(rows, "行选项")); }
    }

    private final SceneNode node;
    private final SceneVirtualList<T, K> rows;
    private final SceneScrollViewport horizontal;

    private SceneDataTable(ScenePageContext page, ReadableSignal<List<T>> items, Function<T, K> key,
            ReadableSignal<List<Column<T, C>>> columnSource, ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        Owner owner = page.scope().child();
        ReadableSignal<SceneControlMetrics> metrics = page.domain().computed(owner, () -> theme.get().metrics(SceneControlMetrics.Kind.TABLE));
        ReadableSignal<List<Column<T, C>>> columns = page.domain().computed(owner, () -> {
            List<Column<T, C>> result = new ArrayList<>(Objects.requireNonNull(columnSource.get(), "数据表列"));
            Set<C> keys = new HashSet<>();
            for (Column<T, C> column : result)
                if (!keys.add(Objects.requireNonNull(column, "列声明").key)) throw new IllegalArgumentException("数据表列键重复: " + column.key);
            return Collections.unmodifiableList(result);
        });
        ReadableSignal<List<Object>> measure = page.domain().computed(owner, () -> {
            List<Object> result = new ArrayList<>(); result.add(options.get().gap);
            for (Column<T, C> column : columns.get()) {
                Size size = width(column);
                result.addAll(Arrays.asList(column, size.mode, size.value, size.min, size.max));
            }
            return Collections.unmodifiableList(result);
        });
        ReadableSignal<Integer> minimum = page.domain().computed(owner, () -> {
            long result = (long)Math.max(0, columns.get().size() - 1) * options.get().gap;
            for (Column<T, C> column : columns.get()) {
                Size size = width(column); result += size.mode == Size.Mode.FIXED ? size.constrain(size.value) : size.min;
            }
            if (result > Integer.MAX_VALUE) throw new IllegalArgumentException("数据表最小宽度超出scene范围");
            return (int)result;
        });
        ReadableSignal<Integer> dataCount = page.domain().computed(owner, () -> items.get().size());
        ReadableSignal<Map<C, Integer>> columnPositions = page.domain().computed(owner, () -> {
            Map<C, Integer> result = new HashMap<>(); List<Column<T, C>> current = columns.get();
            for (int i = 0; i < current.size(); i++) result.put(current.get(i).key, i + 1);
            return Collections.unmodifiableMap(result);
        });
        Map<C, SceneNode> headers = new HashMap<>();
        SceneNode header = line(columns, columnPositions, options, column -> column.header.get(), null, dataCount, headers);
        header.bind(NodeBinding.value(theme, (self, next) -> self.background(next.button)));
        rows = SceneVirtualList.measured(page, () -> columns.get().isEmpty() ? Collections.emptyList() : items.get(), key,
                value -> line(columns, columnPositions, options, column -> column.cell.apply(value),
                        () -> rowPosition(key.apply(value.get())), dataCount, headers), () -> options.get().rows, measure, metrics);
        rows.semanticRoles(null, null);
        SceneNode content = new SceneNode().size(Size.fill(1), Size.fill(1)).add(header).add(rows.node());
        content.bind(NodeBinding.value(minimum, (self, next) -> self.size(Size.fill(1).bounds(next, Integer.MAX_VALUE), Size.fill(1))));
        SceneScrollArea.Area area = SceneScrollArea.create(page, content, () -> new SceneScrollArea.Options().axes(SceneScrollArea.Axes.HORIZONTAL), theme);
        horizontal = SceneScrollViewport.of(page, area.viewport(SceneNode.Axis.ROW), SceneNode.Axis.ROW);
        Signal<Integer> headerHeight = page.domain().signal(0);
        SceneNode frame = new SceneNode().size(Size.fill(1), Size.fill(1)).clip(true).own(owner);
        SceneNode bar = SceneScrollbar.create(page, rows.viewport(), () -> new SceneScrollbar.Options(), theme, frame);
        frame.control(new Frame(area.node(), bar, headerHeight)).add(area.node()).add(bar);
        frame.bind((runtime, self, scope) -> {
            scope.own(runtime.observeGeometry(self, geometry -> {
                SceneGeometryView.Entry entry = geometry == null ? null : geometry.get(header);
                int height = entry == null ? 0 : entry.box.height;
                if (headerHeight.get() != height) headerHeight.set(height);
            }));
            return new BindingActivation() { public void refresh() { } public void close() { } };
        });
        frame.bind(NodeBinding.semantics(() -> SceneSemantics.builder(SceneSemantics.Role.TABLE).count(dataCount.get())
                .tableSize(columns.get().isEmpty() ? 0 : dataCount.get() + 1, columns.get().size()).build()));
        node = frame;
    }

    private static Size width(Column<?, ?> column) {
        Size value = Objects.requireNonNull(column.width.get(), "列宽");
        if (value.mode == Size.Mode.NATURAL) throw new IllegalArgumentException("数据表列宽需要fixed或fill: " + column.key);
        return value;
    }

    private int rowPosition(K key) { return rows.logicalPosition(key); }

    private static <T, C> SceneNode line(ReadableSignal<List<Column<T, C>>> columns, ReadableSignal<Map<C, Integer>> columnPositions,
            ReadableSignal<Options> options, Function<Column<T, C>, SceneNode> factory, ReadableSignal<Integer> dataPosition,
            ReadableSignal<Integer> dataCount, Map<C, SceneNode> headers) {
        SceneNode row = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural());
        row.bind(NodeBinding.value(() -> options.get().gap, SceneNode::gap));
        row.bind(NodeBinding.semantics(() -> {
            int position = dataPosition == null ? 1 : dataPosition.get() + 1;
            return columns.get().isEmpty() || dataPosition != null && position <= 1 ? null
                    : SceneSemantics.builder(SceneSemantics.Role.ROW).position(position, dataCount.get() + 1)
                            .tableSize(dataCount.get() + 1, columns.get().size()).build();
        }));
        row.bind(NodeBinding.repeat(columns, column -> column.key, column -> {
            // 列盒始终占位；同键新定义只结束内部工厂产物，纯重排和宽度变化保留原挂载。
            SceneNode box = new SceneNode().size(Size.fill(1), Size.natural());
            C identity = column.get().key;
            if (dataPosition == null) { headers.put(identity, box); box.own(() -> headers.remove(identity, box)); }
            box.bind(NodeBinding.semantics(() -> {
                Integer col = columnPositions.get().get(identity);
                int position = dataPosition == null ? 1 : dataPosition.get() + 1;
                if (col == null || dataPosition != null && position <= 1) return null;
                SceneSemantics.Builder description = SceneSemantics.builder(dataPosition == null
                        ? SceneSemantics.Role.COLUMN_HEADER : SceneSemantics.Role.CELL)
                        .cell(position, col, dataCount.get() + 1, columns.get().size());
                if (dataPosition == null) description.position(col, columns.get().size()).labelledBy(box.children().toArray(new SceneNode[0]));
                else { SceneNode header = headers.get(identity); if (header != null) description.labelledBy(header); }
                return description.build();
            }));
            box.bind(NodeBinding.value(() -> width(column.get()), (self, size) -> self.size(size, Size.natural())));
            box.bind(NodeBinding.repeat(() -> Collections.singletonList(column.get()), definition -> definition,
                    definition -> Objects.requireNonNull(factory.apply(definition.get()), "列工厂产物")));
            return box;
        }));
        return row;
    }

    public static <T, K, C> SceneDataTable<T, K, C> create(ScenePageContext page, ReadableSignal<List<T>> items, Function<T, K> key,
            ReadableSignal<List<Column<T, C>>> columns) {
        return create(page, items, key, columns, Options::new, page.theme());
    }
    public static <T, K, C> SceneDataTable<T, K, C> create(ScenePageContext page, ReadableSignal<List<T>> items, Function<T, K> key,
            ReadableSignal<List<Column<T, C>>> columns, ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(page, "page").domain().checkWrite();
        return new SceneDataTable<>(page, Objects.requireNonNull(items, "rows"), Objects.requireNonNull(key, "row key"),
                Objects.requireNonNull(columns, "columns"), Objects.requireNonNull(options, "options"), Objects.requireNonNull(theme, "theme"));
    }
    public SceneNode node() { return node; }
    public SceneVirtualList<T, K> rows() { return rows; }
    public SceneScrollViewport horizontalViewport() { return horizontal; }

    /** 只定位外框内的原滚动条，不保有第二偏移或跨批子测量入口。 */
    private static final class Frame implements SceneControl {
        private final SceneNode content, bar;
        private final ReadableSignal<Integer> headerHeight;
        Frame(SceneNode content, SceneNode bar, ReadableSignal<Integer> headerHeight) {
            this.content = content; this.bar = bar; this.headerHeight = headerHeight;
        }
        public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Collections.singletonList(headerHeight); }
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            int padding = node.padding(), w = Math.max(0, box.width - padding * 2), h = Math.max(0, box.height - padding * 2);
            MeasuredSize barSize = children.measureChild(bar, new Constraints(w, h));
            int width = Math.min(w, barSize.width), top = Math.min(h, headerHeight.get());
            return new ChildPlacements(Arrays.asList(new ChildPlacements.Entry(content, new LayoutBox(padding, padding, w, h)),
                    new ChildPlacements.Entry(bar, new LayoutBox(padding + w - width, padding + top, width, Math.max(0, h - top - width)))));
        }
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) { }
    }
}
