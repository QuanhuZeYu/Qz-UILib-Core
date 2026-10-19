package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageState;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 共享图像服务的真实跨域递交；loader仅提供内存像素，不涉及宿主设备。 */
public final class ImageServiceScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("完成通知失败仍递交独立消费者并启动待处理请求", this::completion);
        scenario("显式重载通知失败不截断共享消费者", this::reload);
        scenario("资源包换代通知失败仍重载其余来源", this::resourcesReloaded);
        scenario("呈现反馈通知失败仍递交独立消费者", this::presentation);
    }

    private static final class Consumer implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final RuntimeException failure;
        Owner owner;
        ImageService.Lease lease;
        Observation observation;
        boolean fail;
        Consumer(SharedImageService service, ImageSource source, RuntimeException failure) {
            this.failure = failure;
            domain.execute(() -> {
                owner = domain.owner();
                lease = service.acquire(source, domain);
                owner.own(lease);
                observation = domain.observe(owner, () -> lease.state().get(), () -> {
                    if (fail) throw failure;
                });
                observation.refresh();
            });
        }
        ImageState state() {
            ImageState[] value = new ImageState[1];
            domain.execute(() -> value[0] = lease.state().get());
            return value[0];
        }
        void failNext() { domain.execute(() -> { observation.refresh(); fail = true; }); }
        public void close() { domain.execute(owner::close); }
    }

    private SharedImageService service(AtomicInteger prepared) {
        return own(new SharedImageService(source -> {
            prepared.incrementAndGet();
            return () -> new ImageContent(1, 1, new byte[]{1, 2, 3, 4});
        }, new SharedImageService.Budget(1, 8, 8, 8, 64)));
    }

    private Consumer consumer(SharedImageService service, ImageSource source, RuntimeException failure) {
        return own(new Consumer(service, source, failure));
    }

    private static void awaitBytes(SharedImageService service, long bytes) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (service.retainedBytes() < bytes && System.nanoTime() < deadline) Thread.sleep(1);
        require(service.retainedBytes() >= bytes, "图像worker未完成");
    }

    private static void expectFailure(Runnable action, RuntimeException first, RuntimeException second) {
        try {
            action.run();
            throw new AssertionError("通知必须继续报告原错误");
        } catch (RuntimeException actual) {
            require(actual == first, "通知替换了首错身份");
            if (second != null) require(actual.getSuppressed().length == 1 && actual.getSuppressed()[0] == second,
                    "其余消费者错误没有附着首错");
        }
    }

    private void completion() throws Exception {
        AtomicInteger prepared = new AtomicInteger();
        SharedImageService service = service(prepared);
        ImageSource source = ImageSource.resource("test:shared");
        RuntimeException first = new IllegalStateException("首消费者失败");
        RuntimeException second = new IllegalArgumentException("第二消费者失败");
        Consumer bad = consumer(service, source, first), another = consumer(service, source, second);
        Consumer valid = consumer(service, source, null);
        bad.failNext(); another.failNext();
        service.drain(); awaitBytes(service, 4);
        Consumer pending = consumer(service, ImageSource.resource("test:pending"), null);
        expectFailure(service::drain, first, second);
        require(valid.state().status == ImageState.Status.READY, "已移出的完成结果漏投给有效消费者");
        require(prepared.get() == 2, "完成通知失败阻断已就绪请求启动");
        awaitBytes(service, 8); service.drain();
        require(pending.state().status == ImageState.Status.READY, "新请求未继续完成");

        ImageSource completed = ImageSource.resource("test:completed-before-error");
        ImageSource rejected = ImageSource.resource("test:prepare-error");
        ImageSource following = ImageSource.resource("test:following-error");
        Error prepareFailure = new AssertionError("准备图像快照失败");
        AtomicInteger attempts = new AtomicInteger();
        SharedImageService throwing = own(new SharedImageService(next -> {
            attempts.incrementAndGet();
            if (next.equals(rejected)) throw prepareFailure;
            return () -> new ImageContent(1, 1, new byte[]{1, 2, 3, 4});
        }, new SharedImageService.Budget(1, 8, 8, 8, 64)));
        Consumer accepted = consumer(throwing, completed, null);
        throwing.drain(); awaitBytes(throwing, 4);
        Consumer failed = consumer(throwing, rejected, null);
        Consumer followingConsumer = consumer(throwing, following, null);
        try {
            throwing.drain();
            throw new AssertionError("准备Error必须沿原调用通道抛出");
        } catch (Error actual) {
            require(actual == prepareFailure, "准备Error原始身份被替换");
        }
        require(accepted.state().status == ImageState.Status.READY, "准备Error丢弃本批已完成通知");
        require(attempts.get() == 3, "准备Error阻断同批其余已取得资格的请求");
        awaitBytes(throwing, 8); throwing.drain();
        ImageState errorState = failed.state();
        require(errorState.status == ImageState.Status.FAILED && errorState.source == rejected
                && errorState.stage.equals("load") && errorState.diagnostic.equals(prepareFailure.toString()),
                "准备Error未结束所属加载请求或未保留失败事实");
        require(followingConsumer.state().status == ImageState.Status.READY, "准备Error后的请求不能完成");
        throwing.drain();
        require(attempts.get() == 3, "准备失败自动重新启动请求");
    }

    private void reload() throws Exception {
        SharedImageService service = service(new AtomicInteger());
        ImageSource source = ImageSource.resource("test:reload");
        RuntimeException failure = new IllegalStateException("重载通知失败");
        Consumer bad = consumer(service, source, failure), valid = consumer(service, source, null);
        service.drain(); awaitBytes(service, 4); service.drain();
        long previous = valid.state().request;
        bad.failNext();
        expectFailure(() -> service.reload(source), failure, null);
        ImageState next = valid.state();
        require(next.status == ImageState.Status.LOADING && next.request > previous,
                "显式重载漏投独立消费者");
    }

    private void resourcesReloaded() throws Exception {
        SharedImageService service = service(new AtomicInteger());
        ImageSource firstSource = ImageSource.resource("test:first"), secondSource = ImageSource.resource("test:second");
        RuntimeException failure = new IllegalStateException("资源包通知失败");
        Consumer bad = consumer(service, firstSource, failure), first = consumer(service, firstSource, null);
        Consumer second = consumer(service, secondSource, null);
        service.drain(); awaitBytes(service, 8); service.drain();
        long previous = second.state().request;
        bad.failNext();
        expectFailure(service::resourcesReloaded, failure, null);
        require(first.state().status == ImageState.Status.LOADING && second.state().status == ImageState.Status.LOADING
                && second.state().request > previous, "资源包换代被另一来源的消费者错误截断");
    }

    private void presentation() throws Exception {
        SharedImageService service = service(new AtomicInteger());
        ImageSource source = ImageSource.resource("test:presentation");
        RuntimeException failure = new IllegalStateException("呈现通知失败");
        Consumer bad = consumer(service, source, failure), valid = consumer(service, source, null);
        service.drain(); awaitBytes(service, 4); service.drain();
        ImageState ready = valid.state();
        bad.failNext();
        expectFailure(() -> service.presentation(ready.content.identity, true, null), failure, null);
        ImageState uploading = valid.state();
        require(uploading.status == ImageState.Status.LOADING && uploading.stage.equals("upload")
                && uploading.content == ready.content && uploading.request == ready.request,
                "呈现反馈漏投或改变当前请求事实");
    }
}
