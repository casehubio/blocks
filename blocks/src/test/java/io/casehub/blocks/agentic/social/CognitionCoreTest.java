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
        core.tick("agent1", "tenant1", null, Set.of());

        assertThat(core.mood().currentMood("agent1", "tenant1")).isPresent();
    }

    @Test
    void tickWithDescriptorPopulatesDrives() {
        var core = minimalCore();
        var descriptor = stubDescriptor();
        core.tick("agent1", "tenant1", descriptor, Set.of());

        assertThat(core.mood().currentMood("agent1", "tenant1")).isPresent();
        assertThat(core.drives().currentDrives("agent1", "tenant1")).isPresent();
    }

    @Test
    void recordInteractionSurvivesNullOrchestrators() {
        var core = minimalCore();
        core.recordInteraction("a", "t", "subject", "hello", "hi there");
        core.recordInteraction("a", "t", null, "hello", "hi there");
    }

    @Test
    void promptSectionsSkipsNullOrchestrators() {
        var core = minimalCore();
        var sections = core.promptSections();
        assertThat(sections).hasSize(2);
    }

    @Test
    void tickWithSubjectsSkipsNullOrchestrators() {
        var core = minimalCore();
        core.tick("agent1", "tenant1", null, Set.of("other-agent"));
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
