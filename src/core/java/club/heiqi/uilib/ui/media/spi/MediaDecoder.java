package club.heiqi.uilib.ui.media.spi;

import club.heiqi.uilib.ui.media.MediaInfo;

/** 除 cancellation 外均在同一 decoder owner 线程调用；read 返回 null 是正常 EOF。 */
public interface MediaDecoder extends AutoCloseable {
    MediaInfo info();
    MediaDecoded read() throws Exception;
    /** 顺序实现可返回任一轨，core 立即冻结并在暂缓时计入独立 raw 预算。 */
    default MediaDecoded read(MediaDecodeRequest request) throws Exception { return read(); }
    default MediaInputState inputState() { return MediaInputState.UNKNOWN; }
    default MediaDecoderBuffering buffering() { return MediaDecoderBuffering.EMPTY; }
    void seek(long positionNanos) throws Exception;
    @Override void close() throws Exception;
}
