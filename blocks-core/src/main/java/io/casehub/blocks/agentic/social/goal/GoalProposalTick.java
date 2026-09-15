package io.casehub.blocks.agentic.social.goal;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface GoalProposalTick {
    record NoChange(@Nullable String reason) implements GoalProposalTick {}

    record Changes(List<DriveGoalProposal> newProposals,
                   List<String> abandonedGoalNames,
                   List<PriorityAdjustment> priorityAdjustments,
                   List<GovernanceAttributeUpdate> governanceUpdates) implements GoalProposalTick {
        public Changes {
            newProposals = List.copyOf(newProposals);
            abandonedGoalNames = List.copyOf(abandonedGoalNames);
            priorityAdjustments = List.copyOf(priorityAdjustments);
            governanceUpdates = List.copyOf(governanceUpdates);
        }
    }
}
