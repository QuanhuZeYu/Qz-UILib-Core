package club.heiqi.uilib.ui.reactive;

import club.heiqi.uilib.testing.TestApplication;

/** 同包检查真实依赖边，保证关闭后的回调不再持有长寿命来源；不依赖GC时序。 */
public final class ReactiveLifecycleScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("Effect自身关闭后继续读取不重新订阅来源", this::effectClosesDuringRead);
        scenario("Observation读取时自身关闭后不重新订阅来源", this::observationClosesDuringRead);
    }

    private void effectClosesDuringRead() {
        ReactiveDomain domain = ReactiveDomain.direct();
        Signal<Integer>[] source = new Signal[1];
        Owner[] owner = new Owner[1];
        Effect[] effect = new Effect[1];
        int[] calls = {0}, released = {0};
        domain.execute(() -> {
            source[0] = domain.signal(1);
            owner[0] = domain.owner();
            owner[0].own(() -> released[0]++);
            effect[0] = domain.effect(owner[0], () -> {
                calls[0]++;
                source[0].get();
                owner[0].close();
                require(source[0].get() == 1, "关闭不应销毁外部共享来源");
            });
        });
        domain.execute(() -> {
            require(owner[0].isDisposed() && effect[0].sources.isEmpty(), "已关闭Effect仍持有来源订阅");
            source[0].set(2);
            require(source[0].get() == 2, "共享来源在owner结束后仍可用");
            owner[0].close();
        });
        require(calls[0] == 1 && released[0] == 1, "已关闭Effect重新运行或资源重复关闭");
    }

    private void observationClosesDuringRead() {
        ReactiveDomain domain = ReactiveDomain.direct();
        domain.execute(() -> {
            Signal<Integer> source = domain.signal(1);
            Owner owner = domain.owner();
            int[] calls = {0}, changed = {0};
            Observation observation = domain.observe(owner, () -> {
                calls[0]++;
                source.get();
                owner.close();
                require(source.get() == 1, "关闭不应销毁外部共享来源");
            }, () -> changed[0]++);
            observation.refresh();
            require(owner.isDisposed() && observation.sources.isEmpty(), "已关闭Observation仍持有来源订阅");
            source.set(2);
            require(source.get() == 2 && calls[0] == 1 && changed[0] == 0, "已关闭Observation参与后续通知");
            owner.close();
        });
    }
}
