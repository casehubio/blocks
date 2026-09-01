package io.casehub.blocks.agentic.social.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.blocks.agentic.social.EngagementTrend;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveSource;
import io.casehub.blocks.agentic.social.drive.CuriosityDrive;
import io.casehub.blocks.agentic.social.drive.AffiliationDrive;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.memory.KnowledgeGapSummary;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.GoalSignalStore;
import jakarta.enterprise.inject.Instance;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GoalProposalIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void fullTickCycle_driveToGoalProposal() {
        var hygieneOrchestrator = mock(MemoryHygieneOrchestrator.class);
        var strategyOrchestrator = mock(StrategyLearningOrchestrator.class);
        var userModelOrchestrator = mock(UserModelOrchestrator.class);
        var mentalModelOrchestrator = mock(MentalModelOrchestrator.class);
        var moodOrchestrator = mock(MoodOrchestrator.class);

        when(hygieneOrchestrator.knowledgeGaps("a1", "t1"))
                .thenReturn(new KnowledgeGapSummary(5, 3, 10));
        when(strategyOrchestrator.engagementTrend("a1", "t1"))
                .thenReturn(Optional.empty());
        when(userModelOrchestrator.activeProfiles("a1", "t1"))
                .thenReturn(List.of(
                        new UserProfile("a1", "user-bob", "t1", "acquaintance", 0.1,
                                10, 5, 1, 4, Instant.now().minus(Duration.ofDays(30)),
                                Instant.now(), null, null, null, null, null, Map.of())));
        when(mentalModelOrchestrator.activeSnapshots("a1", "t1"))
                .thenReturn(List.of());
        when(moodOrchestrator.currentMood("a1", "t1")).thenReturn(Optional.empty());

        var curiosityDrive = new CuriosityDrive(hygieneOrchestrator);
        var affiliationDrive = new AffiliationDrive(userModelOrchestrator, 0.3, Duration.ofDays(7));
        DriveSource competence = (agentId, tenantId) ->
                new io.casehub.blocks.agentic.social.drive.DriveIntensity(
                        DriveAxis.COMPETENCE, 0.0, "no data");
        DriveSource autonomy = (agentId, tenantId) ->
                new io.casehub.blocks.agentic.social.drive.DriveIntensity(
                        DriveAxis.AUTONOMY, 0.0, "no data");

        var composer = new DriveComposer();
        var driveOrchestrator = new DriveOrchestrator(
                curiosityDrive, competence, affiliationDrive, autonomy,
                moodOrchestrator, composer, DriveConfig.defaults());

        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of()).build();

        var eidos = mock(io.casehub.eidos.api.AgentDescriptor.class);

        driveOrchestrator.tick("a1", "t1", descriptor);

        var curiosityMapper = new CuriosityGoalMapper(curiosityDrive);
        var affiliationMapper = new AffiliationGoalMapper(
                affiliationDrive, 0.3, Duration.ofDays(7));

        Instance<GoalSignalStore> signalStoreInstance = mock(Instance.class);
        when(signalStoreInstance.isResolvable()).thenReturn(false);

        var clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneId.of("UTC"));
        var goalOrchestrator = new GoalProposalOrchestrator(
                driveOrchestrator,
                List.of(curiosityMapper, affiliationMapper),
                signalStoreInstance,
                GoalProposalConfig.defaults(),
                clock);

        var tick = goalOrchestrator.tick("a1", "t1", descriptor);

        assertThat(tick).isInstanceOf(GoalProposalTick.Changes.class);
        var proposed = (GoalProposalTick.Changes) tick;
        assertThat(proposed.newProposals()).isNotEmpty();

        var goalNames = proposed.newProposals().stream()
                .map(DriveGoalProposal::goalName).toList();
        assertThat(goalNames).contains("explore-knowledge-gaps");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullTickCycle_noProposalsWhenDrivesWeak() {
        var hygieneOrchestrator = mock(MemoryHygieneOrchestrator.class);
        var strategyOrchestrator = mock(StrategyLearningOrchestrator.class);
        var userModelOrchestrator = mock(UserModelOrchestrator.class);
        var mentalModelOrchestrator = mock(MentalModelOrchestrator.class);
        var moodOrchestrator = mock(MoodOrchestrator.class);

        when(hygieneOrchestrator.knowledgeGaps("a1", "t1"))
                .thenReturn(KnowledgeGapSummary.empty());
        when(strategyOrchestrator.engagementTrend("a1", "t1"))
                .thenReturn(Optional.empty());
        when(userModelOrchestrator.activeProfiles("a1", "t1"))
                .thenReturn(List.of());
        when(mentalModelOrchestrator.activeSnapshots("a1", "t1"))
                .thenReturn(List.of());
        when(moodOrchestrator.currentMood("a1", "t1")).thenReturn(Optional.empty());

        var curiosityDrive = new CuriosityDrive(hygieneOrchestrator);
        DriveSource competence = (agentId, tenantId) ->
                new io.casehub.blocks.agentic.social.drive.DriveIntensity(
                        DriveAxis.COMPETENCE, 0.0, "no data");
        var affiliationDrive = new AffiliationDrive(userModelOrchestrator, 0.3, Duration.ofDays(7));
        DriveSource autonomy = (agentId, tenantId) ->
                new io.casehub.blocks.agentic.social.drive.DriveIntensity(
                        DriveAxis.AUTONOMY, 0.0, "no data");

        var composer = new DriveComposer();
        var driveOrchestrator = new DriveOrchestrator(
                curiosityDrive, competence, affiliationDrive, autonomy,
                moodOrchestrator, composer, DriveConfig.defaults());

        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of()).build();

        driveOrchestrator.tick("a1", "t1", descriptor);

        Instance<GoalSignalStore> signalStoreInstance = mock(Instance.class);
        when(signalStoreInstance.isResolvable()).thenReturn(false);

        var clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneId.of("UTC"));
        var goalOrchestrator = new GoalProposalOrchestrator(
                driveOrchestrator, List.of(), signalStoreInstance,
                GoalProposalConfig.defaults(), clock);

        var tick = goalOrchestrator.tick("a1", "t1", descriptor);

        assertThat(tick).isInstanceOf(GoalProposalTick.NoChange.class);
    }
}
