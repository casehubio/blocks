package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.memory.KnowledgeGapSummary;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CuriosityDriveTest {

    @Test
    void evaluate_noGaps_zeroIntensity() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(KnowledgeGapSummary.empty());

        var drive = new CuriosityDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_lowRetentionMemories_positiveIntensity() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(new KnowledgeGapSummary(5, 3, 20));

        var drive = new CuriosityDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.intensity()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void evaluate_manyGaps_highIntensity() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(new KnowledgeGapSummary(15, 8, 30));

        var drive = new CuriosityDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.5);
    }

    @Test
    void evaluate_intensityNeverExceedsOne() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(new KnowledgeGapSummary(100, 50, 200));

        var drive = new CuriosityDrive(orchestrator);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void lastGaps_nullBeforeEvaluation() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        var drive        = new CuriosityDrive(orchestrator);

        assertThat(drive.lastGaps()).isNull();
    }

    @Test
    void lastGaps_cachedAfterEvaluation() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        var gaps         = new KnowledgeGapSummary(5, 3, 20);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1")).thenReturn(gaps);

        var drive = new CuriosityDrive(orchestrator);
        drive.evaluate("agent-1", "tenant-1");

        assertThat(drive.lastGaps()).isSameAs(gaps);
    }

    @Test
    void lastGaps_updatedOnSubsequentEvaluation() {
        var orchestrator = mock(MemoryHygieneOrchestrator.class);
        var gaps1        = new KnowledgeGapSummary(5, 3, 20);
        var gaps2        = new KnowledgeGapSummary(10, 7, 30);
        when(orchestrator.knowledgeGaps("agent-1", "tenant-1")).thenReturn(gaps1, gaps2);

        var drive = new CuriosityDrive(orchestrator);
        drive.evaluate("agent-1", "tenant-1");
        assertThat(drive.lastGaps()).isSameAs(gaps1);

        drive.evaluate("agent-1", "tenant-1");
        assertThat(drive.lastGaps()).isSameAs(gaps2);
    }


}
