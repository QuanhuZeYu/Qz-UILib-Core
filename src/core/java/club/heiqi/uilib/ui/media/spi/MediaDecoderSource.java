package club.heiqi.uilib.ui.media.spi;

import club.heiqi.uilib.ui.media.MediaInfo;

/** 持有同一会话本次来源，跨输出代保留；全部方法由 decoder owner 调用，close 只释放本来源。 */
public interface MediaDecoderSource extends AutoCloseable {
    MediaDecoder open(long positionNanos, boolean seeking, MediaCancellation cancellation) throws Exception;
    /** 首次打开后实际选定的来源时间，供直播初始 join 使用。 */
    default long initialPositionNanos() { return 0; }
    /** 动态来源的下次刷新时刻，使用 System.nanoTime 时钟域；静态来源返回 Long.MAX_VALUE。 */
    default long refreshDeadlineNanos() { return Long.MAX_VALUE; }
    /** 在会话锁外刷新媒体事实，解码推进由 read 负责。 */
    default MediaInfo refresh(MediaInfo current, MediaCancellation cancellation) throws Exception { return current; }
    @Override default void close() throws Exception {}
}
