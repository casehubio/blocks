package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveSource;
import io.casehub.eidos.api.AgentDescriptor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CognitionCoreTest {

    @Test
    void tickPopulatesMood() {
        var core = minimalCore();
        core.tick("agent1", "tenant1", null, (aid, tid) -> Set.of());

        assertThat(core.mood().currentMood("agent1", "tenant1")).isPresent();
    }

    @Test
    void tickWithDescriptorPopulatesDrives() {
        var core = minimalCore();
        var descriptor = stubDescriptor();
        core.tick("agent1", "tenant1", descriptor, (aid, tid) -> Set.of());

        assertThat(core.mood().currentMood("agent1", "tenant1")).isPresent();
        assertThat(core.drives().currentDrives("agent1", "tenant1")).isPresent();
    }

    @Test
    void recordInteractionSurvivesNullOrchestrators() {
        var core = minimalCore();
        core.recordInteraction("a", "t", "subject", "hello", "hi there", null);
        core.recordInteraction("a", "t", null, "hello", "hi there", null);
    }

    @Test
    void recordInteractionWithMoodSignalUsesProvidedSignal() {
        var core = minimalCore();
        core.tick("a", "t", null, (aid, tid) -> Set.of());

        var moodShift = new MoodSignal.DirectShift(0.2, 0.1, 0.05, "gift received");
        var impact = new CognitiveImpact(null, moodShift, false, null, null);
        core.recordInteraction("a", "t", null, "gave a gift", "thank you", impact);

        core.tick("a", "t", null, (aid, tid) -> Set.of());
        var moodAfter = core.mood().currentMood("a", "t").orElseThrow();
        assertThat(moodAfter.pleasure()).isGreaterThan(0.0);
    }

    @Test
    void recordInteractionWithNullImpactPreservesDefaultBehaviour() {
        var core = minimalCore();
        core.tick("a", "t", null, (aid, tid) -> Set.of());

        core.recordInteraction("a", "t", "subject", "hello", "hi", null);
        assertThat(core.mood().currentMood("a", "t")).isPresent();
    }

    @Test
    void recordInteractionWithUserModelSignalUsesProvidedSignal() {
        var core = minimalCore();
        core.tick("a", "t", null, (aid, tid) -> Set.of());

        var signal = new InteractionSignal.CustomSignal("stole item",
                io.casehub.neocortex.memory.relationship.QualitySignal.NEGATIVE);
        var impact = new CognitiveImpact(signal, null, true, null, null);
        core.recordInteraction("a", "t", "subject", "stole", "hey!", impact);
    }

    @Test
    void recordInteractionUsesConversationIdFromImpactAsCaseId() {
        var strategy = mock(StrategyLearningOrchestrator.class);
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        DriveSource baseline = (a, t) ->
                new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var core = new CognitionCore(mood, drives, null, null,
                strategy, null, null, null, null);

        var impact = CognitiveImpact.withConversationId("conv-abc");
        core.recordInteraction("agent", "tenant", "subject",
                "hello", "world", impact);

        var captor = ArgumentCaptor.forClass(EngagementSignal.class);
        verify(strategy).record(captor.capture(), eq("agent"), eq("subject"), eq("tenant"));
        var signal = (EngagementSignal.TurnOutcome) captor.getValue();
        assertThat(signal.event().caseId()).isEqualTo("conv-abc");
    }

    @Test
    void recordInteractionWithNullConversationIdUsesNullCaseId() {
        var strategy = mock(StrategyLearningOrchestrator.class);
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        DriveSource baseline = (a, t) ->
                new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var core = new CognitionCore(mood, drives, null, null,
                strategy, null, null, null, null);

        core.recordInteraction("agent", "tenant", "subject",
                "hello", "world", null);

        var captor = ArgumentCaptor.forClass(EngagementSignal.class);
        verify(strategy).record(captor.capture(), eq("agent"), eq("subject"), eq("tenant"));
        var signal = (EngagementSignal.TurnOutcome) captor.getValue();
        assertThat(signal.event().caseId()).isNull();
    }

    @Test
    void recordInteractionDerivesPositiveQualityFromHighArousalPositivePleasure() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        mood.record(new MoodSignal.InteractionAppraisal(0.2, 0.4, 0.1, "engaged"),
                "a", "t");
        mood.tick("a", "t");

        var userModel = mock(UserModelOrchestrator.class);
        DriveSource baseline = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "b");
        var drives = new DriveOrchestrator(baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var core = new CognitionCore(mood, drives, userModel, null,
                null, null, null, null, null);

        core.recordInteraction("a", "t", "subject", "hello", "world", null);

        var captor = ArgumentCaptor.forClass(InteractionSignal.class);
        verify(userModel).record(captor.capture(), eq("a"), eq("subject"), eq("t"));
        assertThat(((InteractionSignal.CustomSignal) captor.getValue()).quality())
                .isEqualTo(io.casehub.neocortex.memory.relationship.QualitySignal.POSITIVE);
    }

    @Test
    void recordInteractionDerivesNegativeQualityFromLowArousalNegativePleasure() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        mood.record(new MoodSignal.InteractionAppraisal(-0.2, -0.2, -0.1, "bored"),
                "a", "t");
        mood.tick("a", "t");

        var userModel = mock(UserModelOrchestrator.class);
        DriveSource baseline = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "b");
        var drives = new DriveOrchestrator(baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var core = new CognitionCore(mood, drives, userModel, null,
                null, null, null, null, null);

        core.recordInteraction("a", "t", "subject", "hello", "world", null);

        var captor = ArgumentCaptor.forClass(InteractionSignal.class);
        verify(userModel).record(captor.capture(), eq("a"), eq("subject"), eq("t"));
        assertThat(((InteractionSignal.CustomSignal) captor.getValue()).quality())
                .isEqualTo(io.casehub.neocortex.memory.relationship.QualitySignal.NEGATIVE);
    }

    @Test
    void recordInteractionDerivesNeutralForHighArousalNegativePleasure() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        mood.record(new MoodSignal.InteractionAppraisal(-0.15, 0.4, 0.0, "debate"),
                "a", "t");
        mood.tick("a", "t");

        var userModel = mock(UserModelOrchestrator.class);
        DriveSource baseline = (a, t) -> new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "b");
        var drives = new DriveOrchestrator(baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var core = new CognitionCore(mood, drives, userModel, null,
                null, null, null, null, null);

        core.recordInteraction("a", "t", "subject", "hello", "world", null);

        var captor = ArgumentCaptor.forClass(InteractionSignal.class);
        verify(userModel).record(captor.capture(), eq("a"), eq("subject"), eq("t"));
        assertThat(((InteractionSignal.CustomSignal) captor.getValue()).quality())
                .isEqualTo(io.casehub.neocortex.memory.relationship.QualitySignal.NEUTRAL);
    }

    @Test
    void promptSectionsSkipsNullOrchestrators() {
        var core = minimalCore();
        var sections = core.promptSections();
        assertThat(sections).hasSize(2);
    }

    @Test
    void promptSectionsIncludesPersonalityAfterTick() {
        var core = minimalCore();
        var descriptor = stubDescriptor();
        var disposition = mock(io.casehub.eidos.api.AgentDisposition.class);
        when(disposition.dispositionProfile()).thenReturn(
                List.of(new io.casehub.eidos.api.DispositionValue("independent", 0.8)));
        when(descriptor.disposition()).thenReturn(disposition);
        core.tick("a", "t", descriptor, (aid, tid) -> Set.of());
        var sections = core.promptSections();
        assertThat(sections).hasSize(3);
        var personalitySection = sections.get(0);
        var text = personalitySection.contribute(
                new io.casehub.blocks.speech.PromptContext("a", "t", null));
        assertThat(text).contains("independent");
    }

    @Test
    void tickWithSubjectsSkipsNullOrchestrators() {
        var core = minimalCore();
        core.tick("agent1", "tenant1", null, (aid, tid) -> Set.of("other-agent"));
    }

    @Test
    void promptSectionsExcludesHardConstraints() {
        var core = minimalCore();
        var descriptor = stubDescriptor();
        when(descriptor.constraints()).thenReturn(
                List.of(new io.casehub.eidos.api.AgentConstraint(
                        "observation-first", "Always ground understanding in direct observation",
                        io.casehub.eidos.api.Visibility.PUBLIC,
                        io.casehub.eidos.api.ConstraintSeverity.HARD)));
        core.tick("a", "t", descriptor, (aid, tid) -> Set.of());
        var sections = core.promptSections();
        var constraintSection = sections.stream()
                .filter(s -> {
                    var text = s.contribute(new io.casehub.blocks.speech.PromptContext("a", "t", null));
                    return text != null && text.contains("constraints");
                })
                .findFirst();
        assertThat(constraintSection).isEmpty();
    }

    @Test
    void promptSectionsIncludesSoftConstraints() {
        var core = minimalCore();
        var descriptor = stubDescriptor();
        when(descriptor.constraints()).thenReturn(
                List.of(
                        new io.casehub.eidos.api.AgentConstraint(
                                "no-break", "Never break cover",
                                io.casehub.eidos.api.Visibility.PUBLIC,
                                io.casehub.eidos.api.ConstraintSeverity.HARD),
                        new io.casehub.eidos.api.AgentConstraint(
                                "be-polite", "Always be polite",
                                io.casehub.eidos.api.Visibility.PUBLIC,
                                io.casehub.eidos.api.ConstraintSeverity.SOFT)));
        core.tick("a", "t", descriptor, (aid, tid) -> Set.of());
        var sections = core.promptSections();
        var constraintSection = sections.stream()
                .filter(s -> {
                    var text = s.contribute(new io.casehub.blocks.speech.PromptContext("a", "t", null));
                    return text != null && text.contains("Always be polite");
                })
                .findFirst();
        assertThat(constraintSection).isPresent();
        var text = constraintSection.get().contribute(new io.casehub.blocks.speech.PromptContext("a", "t", null));
        assertThat(text).doesNotContain("Never break cover");
    }


    @Test
    void tickExecutesInPhaseOrder() {
        var mood = mock(MoodOrchestrator.class);
        when(mood.currentMood(any(), any())).thenReturn(java.util.Optional.empty());
        var drives        = mock(DriveOrchestrator.class);
        var strategy      = mock(StrategyLearningOrchestrator.class);
        var userModel     = mock(UserModelOrchestrator.class);
        var mentalModel   = mock(MentalModelOrchestrator.class);
        var narrative     = mock(io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator.class);
        var goals         = mock(io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator.class);
        var memoryHygiene = mock(io.casehub.blocks.memory.MemoryHygieneOrchestrator.class);

        var core = new CognitionCore(mood, drives, userModel, mentalModel,
                                     strategy, narrative, goals, memoryHygiene);

        var descriptor = stubDescriptor();
        core.tick("a1", "t1", descriptor, (a, t) -> Set.of("subject-1"));

        var inOrder = org.mockito.Mockito.inOrder(mood, memoryHygiene, narrative, strategy,
                                                  userModel, mentalModel, drives, goals);
        inOrder.verify(mood).tick("a1", "t1");
        inOrder.verify(memoryHygiene).tick("a1", "t1");
        inOrder.verify(narrative).tick("a1", "t1");
        inOrder.verify(strategy).tick("a1", "t1");
        inOrder.verify(userModel).tick("a1", "subject-1", "t1");
        inOrder.verify(mentalModel).tick("a1", "subject-1", "t1");
        inOrder.verify(drives).tick("a1", "t1", descriptor);
        inOrder.verify(goals).tick("a1", "t1", descriptor);
    }

    @Test
    void customParticipantExecutesAtRegisteredPhase() {
        var core     = minimalCore();
        var executed = new java.util.concurrent.atomic.AtomicBoolean(false);
        core.addParticipant(CognitionPhase.TERMINAL, ctx -> executed.set(true));
        core.tick("a1", "t1", stubDescriptor(), (a, t) -> Set.of());
        assertThat(executed.get()).isTrue();
    }

    @Test
    void addParticipantRejectsSourcePerSubject() {
        var core = minimalCore();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                                                   core.addParticipant(CognitionPhase.SOURCE_PER_SUBJECT, ctx -> {}))
                                       .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customParticipantErrorDoesNotCrashTick() {
        var core = minimalCore();
        core.addParticipant(CognitionPhase.FOUNDATION, ctx -> {
            throw new RuntimeException("boom");
        });
        var secondExecuted = new java.util.concurrent.atomic.AtomicBoolean(false);
        core.addParticipant(CognitionPhase.TERMINAL, ctx -> secondExecuted.set(true));
        core.tick("a1", "t1", stubDescriptor(), (a, t) -> Set.of());
        assertThat(secondExecuted.get()).isTrue();
    }

    @Test
    void disabledBuiltinDoesNotPreventCustomParticipant() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        DriveSource baseline = (a, t) ->
                                       new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        var config = CognitionConfig.all().with("drives", false);
        var core = new CognitionCore(mood, drives,
                                     null, null, null, null, null, null, null, null, config);
        var executed = new java.util.concurrent.atomic.AtomicBoolean(false);
        core.addParticipant(CognitionPhase.DERIVED, ctx -> executed.set(true));
        core.tick("a1", "t1", stubDescriptor(), (a, t) -> Set.of());
        assertThat(executed.get()).isTrue();
    }


    private static CognitionCore minimalCore() {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        DriveSource baseline = (a, t) ->
                new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, new DriveComposer(), DriveConfig.defaults());
        return new CognitionCore(mood, drives,
                null, null, null, null, null, null);
    }

    private static AgentDescriptor stubDescriptor() {
        var descriptor = mock(AgentDescriptor.class);
        when(descriptor.name()).thenReturn("agent1");
        when(descriptor.briefing()).thenReturn("Test agent");
        when(descriptor.capabilities()).thenReturn(List.of());
        return descriptor;
    }
}
