package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PaintPlanBuilder {
    private final List<PaintCommand> commands = new ArrayList<>();

    public PaintPlanBuilder add(PaintCommand command) {
        commands.add(Objects.requireNonNull(command, "command"));
        return this;
    }

    public PaintPlanBuilder addTransformed(PaintPlan plan,club.heiqi.uilib.ui.scene.geometry.SceneTransform transform,club.heiqi.uilib.ui.scene.geometry.SceneClipRegion clip) {
        commands.addAll(plan.transformed(transform,clip).commands());return this;
    }

    public PaintPlan build() {
        return new PaintPlan(commands);
    }
}
