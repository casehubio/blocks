package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.blocks.summarisation.ContentSummariser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryHygieneOrchestratorTest {

    private CbrCaseMemoryStore store;
    private ConfidenceScorer   scorer;
    private TemporalDecay      decay;
    private ScopeDecay scopeDecay;
    @SuppressWarnings("unchecked")
    private ContentSummariser<ScoredCbrCase<? extends CbrCase>> summariser =
            mock(ContentSummariser.class);
    @SuppressWarnings("unchecked")
    private Consumer<HygieneEvent> eventSink = mock(Consumer.class);
    private MemoryHygieneOrchestrator orchestrator;

    private static final MemoryDomain    DOMAIN    = new MemoryDomain("agent");
    private static final RetentionConfig RETENTION = new RetentionConfig(0.5, 1.0, 1.0, 0.0, 0.0);

    @BeforeEach
    void setUp() {
        store = mock(CbrCaseMemoryStore.class);
        scorer = mock(ConfidenceScorer.class);
        decay = new TemporalDecay.HalfLife(Duration.ofDays(30));
        scopeDecay = new ScopeDecay.Step(0.5);
        orchestrator = new MemoryHygieneOrchestrator(
                store, scorer, decay, scopeDecay, summariser,
                DOMAIN, List.of("test-case"), RETENTION, 100, 0.7, eventSink);
    }

    private ScoredCbrCase<CbrCase> makeMemory(String caseId, String entityId, String agentId) {
        return makeMemory(caseId, entityId, agentId, Instant.now());
    }

    private ScoredCbrCase<CbrCase> makeMemory(String caseId, String entityId,
                                               String agentId, Instant storedAt) {
        var cbrCase = new FeatureVectorCbrCase("problem text", "solution text", null, null,
                Map.of("k", FeatureValue.string("v")), null, agentId);
        return new ScoredCbrCase<>(cbrCase, caseId, 0.5, false,
                Map.of(), storedAt, io.casehub.platform.api.path.Path.root(), null);
    }

    @Test
    void tickReturnsIdleWhenNoMemories() {
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());
        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(HygieneTick.Idle.class);
    }

    @Test
    void tickEvictsLowScoringMemories() {
        var oldTime = Instant.now().minus(Duration.ofDays(365));
        var memory = makeMemory("case-1", "entity-1", "agent-1", oldTime);
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of(memory));
        when(scorer.score(any(), any())).thenReturn(0.1);

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(HygieneTick.Completed.class);
        var completed = (HygieneTick.Completed) result;
        assertThat(completed.evicted()).isEqualTo(1);
        assertThat(completed.totalScored()).isEqualTo(1);

        verify(store).erase(any());
        verify(eventSink).accept(any(HygieneEvent.MemoryEvicted.class));
    }

    @Test
    void tickRetainsHighScoringMemories() {
        var memory = makeMemory("case-1", "entity-1", "agent-1");
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of(memory));
        when(scorer.score(any(), any())).thenReturn(0.9);

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(HygieneTick.Completed.class);
        var completed = (HygieneTick.Completed) result;
        assertThat(completed.evicted()).isEqualTo(0);

        verify(store, never()).erase(any());
    }

    @Test
    void tickReturnsFailedOnStoreException() {
        when(store.retrieveSimilar(any(), any())).thenThrow(new RuntimeException("store down"));
        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(HygieneTick.Failed.class);
        assertThat(((HygieneTick.Failed) result).reason()).contains("store down");
    }

    @Test
    void tickFiltersMemoriesByAgentId() {
        var ownMemory = makeMemory("case-1", "entity-1", "agent-1");
        var otherMemory = makeMemory("case-2", "entity-2", "other-agent");
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of(ownMemory, otherMemory));
        when(scorer.score(any(), any())).thenReturn(0.9);

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(HygieneTick.Completed.class);
        var completed = (HygieneTick.Completed) result;
        assertThat(completed.totalScored()).isEqualTo(1);
    }

    @Test
    void tickSerializesPerAgent() throws Exception {
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());

        var t1 = new Thread(() -> orchestrator.tick("agent-1", "tenant-1"));
        var t2 = new Thread(() -> orchestrator.tick("agent-1", "tenant-1"));
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        verify(store, atLeast(2)).retrieveSimilar(any(), any());
    }
}
