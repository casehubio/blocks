package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.CuriosityDrive;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.memory.KnowledgeGapSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CuriosityGoalMapperTest {

    private final CuriosityDrive      drive  = mock(CuriosityDrive.class);
    private final CuriosityGoalMapper mapper = new CuriosityGoalMapper(drive);

    @Test
    void returnsProposal_whenKnowledgeGapsExist() {
        when(drive.lastGaps()).thenReturn(new KnowledgeGapSummary(5, 3, 10));
        var proposal = mapper.evaluate("a1", "t1", intensity(0.7));
        assertThat(proposal).isNotNull();
        assertThat(proposal.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(proposal.goalName()).isEqualTo("explore-knowledge-gaps");
        assertThat(proposal.driveIntensity()).isEqualTo(0.7);
    }

    @Test
    void returnsNull_whenNoGaps() {
        when(drive.lastGaps()).thenReturn(new KnowledgeGapSummary(0, 0, 10));
        assertThat(mapper.evaluate("a1", "t1", intensity(0.7))).isNull();
    }

    @Test
    void returnsNull_whenGapsNull() {
        when(drive.lastGaps()).thenReturn(null);
        assertThat(mapper.evaluate("a1", "t1", intensity(0.7))).isNull();
    }

    @Test
    void returnsNull_whenTotalScoredZero() {
        when(drive.lastGaps()).thenReturn(KnowledgeGapSummary.empty());
        assertThat(mapper.evaluate("a1", "t1", intensity(0.7))).isNull();
    }

    private DriveIntensity intensity(double value) {
        return new DriveIntensity(DriveAxis.CURIOSITY, value, "test");
    }
}
