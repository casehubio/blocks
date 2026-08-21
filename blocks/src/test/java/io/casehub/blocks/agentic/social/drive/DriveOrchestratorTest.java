package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.neocortex.memory.mood.MoodState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriveOrchestratorTest {

    private final Instant fixedNow = Instant.parse("2026-08-21T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    private CuriosityDrive curiosity;
    private CompetenceDrive competence;
    private AffiliationDrive affiliation;
    private AutonomyDrive autonomy;
    private MoodOrchestrator moodOrchestrator;
    private DriveComposer composer;
    private AgentDescriptor descriptor;

    @BeforeEach
    void setUp() {
        curiosity = mock(CuriosityDrive.class);
        competence = mock(CompetenceDrive.class);
        affiliation = mock(AffiliationDrive.class);
        autonomy = mock(AutonomyDrive.class);
        moodOrchestrator = mock(MoodOrchestrator.class);
        composer = new DriveComposer();
        descriptor = mock(AgentDescriptor.class);

        when(descriptor.disposition()).thenReturn(AgentDisposition.builder().build());
        when(moodOrchestrator.currentMood("agent-1", "tenant-1")).thenReturn(Optional.empty());

        when(curiosity.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "gaps"));
        when(competence.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.COMPETENCE, 0.3, "ok"));
        when(affiliation.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.AFFILIATION, 0.1, "stable"));
        when(autonomy.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.AUTONOMY, 0.4, "moderate"));
    }

    private DriveOrchestrator createOrchestrator() {
        return new DriveOrchestrator(curiosity, competence, affiliation, autonomy,
                moodOrchestrator, composer, DriveConfig.defaults(), clock);
    }

    @Test
    void tick_firstCall_returnsUpdated() {
        var orchestrator = createOrchestrator();
        var tick = orchestrator.tick("agent-1", "tenant-1", descriptor);

        assertThat(tick).isInstanceOf(DriveTick.Updated.class);
        var updated = (DriveTick.Updated) tick;
        assertThat(updated.current().dominantDrive()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(updated.current().drives()).hasSize(4);
    }

    @Test
    void currentDrives_beforeTick_empty() {
        var orchestrator = createOrchestrator();
        assertThat(orchestrator.currentDrives("agent-1", "tenant-1")).isEmpty();
    }

    @Test
    void currentDrives_afterTick_returnsCachedProfile() {
        var orchestrator = createOrchestrator();
        orchestrator.tick("agent-1", "tenant-1", descriptor);

        var drives = orchestrator.currentDrives("agent-1", "tenant-1");
        assertThat(drives).isPresent();
        assertThat(drives.get().dominantDrive()).isEqualTo(DriveAxis.CURIOSITY);
    }

    @Test
    void tick_noChange_whenBelowThreshold() {
        var orchestrator = createOrchestrator();
        orchestrator.tick("agent-1", "tenant-1", descriptor);

        var tick2 = orchestrator.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick2).isInstanceOf(DriveTick.NoChange.class);
    }

    @Test
    void tick_separateAgents_independentState() {
        var orchestrator = createOrchestrator();
        when(curiosity.evaluate("agent-2", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.CURIOSITY, 0.9, "x"));
        when(competence.evaluate("agent-2", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.COMPETENCE, 0.9, "x"));
        when(affiliation.evaluate("agent-2", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.AFFILIATION, 0.9, "x"));
        when(autonomy.evaluate("agent-2", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.AUTONOMY, 0.9, "x"));
        when(moodOrchestrator.currentMood("agent-2", "tenant-1")).thenReturn(Optional.empty());

        orchestrator.tick("agent-1", "tenant-1", descriptor);
        orchestrator.tick("agent-2", "tenant-1", descriptor);

        var d1 = orchestrator.currentDrives("agent-1", "tenant-1").orElseThrow();
        var d2 = orchestrator.currentDrives("agent-2", "tenant-1").orElseThrow();

        assertThat(d1.compositeMotivation()).isNotEqualTo(d2.compositeMotivation());
    }

    @Test
    void tick_withMood_modulatesResult() {
        var orchestrator = createOrchestrator();
        var mood = new MoodState("agent-1", "tenant-1", 0.8, 0.5, 0.0, "happy", null, Map.of());
        when(moodOrchestrator.currentMood("agent-1", "tenant-1")).thenReturn(Optional.of(mood));

        var tick = orchestrator.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick).isInstanceOf(DriveTick.Updated.class);
        var updated = (DriveTick.Updated) tick;
        assertThat(updated.current().compositeMotivation()).isGreaterThan(0.0);
    }

    @Test
    void tick_detectsChange_whenIntensityShifts() {
        var orchestrator = createOrchestrator();
        orchestrator.tick("agent-1", "tenant-1", descriptor);

        when(curiosity.evaluate("agent-1", "tenant-1"))
                .thenReturn(new DriveIntensity(DriveAxis.CURIOSITY, 0.1, "resolved"));

        var tick2 = orchestrator.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick2).isInstanceOf(DriveTick.Updated.class);
        var updated = (DriveTick.Updated) tick2;
        assertThat(updated.changed()).contains(DriveAxis.CURIOSITY);
    }
}
