package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest.Result;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** 请求只结算一次；显式回调在原批次执行，失败同时交给 Future 和原批次通道。 */
final class SceneSemanticCompletion {
    final CompletableFuture<Result> future;
    private final BiConsumer<Result,Throwable> callback;
    private boolean finished;
    SceneSemanticCompletion(CompletableFuture<Result> future,BiConsumer<Result,Throwable> callback){this.future=future;this.callback=callback;}
    void finish(Result value,Throwable cause){
        if(finished)return;finished=true;
        Throwable failure=cause;
        try { if(callback!=null)callback.accept(value,cause); }
        catch(RuntimeException|Error error){
            if(failure==null)failure=error;
            else if(error!=failure)failure.addSuppressed(error);
        }
        if(failure==null)future.complete(value);else future.completeExceptionally(failure);
        if(cause==null){if(failure instanceof Error)throw (Error)failure;if(failure instanceof RuntimeException)throw (RuntimeException)failure;}
    }
}
