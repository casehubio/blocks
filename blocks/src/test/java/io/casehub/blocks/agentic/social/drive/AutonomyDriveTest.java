package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.AttributedState;
import io.casehub.blocks.agentic.social.BdiDimension;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutonomyDriveTest {

    private final Instant now = Instant.now();

    private MentalModelSnapshot snapshot(String subjectId, List<AttributedState> intentions) {
        return new MentalModelSnapshot("agent-1", subjectId, "tenant-1",
                List.of(), List.of(), intentions, now, null, now);
    }

    private AttributedState intention(String key, double confidence) {
        return new AttributedState(key, key, confidence, 1, now, BdiDimension.INTENTION);
    }

    @Test
    void evaluate_noSnapshots_zeroIntensity() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        when(orchestrator.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of());

        var drive = new AutonomyDrive(orchestrator, 0.5);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.AUTONOMY);
        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_highConfidenceIntentions_positiveIntensity() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        when(orchestrator.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of(
                snapshot("user-1", List.of(
                        intention("redirect-workflow", 0.8),
                        intention("change-approach", 0.7))),
                snapshot("user-2", List.of(
                        intention("override-decision", 0.9)))));

        var drive = new AutonomyDrive(orchestrator, 0.5);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.0);
    }

    @Test
    void evaluate_lowConfidenceIntentions_filtered() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        when(orchestrator.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of(
                snapshot("user-1", List.of(
                        intention("maybe-redirect", 0.3),
                        intention("vague-change", 0.2)))));

        var drive = new AutonomyDrive(orchestrator, 0.5);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_intensityBounded() {
        var orchestrator = mock(MentalModelOrchestrator.class);
        when(orchestrator.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of(
                snapshot("user-1", List.of(
                        intention("a", 1.0), intention("b", 1.0),
                        intention("c", 1.0), intention("d", 1.0),
                        intention("e", 1.0), intention("f", 1.0),
                        intention("g", 1.0), intention("h", 1.0)))));

        var drive = new AutonomyDrive(orchestrator, 0.5);
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isLessThanOrEqualTo(1.0);
    }
}
