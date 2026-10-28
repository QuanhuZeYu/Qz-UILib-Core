package club.heiqi.uilib.ui.config;

/** 声明类型与 JDK 对象树之间的边界。树仅含 null、标量、List 和 String 键 Map。
 * read 的 path 用于定位转换错误，Java 类型由声明选择。
 * snapshot 复制并深冻结发布内容；对象 builder 用冻结字段调用 factory，
 * factory 返回同样深层不可变的业务值。
 * 自定义业务值须按配置语义实现 equals/hashCode，供相同值加载及 set 去重通知。 */
public interface ConfigCodec<T> {
    T read(Object value, String path);
    Object write(T value);
    T snapshot(T value);
    /** 相对字段路径的声明说明，用于首次生成节点；已加载节点保留用户注释。 */
    default java.util.Map<String, String> comments() { return java.util.Collections.emptyMap(); }
    /** 复合 codec 按实际 children 展开路径，包含列表索引和映射键。 */
    default java.util.Map<String, String> comments(T value) { return comments(); }
}
