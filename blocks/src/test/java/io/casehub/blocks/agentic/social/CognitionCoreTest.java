package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveSource;
import io.casehub.eidos.api.AgentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    void promptSectionsIncludesConstraintsAfterTick() {
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
        assertThat(constraintSection).isPresent();
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
