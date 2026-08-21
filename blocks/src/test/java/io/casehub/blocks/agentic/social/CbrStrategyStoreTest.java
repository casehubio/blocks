package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CbrStrategyStoreTest {

    private CbrCaseMemoryStore cbrStore;
    private CbrStrategyStore store;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        store = new CbrStrategyStore(cbrStore, StrategyLearningConfig.defaults());
    }

    @Test void store_writesProfileAsCbrCase() {
        var profile = new StrategyProfile("agent-1", "tenant-1",
                Map.of("verbosity", 0.7, "formality", 0.3),
                List.of("Be concise", "Ask questions"), Instant.now(), 5);

        store.store(profile);

        var captor = ArgumentCaptor.forClass(FeatureVectorCbrCase.class);
        verify(cbrStore).store(captor.capture(), eq("strategy-profile"),
                eq("agent-1"), any(MemoryDomain.class), eq("tenant-1"),
                isNull(), eq(Path.root()));

        var cbrCase = captor.getValue();
        assertThat(cbrCase.problem()).startsWith("guidelines: ");
        assertThat(cbrCase.problem()).contains("Be concise");
        assertThat(cbrCase.solution()).isEqualTo("-");
        assertThat(cbrCase.producerAgentId()).isEqualTo("agent-1");
        assertThat(cbrCase.features().get("verbosity"))
                .isEqualTo(FeatureValue.number(0.7));
    }

    @Test void store_profileWithNoGuidelines() {
        var profile = new StrategyProfile("agent-1", "tenant-1",
                Map.of("verbosity", 0.5), List.of(), Instant.now(), 0);

        store.store(profile);

        var captor = ArgumentCaptor.forClass(FeatureVectorCbrCase.class);
        verify(cbrStore).store(captor.capture(), anyString(), anyString(),
                any(), anyString(), any(), any());
        assertThat(captor.getValue().problem()).contains("no guidelines");
    }

    @Test void lookup_returnsEmpty_whenNoCases() {
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of());
        assertThat(store.lookup("agent-1", "tenant-1")).isEmpty();
    }

    @Test void lookup_returnsProfile_whenCaseExists() {
        var features = Map.<String, FeatureValue>of(
                "agent_id", FeatureValue.string("agent-1"),
                "verbosity", FeatureValue.number(0.7),
                "formality", FeatureValue.number(0.3),
                "initiative", FeatureValue.number(0.5),
                "directness", FeatureValue.number(0.5),
                "questionRate", FeatureValue.number(0.5),
                "evidence_count", FeatureValue.number(5));
        var cbrCase = mock(CbrCase.class);
        when(cbrCase.features()).thenReturn(features);
        when(cbrCase.producerAgentId()).thenReturn("agent-1");
        when(cbrCase.problem()).thenReturn("guidelines: Be concise\nAsk questions");
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0, false,
                Map.of(), Instant.parse("2026-08-21T00:00:00Z"), Path.root(), null);

        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of(scored));

        var result = store.lookup("agent-1", "tenant-1");
        assertThat(result).isPresent();
        assertThat(result.get().agentId()).isEqualTo("agent-1");
        assertThat(result.get().tenantId()).isEqualTo("tenant-1");
        assertThat(result.get().dimensions().get("verbosity")).isEqualTo(0.7);
        assertThat(result.get().dimensions().get("formality")).isEqualTo(0.3);
        assertThat(result.get().guidelines()).containsExactly("Be concise", "Ask questions");
        assertThat(result.get().evidenceCount()).isEqualTo(5);
    }

    @Test void lookup_filtersbyProducerAgentId() {
        var cbrCase = mock(CbrCase.class);
        when(cbrCase.producerAgentId()).thenReturn("other-agent");
        when(cbrCase.features()).thenReturn(Map.of());
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of(scored));

        assertThat(store.lookup("agent-1", "tenant-1")).isEmpty();
    }

    @Test void eraseAgent_deletesProfileAndEngagementCases() {
        var profileCase = mock(CbrCase.class);
        when(profileCase.producerAgentId()).thenReturn("agent-1");
        when(profileCase.features()).thenReturn(Map.of());
        var profileScored = new ScoredCbrCase<>(profileCase, "profile-1", 1.0);

        var engCase = mock(CbrCase.class);
        when(engCase.producerAgentId()).thenReturn("agent-1");
        when(engCase.features()).thenReturn(Map.of());
        var engScored = new ScoredCbrCase<>(engCase, "eng-1", 1.0);

        when(cbrStore.retrieveSimilar(any(), any()))
                .thenReturn(List.of(profileScored))
                .thenReturn(List.of(engScored));

        store.eraseAgent("agent-1", "tenant-1");

        verify(cbrStore, times(2)).erase(any(EraseRequest.class));
    }

    @Test void eraseAgent_skipsOtherAgentsCases() {
        var cbrCase = mock(CbrCase.class);
        when(cbrCase.producerAgentId()).thenReturn("other-agent");
        when(cbrCase.features()).thenReturn(Map.of());
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of(scored));

        store.eraseAgent("agent-1", "tenant-1");

        verify(cbrStore, never()).erase(any(EraseRequest.class));
    }

    @Test void eraseSubject_deletesEngagementCasesForSubject() {
        var features = Map.<String, FeatureValue>of(
                "subjectId", FeatureValue.string("user-X"));
        var cbrCase = mock(CbrCase.class);
        when(cbrCase.producerAgentId()).thenReturn("agent-1");
        when(cbrCase.features()).thenReturn(features);
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of(scored));

        store.eraseSubject("user-X", "tenant-1");

        var captor = ArgumentCaptor.forClass(EraseRequest.class);
        verify(cbrStore).erase(captor.capture());
        assertThat(captor.getValue().caseId()).isEqualTo("case-1");
    }

    @Test void eraseSubject_skipsNonMatchingSubjects() {
        var features = Map.<String, FeatureValue>of(
                "subjectId", FeatureValue.string("other-user"));
        var cbrCase = mock(CbrCase.class);
        when(cbrCase.producerAgentId()).thenReturn("agent-1");
        when(cbrCase.features()).thenReturn(features);
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of(scored));

        store.eraseSubject("user-X", "tenant-1");

        verify(cbrStore, never()).erase(any(EraseRequest.class));
    }

    @Test void subjectInsights_returnsFormattedInsights() {
        var features = Map.<String, FeatureValue>of(
                "subjectId", FeatureValue.string("user-X"),
                "continuationRate", FeatureValue.number(0.8),
                "avgResponseLength", FeatureValue.number(245.0),
                "meanSentimentShift", FeatureValue.number(0.15));
        var cbrCase = mock(CbrCase.class);
        when(cbrCase.producerAgentId()).thenReturn("agent-1");
        when(cbrCase.features()).thenReturn(features);
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of(scored));

        List<String> insights = store.subjectInsights("agent-1", "user-X", "tenant-1");
        assertThat(insights).hasSize(1);
        assertThat(insights.get(0)).contains("user-X");
        assertThat(insights.get(0)).contains("80%");
        assertThat(insights.get(0)).contains("245");
    }

    @Test void subjectInsights_filtersOtherAgents() {
        var features = Map.<String, FeatureValue>of(
                "subjectId", FeatureValue.string("user-X"),
                "continuationRate", FeatureValue.number(0.5));
        var cbrCase = mock(CbrCase.class);
        when(cbrCase.producerAgentId()).thenReturn("other-agent");
        when(cbrCase.features()).thenReturn(features);
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of(scored));

        assertThat(store.subjectInsights("agent-1", "user-X", "tenant-1")).isEmpty();
    }

    @Test void subjectInsights_returnsEmpty_whenNoCases() {
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of());
        assertThat(store.subjectInsights("agent-1", "user-X", "tenant-1")).isEmpty();
    }
}
