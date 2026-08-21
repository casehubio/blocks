package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.EngagementTrend;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;

public class CompetenceDrive implements DriveSource {

    private final StrategyLearningOrchestrator strategyOrchestrator;

    public CompetenceDrive(StrategyLearningOrchestrator strategyOrchestrator) {
        this.strategyOrchestrator = strategyOrchestrator;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var trendOpt = strategyOrchestrator.engagementTrend(agentId, tenantId);
        if (trendOpt.isEmpty()) {
            return new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "no engagement data");
        }

        var trend = trendOpt.get();
        if (trend.dimensionTrends().isEmpty()) {
            return new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "no dimensions");
        }

        long declining = trend.dimensionTrends().values().stream()
                              .filter(d -> d == EngagementTrend.TrendDirection.DECLINING).count();
        long improving = trend.dimensionTrends().values().stream()
                .filter(d -> d == EngagementTrend.TrendDirection.IMPROVING).count();
        int total = trend.dimensionTrends().size();

        double declineRatio = (double) declining / total;
        double improvementDiscount = (double) improving / total * 0.5;
        double intensity = Math.clamp(declineRatio - improvementDiscount, 0.0, 1.0);

        String trigger = declining + " declining, " + improving + " improving of " + total + " dimensions";
        return new DriveIntensity(DriveAxis.COMPETENCE, intensity, trigger);
    }
}
