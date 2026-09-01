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
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrossAxisCompositionTest {

    private DriveOrchestrator driveOrchestrator;
    private NarrativeOrchestrator narrativeOrchestrator;
    private GoalProposalOrchestrator orchestrator;
    private DriveGoalMapper curiosityMapper;
    private Clock clock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        driveOrchestrator = mock(DriveOrchestrator.class);
        narrativeOrchestrator = mock(NarrativeOrchestrator.class);
        curiosityMapper = mock(DriveGoalMapper.class);

        Instance<GoalSignalStore> signalStoreInstance = mock(Instance.class);
        when(signalStoreInstance.isResolvable()).thenReturn(false);

        clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneId.of("UTC"));

        orchestrator = new GoalProposalOrchestrator(
                driveOrchestrator,
                List.of(curiosityMapper),
                null,
                signalStoreInstance,
                narrativeOrchestrator,
                null,
                null,
                GoalProposalConfig.defaults(),
                GoalEscalationConfig.defaults(),
                clock);
    }

    @Test
    void crossAxis_detectsMultiAxisTheme() {
        var theme = theme("connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4));
        setupNarrative(narrativeWith(theme));
        setupDrives(Map.of(
                DriveAxis.CURIOSITY, 0.7,
                DriveAxis.AFFILIATION, 0.6));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var changes = (GoalProposalTick.Changes) tick;
        var crossAxis = changes.newProposals().stream()
                .filter(p -> p.proposalAttributes() != null
                        && p.proposalAttributes().containsKey("crossAxisWeights"))
                .toList();
        assertThat(crossAxis).hasSize(1);
        assertThat(crossAxis.get(0).axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(crossAxis.get(0).goalName()).startsWith("compound-curiosity-affiliation-");
    }

    @Test
    void crossAxis_ignoresNegativeWeights() {
        var theme = theme("suppressor", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, -0.4));
        setupNarrative(narrativeWith(theme));
        setupDrives(Map.of(DriveAxis.CURIOSITY, 0.7));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        if (tick instanceof GoalProposalTick.Changes changes) {
            var crossAxis = changes.newProposals().stream()
                    .filter(p -> p.proposalAttributes() != null
                            && p.proposalAttributes().containsKey("crossAxisWeights"))
                    .toList();
            assertThat(crossAxis).isEmpty();
        }
    }

    @Test
    void crossAxis_ignoresWeightsBelowThreshold() {
        var theme = theme("weak-axes", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.1, DriveAxis.AFFILIATION, 0.15));
        setupNarrative(narrativeWith(theme));
        setupDrives(Map.of(DriveAxis.CURIOSITY, 0.7));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        if (tick instanceof GoalProposalTick.Changes changes) {
            var crossAxis = changes.newProposals().stream()
                    .filter(p -> p.proposalAttributes() != null
                            && p.proposalAttributes().containsKey("crossAxisWeights"))
                    .toList();
            assertThat(crossAxis).isEmpty();
        }
    }

    @Test
    void crossAxis_useDominantAxisIntensity() {
        var theme = theme("connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4));
        setupNarrative(narrativeWith(theme));
        setupDrives(Map.of(
                DriveAxis.CURIOSITY, 0.7,
                DriveAxis.AFFILIATION, 0.9));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        var changes = (GoalProposalTick.Changes) tick;
        var crossAxis = changes.newProposals().stream()
                .filter(p -> p.proposalAttributes() != null
                        && p.proposalAttributes().containsKey("crossAxisWeights"))
                .toList();
        assertThat(crossAxis).hasSize(1);
        assertThat(crossAxis.get(0).driveIntensity()).isEqualTo(0.7);
    }

    @Test
    void crossAxis_skippedWhenNarrativeAbsent() {
        when(narrativeOrchestrator.currentNarrative("a1", "t1"))
                .thenReturn(Optional.empty());
        setupDrives(Map.of(DriveAxis.CURIOSITY, 0.7));
        when(curiosityMapper.evaluate(eq("a1"), eq("t1"), any()))
                .thenReturn(new DriveGoalProposal(DriveAxis.CURIOSITY,
                        "explore", "desc", "reason", 0.7));

        var tick = orchestrator.tick("a1", "t1", descriptorWithGoals());

        var changes = (GoalProposalTick.Changes) tick;
        assertThat(changes.newProposals()).hasSize(1);
        assertThat(changes.newProposals().get(0).proposalAttributes()).isNull();
    }

    @Test
    void crossAxis_enricherUsedWhenPresent() {
        CrossAxisGoalEnricher enricher = (proposal, narrative, theme) ->
                new DriveGoalProposal(proposal.axis(), proposal.goalName(),
                        "Enriched: learn about physics through collaboration",
                        "LLM-enriched", proposal.driveIntensity(),
                        proposal.suggestedPriority(), proposal.proposalAttributes());

        @SuppressWarnings("unchecked")
        Instance<GoalSignalStore> signalStoreInstance = mock(Instance.class);
        when(signalStoreInstance.isResolvable()).thenReturn(false);

        var enrichedOrchestrator = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(curiosityMapper), null,
                signalStoreInstance, narrativeOrchestrator, null, enricher,
                GoalProposalConfig.defaults(), GoalEscalationConfig.defaults(), clock);

        var theme = theme("connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4));
        setupNarrative(narrativeWith(theme));
        setupDrives(Map.of(DriveAxis.CURIOSITY, 0.7, DriveAxis.AFFILIATION, 0.6));

        var tick = enrichedOrchestrator.tick("a1", "t1", descriptorWithGoals());

        var changes = (GoalProposalTick.Changes) tick;
        var crossAxis = changes.newProposals().stream()
                .filter(p -> p.proposalAttributes() != null
                        && p.proposalAttributes().containsKey("crossAxisWeights"))
                .toList();
        assertThat(crossAxis).hasSize(1);
        assertThat(crossAxis.get(0).goalDescription()).startsWith("Enriched:");
    }

    private DerivedTheme theme(String label, double salience, Map<DriveAxis, Double> weights) {
        return new DerivedTheme("t-" + label, Instant.now(), null, List.of(),
                label, salience, weights, List.of());
    }

    private NarrativeState narrativeWith(DerivedTheme... themes) {
        List<NarrativeFragment> fragments = new ArrayList<>(List.of(themes));
        return new NarrativeState("a1", "t1", NarrativeScope.INDIVIDUAL,
                fragments, Instant.now(), 5);
    }

    private void setupNarrative(NarrativeState state) {
        when(narrativeOrchestrator.currentNarrative("a1", "t1"))
                .thenReturn(Optional.of(state));
    }

    private void setupDrives(Map<DriveAxis, Double> intensities) {
        var drives = new java.util.EnumMap<DriveAxis, DriveIntensity>(DriveAxis.class);
        DriveAxis dominant = null;
        double maxIntensity = 0;
        for (var entry : intensities.entrySet()) {
            drives.put(entry.getKey(),
                    new DriveIntensity(entry.getKey(), entry.getValue(), "test"));
            if (entry.getValue() > maxIntensity) {
                maxIntensity = entry.getValue();
                dominant = entry.getKey();
            }
        }
        var profile = new DriveProfile("a1", "t1", drives, maxIntensity,
                dominant != null ? dominant : DriveAxis.CURIOSITY, Instant.now(clock));
        when(driveOrchestrator.currentDrives("a1", "t1")).thenReturn(Optional.of(profile));
    }

    private AgentDescriptor descriptorWithGoals(AgentGoal... goals) {
        return AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of(goals)).build();
    }
}
