package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeScope;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.blocks.memory.KnowledgeGapSummary;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.neocortex.memory.mood.MoodState;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        var mood = new MoodState("agent-1", "tenant-1", null, 0.8, 0.5, 0.0, "happy", null, Map.of());
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

    // --- CDI constructor tests ---

    @SuppressWarnings("unchecked")
    @Test
    void cdiConstructor_with_hygiene_available() {
        var hygiene = mock(MemoryHygieneOrchestrator.class);
        when(hygiene.knowledgeGaps("agent-1", "tenant-1"))
                .thenReturn(new KnowledgeGapSummary(5, 10, 3));

        Instance<MemoryHygieneOrchestrator> hygieneInstance = mock(Instance.class);
        when(hygieneInstance.isResolvable()).thenReturn(true);
        when(hygieneInstance.get()).thenReturn(hygiene);

        var strategy = mock(StrategyLearningOrchestrator.class);
        when(strategy.engagementTrend("agent-1", "tenant-1")).thenReturn(Optional.empty());
        var userModel = mock(UserModelOrchestrator.class);
        when(userModel.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of());
        var mentalModel = mock(MentalModelOrchestrator.class);
        when(mentalModel.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of());

        Instance<NarrativeOrchestrator> narrativeInstance = mock(Instance.class);
        when(narrativeInstance.isResolvable()).thenReturn(false);

        var orch = new DriveOrchestrator(hygieneInstance, strategy, userModel,
                mentalModel, moodOrchestrator, new DriveComposer(), DriveConfig.defaults(),
                narrativeInstance);

        var tick = orch.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick).isInstanceOf(DriveTick.Updated.class);
        var profile = ((DriveTick.Updated) tick).current();
        assertThat(profile.drives().get(DriveAxis.CURIOSITY).intensity()).isGreaterThan(0.0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void cdiConstructor_without_hygiene_returns_zero_curiosity() {
        Instance<MemoryHygieneOrchestrator> hygieneInstance = mock(Instance.class);
        when(hygieneInstance.isResolvable()).thenReturn(false);

        var strategy = mock(StrategyLearningOrchestrator.class);
        when(strategy.engagementTrend("agent-1", "tenant-1")).thenReturn(Optional.empty());
        var userModel = mock(UserModelOrchestrator.class);
        when(userModel.activeProfiles("agent-1", "tenant-1")).thenReturn(List.of());
        var mentalModel = mock(MentalModelOrchestrator.class);
        when(mentalModel.activeSnapshots("agent-1", "tenant-1")).thenReturn(List.of());

        Instance<NarrativeOrchestrator> narrativeInstance = mock(Instance.class);
        when(narrativeInstance.isResolvable()).thenReturn(false);

        var orch = new DriveOrchestrator(hygieneInstance, strategy, userModel,
                mentalModel, moodOrchestrator, new DriveComposer(), DriveConfig.defaults(),
                narrativeInstance);

        var tick = orch.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick).isInstanceOf(DriveTick.Updated.class);
        var profile = ((DriveTick.Updated) tick).current();
        assertThat(profile.drives().get(DriveAxis.CURIOSITY).intensity()).isEqualTo(0.0);
    }

    @Test
    void tick_withNarrativeModulation_amplifiesDrives() {
        var narrativeOrch = mock(NarrativeOrchestrator.class);
        var theme = new DerivedTheme("t1", fixedNow, null,
                List.of("helper"), "crisis-helper", 0.9,
                Map.of(DriveAxis.AFFILIATION, 0.8, DriveAxis.COMPETENCE, 0.5),
                List.of("e1"));
        var narrative = new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, List.of(theme), fixedNow, 5);
        when(narrativeOrch.currentNarrative("agent-1", "tenant-1"))
                .thenReturn(Optional.of(narrative));

        var orchestrator = new DriveOrchestrator(curiosity, competence,
                affiliation, autonomy, moodOrchestrator, composer,
                DriveConfig.defaults(), clock, narrativeOrch);

        var tick = orchestrator.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick).isInstanceOf(DriveTick.Updated.class);
        var profile = ((DriveTick.Updated) tick).current();
        assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity())
                .isGreaterThan(0.1);
    }

    @Test
    void tick_withoutNarrativeOrchestrator_noModulation() {
        var orchestrator = createOrchestrator();
        var tick = orchestrator.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick).isInstanceOf(DriveTick.Updated.class);
        var profile = ((DriveTick.Updated) tick).current();
        assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity()).isEqualTo(0.1);
    }

    @Test
    void tick_withNarrativeButNoState_noModulation() {
        var narrativeOrch = mock(NarrativeOrchestrator.class);
        when(narrativeOrch.currentNarrative("agent-1", "tenant-1"))
                .thenReturn(Optional.empty());

        var orchestrator = new DriveOrchestrator(curiosity, competence,
                affiliation, autonomy, moodOrchestrator, composer,
                DriveConfig.defaults(), clock, narrativeOrch);

        var tick = orchestrator.tick("agent-1", "tenant-1", descriptor);
        assertThat(tick).isInstanceOf(DriveTick.Updated.class);
        var profile = ((DriveTick.Updated) tick).current();
        assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity()).isEqualTo(0.1);
    }

    @Test
    void curiosityDrive_returnsConcrete_whenCuriosityDriveInstance() {
        var orchestrator = createOrchestrator();
        assertThat(orchestrator.curiosityDrive()).isSameAs(curiosity);
    }

    @Test
    void curiosityDrive_returnsNull_whenLambdaFallback() {
        DriveSource lambda = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "fallback");
        var orchestrator = new DriveOrchestrator(lambda, competence, affiliation, autonomy,
                                                 moodOrchestrator, composer, DriveConfig.defaults(), clock);
        assertThat(orchestrator.curiosityDrive()).isNull();
    }

    @Test
    void competenceDrive_returnsConcrete() {
        var orchestrator = createOrchestrator();
        assertThat(orchestrator.competenceDrive()).isSameAs(competence);
    }

    @Test
    void affiliationDrive_returnsConcrete() {
        var orchestrator = createOrchestrator();
        assertThat(orchestrator.affiliationDrive()).isSameAs(affiliation);
    }

    @Test
    void autonomyDrive_returnsConcrete() {
        var orchestrator = createOrchestrator();
        assertThat(orchestrator.autonomyDrive()).isSameAs(autonomy);
    }
}
