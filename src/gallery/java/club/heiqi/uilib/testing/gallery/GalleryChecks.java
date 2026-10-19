package club.heiqi.uilib.testing.gallery;

import java.util.List;
import java.util.ServiceLoader;

/** 广场只声明检查目录与结果；实现由独立 core-verification 测试产物提供。 */
public interface GalleryChecks {
    final class Check {
        public final String id, title, description;
        public Check(String id, String title, String description) {
            this.id = id; this.title = title; this.description = description;
        }
    }
    final class Result {
        public final String id, details;
        public final long elapsedNanos;
        public final Throwable failure;
        public Result(String id, long elapsedNanos, String details, Throwable failure) {
            this.id = id; this.elapsedNanos = elapsedNanos; this.details = details; this.failure = failure;
        }
        public boolean passed() { return failure == null; }
    }
    List<Check> checks();
    Result run(String id);

    static GalleryChecks load() {
        java.util.Iterator<GalleryChecks> providers = ServiceLoader.load(
                GalleryChecks.class, GalleryChecks.class.getClassLoader()).iterator();
        return providers.hasNext() ? providers.next() : null;
    }
}
