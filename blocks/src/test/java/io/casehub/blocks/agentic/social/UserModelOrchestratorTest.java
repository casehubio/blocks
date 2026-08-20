package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.relationship.QualitySignal;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserModelOrchestratorTest {

    @Mock UserProfileStore profileStore;
    @Mock AgentProvider agentProvider;
    UserModelOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new UserModelOrchestrator(profileStore, agentProvider,
                UserModelConfig.defaults());
    }

    @Test
    void tickWithNoSignalsReturnsUnchanged() {
        var tick = orchestrator.tick("agent-1", "user-1", "t1");
        assertThat(tick).isInstanceOf(UserModelTick.Unchanged.class);
    }

    @Test
    void recordThenTickProducesUpdated() {
        var signal = new InteractionSignal.CustomSignal("hello", QualitySignal.POSITIVE);
        orchestrator.record(signal, "agent-1", "user-1", "t1");

        var tick = orchestrator.tick("agent-1", "user-1", "t1");
        assertThat(tick).isInstanceOf(UserModelTick.Updated.class);

        var updated = (UserModelTick.Updated) tick;
        assertThat(updated.profile().totalInteractions()).isEqualTo(1);
        assertThat(updated.profile().positiveSignals()).isEqualTo(1);
        assertThat(updated.profile().relationshipStage()).isEqualTo("stranger");

        verify(profileStore).store(any(UserProfile.class));
    }

    @Test
    void multiplePositiveSignalsIncreaseFamiliarityAndStage() {
        for (int i = 0; i < 30; i++) {
            orchestrator.record(
                    new InteractionSignal.CustomSignal("chat " + i, QualitySignal.POSITIVE),
                    "agent-1", "user-1", "t1");
        }

        var tick = orchestrator.tick("agent-1", "user-1", "t1");
        assertThat(tick).isInstanceOf(UserModelTick.Updated.class);
        var profile = ((UserModelTick.Updated) tick).profile();
        assertThat(profile.familiarityScore()).isGreaterThan(0.5);
        assertThat(profile.relationshipStage()).isNotEqualTo("stranger");
    }

    @Test
    void currentProfileReturnsNullBeforeFirstTick() {
        assertThat(orchestrator.currentProfile("agent-1", "user-1", "t1")).isNull();
    }

    @Test
    void currentProfileReturnsCachedAfterTick() {
        orchestrator.record(
                new InteractionSignal.CustomSignal("hi", QualitySignal.POSITIVE),
                "agent-1", "user-1", "t1");
        orchestrator.tick("agent-1", "user-1", "t1");

        assertThat(orchestrator.currentProfile("agent-1", "user-1", "t1")).isNotNull();
    }

    @Test
    void tickDoesNotTriggerLlmBelowMinSignals() {
        orchestrator.record(
                new InteractionSignal.CustomSignal("hi", QualitySignal.POSITIVE),
                "agent-1", "user-1", "t1");

        orchestrator.tick("agent-1", "user-1", "t1");

        verifyNoInteractions(agentProvider);
    }

    @Test
    void negativeSignalsReduceFamiliarity() {
        for (int i = 0; i < 10; i++) {
            orchestrator.record(
                    new InteractionSignal.CustomSignal("neg " + i, QualitySignal.NEGATIVE),
                    "agent-1", "user-1", "t1");
        }

        var tick = orchestrator.tick("agent-1", "user-1", "t1");
        assertThat(tick).isInstanceOf(UserModelTick.Updated.class);
        var profile = ((UserModelTick.Updated) tick).profile();
        assertThat(profile.familiarityScore()).isLessThan(0.5);
    }

    @Test
    void isolatedSubjectStates() {
        orchestrator.record(
                new InteractionSignal.CustomSignal("hi alice", QualitySignal.POSITIVE),
                "agent-1", "alice", "t1");
        orchestrator.record(
                new InteractionSignal.CustomSignal("hi bob", QualitySignal.NEGATIVE),
                "agent-1", "bob", "t1");

        orchestrator.tick("agent-1", "alice", "t1");
        orchestrator.tick("agent-1", "bob", "t1");

        var alice = orchestrator.currentProfile("agent-1", "alice", "t1");
        var bob = orchestrator.currentProfile("agent-1", "bob", "t1");
        assertThat(alice).isNotNull();
        assertThat(bob).isNotNull();
        assertThat(alice.familiarityScore()).isGreaterThan(bob.familiarityScore());
    }

    @Test
    void llmSynthesisTriggersWhenMinSignalsReached() {
        var config = new UserModelConfig(
                3, Duration.ofMillis(0), 0.01, 1.0, 0.5,
                RelationshipStageConfig.defaults(),
                Duration.ofHours(1), Duration.ofDays(7),
                "user-model", "user-profile", 50);
        orchestrator = new UserModelOrchestrator(profileStore, agentProvider, config);

        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("{\"communicationStyle\":\"casual\","
                                + "\"topicsOfInterest\":\"gaming\","
                                + "\"preferences\":null,"
                                + "\"synthesisNotes\":null}")));

        for (int i = 0; i < 5; i++) {
            orchestrator.record(
                    new InteractionSignal.CustomSignal("event " + i, QualitySignal.POSITIVE),
                    "a", "s", "t");
        }

        var tick = orchestrator.tick("a", "s", "t");
        assertThat(tick).isInstanceOf(UserModelTick.Synthesised.class);
        var synth = (UserModelTick.Synthesised) tick;
        assertThat(synth.profile().communicationStyle()).isEqualTo("casual");
        assertThat(synth.profile().topicsOfInterest()).isEqualTo("gaming");
    }

    @Test
    void llmParseFailureRetainsPreviousFieldsAndReturnsUpdated() {
        var config = new UserModelConfig(
                1, Duration.ofMillis(0), 0.01, 1.0, 0.5,
                RelationshipStageConfig.defaults(),
                Duration.ofHours(1), Duration.ofDays(7),
                "user-model", "user-profile", 50);
        orchestrator = new UserModelOrchestrator(profileStore, agentProvider, config);

        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("not json at all")));

        orchestrator.record(
                new InteractionSignal.CustomSignal("test", QualitySignal.POSITIVE),
                "a", "s", "t");

        var tick = orchestrator.tick("a", "s", "t");
        assertThat(tick).isInstanceOf(UserModelTick.Updated.class);
    }
}
