package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.index.TemporalEntry;
import io.casehub.neocortex.cognitive.index.TemporalFocusConfig;
import io.casehub.neocortex.cognitive.index.TemporalIndex;
import io.casehub.neocortex.cognitive.index.TemporalQuery;
import io.casehub.neocortex.cognitive.index.TemporalSource;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemporalFocusOrchestratorTest {

    @Test
    void tickProducesRankedAttentionItems() {
        var index = mock(TemporalIndex.class);
        var memoryStore = mock(CaseMemoryStore.class);

        var node = mock(MindMapNode.class);
        when(node.id()).thenReturn("entity-1");
        var entry = new TemporalEntry(
                Instant.now().minusSeconds(60),
                new TemporalSource.FromMindMap(node),
                "tenant1", Confidence.stated(0.9, Instant.now()));
        when(index.query(any(TemporalQuery.class))).thenReturn(List.of(entry));
        when(memoryStore.query(any())).thenReturn(List.of());

        var orchestrator = new TemporalFocusOrchestrator(
                index, memoryStore, TemporalFocusConfig.defaults());

        orchestrator.tick("agent1", "tenant1", Set.of("entity-1"));

        var focus = orchestrator.lastFocus();
        assertThat(focus).isNotEmpty();
        assertThat(focus.getFirst().salience()).isGreaterThan(0);
        assertThat(focus.getFirst().reason()).contains("recent");
    }

    @Test
    void tickWithNoEntriesProducesEmptyFocus() {
        var index = mock(TemporalIndex.class);
        var memoryStore = mock(CaseMemoryStore.class);
        when(index.query(any(TemporalQuery.class))).thenReturn(List.of());

        var orchestrator = new TemporalFocusOrchestrator(
                index, memoryStore, TemporalFocusConfig.defaults());

        orchestrator.tick("agent1", "tenant1", Set.of());

        assertThat(orchestrator.lastFocus()).isEmpty();
    }

    @Test
    void tickWithoutAffectMemoriesScoresByRecencyOnly() {
        var index = mock(TemporalIndex.class);
        var memoryStore = mock(CaseMemoryStore.class);

        var recentNode = mock(MindMapNode.class);
        when(recentNode.id()).thenReturn("entity-recent");
        var oldNode = mock(MindMapNode.class);
        when(oldNode.id()).thenReturn("entity-old");

        var now = Instant.now();
        var recentEntry = new TemporalEntry(
                now.minusSeconds(60),
                new TemporalSource.FromMindMap(recentNode),
                "t", Confidence.stated(0.9, now));
        var oldEntry = new TemporalEntry(
                now.minusSeconds(7200),
                new TemporalSource.FromMindMap(oldNode),
                "t", Confidence.stated(0.9, now));

        when(index.query(any(TemporalQuery.class)))
                .thenReturn(List.of(oldEntry, recentEntry))
                .thenReturn(List.of());
        when(memoryStore.query(any())).thenReturn(List.of());

        var orchestrator = new TemporalFocusOrchestrator(
                index, memoryStore, TemporalFocusConfig.defaults());

        orchestrator.tick("agent", "t", Set.of("entity-recent", "entity-old"));

        var focus = orchestrator.lastFocus();
        assertThat(focus).hasSize(2);
        assertThat(focus.get(0).salience()).isGreaterThan(focus.get(1).salience());
    }
}
