package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.memory.KnowledgeGapSummary;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
}
