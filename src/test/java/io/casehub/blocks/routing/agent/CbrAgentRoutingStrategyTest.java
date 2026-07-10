package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentAssignment;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.eidos.api.MatchDegree;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbrAgentRoutingStrategyTest {

    @Mock CbrCaseMemoryStore cbrStore;
    @Mock AgentGraphQuery graphQuery;
    @Mock TrustCandidateClassifier classifier;
    @Mock TrustScoreSource scoreSource;
    @Mock TrustRoutingPolicyProvider policyProvider;

    private AgentRoutingContext context(String capability) {
        return new AgentRoutingContext(UUID.randomUUID(), capability, NullNode.instance, "test-tenant", List.of());
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(id, Set.of("analysis"), 0, AgentHealth.READY, null, new MatchDegree.None());
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> presentInstance(T value) {
        var inst = mock(Instance.class);
        when(inst.isUnsatisfied()).thenReturn(false);
        when(inst.get()).thenReturn(value);
        return inst;
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> absentInstance() {
        var inst = mock(Instance.class);
        when(inst.isUnsatisfied()).thenReturn(true);
        return inst;
    }

    @Test
    void idIsCbr() {
        var strategy = new CbrAgentRoutingStrategy(
                10, 0.5,
                absentInstance(), absentInstance(), absentInstance(), absentInstance(), absentInstance(),
                new TextOnlyFeatureExtractor());
        assertThat(strategy.id()).isEqualTo("cbr");
    }

    @Test
    void usesFeatureExtractorForQuery() {
        var customExtractor = mock(RoutingFeatureExtractor.class);
        when(customExtractor.extractProblem(any())).thenReturn("custom problem");
        when(customExtractor.extractFeatures(any())).thenReturn(Map.of("domain", "aml"));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                .thenReturn(List.of("agent-a"));

        var strategy = new CbrAgentRoutingStrategy(
                10, 0.5, presentInstance(cbrStore), presentInstance(graphQuery),
                absentInstance(), absentInstance(), absentInstance(),
                customExtractor);

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        var queryCaptor = org.mockito.ArgumentCaptor.forClass(CbrQuery.class);
        verify(cbrStore).retrieveSimilar(queryCaptor.capture(), eq(CbrCase.class));
        assertThat(queryCaptor.getValue().problem()).isEqualTo("custom problem");
        assertThat(queryCaptor.getValue().features()).containsEntry("domain", "aml");
    }

    @Test
    void structuredFeaturesFlowToCbrQuery() {
        var customExtractor = mock(RoutingFeatureExtractor.class);
        when(customExtractor.extractFeatures(any())).thenReturn(Map.of(
                "riskScore", 0.87,
                "entityType", "PEP",
                "knownHighRisk", true));
        when(customExtractor.extractProblem(any())).thenReturn("entityType=PEP, riskScore=0.87");

        var planTrace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var cbrCase = new PlanCbrCase("problem", "solution", "RESOLVED", 0.9,
                Map.of("riskScore", 0.85, "entityType", "PEP", "knownHighRisk", true),
                List.of(planTrace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.92)));

        var strategy = new CbrAgentRoutingStrategy(
                10, 0.5, presentInstance(cbrStore), presentInstance(graphQuery),
                absentInstance(), absentInstance(), absentInstance(),
                customExtractor);

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(CbrQuery.class);
        verify(cbrStore).retrieveSimilar(queryCaptor.capture(), eq(CbrCase.class));
        var query = queryCaptor.getValue();
        assertThat(query.features())
                .containsEntry("riskScore", 0.87)
                .containsEntry("entityType", "PEP")
                .containsEntry("knownHighRisk", true);
        assertThat(query.problem()).isEqualTo("entityType=PEP, riskScore=0.87");
    }

    @Test
    void nullProblemTextProducesQueryWithoutProblem() {
        var customExtractor = mock(RoutingFeatureExtractor.class);
        when(customExtractor.extractFeatures(any())).thenReturn(Map.of("grade", 3));
        when(customExtractor.extractProblem(any())).thenReturn(null);
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                .thenReturn(List.of("agent-a"));

        var strategy = new CbrAgentRoutingStrategy(
                10, 0.5, presentInstance(cbrStore), presentInstance(graphQuery),
                absentInstance(), absentInstance(), absentInstance(),
                customExtractor);

        strategy.select(context("analysis"),
                List.of(candidate("agent-a"))).await().indefinitely();

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(CbrQuery.class);
        verify(cbrStore).retrieveSimilar(queryCaptor.capture(), eq(CbrCase.class));
        assertThat(queryCaptor.getValue().problem()).isNull();
        assertThat(queryCaptor.getValue().features()).containsEntry("grade", 3);
    }

    @Nested
    class PlanBasedCbr {
        private CbrAgentRoutingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance(),
                    new TextOnlyFeatureExtractor());
        }

        @Test
        void selectsWorkerWithHighestSuccessRate() {
            var winnerTrace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var loserTrace = new PlanTrace("binding-a", "analysis", "agent-b", "FAILURE", 1, Map.of());
            var winner2Trace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());

            var case1 = new PlanCbrCase("problem1", "sol1", "RESOLVED", 0.9, Map.of(),
                    List.of(winnerTrace));
            var case2 = new PlanCbrCase("problem2", "sol2", "UNRESOLVED", 0.3, Map.of(),
                    List.of(loserTrace));
            var case3 = new PlanCbrCase("problem3", "sol3", "RESOLVED", 0.8, Map.of(),
                    List.of(winner2Trace));

            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(
                            new ScoredCbrCase<>(case1, 0.9),
                            new ScoredCbrCase<>(case2, 0.85),
                            new ScoredCbrCase<>(case3, 0.8)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void ignoresWorkersNotInCandidateList() {
            var trace = new PlanTrace("binding-a", "analysis", "not-a-candidate", "SUCCESS", 1, Map.of());
            var cbrCase = new PlanCbrCase("problem", "solution", "RESOLVED", 0.9, Map.of(),
                    List.of(trace));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            // Falls through to AgentGraphQuery or Unresolvable
            assertThat(result).isNotNull();
        }

        @Test
        void usesConfiguredTopKAndMinSimilarity() {
            var customStrategy = new CbrAgentRoutingStrategy(
                    5, 0.8,
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance(),
                    new TextOnlyFeatureExtractor());

            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of());
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(5)))
                    .thenReturn(List.of("agent-a"));

            var result = customStrategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        }

        @Test
        void nullNodeCaseContextBuildsQueryWithoutProblem() {
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of());
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("agent-a"));

            var nullContext = new AgentRoutingContext(
                    UUID.randomUUID(), "analysis", NullNode.instance, "test-tenant", List.of());
            var result = strategy.select(nullContext,
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        }
    }

    @Nested
    class TextualFallback {
        @Test
        void textualCasesDoNotProduceWorkerMatch() {
            var textCase = new TextualCbrCase("similar problem", "some solution", "RESOLVED", 0.8);
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(textCase, 0.9)));

            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance(),
                    new TextOnlyFeatureExtractor());

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            // Textual cases lack worker identity — falls through
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class Fallbacks {
        @Test
        void fallsBackToGraphQueryWhenCbrStoreUnavailable() {
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("agent-b", "agent-a"));

            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    absentInstance(), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance(),
                    new TextOnlyFeatureExtractor());

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
        }

        @Test
        void bothSourcesUnavailableReturnsUnresolvable() {
            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    absentInstance(), absentInstance(),
                    absentInstance(), absentInstance(), absentInstance(),
                    new TextOnlyFeatureExtractor());
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }

        @Test
        void emptyCandidatesReturnsUnresolvable() {
            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    presentInstance(cbrStore), absentInstance(),
                    absentInstance(), absentInstance(), absentInstance(),
                    new TextOnlyFeatureExtractor());
            var result = strategy.select(context("analysis"), List.of()).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }
    }

    @Nested
    class WithTrust {
        @Test
        void excludedCandidatesFilteredBeforeCbr() {
            var policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), false, null);
            when(policyProvider.forCapability("analysis")).thenReturn(policy);
            var good = candidate("qualified");
            var bad = candidate("excluded");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            good, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0),
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            bad, TrustCandidateClassifier.Phase.EXCLUDED_PHASE2B,
                            OptionalDouble.of(0.3), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("qualified"));

            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    absentInstance(), presentInstance(graphQuery),
                    presentInstance(classifier), presentInstance(scoreSource),
                    presentInstance(policyProvider),
                    new TextOnlyFeatureExtractor());

            var result = strategy.select(context("analysis"),
                    List.of(good, bad)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("qualified");
        }

        @Test
        void qualifiedPlusBorderlineFiltersOnlyBorderline() {
            var policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), false, null);
            when(policyProvider.forCapability("analysis")).thenReturn(policy);
            var qualified = candidate("qual-agent");
            var borderline = candidate("border-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0),
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            borderline, TrustCandidateClassifier.Phase.BORDERLINE,
                            OptionalDouble.of(0.65), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("qual-agent"));

            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    absentInstance(), presentInstance(graphQuery),
                    presentInstance(classifier), presentInstance(scoreSource),
                    presentInstance(policyProvider),
                    new TextOnlyFeatureExtractor());

            var result = strategy.select(context("analysis"),
                    List.of(qualified, borderline)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("qual-agent");
        }

        @Test
        void qualifiedPlusBootstrapGuardOnFiltersBootstrap() {
            var bootstrapPolicy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), true, null);
            when(policyProvider.forCapability("analysis")).thenReturn(bootstrapPolicy);
            var qualified = candidate("qual-agent");
            var bootstrap = candidate("boot-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0),
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            bootstrap, TrustCandidateClassifier.Phase.BOOTSTRAP,
                            OptionalDouble.empty(), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(bootstrapPolicy), eq(scoreSource)))
                    .thenReturn(classified);
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("qual-agent"));

            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    absentInstance(), presentInstance(graphQuery),
                    presentInstance(classifier), presentInstance(scoreSource),
                    presentInstance(policyProvider),
                    new TextOnlyFeatureExtractor());

            var result = strategy.select(context("analysis"),
                    List.of(qualified, bootstrap)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("qual-agent");
        }

        @Test
        void qualifiedPlusBootstrapGuardOffPassesBothThrough() {
            var policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), false, null);
            when(policyProvider.forCapability("analysis")).thenReturn(policy);
            var qualified = candidate("qual-agent");
            var bootstrap = candidate("boot-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0),
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            bootstrap, TrustCandidateClassifier.Phase.BOOTSTRAP,
                            OptionalDouble.empty(), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("boot-agent", "qual-agent"));

            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    absentInstance(), presentInstance(graphQuery),
                    presentInstance(classifier), presentInstance(scoreSource),
                    presentInstance(policyProvider),
                    new TextOnlyFeatureExtractor());

            var result = strategy.select(context("analysis"),
                    List.of(qualified, bootstrap)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("boot-agent");
        }

        @Test
        void noMatchFallsBackToClassifierDecideWhenTrustPresent() {
            var policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), false, null);
            when(policyProvider.forCapability("analysis")).thenReturn(policy);
            var candidate = candidate("qualified");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            candidate, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            // CBR returns empty, graph returns no match
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of());
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of());
            when(classifier.decide(any(), any(), eq("analysis")))
                    .thenReturn(AgentAssignment.assign("qualified", "classifier fallback"));

            var strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    presentInstance(classifier), presentInstance(scoreSource),
                    presentInstance(policyProvider),
                    new TextOnlyFeatureExtractor());

            var result = strategy.select(context("analysis"),
                    List.of(candidate)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            verify(classifier).decide(any(), any(), eq("analysis"));
        }
    }

    @Nested
    class WeightedOutcomes {
        private CbrAgentRoutingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new CbrAgentRoutingStrategy(
                    10, 0.5,
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance(),
                    new TextOnlyFeatureExtractor());
        }

        @Test
        void gateExpiredCountsAsHalfCredit() {
            var expiredTrace = new PlanTrace("b", "analysis", "agent-a", "GATE_EXPIRED", 1, Map.of());
            var failTrace = new PlanTrace("b", "analysis", "agent-b", "FAILURE", 1, Map.of());
            var cbrCase = new PlanCbrCase("p", "s", "RESOLVED", 0.9, Map.of(),
                    List.of(expiredTrace, failTrace));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void gateRejectedCountsAsQuarterCredit() {
            var rejectedTrace = new PlanTrace("b", "analysis", "agent-a", "GATE_REJECTED", 1, Map.of());
            var failTrace = new PlanTrace("b", "analysis", "agent-b", "FAILURE", 1, Map.of());
            var cbrCase = new PlanCbrCase("p", "s", "RESOLVED", 0.9, Map.of(),
                    List.of(rejectedTrace, failTrace));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void boldWorkerBeatsFailingWorker() {
            var aSuccess = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var aRejected = new PlanTrace("b", "analysis", "agent-a", "GATE_REJECTED", 1, Map.of());
            var bSuccess = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
            var bFail = new PlanTrace("b", "analysis", "agent-b", "FAILURE", 1, Map.of());
            var case1 = new PlanCbrCase("p1", "s1", "R", 0.9, Map.of(), List.of(aSuccess, bSuccess));
            var case2 = new PlanCbrCase("p2", "s2", "R", 0.8, Map.of(), List.of(aRejected, bFail));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(case1, 0.9), new ScoredCbrCase<>(case2, 0.8)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void consistentSuccessBeatsGateRejected() {
            var successTrace = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var rejectedTrace = new PlanTrace("b", "analysis", "agent-b", "GATE_REJECTED", 1, Map.of());
            var cbrCase = new PlanCbrCase("p", "s", "R", 0.9, Map.of(),
                    List.of(successTrace, rejectedTrace));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void mixedOutcomesWeightedCorrectly() {
            var aS1 = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var aS2 = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var aR = new PlanTrace("b", "analysis", "agent-a", "GATE_REJECTED", 1, Map.of());
            var aF = new PlanTrace("b", "analysis", "agent-a", "FAILURE", 1, Map.of());
            var bS = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
            var bE = new PlanTrace("b", "analysis", "agent-b", "GATE_EXPIRED", 1, Map.of());
            var c1 = new PlanCbrCase("p", "s", "R", 0.9, Map.of(), List.of(aS1, aS2, aR, aF, bS, bE));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(c1, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
        }

        @Test
        void crossCaseAccumulationWeightsCorrectly() {
            var aSuccess = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var aRejected = new PlanTrace("b", "analysis", "agent-a", "GATE_REJECTED", 1, Map.of());
            var case1 = new PlanCbrCase("p1", "s1", "R", 0.9, Map.of(), List.of(aSuccess));
            var case2 = new PlanCbrCase("p2", "s2", "R", 0.8, Map.of(), List.of(aRejected));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(case1, 0.9), new ScoredCbrCase<>(case2, 0.8)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void tiesBrokenByTotalCount() {
            var aTrace = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var bTrace1 = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
            var bTrace2 = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
            var c1 = new PlanCbrCase("p", "s", "R", 0.9, Map.of(), List.of(aTrace, bTrace1, bTrace2));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(c1, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
        }

        @Test
        void unknownOutcomeTreatedAsFailure() {
            var unknownTrace = new PlanTrace("b", "analysis", "agent-a", "UNKNOWN_OUTCOME", 1, Map.of());
            var successTrace = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
            var cbrCase = new PlanCbrCase("p", "s", "R", 0.9, Map.of(),
                    List.of(unknownTrace, successTrace));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
        }

        @Test
        void successOnlyWorkerScoresOne() {
            var s1 = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var s2 = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var cbrCase = new PlanCbrCase("p", "s", "R", 0.9, Map.of(), List.of(s1, s2));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void failureOnlyWorkerLosesToSuccessWorker() {
            var failTrace = new PlanTrace("b", "analysis", "agent-a", "FAILURE", 1, Map.of());
            var successTrace = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
            var cbrCase = new PlanCbrCase("p", "s", "R", 0.9, Map.of(),
                    List.of(failTrace, successTrace));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
        }
    }
}
