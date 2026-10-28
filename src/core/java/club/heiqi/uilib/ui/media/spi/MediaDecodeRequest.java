package club.heiqi.uilib.ui.media.spi;

/** 声明 core 对两轨的背压，适配按有界预算暂存另一轨压缩包。 */
public final class MediaDecodeRequest {
    public final boolean video, audio;
    public final long deferredBytes, deferredNanos;
    public final int deferredPackets;
    public MediaDecodeRequest(boolean video,boolean audio,long bytes,int packets,long nanos) {
        this.video=video; this.audio=audio; deferredBytes=bytes; deferredPackets=packets; deferredNanos=nanos;
    }
}
