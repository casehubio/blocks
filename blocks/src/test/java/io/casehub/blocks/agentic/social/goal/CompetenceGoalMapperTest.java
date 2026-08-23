package io.casehub.blocks.agentic.social.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.blocks.agentic.social.EngagementTrend;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompetenceGoalMapperTest {

    private final StrategyLearningOrchestrator orchestrator =
            mock(StrategyLearningOrchestrator.class);
    private final CompetenceGoalMapper mapper = new CompetenceGoalMapper(orchestrator);

    @Test
    void returnsProposal_whenDecliningDimensions() {
        var trend = new EngagementTrend(
                Map.of("technical", EngagementTrend.TrendDirection.DECLINING), 0.3, 50);
        when(orchestrator.engagementTrend("a1", "t1")).thenReturn(Optional.of(trend));

        var proposal = mapper.evaluate("a1", "t1", intensity(0.6));
        assertThat(proposal).isNotNull();
        assertThat(proposal.axis()).isEqualTo(DriveAxis.COMPETENCE);
        assertThat(proposal.goalName()).isEqualTo("improve-technical");
    }

    @Test
    void returnsNull_whenNoDecliningDimensions() {
        var trend = new EngagementTrend(
                Map.of("technical", EngagementTrend.TrendDirection.IMPROVING), 0.8, 100);
        when(orchestrator.engagementTrend("a1", "t1")).thenReturn(Optional.of(trend));

        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    @Test
    void returnsNull_whenNoTrend() {
        when(orchestrator.engagementTrend("a1", "t1")).thenReturn(Optional.empty());
        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    @Test
    void returnsNull_whenEmptyDimensions() {
        var trend = new EngagementTrend(Map.of(), 0.5, 20);
        when(orchestrator.engagementTrend("a1", "t1")).thenReturn(Optional.of(trend));
        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    private DriveIntensity intensity(double value) {
        return new DriveIntensity(DriveAxis.COMPETENCE, value, "test");
    }
}
