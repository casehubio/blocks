package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StrategyLearningOrchestratorTest {

    private StrategyStore strategyStore;
    private CbrCaseMemoryStore cbrStore;
    private ReflectionOrchestrator reflectionOrchestrator;
    private AgentProvider agentProvider;
    private StrategyLearningOrchestrator orchestrator;
    private Clock clock;

    @BeforeEach
    void setUp() {
        strategyStore = mock(StrategyStore.class);
        cbrStore = mock(CbrCaseMemoryStore.class);
        reflectionOrchestrator = mock(ReflectionOrchestrator.class);
        agentProvider = mock(AgentProvider.class);
        clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneId.of("UTC"));
        orchestrator = new StrategyLearningOrchestrator(
                strategyStore, cbrStore, reflectionOrchestrator, agentProvider,
                null, StrategyLearningConfig.defaults(), clock);
    }

    // --- record + tick: tier 1 ---

    @Test void tick_noSignals_returnsNoChange() {
        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyLearningTick.NoChange.class);
    }

    @Test void tick_withTurnOutcomes_belowThreshold_returnsObserved() {
        orchestrator.record(turnOutcome("case-1", true, 0.5, 100), "agent-1", "user-1", "tenant-1");
        orchestrator.record(turnOutcome("case-1", false, -0.5, 200), "agent-1", "user-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyLearningTick.Observed.class);
        var observed = (StrategyLearningTick.Observed) result;
        assertThat(observed.signalsProcessed()).isEqualTo(2);
        assertThat(observed.engagementRate()).isCloseTo(0.5, within(0.01));
        assertThat(observed.meanSentiment()).isCloseTo(0.0, within(0.01));
    }

    @Test void tick_secondTickAccumulatesCounters() {
        orchestrator.record(turnOutcome("case-1", true, 0.5, 100), "agent-1", "user-1", "tenant-1");
        orchestrator.tick("agent-1", "tenant-1");

        orchestrator.record(turnOutcome("case-1", false, -0.5, 200), "agent-1", "user-1", "tenant-1");
        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyLearningTick.Observed.class);
        var observed = (StrategyLearningTick.Observed) result;
        assertThat(observed.signalsProcessed()).isEqualTo(1);
        assertThat(observed.engagementRate()).isCloseTo(0.5, within(0.01));
    }

    @Test void tick_afterDrain_secondTickWithNoSignals_returnsNoChange() {
        orchestrator.record(turnOutcome("case-1", true, 0.5, 100), "agent-1", "user-1", "tenant-1");
        orchestrator.tick("agent-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyLearningTick.NoChange.class);
    }

    // --- record + tick: tier 2 ---

    @Test void tick_withConversationOutcome_returnsLearned() {
        orchestrator.record(turnOutcome("case-1", true, 0.3, 150), "agent-1", "user-1", "tenant-1");
        orchestrator.record(turnOutcome("case-1", true, 0.5, 200), "agent-1", "user-1", "tenant-1");
        orchestrator.record(turnOutcome("case-1", true, 0.1, 100), "agent-1", "user-1", "tenant-1");
        orchestrator.record(
                new EngagementSignal.ConversationOutcome("case-1", "summary", 3),
                "agent-1", "user-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyLearningTick.Learned.class);
        var learned = (StrategyLearningTick.Learned) result;
        assertThat(learned.signalsProcessed()).isEqualTo(3);
        assertThat(learned.casesStored()).isEqualTo(1);
        assertThat(learned.conversationsStored()).contains("case-1");
        verify(cbrStore).store(any(FeatureVectorCbrCase.class), eq("engagement-evidence"),
                eq("agent-1"), any(), eq("tenant-1"), isNull(), any());
    }

    @Test void tick_conversationCorrelation_matchesByCaseId() {
        orchestrator.record(turnOutcome("case-1", true, 0.3, 100), "agent-1", "user-1", "tenant-1");
        orchestrator.record(turnOutcome("case-2", true, 0.5, 200), "agent-1", "user-1", "tenant-1");
        orchestrator.record(turnOutcome("case-1", true, 0.1, 150), "agent-1", "user-1", "tenant-1");

        orchestrator.record(
                new EngagementSignal.ConversationOutcome("case-1", "summary", 2),
                "agent-1", "user-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyLearningTick.Learned.class);
        assertThat(((StrategyLearningTick.Learned) result).casesStored()).isEqualTo(1);

        var captor = ArgumentCaptor.forClass(FeatureVectorCbrCase.class);
        verify(cbrStore).store(captor.capture(), anyString(), anyString(),
                any(), anyString(), any(), any());
        var storedFeatures = captor.getValue().features();
        assertThat(((FeatureValue.NumberVal) storedFeatures.get("turnCount")).value())
                .isEqualTo(2.0);
    }

    @Test void tick_featureExtraction_avgResponseLength() {
        orchestrator.record(turnOutcome("case-1", true, 0.0, 100), "agent-1", "user-1", "tenant-1");
        orchestrator.record(turnOutcome("case-1", true, 0.0, 300), "agent-1", "user-1", "tenant-1");
        orchestrator.record(
                new EngagementSignal.ConversationOutcome("case-1", "summary", 2),
                "agent-1", "user-1", "tenant-1");

        orchestrator.tick("agent-1", "tenant-1");

        var captor = ArgumentCaptor.forClass(FeatureVectorCbrCase.class);
        verify(cbrStore).store(captor.capture(), anyString(), anyString(),
                any(), anyString(), any(), any());
        var features = captor.getValue().features();
        assertThat(((FeatureValue.NumberVal) features.get("avgResponseLength")).value())
                .isCloseTo(200.0, within(0.01));
        assertThat(((FeatureValue.NumberVal) features.get("continuationRate")).value())
                .isEqualTo(1.0);
    }

    @Test void tick_featureExtraction_dimensionalSnapshotAveraging() {
        var snap1 = Map.of("verbosity", 0.3, "formality", 0.7);
        var snap2 = Map.of("verbosity", 0.5, "formality", 0.9);
        orchestrator.record(
                new EngagementSignal.TurnOutcome(engagementEvent("case-1", true, 0.0, 100), snap1, null),
                "agent-1", "user-1", "tenant-1");
        orchestrator.record(
                new EngagementSignal.TurnOutcome(engagementEvent("case-1", true, 0.0, 100), snap2, null),
                "agent-1", "user-1", "tenant-1");
        orchestrator.record(
                new EngagementSignal.ConversationOutcome("case-1", "summary", 2),
                "agent-1", "user-1", "tenant-1");

        orchestrator.tick("agent-1", "tenant-1");

        var captor = ArgumentCaptor.forClass(FeatureVectorCbrCase.class);
        verify(cbrStore).store(captor.capture(), anyString(), anyString(),
                any(), anyString(), any(), any());
        var features = captor.getValue().features();
        assertThat(((FeatureValue.NumberVal) features.get("avgSnapshot_verbosity")).value())
                .isCloseTo(0.4, within(0.01));
        assertThat(((FeatureValue.NumberVal) features.get("avgSnapshot_formality")).value())
                .isCloseTo(0.8, within(0.01));
    }

    @Test void tick_nullEngagementFields_handledGracefully() {
        var event = new EngagementEvent("agent-1", "user-1", "tenant-1", "case-1",
                                        "turn-1", null, "test", null, Map.of(),
                                        null, null, null, null, null, null);
        orchestrator.record(
                new EngagementSignal.TurnOutcome(event, Map.of(), null),
                "agent-1", "user-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyLearningTick.Observed.class);
    }

    // --- currentStrategy ---

    @Test void currentStrategy_returnsEmpty_whenNoProfile() {
        when(strategyStore.lookup("agent-1", "tenant-1")).thenReturn(java.util.Optional.empty());
        assertThat(orchestrator.currentStrategy("agent-1", "tenant-1")).isEmpty();
    }

    @Test void currentStrategy_returnsFromStore_whenNoInMemoryState() {
        var profile = new StrategyProfile("agent-1", "tenant-1",
                Map.of("verbosity", 0.3), List.of("Be concise"), Instant.now(), 5);
        when(strategyStore.lookup("agent-1", "tenant-1"))
                .thenReturn(java.util.Optional.of(profile));
        var result = orchestrator.currentStrategy("agent-1", "tenant-1");
        assertThat(result).isPresent();
        assertThat(result.get().dimensions().get("verbosity")).isEqualTo(0.3);
    }

    // --- reflect ---

    @Test void reflect_insufficientCases_returnsNoChange() {
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(List.of());
        var result = orchestrator.reflect("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyReflection.NoChange.class);
        assertThat(((StrategyReflection.NoChange) result).reason()).contains("insufficient");
    }

    @Test void reflect_withSufficientCases_producesReflected() {
        var cases = buildEngagementCases(5, "agent-1");
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(cases);
        when(reflectionOrchestrator.reflect(any(), any(), any(), anyInt()))
                .thenReturn(List.of("Agent tends to be verbose"));

        mockLlmResponse("{\"guidelines\":[\"Be more concise\",\"Ask follow-up questions\"]," +
                "\"dimensionDeltas\":{\"verbosity\":-0.1}}");

        var result = orchestrator.reflect("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyReflection.Reflected.class);
        var reflected = (StrategyReflection.Reflected) result;
        assertThat(reflected.newGuidelines()).contains("Be more concise");
        assertThat(reflected.profile().dimensions().get("verbosity")).isCloseTo(0.4, within(0.01));
        assertThat(reflected.profile().dimensions().get("formality")).isCloseTo(0.5, within(0.01));
        assertThat(reflected.evidenceCases()).isEqualTo(5);
        verify(strategyStore).store(any(StrategyProfile.class));
    }

    @Test void reflect_malformedLlmOutput_returnsNoChange() {
        var cases = buildEngagementCases(5, "agent-1");
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(cases);
        when(reflectionOrchestrator.reflect(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        mockLlmResponse("not valid json at all");

        var result = orchestrator.reflect("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyReflection.NoChange.class);
        verify(strategyStore, never()).store(any());
    }

    @Test void reflect_clampsDeltas_toRange() {
        var profile = new StrategyProfile("agent-1", "tenant-1",
                Map.of("verbosity", 0.95, "formality", 0.05,
                        "initiative", 0.5, "directness", 0.5, "questionRate", 0.5),
                List.of(), Instant.now(), 0);
        when(strategyStore.lookup("agent-1", "tenant-1"))
                .thenReturn(java.util.Optional.of(profile));

        var cases = buildEngagementCases(5, "agent-1");
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(cases);
        when(reflectionOrchestrator.reflect(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        mockLlmResponse("{\"guidelines\":[\"Test\"]," +
                "\"dimensionDeltas\":{\"verbosity\":0.2,\"formality\":-0.2}}");

        var result = orchestrator.reflect("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyReflection.Reflected.class);
        var reflected = (StrategyReflection.Reflected) result;
        assertThat(reflected.profile().dimensions().get("verbosity")).isEqualTo(1.0);
        assertThat(reflected.profile().dimensions().get("formality")).isEqualTo(0.0);
    }

    @Test void reflect_ignoresUnknownDimensionKeys() {
        var cases = buildEngagementCases(5, "agent-1");
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(cases);
        when(reflectionOrchestrator.reflect(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        mockLlmResponse("{\"guidelines\":[\"Test\"]," +
                "\"dimensionDeltas\":{\"verbosity\":-0.1,\"unknown_dim\":0.5}}");

        var result = orchestrator.reflect("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyReflection.Reflected.class);
        var reflected = (StrategyReflection.Reflected) result;
        assertThat(reflected.profile().dimensions()).doesNotContainKey("unknown_dim");
        assertThat(reflected.profile().dimensions().get("verbosity")).isCloseTo(0.4, within(0.01));
    }

    @Test void reflect_emptyGuidelines_retainsPrevious() {
        var profile = new StrategyProfile("agent-1", "tenant-1",
                Map.of("verbosity", 0.5, "formality", 0.5,
                        "initiative", 0.5, "directness", 0.5, "questionRate", 0.5),
                List.of("Existing guideline"), Instant.now(), 3);
        when(strategyStore.lookup("agent-1", "tenant-1"))
                .thenReturn(java.util.Optional.of(profile));

        var cases = buildEngagementCases(5, "agent-1");
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(cases);
        when(reflectionOrchestrator.reflect(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        mockLlmResponse("{\"guidelines\":[],\"dimensionDeltas\":{}}");

        var result = orchestrator.reflect("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyReflection.Reflected.class);
        var reflected = (StrategyReflection.Reflected) result;
        assertThat(reflected.newGuidelines()).containsExactly("Existing guideline");
    }

    @Test void reflect_filtersOtherAgentsCases() {
        var ownCases = buildEngagementCases(3, "agent-1");
        var otherCases = buildEngagementCases(3, "other-agent");
        var allCases = new ArrayList<>(ownCases);
        allCases.addAll(otherCases);
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(allCases);

        var result = orchestrator.reflect("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyReflection.NoChange.class);
        assertThat(((StrategyReflection.NoChange) result).reason()).contains("insufficient");
    }

    @Test void reflect_llmWithCodeFences_stripsAndParses() {
        var cases = buildEngagementCases(5, "agent-1");
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(cases);
        when(reflectionOrchestrator.reflect(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        mockLlmResponse("```json\n{\"guidelines\":[\"Fenced output\"],\"dimensionDeltas\":{}}\n```");

        var result = orchestrator.reflect("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(StrategyReflection.Reflected.class);
        var reflected = (StrategyReflection.Reflected) result;
        assertThat(reflected.newGuidelines()).contains("Fenced output");
    }

    @Test void reflect_storesUpdatedProfile() {
        var cases = buildEngagementCases(5, "agent-1");
        when(cbrStore.retrieveSimilar(any(), any())).thenReturn(cases);
        when(reflectionOrchestrator.reflect(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        mockLlmResponse("{\"guidelines\":[\"New guideline\"],\"dimensionDeltas\":{\"formality\":0.15}}");

        orchestrator.reflect("agent-1", "tenant-1");

        var captor = ArgumentCaptor.forClass(StrategyProfile.class);
        verify(strategyStore).store(captor.capture());
        assertThat(captor.getValue().agentId()).isEqualTo("agent-1");
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(captor.getValue().guidelines()).contains("New guideline");
        assertThat(captor.getValue().dimensions().get("formality")).isCloseTo(0.65, within(0.01));
    }

    // --- helpers ---

    private EngagementSignal.TurnOutcome turnOutcome(String caseId, boolean responded,
                                                      double sentiment, int responseLength) {
        return new EngagementSignal.TurnOutcome(
                engagementEvent(caseId, responded, sentiment, responseLength),
                Map.of("verbosity", 0.5, "formality", 0.5),
                "excerpt");
    }

    private EngagementEvent engagementEvent(String caseId, boolean responded,
                                             double sentiment, int responseLength) {
        return new EngagementEvent("agent-1", "user-1", "tenant-1", caseId,
                                   "turn-1", null, "test description", null, Map.of(),
                                   responded, null, responseLength, sentiment, null, responded);
    }

    @SuppressWarnings("unchecked")
    private List<ScoredCbrCase<CbrCase>> buildEngagementCases(int count, String agentId) {
        var cases = new ArrayList<ScoredCbrCase<CbrCase>>();
        for (int i = 0; i < count; i++) {
            var cbrCase = mock(CbrCase.class);
            when(cbrCase.producerAgentId()).thenReturn(agentId);
            when(cbrCase.features()).thenReturn(Map.of(
                    "agentId", FeatureValue.string(agentId),
                    "subjectId", FeatureValue.string("user-" + (i % 2)),
                    "conversationTimestamp", FeatureValue.number((double) (1000 + i * 100)),
                    "continuationRate", FeatureValue.number(0.7 + i * 0.02),
                    "avgResponseLength", FeatureValue.number(200.0 + i * 10),
                    "meanSentimentShift", FeatureValue.number(0.1 + i * 0.05),
                    "avgSnapshot_verbosity", FeatureValue.number(0.5),
                    "avgSnapshot_formality", FeatureValue.number(0.5)));
            cases.add(new ScoredCbrCase<>(cbrCase, "case-" + i, 1.0));
        }
        return cases;
    }

    private void mockLlmResponse(String text) {
        var textDelta = mock(AgentEvent.TextDelta.class);
        when(textDelta.text()).thenReturn(text);
        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(textDelta));
    }
}
