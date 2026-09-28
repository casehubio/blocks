package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveSource;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CognitionSnapshotTest {

    @Test
    void emptySnapshotHasNullOptionalFields() {
        var core = minimalCore();
        var snap = CognitionSnapshot.capture(core, "agent", "tenant",
                0, Set.of());

        assertThat(snap.agentId()).isEqualTo("agent");
        assertThat(snap.tenantId()).isEqualTo("tenant");
        assertThat(snap.turnNumber()).isEqualTo(0);
        assertThat(snap.capturedAt()).isNotNull();
        assertThat(snap.mood()).isNull();
        assertThat(snap.drives()).isNull();
        assertThat(snap.mentalModels()).isEmpty();
        assertThat(snap.userProfiles()).isEmpty();
        assertThat(snap.strategy()).isNull();
        assertThat(snap.narrative()).isNull();
        assertThat(snap.goalProposals()).isEmpty();
        assertThat(snap.lastBriefing()).isNull();
    }

    @Test
    void snapshotAfterTickCapturesMoodAndDrives() {
        var core = minimalCore();
        var descriptor = stubDescriptor();
        core.tick("agent", "tenant", descriptor, (aid, tid) -> Set.of());

        var snap = CognitionSnapshot.capture(core, "agent", "tenant",
                1, Set.of());

        assertThat(snap.mood()).isNotNull();
        assertThat(snap.drives()).isNotNull();
    }

    @Test
    void diffFromNullPreviousReturnsAbsoluteValues() {
        var core = minimalCore();
        var descriptor = stubDescriptor();
        core.tick("agent", "tenant", descriptor, (aid, tid) -> Set.of());

        var snap = CognitionSnapshot.capture(core, "agent", "tenant",
                1, Set.of());
        var delta = snap.diffFrom(null);

        assertThat(delta).isNotNull();
        assertThat(delta.mood()).isNotNull();
        assertThat(delta.drives()).isNotNull();
        assertThat(delta.mentalModelDeltas()).isEmpty();
        assertThat(delta.newGoals()).isEmpty();
    }

    @Test
    void diffBetweenTwoSnapshotsComputesDeltas() {
        var core = minimalCore();
        var descriptor = stubDescriptor();

        var before = CognitionSnapshot.capture(core, "agent", "tenant",
                0, Set.of());
        core.tick("agent", "tenant", descriptor, (aid, tid) -> Set.of());
        var after = CognitionSnapshot.capture(core, "agent", "tenant",
                1, Set.of());

        var delta = after.diffFrom(before);

        assertThat(delta).isNotNull();
        assertThat(delta.mood()).isNotNull();
    }

    @Test
    void metricsFromSnapshotDiff() {
        var core = minimalCore();
        var descriptor = stubDescriptor();

        var before = CognitionSnapshot.capture(core, "agent", "tenant",
                0, Set.of());
        core.tick("agent", "tenant", descriptor, (aid, tid) -> Set.of());
        var after = CognitionSnapshot.capture(core, "agent", "tenant",
                1, Set.of());
        var delta = after.diffFrom(before);

        var metrics = new CognitionMetrics(1, "agent", 0,
                java.util.Map.of(), delta, after);

        assertThat(metrics.summary()).contains("[sections]");
        assertThat(metrics.summary()).contains("[mood]");
        assertThat(metrics.toMarkdownRow()).startsWith("| 1 | agent |");
    }

    @Test
    void diffReportsAttentionAppeared() {
        var before = new CognitionSnapshot("agent", "tenant", 0,
                                           Instant.now(), null, null, java.util.Map.of(), java.util.Map.of(),
                                           null, null, List.of(), null);

        var signals = List.of(
                new AttentionSignal("agent", "tenant", SignalCategory.URGENCY_SPIKE,
                                    "node-1", "test-node", 0.9, "test signal"));
        var briefing = new AttentionBriefing("agent", "tenant",
                                             signals, 0.9, Instant.now());

        var after = new CognitionSnapshot("agent", "tenant", 1,
                                          Instant.now(), null, null, java.util.Map.of(), java.util.Map.of(),
                                          null, null, List.of(), briefing);

        var delta = after.diffFrom(before);

        assertThat(delta.attention()).isNotNull();
        assertThat(delta.attention().briefingAppeared()).isTrue();
        assertThat(delta.attention().briefingDisappeared()).isFalse();
        assertThat(delta.attention().signalCountDelta()).isEqualTo(1);
    }

    @Test
    void diffReportsAttentionDisappeared() {
        var signals = List.of(
                new AttentionSignal("agent", "tenant", SignalCategory.DECAY_DETECTED,
                                    "node-1", "test-node", 0.8, "decay detected"));
        var briefing = new AttentionBriefing("agent", "tenant",
                                             signals, 0.8, Instant.now());

        var before = new CognitionSnapshot("agent", "tenant", 0,
                                           Instant.now(), null, null, java.util.Map.of(), java.util.Map.of(),
                                           null, null, List.of(), briefing);
        var after = new CognitionSnapshot("agent", "tenant", 1,
                                          Instant.now(), null, null, java.util.Map.of(), java.util.Map.of(),
                                          null, null, List.of(), null);

        var delta = after.diffFrom(before);

        assertThat(delta.attention()).isNotNull();
        assertThat(delta.attention().briefingAppeared()).isFalse();
        assertThat(delta.attention().briefingDisappeared()).isTrue();
        assertThat(delta.attention().signalCountDelta()).isEqualTo(-1);
    }

    @Test
    void diffReportsNullAttentionWhenBothNull() {
        var before = new CognitionSnapshot("agent", "tenant", 0,
                                           Instant.now(), null, null, java.util.Map.of(), java.util.Map.of(),
                                           null, null, List.of(), null);
        var after = new CognitionSnapshot("agent", "tenant", 1,
                                          Instant.now(), null, null, java.util.Map.of(), java.util.Map.of(),
                                          null, null, List.of(), null);

        var delta = after.diffFrom(before);

        assertThat(delta.attention()).isNull();
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
        when(descriptor.name()).thenReturn("agent");
        when(descriptor.briefing()).thenReturn("Test agent");
        when(descriptor.capabilities()).thenReturn(List.of());
        return descriptor;
    }
}
