package club.heiqi.uilib.ui.media.spi;

/** 音频 owner 线程专用输出，设备、context 与增益限本播放流。 */
public interface MediaAudioSink extends AutoCloseable {
    boolean queue(MediaPcm pcm) throws Exception;
    void playing(boolean playing) throws Exception;
    void gain(double zeroToOne) throws Exception;
    void reset(long generation, long positionNanos) throws Exception;
    MediaAudioPosition position() throws Exception;
    String deviceDescription();
    @Override void close() throws Exception;
}
