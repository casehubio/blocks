package io.casehub.blocks.agentic.social.goal;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface DriveGoalFormationStrategy {
    @Nullable DriveGoalProposal propose(DriveGoalFormationContext context);
}
