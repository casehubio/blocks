package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.ReactionGroup;
import io.casehub.work.progress.ProgressInstance;

import java.util.List;
import java.util.Map;

public record RenderContext(
        Map<Long, List<ReactionGroup>> reactions,
        CommonGroundState commonGround,
        ConvergenceSignal convergence,
        Map<String, List<ProgressInstance>> progress) {

    public static final RenderContext EMPTY =
            new RenderContext(Map.of(), null, null, Map.of());

    public static RenderContext withReactions(Map<Long, List<ReactionGroup>> reactions) {
        return new RenderContext(reactions, null, null, Map.of());
    }

    public static RenderContext withProgress(Map<String, List<ProgressInstance>> progress) {
        return new RenderContext(Map.of(), null, null, progress);
    }
}
