package io.casehub.blocks.agentic.social.goal;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface GoalProposalTick {
    record NoChange(@Nullable String reason) implements GoalProposalTick {}

    record Proposed(List<DriveGoalProposal> newProposals,
                    List<String> abandonedGoalNames) implements GoalProposalTick {
        public Proposed {
            newProposals = List.copyOf(newProposals);
            abandonedGoalNames = List.copyOf(abandonedGoalNames);
        }
    }
}
