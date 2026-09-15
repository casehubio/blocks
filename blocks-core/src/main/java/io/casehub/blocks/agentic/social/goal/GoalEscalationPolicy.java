package io.casehub.blocks.agentic.social.goal;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface GoalEscalationPolicy {
    @Nullable EscalationResult evaluate(DriveGoalProposal proposal,
                                         GoalEscalationContext context);
}
