package io.casehub.blocks.agentic.social.emergence;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class EmergenceTypesTest {

    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void socialNorm_validConstruction() {
        var norm = new SocialNorm("n1", "verify before escalating",
                "verify-then-escalate", 0.85, 20,
                Set.of("agent-a", "agent-b"), now, now, NormStrength.ESTABLISHED);
        assertThat(norm.adherenceRate()).isEqualTo(0.85);
        assertThat(norm.strength()).isEqualTo(NormStrength.ESTABLISHED);
        assertThat(norm.participatingAgents()).containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void socialNorm_rejectsAdherenceAboveOne() {
        assertThatThrownBy(() -> new SocialNorm("n1", "desc", "pattern",
                1.5, 10, Set.of(), now, now, NormStrength.EMERGING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adherenceRate");
    }

    @Test
    void socialNorm_rejectsNegativeAdherence() {
        assertThatThrownBy(() -> new SocialNorm("n1", "desc", "pattern",
                -0.1, 10, Set.of(), now, now, NormStrength.EMERGING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void socialNorm_defensiveCopyParticipants() {
        var agents = new java.util.HashSet<>(Set.of("a"));
        var norm = new SocialNorm("n1", "d", "p", 0.5, 5, agents, now, now, NormStrength.EMERGING);
        agents.add("b");
        assertThat(norm.participatingAgents()).containsExactly("a");
    }

    @Test
    void normObservation_validConstruction() {
        var obs = new NormObservation("o1", "tenant-1", "verify-then-escalate",
                Set.of("agent-a", "agent-b"), "conv-1", now, true);
        assertThat(obs.patternFollowed()).isTrue();
        assertThat(obs.involvedAgents()).hasSize(2);
    }

    @Test
    void normObservation_rejectsNullPattern() {
        assertThatThrownBy(() -> new NormObservation("o1", "t", null,
                Set.of(), "c", now, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void detectedNorms_establishedFilter() {
        var established = new SocialNorm("n1", "d", "p", 0.8, 15,
                Set.of("a"), now, now, NormStrength.ESTABLISHED);
        var emerging = new SocialNorm("n2", "d", "p", 0.5, 8,
                Set.of("a"), now, now, NormStrength.EMERGING);
        var declining = new SocialNorm("n3", "d", "p", 0.3, 20,
                Set.of("a"), now, now, NormStrength.DECLINING);
        var detected = new DetectedNorms(List.of(established, emerging, declining), 30, now);

        assertThat(detected.established()).hasSize(1);
        assertThat(detected.established().getFirst().normId()).isEqualTo("n1");
        assertThat(detected.norms()).hasSize(3);
    }

    @Test
    void detectedNorms_defensiveCopy() {
        var norms = new java.util.ArrayList<>(List.of(
                new SocialNorm("n1", "d", "p", 0.5, 5, Set.of(), now, now, NormStrength.EMERGING)));
        var detected = new DetectedNorms(norms, 10, now);
        norms.add(new SocialNorm("n2", "d", "p", 0.7, 10, Set.of(), now, now, NormStrength.ESTABLISHED));
        assertThat(detected.norms()).hasSize(1);
    }

    @Test
    void normDetectionConfig_defaults() {
        var config = NormDetectionConfig.defaults();
        assertThat(config.minObservationsForNorm()).isEqualTo(10);
        assertThat(config.establishedThreshold()).isEqualTo(0.7);
        assertThat(config.decliningThreshold()).isEqualTo(0.4);
        assertThat(config.minAgentsForNorm()).isEqualTo(2);
    }

    @Test
    void normDetectionConfig_rejectsZeroObservations() {
        assertThatThrownBy(() -> new NormDetectionConfig(0, 0.7, 0.4, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normDetectionConfig_rejectsThresholdOutOfRange() {
        assertThatThrownBy(() -> new NormDetectionConfig(10, 1.5, 0.4, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void driveAlignment_validConstruction() {
        var alignment = new DriveAlignment(
                Set.of("agent-a", "agent-b"),
                Map.of(DriveAxis.CURIOSITY, 0.9, DriveAxis.AFFILIATION, 0.7),
                0.8, DriveAxis.CURIOSITY, now);
        assertThat(alignment.compositeAlignment()).isEqualTo(0.8);
        assertThat(alignment.dominantSharedAxis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(alignment.agentIds()).hasSize(2);
    }

    @Test
    void driveAlignment_rejectsCompositeAboveOne() {
        assertThatThrownBy(() -> new DriveAlignment(Set.of("a"), Map.of(),
                1.5, null, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void driveAlignment_allowsNullDominantAxis() {
        var alignment = new DriveAlignment(Set.of("a"), Map.of(),
                0.0, null, now);
        assertThat(alignment.dominantSharedAxis()).isNull();
    }

    @Test
    void collectiveGoalProposal_validConstruction() {
        var alignment = new DriveAlignment(Set.of("a", "b"),
                Map.of(DriveAxis.CURIOSITY, 0.8), 0.8, DriveAxis.CURIOSITY, now);
        var proposal = new CollectiveGoalProposal(alignment,
                "explore shared knowledge gap",
                Set.of("a", "b"), DriveAxis.CURIOSITY);
        assertThat(proposal.goalDescription()).isEqualTo("explore shared knowledge gap");
        assertThat(proposal.proposedParticipants()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void collectiveGoalProposal_rejectsNullAlignment() {
        assertThatThrownBy(() -> new CollectiveGoalProposal(null,
                "desc", Set.of("a"), DriveAxis.CURIOSITY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void collectiveGoalConfig_defaults() {
        var config = CollectiveGoalConfig.defaults();
        assertThat(config.alignmentThreshold()).isEqualTo(0.6);
        assertThat(config.minAlignedAgents()).isEqualTo(2);
        assertThat(config.cooldown()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void collectiveGoalConfig_rejectsMinAgentsBelow2() {
        assertThatThrownBy(() -> new CollectiveGoalConfig(0.6, 1, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minAlignedAgents");
    }

    @Test
    void collectiveGoalConfig_rejectsThresholdOutOfRange() {
        assertThatThrownBy(() -> new CollectiveGoalConfig(1.5, 2, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normDetectionTick_noChange() {
        var tick = new NormDetectionTick.NoChange("no observations");
        assertThat(tick.reason()).isEqualTo("no observations");
    }

    @Test
    void normDetectionTick_updated() {
        var prev = new DetectedNorms(List.of(), 0, now);
        var norm = new SocialNorm("n1", "d", "p", 0.8, 15, Set.of("a"), now, now, NormStrength.ESTABLISHED);
        var curr = new DetectedNorms(List.of(norm), 15, now);
        var tick = new NormDetectionTick.Updated(prev, curr, List.of("n1"), List.of());
        assertThat(tick.newNormIds()).containsExactly("n1");
        assertThat(tick.declinedNormIds()).isEmpty();
    }

    @Test
    void collectiveGoalTick_noChange() {
        var tick = new CollectiveGoalTick.NoChange("no alignment");
        assertThat(tick.reason()).isEqualTo("no alignment");
    }

    @Test
    void collectiveGoalTick_proposed() {
        var alignment = new DriveAlignment(Set.of("a", "b"),
                Map.of(DriveAxis.CURIOSITY, 0.9), 0.9, DriveAxis.CURIOSITY, now);
        var proposal = new CollectiveGoalProposal(alignment, "explore",
                Set.of("a", "b"), DriveAxis.CURIOSITY);
        var tick = new CollectiveGoalTick.Proposed(List.of(proposal), List.of(alignment));
        assertThat(tick.proposals()).hasSize(1);
        assertThat(tick.alignments()).hasSize(1);
    }

    @Test
    void normStrength_values() {
        assertThat(NormStrength.values()).containsExactly(
                NormStrength.EMERGING, NormStrength.ESTABLISHED, NormStrength.DECLINING);
    }
}
