package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Objects;

/** 复合内容提供有限的逻辑目标准备，焦点权威仍在SceneFocusRouter。
 * Cursor由单次范围选择或导航拥有，WAITING必须发布可追踪的呈现准备意图。 */
public interface SceneFocusTraversal {
    Cursor begin(SceneNode from, boolean backward);

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
