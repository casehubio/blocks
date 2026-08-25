package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GroupNarrativeOrchestratorTest {

    private static final Instant T1 = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-25T11:00:00Z");
    private static final String GROUP = "team-alpha";
    private static final String TENANT = "tenant-1";

    private NarrativeStore store;
    private GroupNarrativeOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        store = mock(NarrativeStore.class);
        orchestrator = new GroupNarrativeOrchestrator(store, Set.of("agent-a", "agent-b", "agent-c"));
    }

    private NarrativeState groupState(String groupId, Instant synthesisedAt,
                                       List<NarrativeFragment> fragments) {
        return new NarrativeState(groupId, TENANT,
                NarrativeScope.GROUP, fragments, synthesisedAt, 5);
    }

    private GroupEpisode groupEpisode(String id) {
        return new GroupEpisode(id, T1, null, List.of("collaboration"),
                "desc-" + id, 0.6,
                Set.of("agent-a", "agent-b"), Map.of("agent-a", "lead", "agent-b", "support"),
                0.8);
    }

    private DerivedTheme theme(String label) {
        return new DerivedTheme("t-" + label, T1, null, List.of(),
                label, 0.7, Map.of(DriveAxis.AFFILIATION, 0.4), List.of());
    }

    @Test
    void tick_firstLoad_returnsUpdated() {
        var state = groupState(GROUP, T1, List.of(groupEpisode("ge1"), theme("team-spirit")));
        when(store.load(GROUP, TENANT)).thenReturn(state);

        var tick = orchestrator.tick(GROUP, TENANT);

        assertThat(tick).isInstanceOf(NarrativeTick.Updated.class);
        var updated = (NarrativeTick.Updated) tick;
        assertThat(updated.previous()).isNull();
        assertThat(updated.current()).isSameAs(state);
        assertThat(updated.newEpisodeIds()).containsExactly("ge1");
        assertThat(updated.newThemeLabels()).containsExactly("team-spirit");
    }

    @Test
    void tick_noStateInStore_returnsNoChange() {
        when(store.load(GROUP, TENANT)).thenReturn(null);

        var tick = orchestrator.tick(GROUP, TENANT);

        assertThat(tick).isInstanceOf(NarrativeTick.NoChange.class);
        assertThat(((NarrativeTick.NoChange) tick).reason())
                .isEqualTo("no group narrative in store");
    }

    @Test
    void tick_sameTimestamp_returnsNoChange() {
        var state = groupState(GROUP, T1, List.of(groupEpisode("ge1")));
        when(store.load(GROUP, TENANT)).thenReturn(state);

        orchestrator.tick(GROUP, TENANT);
        var tick2 = orchestrator.tick(GROUP, TENANT);

        assertThat(tick2).isInstanceOf(NarrativeTick.NoChange.class);
        assertThat(((NarrativeTick.NoChange) tick2).reason())
                .isEqualTo("no new synthesis");
    }

    @Test
    void tick_newSynthesis_detectsNewGroupEpisodesAndThemes() {
        var state1 = groupState(GROUP, T1, List.of(groupEpisode("ge1"), theme("team-spirit")));
        when(store.load(GROUP, TENANT)).thenReturn(state1);
        orchestrator.tick(GROUP, TENANT);

        var state2 = groupState(GROUP, T2, List.of(
                groupEpisode("ge1"), groupEpisode("ge2"),
                theme("team-spirit"), theme("problem-solvers")));
        when(store.load(GROUP, TENANT)).thenReturn(state2);

        var tick = orchestrator.tick(GROUP, TENANT);

        assertThat(tick).isInstanceOf(NarrativeTick.Updated.class);
        var updated = (NarrativeTick.Updated) tick;
        assertThat(updated.previous()).isNotNull();
        assertThat(updated.newEpisodeIds()).containsExactly("ge2");
        assertThat(updated.newThemeLabels()).containsExactly("problem-solvers");
    }

    @Test
    void currentNarrative_beforeTick_returnsEmpty() {
        assertThat(orchestrator.currentNarrative(GROUP, TENANT)).isEmpty();
    }

    @Test
    void currentNarrative_afterTick_returnsCachedState() {
        var state = groupState(GROUP, T1, List.of(groupEpisode("ge1")));
        when(store.load(GROUP, TENANT)).thenReturn(state);

        orchestrator.tick(GROUP, TENANT);

        assertThat(orchestrator.currentNarrative(GROUP, TENANT))
                .isPresent()
                .containsSame(state);
    }

    @Test
    void tick_perGroupIsolation() {
        var state1 = groupState("group-1", T1, List.of(groupEpisode("ge1")));
        var state2 = groupState("group-2", T1, List.of(groupEpisode("ge2"), groupEpisode("ge3")));
        when(store.load("group-1", TENANT)).thenReturn(state1);
        when(store.load("group-2", TENANT)).thenReturn(state2);

        orchestrator.tick("group-1", TENANT);
        orchestrator.tick("group-2", TENANT);

        assertThat(orchestrator.currentNarrative("group-1", TENANT).get()
                .groupEpisodes()).hasSize(1);
        assertThat(orchestrator.currentNarrative("group-2", TENANT).get()
                .groupEpisodes()).hasSize(2);
    }

    @Test
    void members_returnsConfiguredSet() {
        assertThat(orchestrator.members())
                .containsExactlyInAnyOrder("agent-a", "agent-b", "agent-c");
    }

    @Test
    void tick_threadSafety() throws InterruptedException {
        var state = groupState(GROUP, T1, List.of(groupEpisode("ge1")));
        when(store.load(anyString(), anyString())).thenReturn(state);

        var errors = new AtomicInteger(0);
        var latch = new CountDownLatch(10);
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 10; i++) {
                var groupId = "group-" + (i % 3);
                executor.submit(() -> {
                    try {
                        orchestrator.tick(groupId, TENANT);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
        assertThat(errors.get()).isZero();
    }
}
