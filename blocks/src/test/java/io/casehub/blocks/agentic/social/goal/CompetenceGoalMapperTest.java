package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.EngagementTrend;
import io.casehub.blocks.agentic.social.drive.CompetenceDrive;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompetenceGoalMapperTest {

    private final CompetenceDrive      drive  = mock(CompetenceDrive.class);
    private final CompetenceGoalMapper mapper = new CompetenceGoalMapper(drive);

    @Test
    void returnsProposal_whenDecliningDimensions() {
        var trend = new EngagementTrend(
                Map.of("technical", EngagementTrend.TrendDirection.DECLINING), 0.3, 50);
        when(drive.lastTrend()).thenReturn(trend);

        var proposal = mapper.evaluate("a1", "t1", intensity(0.6));
        assertThat(proposal).isNotNull();
        assertThat(proposal.axis()).isEqualTo(DriveAxis.COMPETENCE);
        assertThat(proposal.goalName()).isEqualTo("improve-technical");
    }

    @Test
    void returnsNull_whenNoDecliningDimensions() {
        var trend = new EngagementTrend(
                Map.of("technical", EngagementTrend.TrendDirection.IMPROVING), 0.8, 100);
        when(drive.lastTrend()).thenReturn(trend);

        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    @Test
    void returnsNull_whenNoTrend() {
        when(drive.lastTrend()).thenReturn(null);
        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    @Test
    void returnsNull_whenEmptyDimensions() {
        var trend = new EngagementTrend(Map.of(), 0.5, 20);
        when(drive.lastTrend()).thenReturn(trend);
        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    private DriveIntensity intensity(double value) {
        return new DriveIntensity(DriveAxis.COMPETENCE, value, "test");
    }
}
