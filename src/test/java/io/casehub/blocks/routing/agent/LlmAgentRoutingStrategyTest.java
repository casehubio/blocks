package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.RoutingPromptAssembler;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.MatchDegree;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
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
class LlmAgentRoutingStrategyTest {

    @Mock AgentProvider agentProvider;
    @Mock TrustCandidateClassifier classifier;
    @Mock TrustScoreSource scoreSource;
    @Mock TrustRoutingPolicyProvider policyProvider;

    private AgentRoutingContext context(String capability) {
        return new AgentRoutingContext(UUID.randomUUID(), capability, NullNode.instance, "test-tenant", List.of());
    }

    private AgentCandidate candidate(String id) {
        return candidate(id, "Does " + id);
    }

    private AgentCandidate candidate(String id, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(id).name(id).slot("agent").tenancyId("test")
                .capabilities(List.of(AgentCapability.builder().name("analysis").build()))
                .briefing(briefing).build();
        return new AgentCandidate(id, Set.of("analysis"), 0, AgentHealth.READY, descriptor, new MatchDegree.None());
    }

    private void agentReturns(String text) {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(text)));
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
    void idIsLlm() {
        var strategy = new LlmAgentRoutingStrategy(
                presentInstance(agentProvider), absentInstance(), absentInstance(), absentInstance(),
                new RoutingPromptAssembler(List.of()));
        assertThat(strategy.id()).isEqualTo("llm");
    }

    @Nested
    class WithoutTrust {
        private LlmAgentRoutingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new LlmAgentRoutingStrategy(
                    presentInstance(agentProvider), absentInstance(), absentInstance(), absentInstance(),
                    new RoutingPromptAssembler(List.of()));
        }

        @Test
        void selectsAgentByLlmResponse() {
            agentReturns("{\"agent\": \"agent-b\", \"reason\": \"best fit\"}");
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b")));
            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
        }

        @Test
        void emptyCandidatesReturnsUnresolvable() {
            var result = strategy.select(context("analysis"), List.of());
            assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
        }

        @Test
        void unknownAgentReturnsUnresolvable() {
            agentReturns("{\"agent\": \"ghost\", \"reason\": \"?\"}");
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a")));
            assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
        }

        @Test
        void unparseableResponseReturnsUnresolvable() {
            agentReturns("not json");
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a")));
            assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
        }

        @Test
        void providerFailureReturnsUnresolvable() {
            when(agentProvider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM down")));
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a")));
            assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
        }

        @Test
        void neverReturnsEscalateWithoutTrust() {
            agentReturns("{\"agent\": \"agent-a\", \"reason\": \"ok\"}");
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a")));
            assertThat(result).isNotInstanceOf(RoutingResult.Escalated.class);
        }

        @Test
        void nullNodeCaseContextNotSentAsStringNull() {
            agentReturns("{\"agent\": \"agent-a\", \"reason\": \"ok\"}");
            var nullContext = new AgentRoutingContext(
                    UUID.randomUUID(), "analysis", NullNode.instance, "test-tenant", List.of());
            var result = strategy.select(nullContext,
                    List.of(candidate("agent-a")));
            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
        }

        @Test
        void emptyTextDeltaStreamReturnsUnresolvable() {
            when(agentProvider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().empty());
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a")));
            assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
        }
    }

    @Nested
    class WithTrust {
        private LlmAgentRoutingStrategy strategy;
        private TrustRoutingPolicy policy;

        @BeforeEach
        void setUp() {
            policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), false, null, Set.of(), 0.0);
            lenient().when(policyProvider.forCapability(anyString())).thenReturn(policy);
            strategy = new LlmAgentRoutingStrategy(
                    presentInstance(agentProvider), presentInstance(classifier),
                    presentInstance(scoreSource), presentInstance(policyProvider),
                    new RoutingPromptAssembler(List.of()));
        }

        @Test
        void excludedCandidatesFilteredBeforeLlm() {
            var good = candidate("qualified-agent");
            var bad = candidate("excluded-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            good, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0),
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            bad, TrustCandidateClassifier.Phase.EXCLUDED_PHASE2B,
                            OptionalDouble.of(0.3), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            agentReturns("{\"agent\": \"qualified-agent\", \"reason\": \"only option\"}");

            var result = strategy.select(context("analysis"),
                    List.of(good, bad));
            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("qualified-agent");
        }

        @Test
        void borderlineOnlyPoolEscalates() {
            var a = candidate("borderline-a");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            a, TrustCandidateClassifier.Phase.BORDERLINE,
                            OptionalDouble.of(0.65), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            when(classifier.decide(any(), any(), eq("analysis")))
                    .thenReturn(RoutingResult.escalate("analysis", EscalationReason.BORDERLINE_STALEMATE, "all borderline"));

            var result = strategy.select(context("analysis"), List.of(a));
            assertThat(result).isInstanceOf(RoutingResult.Escalated.class);
            assertThat(((RoutingResult.Escalated) result).escalationReason())
                    .isEqualTo(EscalationReason.BORDERLINE_STALEMATE);
        }

        @Test
        void bootstrapGuardEscalatesWhenPolicyRequires() {
            var bootstrapPolicy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), true, null, Set.of(), 0.0);
            when(policyProvider.forCapability("analysis")).thenReturn(bootstrapPolicy);
            var a = candidate("bootstrap-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            a, TrustCandidateClassifier.Phase.BOOTSTRAP,
                            OptionalDouble.empty(), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(bootstrapPolicy), eq(scoreSource)))
                    .thenReturn(classified);

            var result = strategy.select(context("analysis"), List.of(a));
            assertThat(result).isInstanceOf(RoutingResult.Escalated.class);
            assertThat(((RoutingResult.Escalated) result).escalationReason())
                    .isEqualTo(EscalationReason.NO_QUALIFIED_AGENT);
        }

        @Test
        void qualifiedPlusBorderlineFiltersOnlyBorderline() {
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
            agentReturns("{\"agent\": \"qual-agent\", \"reason\": \"only eligible\"}");

            var result = strategy.select(context("analysis"),
                    List.of(qualified, borderline));
            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("qual-agent");
        }

        @Test
        void qualifiedPlusBootstrapGuardOnFiltersBootstrap() {
            var bootstrapPolicy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), true, null, Set.of(), 0.0);
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
            agentReturns("{\"agent\": \"qual-agent\", \"reason\": \"qualified\"}");

            var result = strategy.select(context("analysis"),
                    List.of(qualified, bootstrap));
            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("qual-agent");
        }

        @Test
        void qualifiedPlusBootstrapGuardOffPassesBothThrough() {
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
            agentReturns("{\"agent\": \"boot-agent\", \"reason\": \"picked bootstrap\"}");

            var result = strategy.select(context("analysis"),
                    List.of(qualified, bootstrap));
            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("boot-agent");
        }

        @Test
        void llmFailureFallsBackToClassifierDecideWhenTrustPresent() {
            var qualified = candidate("qual-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            when(agentProvider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM down")));
            when(classifier.decide(any(), any(), eq("analysis")))
                    .thenReturn(RoutingResult.assigned("qual-agent", "classifier fallback"));

            var result = strategy.select(context("analysis"),
                    List.of(qualified));
            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            verify(classifier).decide(any(), any(), eq("analysis"));
        }

        @Test
        void unparseableLlmResponseFallsBackToClassifierDecideWhenTrustPresent() {
            var qualified = candidate("qual-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            agentReturns("not json at all");
            when(classifier.decide(any(), any(), eq("analysis")))
                    .thenReturn(RoutingResult.assigned("qual-agent", "classifier fallback"));

            var result = strategy.select(context("analysis"),
                    List.of(qualified));
            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            verify(classifier).decide(any(), any(), eq("analysis"));
        }
    }

    @Nested
    class InertWhenNoProvider {
        @Test
        void returnsUnresolvableWhenAgentProviderAbsent() {
            var strategy = new LlmAgentRoutingStrategy(
                    absentInstance(), absentInstance(), absentInstance(), absentInstance(),
                    new RoutingPromptAssembler(List.of()));
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a")));
            assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
        }
    }

    @Nested
    class WithPromptEnrichment {
        @Test
        void assemblerOutputAppendedToPrompt() {
            var assembler = mock(RoutingPromptAssembler.class);
            when(assembler.assemble(any(), any())).thenReturn("Historical context: 3 cases");
            agentReturns("{\"agent\": \"agent-a\", \"reason\": \"best\"}");

            var strategy = new LlmAgentRoutingStrategy(
                    presentInstance(agentProvider), absentInstance(),
                    absentInstance(), absentInstance(), assembler);

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a")));

            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            // Verify the prompt passed to agentProvider includes the enrichment
            var configCaptor = org.mockito.ArgumentCaptor.forClass(AgentSessionConfig.class);
            verify(agentProvider).invoke(configCaptor.capture());
            assertThat(configCaptor.getValue().userPrompt())
                    .contains("Historical context: 3 cases");
        }

        @Test
        void assemblerReturnsNull_promptUnchanged() {
            var assembler = mock(RoutingPromptAssembler.class);
            when(assembler.assemble(any(), any())).thenReturn(null);
            agentReturns("{\"agent\": \"agent-a\", \"reason\": \"best\"}");

            var strategy = new LlmAgentRoutingStrategy(
                    presentInstance(agentProvider), absentInstance(),
                    absentInstance(), absentInstance(), assembler);

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a")));

            assertThat(result).isInstanceOf(RoutingResult.Selected.class);
            var configCaptor = org.mockito.ArgumentCaptor.forClass(AgentSessionConfig.class);
            verify(agentProvider).invoke(configCaptor.capture());
            assertThat(configCaptor.getValue().userPrompt())
                    .doesNotContain("Historical context");
        }
    }
}
