package io.casehub.blocks.agentic.social.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.memory.KnowledgeGapSummary;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import org.junit.jupiter.api.Test;

class CuriosityGoalMapperTest {

    private final MemoryHygieneOrchestrator orchestrator = mock(MemoryHygieneOrchestrator.class);
    private final CuriosityGoalMapper mapper = new CuriosityGoalMapper(orchestrator);

    @Test
    void returnsProposal_whenKnowledgeGapsExist() {
        when(orchestrator.knowledgeGaps("a1", "t1"))
                .thenReturn(new KnowledgeGapSummary(5, 3, 10));
        var proposal = mapper.evaluate("a1", "t1", intensity(0.7));
        assertThat(proposal).isNotNull();
        assertThat(proposal.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(proposal.goalName()).isEqualTo("explore-knowledge-gaps");
        assertThat(proposal.driveIntensity()).isEqualTo(0.7);
    }

    @Test
    void returnsNull_whenNoGaps() {
        when(orchestrator.knowledgeGaps("a1", "t1"))
                .thenReturn(new KnowledgeGapSummary(0, 0, 10));
        assertThat(mapper.evaluate("a1", "t1", intensity(0.7))).isNull();
    }

    @Test
    void returnsNull_whenGapsNull() {
        when(orchestrator.knowledgeGaps("a1", "t1")).thenReturn(null);
        assertThat(mapper.evaluate("a1", "t1", intensity(0.7))).isNull();
    }

    @Test
    void returnsNull_whenTotalScoredZero() {
        when(orchestrator.knowledgeGaps("a1", "t1"))
                .thenReturn(KnowledgeGapSummary.empty());
        assertThat(mapper.evaluate("a1", "t1", intensity(0.7))).isNull();
    }

    private DriveIntensity intensity(double value) {
        return new DriveIntensity(DriveAxis.CURIOSITY, value, "test");
    }
}
