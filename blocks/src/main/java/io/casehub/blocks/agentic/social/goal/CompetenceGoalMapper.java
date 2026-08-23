package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.EngagementTrend;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import org.jspecify.annotations.Nullable;

public class CompetenceGoalMapper implements DriveGoalMapper {

    private final StrategyLearningOrchestrator strategyOrchestrator;

    public CompetenceGoalMapper(StrategyLearningOrchestrator strategyOrchestrator) {
        this.strategyOrchestrator = strategyOrchestrator;
    }

    @Override
    public @Nullable DriveGoalProposal evaluate(String agentId, String tenantId,
                                                 DriveIntensity intensity) {
        if (intensity.axis() != DriveAxis.COMPETENCE) return null;
        var trendOpt = strategyOrchestrator.engagementTrend(agentId, tenantId);
        if (trendOpt.isEmpty()) {
            return null;
        }

        var trend = trendOpt.get();
        String mostDeclining = null;
        for (var entry : trend.dimensionTrends().entrySet()) {
            if (entry.getValue() == EngagementTrend.TrendDirection.DECLINING) {
                mostDeclining = entry.getKey();
                break;
            }
        }

        if (mostDeclining == null) {
            return null;
        }

        return new DriveGoalProposal(
                DriveAxis.COMPETENCE,
                "improve-" + mostDeclining.toLowerCase(),
                "Improve engagement in the " + mostDeclining + " dimension",
                "competence: " + mostDeclining + " dimension declining",
                intensity.intensity());
    }
}
