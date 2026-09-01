package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalPriority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
public class NarrativeGoalEscalationPolicy implements GoalEscalationPolicy {

    private final GoalEscalationConfig config;

    @Inject
    public NarrativeGoalEscalationPolicy(GoalEscalationConfig config) {
        this.config = config;
    }

    @Override
    public @Nullable EscalationResult evaluate(DriveGoalProposal proposal,
                                                GoalEscalationContext context) {
        if (countPrimaryDriveGoals(context.descriptor()) >= config.maxPrimaryDriveGoals()) {
            return null;
        }

        DerivedTheme bestTheme = null;
        double bestScore = 0.0;

        for (var theme : context.narrative().themes()) {
            if (theme.salience() <= config.escalationSalienceThreshold()) continue;

            Double weight = theme.axisModulationWeights().get(proposal.axis());
            if (weight == null || weight <= config.minAxisAlignmentWeight()) continue;

            double score = theme.salience() * weight;
            if (score > bestScore) {
                bestScore = score;
                bestTheme = theme;
            }
        }

        if (bestTheme == null) return null;

        Double bestWeight = bestTheme.axisModulationWeights().get(proposal.axis());
        return new EscalationResult(
                GoalPriority.PRIMARY,
                bestTheme.label(),
                "goal aligns with identity theme '" + bestTheme.label()
                + "' (salience=" + String.format("%.2f", bestTheme.salience())
                + ", axisWeight=" + String.format("%.2f", bestWeight) + ")");
    }

    private int countPrimaryDriveGoals(AgentDescriptor descriptor) {
        int count = 0;
        for (AgentGoal goal : descriptor.goals()) {
            if (goal.priority() == GoalPriority.PRIMARY
                && goal.attributes() != null
                && "drive".equals(goal.attributes().get("source"))) {
                count++;
            }
        }
        return count;
    }
}
