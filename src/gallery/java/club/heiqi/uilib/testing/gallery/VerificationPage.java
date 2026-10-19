package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 检查在独立无窗口环境运行，UI 只通过当前状态域接收实际结果。 */
final class VerificationPage {
    private final ScenePageContext context;
    private final GalleryChecks checks;
    private final Signal<Map<String, GalleryChecks.Result>> results;
    private final Signal<String> running, selected;
    private final ExecutorService worker;
    private volatile boolean closed;

    private VerificationPage(ScenePageContext context, GalleryChecks checks) {
        this.context = context; this.checks = checks;
        results = context.domain().signal(Collections.emptyMap());
        running = context.domain().signal(null);
        selected = context.domain().signal(null);
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "uilib-gallery-checks"); thread.setDaemon(true); return thread;
        });
        context.own(() -> { closed = true; worker.shutdownNow(); });
    }

    static SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme, Runnable back) {
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).padding(28).gap(20).scrollable(true);
        root.add(GalleryStyle.header(context, theme, "核心验证", "运行核心检查，查看场景结果和失败原因。", back));
        GalleryChecks service = GalleryChecks.load();
        if (service == null) {
            root.add(GalleryStyle.label(context, () -> "当前测试包缺少 core-verification，请装配同版本测试产物。", theme, 16, false));
            return root;
        }
        VerificationPage page = new VerificationPage(context, service);
        root.add(GalleryStyle.primary(context, () -> page.running.get() == null ? "运行全部检查" : "正在运行检查", theme,
                () -> page.start(service.checks()))
                .bind(NodeBinding.value(() -> page.running.get() == null, SceneNode::enabled)));
        root.add(GalleryStyle.label(context, page::summary, theme, 15, true));
        for (GalleryChecks.Check check : service.checks()) {
            SceneNode row = GalleryStyle.card(theme).gap(8);
            row.add(GalleryStyle.label(context, () -> check.title, theme, 18, false));
            row.add(GalleryStyle.label(context, () -> check.description, theme, 14, true));
            row.add(GalleryStyle.label(context, () -> page.status(check.id), theme, 15, false));
            SceneNode actions = GalleryStyle.centeredRow().gap(12);
            actions.add(GalleryStyle.button(context, () -> "运行 · " + check.title, theme,
                    () -> page.start(Collections.singletonList(check)))
                    .bind(NodeBinding.value(() -> page.running.get() == null, SceneNode::enabled)));
            actions.add(GalleryStyle.button(context, () -> "查看结果 · " + check.title, theme,
                    () -> page.selected.set(check.id))
                    .bind(NodeBinding.value(() -> page.results.get().containsKey(check.id), SceneNode::enabled)));
            row.add(actions);
            row.add(GalleryStyle.label(context, () -> {
                GalleryChecks.Result result = page.results.get().get(check.id);
                return result == null ? "" : result.details;
            }, theme, 14, false).bind(NodeBinding.value(
                    () -> check.id.equals(page.selected.get()) && page.results.get().containsKey(check.id), SceneNode::visible)));
            root.add(GalleryStyle.frame(row, theme));
        }
        return root;
    }

    private String status(String id) {
        if (id.equals(running.get())) return "运行中";
        GalleryChecks.Result result = results.get().get(id);
        return result == null ? "未运行" : (result.passed() ? "通过" : "失败") + " · " + result.elapsedNanos / 1_000_000 + " ms";
    }
    private String summary() {
        int passed = 0, failed = 0;
        for (GalleryChecks.Result result : results.get().values()) if (result.passed()) passed++; else failed++;
        return "通过 " + passed + " · 失败 " + failed + " · 共 " + checks.checks().size() + " 项";
    }
    private void start(List<GalleryChecks.Check> requested) {
        if (running.get() != null) return;
        running.set(requested.get(0).id);
        worker.execute(() -> {
            for (GalleryChecks.Check check : requested) {
                if (closed || Thread.currentThread().isInterrupted()) break;
                context.domain().execute(() -> { if (!closed) running.set(check.id); });
                GalleryChecks.Result result = checks.run(check.id);
                context.domain().execute(() -> {
                    if (closed) return;
                    Map<String, GalleryChecks.Result> next = new LinkedHashMap<>(results.get());
                    next.put(check.id, result); results.set(Collections.unmodifiableMap(next)); selected.set(check.id);
                });
            }
            context.domain().execute(() -> { if (!closed) running.set(null); });
        });
    }
}
