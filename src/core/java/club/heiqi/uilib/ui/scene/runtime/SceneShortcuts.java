package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneShortcut;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 挂载声明的有限索引；范围事实来自原runtime，不拥有焦点或第二棵输入树。 */
final class SceneShortcuts {
    private static final class Registration {
        final MountHandle mount;
        final SceneShortcut.Scope scope;
        final List<SceneShortcut> entries;
        Registration(MountHandle mount, SceneShortcut.Scope scope, SceneShortcut[] entries) {
            this.mount = mount; this.scope = scope; this.entries = Arrays.asList(entries.clone());
            for (SceneShortcut entry : this.entries) Objects.requireNonNull(entry, "shortcut");
        }
    }
    private final SceneRuntime runtime;
    private final List<Registration> registrations = new ArrayList<>();
    SceneShortcuts(SceneRuntime runtime) { this.runtime = runtime; }

    AutoCloseable bind(SceneNode anchor, SceneShortcut.Scope scope, SceneShortcut[] entries) {
        runtime.domain().checkWrite();
        Registration registration = new Registration(runtime.captureMount(anchor), Objects.requireNonNull(scope, "scope"), entries);
        registrations.add(registration);
        return () -> { runtime.domain().checkWrite(); registrations.remove(registration); };
    }

    boolean dispatch(SceneKeyEvent event) {
        if (event.phase != SceneKeyEvent.Phase.DOWN) return false;
        // 应用动作只由fresh执行；原NAVIGATION的无修饰续段仍可继续。
        if (event.repeat) return event.applicationChord();
        SceneNode modal = runtime.activeModal();
        Registration found = null; SceneShortcut binding = null;
        for (Registration registration : registrations) {
            SceneNode node = registration.mount.node();
            if (!registration.mount.active() || !runtime.interactive(node)) continue;
            if (registration.scope == SceneShortcut.Scope.MODAL) {
                if (modal != node) continue;
            } else if (modal != null) continue;
            for (SceneShortcut candidate : registration.entries) if (candidate.matches(event)) {
                if (binding != null) throw new IllegalStateException("范围快捷键冲突: " + event.key
                        + (event.primary ? "+Primary" : "") + (event.shift ? "+Shift" : "")
                        + (event.alt ? "+Alt" : "") + (event.extra ? "+Extra" : "")
                        + " / " + found.mount.node().id() + " / " + node.id());
                binding = candidate; found = registration;
            }
        }
        if (binding != null) { binding.command.tryExecute(); return true; }
        return modal != null && event.applicationChord();
    }
}
