package io.casehub.blocks.agentic.social;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReflectionRetrievalOrchestratorTest {

    @Test
    void tickCachesSalientReflections() {
        var store = mock(ReflectionQueryStore.class);
        var entry = new ReflectionEntry("agent1", "tenant1",
                "When facing resistance, try indirect approaches",
                Instant.now(), List.of("case-1"));
        when(store.findSalient("agent1", "tenant1")).thenReturn(List.of(entry));

        var orchestrator = new ReflectionRetrievalOrchestrator(store);
        orchestrator.tick("agent1", "tenant1");

        assertThat(orchestrator.lastReflections()).hasSize(1);
        assertThat(orchestrator.lastReflections().getFirst().insight())
                .isEqualTo("When facing resistance, try indirect approaches");
    }

    @Test
    void lastReflectionsEmptyBeforeTick() {
        var store = mock(ReflectionQueryStore.class);
        var orchestrator = new ReflectionRetrievalOrchestrator(store);

        assertThat(orchestrator.lastReflections()).isEmpty();
    }

    @Test
    void tickWithEmptyStoreProducesEmptyList() {
        var store = mock(ReflectionQueryStore.class);
        when(store.findSalient("agent1", "tenant1")).thenReturn(List.of());

        var orchestrator = new ReflectionRetrievalOrchestrator(store);
        orchestrator.tick("agent1", "tenant1");

        assertThat(orchestrator.lastReflections()).isEmpty();
    }

    @Test
    void subsequentTickRefreshesCache() {
        var store = mock(ReflectionQueryStore.class);
        var entry1 = new ReflectionEntry("agent1", "tenant1",
                "Insight one", Instant.now(), List.of());
        var entry2 = new ReflectionEntry("agent1", "tenant1",
                "Insight two", Instant.now(), List.of());
        when(store.findSalient("agent1", "tenant1"))
                .thenReturn(List.of(entry1))
                .thenReturn(List.of(entry2));

        var orchestrator = new ReflectionRetrievalOrchestrator(store);

        orchestrator.tick("agent1", "tenant1");
        assertThat(orchestrator.lastReflections().getFirst().insight()).isEqualTo("Insight one");

        orchestrator.tick("agent1", "tenant1");
        assertThat(orchestrator.lastReflections().getFirst().insight()).isEqualTo("Insight two");
    }
}
