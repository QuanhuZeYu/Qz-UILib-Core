package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Objects;

/** 复合内容提供有限的逻辑目标准备，焦点权威仍在SceneFocusRouter。
 * Cursor由单次范围选择或导航拥有，WAITING必须发布可追踪的呈现准备意图。 */
public interface SceneFocusTraversal {
    Cursor begin(SceneNode from, boolean backward);

    /** 可选空间方向准备；null沿现有普通子树几何，旧逻辑游标不增加几何前提。 */
    default DirectionalCursor beginDirection(SceneNode from, SceneNavigationEvent direction,
            club.heiqi.uilib.ui.scene.layout.LayoutBox origin) { return null; }

    interface DirectionalCursor extends AutoCloseable {
        /** 仅在当批正式几何就绪后调用；WAITING必须发布真实呈现准备意图。 */
        Result prepare(club.heiqi.uilib.ui.scene.layout.SceneGeometryView geometry);
        @Override void close();
    }

    interface Cursor extends AutoCloseable {
        Result prepare();
        @Override void close();
    }

    final class Result {
        public enum Status { READY, WAITING, END, CANCELLED }
        public final Status status;
        public final SceneNode target;
        private Result(Status status, SceneNode target) { this.status = status; this.target = target; }
        public static Result ready(SceneNode target) { return new Result(Status.READY, Objects.requireNonNull(target)); }
        public static Result waiting() { return new Result(Status.WAITING, null); }
        public static Result end() { return new Result(Status.END, null); }
        /** 已准备目标真实退出，本次意图结束，不转投另一业务对象。 */
        public static Result cancelled() { return new Result(Status.CANCELLED, null); }
    }
}
