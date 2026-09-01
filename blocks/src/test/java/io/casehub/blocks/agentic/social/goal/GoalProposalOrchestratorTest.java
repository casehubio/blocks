package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalOutcomeCounts;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalProposalOrchestratorTest {

    private DriveOrchestrator driveOrchestrator;
    private GoalProposalOrchestrator orchestrator;
    private DriveGoalMapper curiosityMapper;
    private DriveGoalMapper competenceMapper;
    private Clock clock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        driveOrchestrator = mock(DriveOrchestrator.class);
        curiosityMapper = mock(DriveGoalMapper.class);
        competenceMapper = mock(DriveGoalMapper.class);

        Instance<GoalSignalStore> signalStoreInstance = mock(Instance.class);
        when(signalStoreInstance.isResolvable()).thenReturn(false);

        clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneId.of("UTC"));

        orchestrator = new GoalProposalOrchestrator(
                driveOrchestrator,
                List.of(curiosityMapper, competenceMapper),
                signalStoreInstance,
                GoalProposalConfig.defaults(),
                clock);
    }

    @Test
    void returnsNoChange_whenNoDrives() {
        when(driveOrchestrator.currentDrives("a1", "t1")).thenReturn(Optional.empty());

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());
        assertThat(tick).isInstanceOf(GoalProposalTick.NoChange.class);
    }

    @Test
    void proposesGoal_whenDriveAboveThreshold() {
        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        var proposal = new DriveGoalProposal(
                DriveAxis.CURIOSITY, "explore-knowledge-gaps",
                "Explore fragmented knowledge areas", "curiosity: 5 low-retention", 0.7);
        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.7)))
                .thenReturn(proposal);

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var proposed = (GoalProposalTick.Changes) tick;
        assertThat(proposed.newProposals()).hasSize(1);
        assertThat(proposed.newProposals().get(0).goalName()).isEqualTo("explore-knowledge-gaps");
    }

    @Test
    void returnsNoChange_whenDriveBelowThreshold() {
        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.2);

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());
        assertThat(tick).isInstanceOf(GoalProposalTick.NoChange.class);
    }

    @Test
    void respectsMaxDriveGoals() {
        var config = new GoalProposalConfig(0.4, 0.2, 2, Duration.ofMinutes(120),
                Duration.ofMinutes(60), 5);
        @SuppressWarnings("unchecked")
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(false);
        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper, competenceMapper), si, config, clock);

        var existingDriveGoals = List.of(
                driveGoal("goal-a"), driveGoal("goal-b"));
        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("A").slot("s").tenancyId("t1")
                .goals(existingDriveGoals).build();

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.8);
        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.8)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.CURIOSITY, "new-goal", "d", "r", 0.8));

        var tick = orch.tick("a1", "t1", descriptor);
        assertThat(tick).isInstanceOf(GoalProposalTick.NoChange.class);
    }

    @Test
    void ranksProposalsByIntensity() {
        var config = new GoalProposalConfig(0.4, 0.2, 1, Duration.ofMinutes(120),
                Duration.ofMinutes(60), 5);
        @SuppressWarnings("unchecked")
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(false);
        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper, competenceMapper), si, config, clock);

        var profile = new DriveProfile("a1", "t1",
                Map.of(DriveAxis.CURIOSITY, driveIntensity(DriveAxis.CURIOSITY, 0.5),
                       DriveAxis.COMPETENCE, driveIntensity(DriveAxis.COMPETENCE, 0.9)),
                0.7, DriveAxis.COMPETENCE, Instant.now(clock));
        when(driveOrchestrator.currentDrives("a1", "t1")).thenReturn(Optional.of(profile));

        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.5)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.CURIOSITY, "explore", "d", "r", 0.5));
        when(competenceMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.COMPETENCE, 0.9)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.COMPETENCE, "improve", "d", "r", 0.9));

        var tick = orch.tick("a1", "t1", descriptorWithGoals());

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var proposed = (GoalProposalTick.Changes) tick;
        assertThat(proposed.newProposals()).hasSize(1);
        assertThat(proposed.newProposals().get(0).goalName()).isEqualTo("improve");
    }

    @Test
    void abandonsGoal_whenDriveWeakensAndStaleElapsed() {
        @SuppressWarnings("unchecked")
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(false);
        var config = new GoalProposalConfig(0.4, 0.2, 3, Duration.ZERO,
                Duration.ZERO, 5);
        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper), si, config, clock);

        var driveGoal = driveGoal("explore-knowledge-gaps");
        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("A").slot("s").tenancyId("t1")
                .goals(List.of(driveGoal)).build();

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.1);
        var tick = orch.tick("a1", "t1", descriptor);

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var proposed = (GoalProposalTick.Changes) tick;
        assertThat(proposed.abandonedGoalNames()).contains("explore-knowledge-gaps");
    }

    @Test
    void respectsCooldown() {
        var config = new GoalProposalConfig(0.4, 0.2, 3, Duration.ofMinutes(120),
                Duration.ofMinutes(60), 5);
        @SuppressWarnings("unchecked")
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(false);
        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper), si, config, clock);

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.7)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.CURIOSITY, "explore", "d", "r", 0.7));

        orch.tick("a1", "t1", descriptorWithGoals());
        var second = orch.tick("a1", "t1", descriptorWithGoals());

        assertThat(second).isInstanceOf(GoalProposalTick.NoChange.class);
    }

    @Test
    void compositorGuarantee_noSideEffects() {
        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.7)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.CURIOSITY, "explore", "d", "r", 0.7));

        var tick1 = orchestrator.tick("a1", "t1", descriptorWithGoals());
        assertThat(tick1).isInstanceOf(GoalProposalTick.Changes.class);

        assertThat(orchestrator.currentProposals("a1", "t1")).isPresent();
    }

    @Test
    void currentProposals_emptyWhenNoTick() {
        assertThat(orchestrator.currentProposals("a1", "t1")).isEmpty();
    }

    @Test
    void mapperReturningNull_isSkipped() {
        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.7)))
                .thenReturn(null);

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());
        assertThat(tick).isInstanceOf(GoalProposalTick.NoChange.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failureAbandonment_whenThresholdExceeded() {
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(true);
        GoalSignalStore store = mock(GoalSignalStore.class);
        when(si.get()).thenReturn(store);
        when(store.outcomeCounts("a1", "t1"))
                .thenReturn(Map.of("explore-knowledge-gaps", new GoalOutcomeCounts(0, 5)));

        var config = new GoalProposalConfig(0.4, 0.2, 3, Duration.ofMinutes(120),
                Duration.ZERO, 5);
        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper), si, config, clock);

        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("A").slot("s").tenancyId("t1")
                .goals(List.of(driveGoal("explore-knowledge-gaps"))).build();

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);

        var tick = orch.tick("a1", "t1", descriptor);

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var proposed = (GoalProposalTick.Changes) tick;
        assertThat(proposed.abandonedGoalNames()).contains("explore-knowledge-gaps");
    }

    @Test
    @SuppressWarnings("unchecked")
    void failureSuppression_preventsReproposal() {
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(true);
        GoalSignalStore store = mock(GoalSignalStore.class);
        when(si.get()).thenReturn(store);
        when(store.outcomeCounts("a1", "t1"))
                .thenReturn(Map.of("explore-knowledge-gaps", new GoalOutcomeCounts(0, 5)));

        var config = new GoalProposalConfig(0.4, 0.2, 3, Duration.ofMinutes(120),
                Duration.ZERO, 5);
        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper), si, config, clock);

        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("A").slot("s").tenancyId("t1")
                .goals(List.of(driveGoal("explore-knowledge-gaps"))).build();

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.7)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.CURIOSITY, "explore-knowledge-gaps", "d", "r", 0.7));

        orch.tick("a1", "t1", descriptor);

        var emptyDescriptor = descriptorWithGoals();
        when(store.outcomeCounts("a1", "t1")).thenReturn(Map.of());
        var tick2 = orch.tick("a1", "t1", emptyDescriptor);

        if (tick2 instanceof GoalProposalTick.Changes p) {
            assertThat(p.newProposals().stream().map(DriveGoalProposal::goalName).toList())
                    .doesNotContain("explore-knowledge-gaps");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesFormationStrategy_whenPresent() {
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(false);

        DriveGoalFormationStrategy strategy = mock(DriveGoalFormationStrategy.class);
        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper), strategy,
                si, GoalProposalConfig.defaults(), clock);

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(strategy.propose(org.mockito.ArgumentMatchers.any(DriveGoalFormationContext.class)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.CURIOSITY, "llm-explore-physics",
                        "Deep exploration of physics concepts", "curiosity: LLM refined", 0.7));

        var tick = orch.tick("a1", "t1", descriptorWithGoals());

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var proposed = (GoalProposalTick.Changes) tick;
        assertThat(proposed.newProposals()).hasSize(1);
        assertThat(proposed.newProposals().get(0).goalName()).isEqualTo("llm-explore-physics");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToMapper_whenStrategyReturnsNull() {
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(false);

        DriveGoalFormationStrategy strategy = mock(DriveGoalFormationStrategy.class);
        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper), strategy,
                si, GoalProposalConfig.defaults(), clock);

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(strategy.propose(org.mockito.ArgumentMatchers.any(DriveGoalFormationContext.class)))
                .thenReturn(null);
        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.7)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.CURIOSITY, "explore-knowledge-gaps",
                        "Heuristic fallback", "curiosity: heuristic", 0.7));

        var tick = orch.tick("a1", "t1", descriptorWithGoals());

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var proposed = (GoalProposalTick.Changes) tick;
        assertThat(proposed.newProposals().get(0).goalName()).isEqualTo("explore-knowledge-gaps");
    }

    @Test
    @SuppressWarnings("unchecked")
    void strategyReceivesCorrectContext() {
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(false);

        org.mockito.ArgumentCaptor<DriveGoalFormationContext> captor =
                org.mockito.ArgumentCaptor.forClass(DriveGoalFormationContext.class);
        DriveGoalFormationStrategy strategy = mock(DriveGoalFormationStrategy.class);
        when(strategy.propose(captor.capture())).thenReturn(null);

        var existing = new AgentGoal("assigned-goal", "Do work",
                                     GoalPriority.PRIMARY, Visibility.PUBLIC, List.of(), null);
        var descriptor = AgentDescriptor.builder()
                                        .agentId("a1").name("A").slot("s").tenancyId("t1")
                                        .goals(List.of(existing)).build();

        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(), strategy,
                si, GoalProposalConfig.defaults(), clock);

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.65);
        orch.tick("a1", "t1", descriptor);

        var ctx = captor.getValue();
        assertThat(ctx.agentId()).isEqualTo("a1");
        assertThat(ctx.tenantId()).isEqualTo("t1");
        assertThat(ctx.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(ctx.intensity()).isEqualTo(0.65);
        assertThat(ctx.existingGoals()).containsExactly(existing);
        assertThat(ctx.remainingCapacity()).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToMapper_whenStrategyThrows() {
        Instance<GoalSignalStore> si = mock(Instance.class);
        when(si.isResolvable()).thenReturn(false);

        DriveGoalFormationStrategy strategy = mock(DriveGoalFormationStrategy.class);
        when(strategy.propose(org.mockito.ArgumentMatchers.any(DriveGoalFormationContext.class)))
                .thenThrow(new RuntimeException("LLM unavailable"));

        var orch = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper), strategy,
                si, GoalProposalConfig.defaults(), clock);

        setupDrives("a1", "t1", DriveAxis.CURIOSITY, 0.7);
        when(curiosityMapper.evaluate("a1", "t1", driveIntensity(DriveAxis.CURIOSITY, 0.7)))
                .thenReturn(new DriveGoalProposal(
                        DriveAxis.CURIOSITY, "explore-knowledge-gaps",
                        "Heuristic fallback after exception", "curiosity: heuristic", 0.7));

        var tick = orch.tick("a1", "t1", descriptorWithGoals());

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var proposed = (GoalProposalTick.Changes) tick;
        assertThat(proposed.newProposals().get(0).goalName()).isEqualTo("explore-knowledge-gaps");
    }


    private void setupDrives(String agentId, String tenantId, DriveAxis axis, double intensity) {
        var profile = new DriveProfile(agentId, tenantId,
                Map.of(axis, driveIntensity(axis, intensity)),
                intensity, axis, Instant.now(clock));
        when(driveOrchestrator.currentDrives(agentId, tenantId)).thenReturn(Optional.of(profile));
    }

    private DriveIntensity driveIntensity(DriveAxis axis, double intensity) {
        return new DriveIntensity(axis, intensity, "test");
    }

    private AgentDescriptor descriptorWithGoals(AgentGoal... goals) {
        return AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of(goals)).build();
    }

    private AgentGoal driveGoal(String name) {
        return new AgentGoal(name, "desc-" + name, GoalPriority.SECONDARY,
                Visibility.PUBLIC, List.of(),
                Map.of("source", "drive", "driveAxis", "CURIOSITY"));
    }
}
