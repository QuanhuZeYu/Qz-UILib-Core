package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSession;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;

/** 应用滚轮默认距离；借用唯一配置会话，页面只拥有所属域投影的订阅。 */
public final class ScrollConfiguration {
    public static final ConfigKey<Integer> KEY =
            new ConfigKey<>("ui.scrollStep", ConfigCodecs.INTEGER, 60, ScrollConfiguration::validStep)
                    .withComment("UILib全局滚轮距离：每单位滚轮输入移动的像素数，须为正整数；应用或文件重载后生效，保存后重启保留。节点显式scrollStep优先。");

    /** 配置、设置字段与运行时来源共用的合法距离。 */
    public static boolean validStep(int value) { return value > 0; }

    private final ReactiveDomain domain;
    private final ConfigSession session;
    private final Owner scope;
    private volatile int published;

    public ScrollConfiguration(ReactiveDomain domain, ConfigSession session, Owner owner) {
        this.domain = Objects.requireNonNull(domain, "domain");
        domain.checkWrite();
        this.session = Objects.requireNonNull(session, "session");
        published = session.get(KEY);
        scope = Objects.requireNonNull(owner, "owner").child();
        domain.effect(scope, () -> published = session.get(KEY));
    }

    /** 跨域通知归页面Owner；迟到旧批次和关闭后通知不覆盖当前值。 */
    public ReadableSignal<Integer> bind(ReactiveDomain target, Owner owner) {
        Objects.requireNonNull(target, "target").checkWrite();
        Objects.requireNonNull(owner, "owner");
        if (target == domain) return () -> session.get(KEY);
        Signal<Integer> step = target.signal(published);
        Delivery delivery = new Delivery();
        owner.own(() -> {
            delivery.closed = true;
            domain.execute(() -> { if (delivery.subscription != null) delivery.subscription.close(); });
        });
        domain.execute(() -> {
            if (delivery.closed || scope.isDisposed()) return;
            delivery.subscription = scope.child();
            domain.effect(delivery.subscription, () -> {
                int next = session.get(KEY);
                Object token = new Object();
                delivery.latest = token;
                if (!delivery.closed) target.execute(() -> {
                    if (!delivery.closed && delivery.latest == token) step.set(next);
                });
            });
        });
        return step;
    }

    private static final class Delivery {
        volatile boolean closed;
        volatile Object latest;
        Owner subscription;
    }
}
