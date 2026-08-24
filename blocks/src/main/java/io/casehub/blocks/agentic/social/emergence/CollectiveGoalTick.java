package io.casehub.blocks.agentic.social.emergence;

import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface CollectiveGoalTick {
    record NoChange(@Nullable String reason) implements CollectiveGoalTick {}
    record Proposed(List<CollectiveGoalProposal> proposals,
                    List<DriveAlignment> alignments) implements CollectiveGoalTick {}
}
