package club.heiqi.uilib.ui.scene.image;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 左上原点的不可变 straight RGBA8 内容，身份标识本份内容版本。 */
public class ImageContent {
    public enum Kind {
        PIXELS,
        HOST_ITEM
    }

    public final Kind kind;
    private static final AtomicLong IDS = new AtomicLong();
    public final long identity = IDS.incrementAndGet();
    public final int width, height;
    private final byte[] rgba;

    public ImageContent(int width, int height, byte[] rgba) {
        if (width < 1 || height < 1 || (long) width * height * 4 > Integer.MAX_VALUE)
            throw new IllegalArgumentException("无效图像尺寸");
        if (Objects.requireNonNull(rgba, "rgba").length != (long) width * height * 4)
            throw new IllegalArgumentException("RGBA字节数不符");
        this.kind = Kind.PIXELS;
        this.width = width;
        this.height = height;
        this.rgba = rgba.clone();
    }

    /** 宿主提供不可变物品快照，core 消费自然尺寸与中性身份；子类保持同样的不可变合同。 */
    protected ImageContent(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("无效宿主图像尺寸");
        this.kind = Kind.HOST_ITEM;
        this.width = width;
        this.height = height;
        this.rgba = null;
    }

    public ItemImageFacts itemFacts() {
        return null;
    }

    public long bytes() {
        return rgba == null ? 0 : rgba.length;
    }

    public byte[] pixels() {
        if (rgba == null) throw new UnsupportedOperationException("宿主物品不是位图");
        return rgba.clone();
    }
}
