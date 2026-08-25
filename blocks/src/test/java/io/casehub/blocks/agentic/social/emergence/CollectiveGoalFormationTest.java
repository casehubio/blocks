package io.casehub.blocks.agentic.social.emergence;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectiveGoalFormationTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String TENANT = "tenant-1";

    private DriveOrchestrator driveOrchestrator;
    private MutableClock clock;
    private CollectiveGoalFormation formation;

    @BeforeEach
    void setUp() {
        driveOrchestrator = mock(DriveOrchestrator.class);
        clock = new MutableClock(NOW);
        formation = new CollectiveGoalFormation(
                driveOrchestrator, List.of("agent-a", "agent-b"),
                CollectiveGoalConfig.defaults(), clock);
    }

    @Test
    void tick_noProfiles_returnsNoChange() {
        when(driveOrchestrator.currentDrives("agent-a", TENANT)).thenReturn(Optional.empty());
        when(driveOrchestrator.currentDrives("agent-b", TENANT)).thenReturn(Optional.empty());

        var tick = formation.tick(TENANT);
        assertThat(tick).isInstanceOf(CollectiveGoalTick.NoChange.class);
    }

    @Test
    void tick_singleProfile_insufficientForAlignment() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        when(driveOrchestrator.currentDrives("agent-b", TENANT)).thenReturn(Optional.empty());

        var tick = formation.tick(TENANT);
        assertThat(tick).isInstanceOf(CollectiveGoalTick.NoChange.class);
    }

    @Test
    void tick_lowAlignment_returnsNoChange() {
        stubProfile("agent-a", TENANT, 0.9, 0.1, 0.2, 0.8);
        stubProfile("agent-b", TENANT, 0.1, 0.9, 0.8, 0.2);

        var tick = formation.tick(TENANT);
        assertThat(tick).isInstanceOf(CollectiveGoalTick.NoChange.class);
    }

    @Test
    void tick_highAlignment_proposesCollectiveGoal() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);

        var tick = formation.tick(TENANT);
        assertThat(tick).isInstanceOf(CollectiveGoalTick.Proposed.class);
        var proposed = (CollectiveGoalTick.Proposed) tick;
        assertThat(proposed.proposals()).hasSize(1);
    }

    @Test
    void tick_proposalContainsAllAlignedParticipants() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        var proposal = proposed.proposals().getFirst();
        assertThat(proposal.proposedParticipants()).containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void tick_proposalPrimaryAxisReflectsDominantSharedDrive() {
        stubProfile("agent-a", TENANT, 0.9, 0.1, 0.1, 0.1);
        stubProfile("agent-b", TENANT, 0.8, 0.2, 0.2, 0.2);

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        assertThat(proposed.proposals().getFirst().primaryAxis()).isEqualTo(DriveAxis.CURIOSITY);
    }

    @Test
    void tick_alignmentPerAxisComputedCorrectly() {
        stubProfile("agent-a", TENANT, 0.8, 0.4, 0.6, 0.2);
        stubProfile("agent-b", TENANT, 0.6, 0.4, 0.4, 0.4);
        // alignment: C=1-|0.8-0.6|=0.8, CM=1-|0.4-0.4|=1.0, AF=1-|0.6-0.4|=0.8, AU=1-|0.2-0.4|=0.8

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        var alignment = proposed.proposals().getFirst().alignment();
        assertThat(alignment.alignmentPerAxis().get(DriveAxis.CURIOSITY)).isCloseTo(0.8, offset(0.01));
        assertThat(alignment.alignmentPerAxis().get(DriveAxis.COMPETENCE)).isCloseTo(1.0, offset(0.01));
        assertThat(alignment.alignmentPerAxis().get(DriveAxis.AFFILIATION)).isCloseTo(0.8, offset(0.01));
        assertThat(alignment.alignmentPerAxis().get(DriveAxis.AUTONOMY)).isCloseTo(0.8, offset(0.01));
    }

    @Test
    void tick_compositeAlignmentIsAverageOfAxes() {
        stubProfile("agent-a", TENANT, 0.8, 0.4, 0.6, 0.2);
        stubProfile("agent-b", TENANT, 0.6, 0.4, 0.4, 0.4);
        // composite = (0.8 + 1.0 + 0.8 + 0.8) / 4 = 0.85

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        assertThat(proposed.proposals().getFirst().alignment().compositeAlignment())
                .isCloseTo(0.85, offset(0.01));
    }

    @Test
    void tick_cooldownPreventsRepeatedProposal() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);

        formation.tick(TENANT);

        clock.advance(Duration.ofMinutes(30));
        var tick2 = formation.tick(TENANT);
        assertThat(tick2).isInstanceOf(CollectiveGoalTick.NoChange.class);
    }

    @Test
    void tick_afterCooldownExpires_proposesAgain() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);

        formation.tick(TENANT);

        clock.advance(Duration.ofMinutes(61));
        var tick2 = formation.tick(TENANT);
        assertThat(tick2).isInstanceOf(CollectiveGoalTick.Proposed.class);
    }

    @Test
    void currentProposals_beforeTick_returnsEmpty() {
        assertThat(formation.currentProposals(TENANT)).isEmpty();
    }

    @Test
    void currentProposals_afterTick_returnsCachedState() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);
        formation.tick(TENANT);

        var proposals = formation.currentProposals(TENANT);
        assertThat(proposals).isPresent();
        assertThat(proposals.get()).hasSize(1);
    }

    @Test
    void tick_perTenantIsolation() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);

        when(driveOrchestrator.currentDrives("agent-a", "tenant-2")).thenReturn(Optional.empty());
        when(driveOrchestrator.currentDrives("agent-b", "tenant-2")).thenReturn(Optional.empty());

        formation.tick(TENANT);
        formation.tick("tenant-2");

        assertThat(formation.currentProposals(TENANT).get()).hasSize(1);
        assertThat(formation.currentProposals("tenant-2")).isEmpty();
    }

    @Test
    void tick_threeAgentsAllAligned_singleGroupProposal() {
        formation = new CollectiveGoalFormation(
                driveOrchestrator, List.of("agent-a", "agent-b", "agent-c"),
                CollectiveGoalConfig.defaults(), clock);

        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);
        stubProfile("agent-c", TENANT, 0.75, 0.35, 0.55, 0.35);

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        assertThat(proposed.proposals()).hasSize(1);
        assertThat(proposed.proposals().getFirst().proposedParticipants())
                .containsExactlyInAnyOrder("agent-a", "agent-b", "agent-c");
    }

    @Test
    void tick_threeAgentsPartiallyAligned_onlyAlignedPairProposed() {
        formation = new CollectiveGoalFormation(
                driveOrchestrator, List.of("agent-a", "agent-b", "agent-c"),
                CollectiveGoalConfig.defaults(), clock);

        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);
        stubProfile("agent-c", TENANT, 0.1, 0.9, 0.1, 0.9);

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        assertThat(proposed.proposals()).hasSize(1);
        assertThat(proposed.proposals().getFirst().proposedParticipants())
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void tick_proposedAlignmentsIncludeQualifyingPairs() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        assertThat(proposed.alignments()).hasSize(1);
        assertThat(proposed.alignments().getFirst().agentIds())
                .containsExactlyInAnyOrder("agent-a", "agent-b");
    }

    @Test
    void tick_goalDescriptionReflectsPrimaryAxis() {
        stubProfile("agent-a", TENANT, 0.9, 0.1, 0.1, 0.1);
        stubProfile("agent-b", TENANT, 0.8, 0.2, 0.2, 0.2);

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        assertThat(proposed.proposals().getFirst().goalDescription()).contains("exploration");
    }

    @Test
    void toJointIntention_bridgesProposalCorrectly() {
        stubProfile("agent-a", TENANT, 0.8, 0.3, 0.6, 0.4);
        stubProfile("agent-b", TENANT, 0.7, 0.4, 0.5, 0.3);

        var proposed = (CollectiveGoalTick.Proposed) formation.tick(TENANT);
        var proposal = proposed.proposals().getFirst();

        var intention = CollectiveGoalFormation.toJointIntention(proposal, NOW);
        assertThat(intention.planDescription()).isEqualTo(proposal.goalDescription());
        assertThat(intention.committedParties()).isEqualTo(proposal.proposedParticipants());
        assertThat(intention.formedAt()).isEqualTo(NOW);
        assertThat(intention.status()).isEqualTo(io.casehub.blocks.agentic.intention.IntentionStatus.FORMED);
        assertThat(intention.intentionId()).startsWith("collective-");
    }


    @Test
    void tick_noChangeOnSecondTickWithSameState() {
        stubProfile("agent-a", TENANT, 0.9, 0.1, 0.2, 0.8);
        stubProfile("agent-b", TENANT, 0.1, 0.9, 0.8, 0.2);

        formation.tick(TENANT);
        var tick2 = formation.tick(TENANT);
        assertThat(tick2).isInstanceOf(CollectiveGoalTick.NoChange.class);
    }

    // --- helpers ---

    private void stubProfile(String agentId, String tenantId,
                              double curiosity, double competence,
                              double affiliation, double autonomy) {
        var drives = new EnumMap<DriveAxis, DriveIntensity>(DriveAxis.class);
        drives.put(DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, curiosity, "test"));
        drives.put(DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, competence, "test"));
        drives.put(DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, affiliation, "test"));
        drives.put(DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, autonomy, "test"));

        double max = Math.max(Math.max(curiosity, competence), Math.max(affiliation, autonomy));
        DriveAxis dominant = curiosity >= max ? DriveAxis.CURIOSITY
                : competence >= max ? DriveAxis.COMPETENCE
                : affiliation >= max ? DriveAxis.AFFILIATION
                : DriveAxis.AUTONOMY;

        var profile = new DriveProfile(agentId, tenantId, drives,
                (curiosity + competence + affiliation + autonomy) / 4.0,
                dominant, NOW);
        when(driveOrchestrator.currentDrives(agentId, tenantId)).thenReturn(Optional.of(profile));
    }

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
