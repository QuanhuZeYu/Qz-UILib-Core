package club.heiqi.uilib.ui.scene.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 控件排列端口返回相对区域，结果由框架保存并转换为统一几何。 */
public final class ChildPlacements {
    public static final class Entry {
        public final SceneNode child;
        public final LayoutBox relativeBox;
        public final boolean contentBox;

        /** 普通布局分配，框架在发布前核验子节点的 fixed/min/max。 */
        public Entry(SceneNode child, LayoutBox relativeBox) {
            this(child, relativeBox, false);
        }

        private Entry(SceneNode child, LayoutBox relativeBox, boolean contentBox) {
            this.child = child;
            this.relativeBox = relativeBox;
            this.contentBox = contentBox;
        }

        /**
         * 内容协议明确给定的盒，例如 specs/content/uilib-math-content-spec.md 第 2 节的数学 em 嵌入。
         * 该协议负责尺寸分配，不自动求解子节点的 Size；仍使用同树几何、裁剪和输入。
         * 普通容器应使用构造器并尊重子节点尺寸，不能以内容盒掩盖非法排列。
         */
        public static Entry contentBox(SceneNode child, LayoutBox relativeBox) {
            return new Entry(child, relativeBox, true);
        }
    }

    private final List<Entry> entries;
    private final MeasuredSize contentSize;

    public ChildPlacements(List<Entry> entries) {
        this(entries, null);
    }

    /** 相对本容器原点的纯内容范围，含声明内距；按需集合用它表达尚未实例化的尾部。
     * 实际子盒仍参与包络，不能用较小声明截断已有内容。旧构造继续只从子盒求范围。 */
    public ChildPlacements(List<Entry> entries, MeasuredSize contentSize) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.contentSize = contentSize;
    }

    public MeasuredSize contentSize() { return contentSize; }

    public List<Entry> entries() {
        return entries;
    }
}
