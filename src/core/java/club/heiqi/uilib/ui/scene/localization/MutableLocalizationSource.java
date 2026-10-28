package club.heiqi.uilib.ui.scene.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 拥有独立语言资料，每次 publish 表示一次已完成加载。
 * close 解除共享来源订阅，绑定已持有的不可变资料继续可用。 */
public final class MutableLocalizationSource implements LocalizationSource, AutoCloseable {
    private LocalizationSnapshot current;
    private Object publication;
    private final List<Consumer<LocalizationSnapshot>> listeners = new ArrayList<>();
    private boolean closed;

    public MutableLocalizationSource(LocalizationSnapshot initial) {
        current = Objects.requireNonNull(initial, "initial");
    }

    @Override
    public synchronized LocalizationSnapshot current() {
        checkAlive();
        return current;
    }

    @Override
    public synchronized Subscription subscribe(Consumer<LocalizationSnapshot> listener) {
        checkAlive();
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        try {
            listener.accept(current);
        } catch (RuntimeException | Error failure) {
            listeners.remove(listener);
            throw failure;
        }
        return () -> {
            synchronized (MutableLocalizationSource.this) {
                listeners.remove(listener);
            }
        };
    }

    public synchronized void publish(LocalizationSnapshot next) {
        checkAlive();
        current = Objects.requireNonNull(next, "next");
        Object token = new Object();
        publication = token;
        // 回调可能重入发布；即使复用同一snapshot实例，也是一批新的资料事实。
        // 新事实立即可读，通知按 publication 代际继续。
        Throwable failure = null;
        for (Consumer<LocalizationSnapshot> listener : new ArrayList<>(listeners)) {
            if (closed || publication != token) break;
            // 某订阅在通知期间解除时，已拷贝的迟到回调由绑定的closed边界拦截。
            try {
                listener.accept(next);
            } catch (RuntimeException | Error nextFailure) {
                if (failure == null) failure = nextFailure;
                else if (nextFailure != failure) failure.addSuppressed(nextFailure);
            }
        }
        // 逐项通知仍有效批次的订阅，结束后传播首错并附加其余错误。
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }

    private void checkAlive() {
        if (closed) throw new IllegalStateException("语言资料来源已关闭");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        listeners.clear();
    }
}
