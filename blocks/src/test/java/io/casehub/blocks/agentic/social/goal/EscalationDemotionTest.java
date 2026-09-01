package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeScope;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.GoalSignalStore;
import io.casehub.eidos.api.Visibility;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EscalationDemotionTest {

    private DriveOrchestrator driveOrchestrator;
    private NarrativeOrchestrator narrativeOrchestrator;
    private NarrativeGoalEscalationPolicy escalationPolicy;
    private GoalProposalOrchestrator orchestrator;
    private DriveGoalMapper curiosityMapper;
    private Instant baseTime;
    private Clock clock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        driveOrchestrator = mock(DriveOrchestrator.class);
        narrativeOrchestrator = mock(NarrativeOrchestrator.class);
        escalationPolicy = new NarrativeGoalEscalationPolicy(GoalEscalationConfig.defaults());
        curiosityMapper = mock(DriveGoalMapper.class);

        Instance<GoalSignalStore> signalStoreInstance = mock(Instance.class);
        when(signalStoreInstance.isResolvable()).thenReturn(false);

        baseTime = Instant.parse("2026-08-23T12:00:00Z");
        clock = Clock.fixed(baseTime, ZoneId.of("UTC"));

        var zeroCooldownConfig = new GoalProposalConfig(
                0.4, 0.2, 3, Duration.ofMinutes(120), Duration.ZERO, 5);

        orchestrator = new GoalProposalOrchestrator(
                driveOrchestrator,
                List.of(curiosityMapper),
                null,
                signalStoreInstance,
                narrativeOrchestrator,
                escalationPolicy,
                null,
                zeroCooldownConfig,
                GoalEscalationConfig.defaults(),
                clock);
    }

    @Test
    void escalation_notTriggeredOnFirstSynthesis() {
        var theme = theme("explorer", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5));
        setupDrives(DriveAxis.CURIOSITY, 0.7);
        setupMapper(DriveAxis.CURIOSITY, 0.7);
        setupNarrative(narrativeWithSynthesisAt(theme, baseTime));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        var changes = (GoalProposalTick.Changes) tick;
        assertThat(changes.newProposals()).hasSize(1);
        assertThat(changes.newProposals().get(0).suggestedPriority()).isNull();
    }

    @Test
    void escalation_triggeredAfterTwoSynthesisCycles() {
        var theme = theme("explorer", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5));
        setupDrives(DriveAxis.CURIOSITY, 0.7);
        setupMapper(DriveAxis.CURIOSITY, 0.7);

        // Tick 1: first synthesis cycle — not yet escalated
        setupNarrative(narrativeWithSynthesisAt(theme, baseTime));
        orchestrator.tick("a1", "t1", descriptorWithGoals());

        // Tick 2: second synthesis cycle — escalated
        setupNarrative(narrativeWithSynthesisAt(theme, baseTime.plus(Duration.ofHours(1))));
        var tick2 = orchestrator.tick("a1", "t1", descriptorWithGoals());

        var changes = (GoalProposalTick.Changes) tick2;
        assertThat(changes.newProposals()).hasSize(1);
        assertThat(changes.newProposals().get(0).suggestedPriority())
                .isEqualTo(GoalPriority.PRIMARY);
        assertThat(changes.newProposals().get(0).proposalAttributes())
                .containsEntry("escalatedBy", "narrative")
                .containsEntry("escalation.theme", "explorer");
    }

    @Test
    void escalation_resetWhenAlignmentLost() {
        var theme = theme("explorer", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5));
        var noTheme = theme("unrelated", 0.9, Map.of(DriveAxis.AFFILIATION, 0.8));

        setupDrives(DriveAxis.CURIOSITY, 0.7);
        setupMapper(DriveAxis.CURIOSITY, 0.7);

        // Tick 1: aligned
        setupNarrative(narrativeWithSynthesisAt(theme, baseTime));
        orchestrator.tick("a1", "t1", descriptorWithGoals());

        // Tick 2: alignment lost — counter resets
        setupNarrative(narrativeWithSynthesisAt(noTheme, baseTime.plus(Duration.ofHours(1))));
        orchestrator.tick("a1", "t1", descriptorWithGoals());

        // Tick 3: alignment restored — only 1 cycle, not escalated
        setupNarrative(narrativeWithSynthesisAt(theme, baseTime.plus(Duration.ofHours(2))));
        var tick3 = orchestrator.tick("a1", "t1", descriptorWithGoals());

        var changes = (GoalProposalTick.Changes) tick3;
        assertThat(changes.newProposals().get(0).suggestedPriority()).isNull();
    }

    @Test
    void escalation_noIncrementWithoutNewSynthesis() {
        var theme = theme("explorer", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5));
        setupDrives(DriveAxis.CURIOSITY, 0.7);
        setupMapper(DriveAxis.CURIOSITY, 0.7);

        // Tick 1: first synthesis
        setupNarrative(narrativeWithSynthesisAt(theme, baseTime));
        orchestrator.tick("a1", "t1", descriptorWithGoals());

        // Tick 2: same synthesisedAt — no increment
        var tick2 = orchestrator.tick("a1", "t1", descriptorWithGoals());

        if (tick2 instanceof GoalProposalTick.Changes changes) {
            for (var p : changes.newProposals()) {
                assertThat(p.suggestedPriority()).isNull();
            }
        }
    }

    @Test
    void demotion_afterSustainedMisalignment() {
        var existingPrimary = new AgentGoal("explore", "desc", GoalPriority.PRIMARY,
                Visibility.PUBLIC, List.of(),
                Map.of("source", "drive", "driveAxis", "CURIOSITY",
                       "escalatedBy", "narrative", "escalation.theme", "explorer",
                       "escalation.lastAlignedSynthesisAt", baseTime.toString()));

        setupDrives(DriveAxis.CURIOSITY, 0.7);
        setupMapper(DriveAxis.CURIOSITY, 0.7);

        // Tick 1: no matching theme — first misaligned cycle
        setupNarrative(narrativeWithSynthesisAt(
                theme("other", 0.5, Map.of(DriveAxis.AFFILIATION, 0.3)),
                baseTime.plus(Duration.ofHours(1))));
        var tick1 = orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));
        assertThat(((GoalProposalTick.Changes) tick1).priorityAdjustments()).isEmpty();

        // Tick 2: still misaligned — second cycle, demotion triggered
        setupNarrative(narrativeWithSynthesisAt(
                theme("other", 0.5, Map.of(DriveAxis.AFFILIATION, 0.3)),
                baseTime.plus(Duration.ofHours(2))));
        var tick2 = orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));

        var changes = (GoalProposalTick.Changes) tick2;
        assertThat(changes.priorityAdjustments()).hasSize(1);
        assertThat(changes.priorityAdjustments().get(0).goalName()).isEqualTo("explore");
        assertThat(changes.priorityAdjustments().get(0).newPriority())
                .isEqualTo(GoalPriority.SECONDARY);
    }

    @Test
    void demotion_resetWhenAlignmentRestored() {
        var theme = theme("explorer", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5));
        var existingPrimary = new AgentGoal("explore", "desc", GoalPriority.PRIMARY,
                Visibility.PUBLIC, List.of(),
                Map.of("source", "drive", "driveAxis", "CURIOSITY",
                       "escalatedBy", "narrative", "escalation.theme", "explorer",
                       "escalation.lastAlignedSynthesisAt", baseTime.toString()));

        setupDrives(DriveAxis.CURIOSITY, 0.7);
        setupMapper(DriveAxis.CURIOSITY, 0.7);

        // Tick 1: misaligned
        setupNarrative(narrativeWithSynthesisAt(
                theme("other", 0.5, Map.of(DriveAxis.AFFILIATION, 0.3)),
                baseTime.plus(Duration.ofHours(1))));
        orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));

        // Tick 2: alignment restored — counter resets
        setupNarrative(narrativeWithSynthesisAt(theme,
                baseTime.plus(Duration.ofHours(2))));
        orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));

        // Tick 3: misaligned again — only 1 cycle, no demotion
        setupNarrative(narrativeWithSynthesisAt(
                theme("other", 0.5, Map.of(DriveAxis.AFFILIATION, 0.3)),
                baseTime.plus(Duration.ofHours(3))));
        var tick3 = orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));

        assertThat(((GoalProposalTick.Changes) tick3).priorityAdjustments()).isEmpty();
    }

    @Test
    void demotion_governanceUpdateEmitted() {
        var existingPrimary = new AgentGoal("explore", "desc", GoalPriority.PRIMARY,
                Visibility.PUBLIC, List.of(),
                Map.of("source", "drive", "driveAxis", "CURIOSITY",
                       "escalatedBy", "narrative", "escalation.theme", "explorer",
                       "escalation.lastAlignedSynthesisAt", baseTime.toString()));

        setupDrives(DriveAxis.CURIOSITY, 0.7);
        setupMapper(DriveAxis.CURIOSITY, 0.7);

        // Tick with misaligned narrative — governance update emitted
        setupNarrative(narrativeWithSynthesisAt(
                theme("other", 0.5, Map.of(DriveAxis.AFFILIATION, 0.3)),
                baseTime.plus(Duration.ofHours(1))));
        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals(existingPrimary));

        var changes = (GoalProposalTick.Changes) tick;
        assertThat(changes.governanceUpdates()).isNotEmpty();
        var update = changes.governanceUpdates().get(0);
        assertThat(update.goalName()).isEqualTo("explore");
        assertThat(update.attributeUpdates()).containsKey("escalation.misalignedCycleCount");
    }

    private DerivedTheme theme(String label, double salience, Map<DriveAxis, Double> weights) {
        return new DerivedTheme("t-" + label, Instant.now(), null, List.of(),
                label, salience, weights, List.of());
    }

    private NarrativeState narrativeWithSynthesisAt(DerivedTheme theme, Instant synthesisedAt) {
        List<NarrativeFragment> fragments = new ArrayList<>();
        fragments.add(theme);
        return new NarrativeState("a1", "t1", NarrativeScope.INDIVIDUAL,
                fragments, synthesisedAt, 5);
    }

    private void setupNarrative(NarrativeState state) {
        when(narrativeOrchestrator.currentNarrative("a1", "t1"))
                .thenReturn(Optional.of(state));
    }

    private void setupDrives(DriveAxis axis, double intensity) {
        var drives = new EnumMap<DriveAxis, DriveIntensity>(DriveAxis.class);
        drives.put(axis, new DriveIntensity(axis, intensity, "test"));
        var profile = new DriveProfile("a1", "t1", drives, intensity, axis, Instant.now(clock));
        when(driveOrchestrator.currentDrives("a1", "t1")).thenReturn(Optional.of(profile));
    }

    private void setupMapper(DriveAxis axis, double intensity) {
        when(curiosityMapper.evaluate(eq("a1"), eq("t1"), any()))
                .thenReturn(new DriveGoalProposal(axis,
                        "explore-knowledge-gaps", "desc", "reason", intensity));
    }

    private AgentDescriptor descriptorWithGoals(AgentGoal... goals) {
        return AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of(goals)).build();
    }
}
