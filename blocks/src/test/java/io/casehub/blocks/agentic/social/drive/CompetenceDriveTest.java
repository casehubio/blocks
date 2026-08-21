package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.EngagementTrend;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompetenceDriveTest {

    @Test
    void evaluate_noTrend_zeroIntensity() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        when(orchestrator.engagementTrend("agent-1", "tenant-1"))
                .thenReturn(Optional.empty());

        var drive = new CompetenceDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.COMPETENCE);
        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_allDeclining_highIntensity() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        when(orchestrator.engagementTrend("agent-1", "tenant-1"))
                .thenReturn(Optional.of(new EngagementTrend(
                        Map.of("clarity", EngagementTrend.TrendDirection.DECLINING,
                               "engagement", EngagementTrend.TrendDirection.DECLINING),
                        -0.3, 10)));

        var drive = new CompetenceDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.5);
    }

    @Test
    void evaluate_allImproving_zeroIntensity() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        when(orchestrator.engagementTrend("agent-1", "tenant-1"))
                .thenReturn(Optional.of(new EngagementTrend(
                        Map.of("clarity", EngagementTrend.TrendDirection.IMPROVING,
                               "engagement", EngagementTrend.TrendDirection.IMPROVING),
                        0.5, 10)));

        var drive = new CompetenceDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_mixed_moderateIntensity() {
        var orchestrator = mock(StrategyLearningOrchestrator.class);
        when(orchestrator.engagementTrend("agent-1", "tenant-1"))
                .thenReturn(Optional.of(new EngagementTrend(
                        Map.of("clarity", EngagementTrend.TrendDirection.DECLINING,
                               "engagement", EngagementTrend.TrendDirection.IMPROVING,
                               "depth", EngagementTrend.TrendDirection.STABLE),
                        0.2, 15)));

        var drive = new CompetenceDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.0).isLessThan(0.5);
    }
}
