package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AffiliationDriveTest {

    private final Instant now = Instant.now();

    private UserProfile profile(String subjectId, double familiarity, Instant lastInteraction) {
        return new UserProfile("agent-1", subjectId, "tenant-1", "established",
                familiarity, 10, 5, 2, 3, lastInteraction, now.minus(Duration.ofDays(30)),
                null, null, null, null, null, Map.of());
    }

    @Test
    void evaluate_noProfiles_zeroIntensity() {
        var orchestrator = mock(UserModelOrchestrator.class);
        when(orchestrator.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of());

        var drive = new AffiliationDrive(orchestrator, 0.4, Duration.ofDays(7));
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.axis()).isEqualTo(DriveAxis.AFFILIATION);
        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_decayingRelationships_positiveIntensity() {
        var orchestrator = mock(UserModelOrchestrator.class);
        when(orchestrator.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of(
                profile("user-1", 0.3, now.minus(Duration.ofDays(10))),
                profile("user-2", 0.2, now.minus(Duration.ofDays(14)))));

        var drive = new AffiliationDrive(orchestrator, 0.4, Duration.ofDays(7));
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isGreaterThan(0.0);
    }

    @Test
    void evaluate_healthyRelationships_zeroIntensity() {
        var orchestrator = mock(UserModelOrchestrator.class);
        when(orchestrator.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of(
                profile("user-1", 0.9, now.minus(Duration.ofHours(2))),
                profile("user-2", 0.8, now.minus(Duration.ofHours(6)))));

        var drive = new AffiliationDrive(orchestrator, 0.4, Duration.ofDays(7));
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isEqualTo(0.0);
    }

    @Test
    void evaluate_mixedRelationships_partialIntensity() {
        var orchestrator = mock(UserModelOrchestrator.class);
        when(orchestrator.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of(
                profile("user-1", 0.9, now.minus(Duration.ofHours(2))),
                profile("user-2", 0.2, now.minus(Duration.ofDays(14)))));

        var drive = new AffiliationDrive(orchestrator, 0.4, Duration.ofDays(7));
        var result = drive.evaluate("agent-1", "tenant-1");

        assertThat(result.intensity()).isCloseTo(0.5, within(0.01));
    }
}
