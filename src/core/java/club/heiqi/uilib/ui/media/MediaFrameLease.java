package club.heiqi.uilib.ui.media;

import java.util.concurrent.atomic.AtomicBoolean;

/** Prepared 与回放持有的有限提交联系，关闭时解除本次持有；像素归媒体帧持有。 */
public final class MediaFrameLease implements AutoCloseable {
    public final MediaFrameKey key;
    private final MediaSession session;
    private final AtomicBoolean closed=new AtomicBoolean();
    MediaFrameLease(MediaSession session,MediaFrameKey key) {this.session=session;this.key=key;}
    /** draw/提交之前再次检查；seek/source/close 立即使旧代联系失效。 */
    public boolean active() {return !closed.get() && session.presentationActive(key);}
    @Override public void close() {if(closed.compareAndSet(false,true)) session.releasePresentation(key);}
}
