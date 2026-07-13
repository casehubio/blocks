package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingSupportTest {

    private AgentCandidate candidate(String workerId, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(workerId)
                .name(workerId)
                .slot("agent")
                .tenancyId("test")
                .capabilities(List.of(AgentCapability.builder().name("analysis").build()))
                .briefing(briefing)
                .build();
        return new AgentCandidate(workerId, Set.of("analysis"), 0, AgentHealth.READY, descriptor, new MatchDegree.None());
    }

    private AgentCandidate candidateNoDescriptor(String workerId) {
        return new AgentCandidate(workerId, Set.of("analysis"), 0, AgentHealth.READY, null, new MatchDegree.None());
    }

    @Nested
    class PromptBuilding {
        @Test
        void includesCapabilityAndContext() {
            var prompt = RoutingSupport.buildUserPrompt(
                    "data-analysis", "Quarterly report data",
                    List.of(candidate("analyst", "Analyses data")));
            assertThat(prompt).contains("data-analysis").contains("Quarterly report data");
        }

        @Test
        void includesCandidateBriefingAndCapabilities() {
            var prompt = RoutingSupport.buildUserPrompt(
                    "review", null,
                    List.of(candidate("reviewer", "Reviews code for security")));
            assertThat(prompt).contains("reviewer").contains("Reviews code for security").contains("analysis");
        }

        @Test
        void handlesCandidateWithoutDescriptor() {
            var prompt = RoutingSupport.buildUserPrompt(
                    "task", null,
                    List.of(candidateNoDescriptor("plain-worker")));
            assertThat(prompt).contains("plain-worker");
        }
    }

    @Nested
    class ResponseParsing {
        @Test
        void extractsAgentNameFromValidJson() {
            assertThat(RoutingSupport.extractAgentName(
                    "{\"agent\": \"reviewer\", \"reason\": \"best fit\"}"))
                    .isEqualTo("reviewer");
        }

        @Test
        void returnsNullForMalformedResponse() {
            assertThat(RoutingSupport.extractAgentName("not json")).isNull();
        }

        @Test
        void returnsNullForNullInput() {
            assertThat(RoutingSupport.extractAgentName(null)).isNull();
        }

        @Test
        void extractsAgentNameFromMarkdownWrappedJson() {
            assertThat(RoutingSupport.extractAgentName(
                    "```json\n{\"agent\": \"reviewer\", \"reason\": \"best fit\"}\n```"))
                    .isEqualTo("reviewer");
        }

        @Test
        void extractsAgentNameWithEscapedQuotes() {
            assertThat(RoutingSupport.extractAgentName(
                    "{\"agent\": \"agent-with-\\\"quotes\\\"\", \"reason\": \"ok\"}"))
                    .isEqualTo("agent-with-\"quotes\"");
        }

        @Test
        void returnsNullForEmptyString() {
            assertThat(RoutingSupport.extractAgentName("")).isNull();
        }

        @Test
        void returnsNullForMissingAgentField() {
            assertThat(RoutingSupport.extractAgentName("{\"reason\": \"no agent\"}")).isNull();
        }

        @Test
        void returnsNullForNumericAgentValue() {
            assertThat(RoutingSupport.extractAgentName("{\"agent\": 42}")).isNull();
        }

        @Test
        void parsesSelectionToWorkerId() {
            var candidates = List.of(
                    candidate("agent-a", "Does A"),
                    candidate("agent-b", "Does B"));
            var result = RoutingSupport.parseSelection(
                    "{\"agent\": \"agent-b\", \"reason\": \"better fit\"}", candidates);
            assertThat(result).isEqualTo("agent-b");
        }

        @Test
        void returnsNullForUnknownAgent() {
            var candidates = List.of(candidate("agent-a", "Does A"));
            var result = RoutingSupport.parseSelection(
                    "{\"agent\": \"unknown\", \"reason\": \"?\"}", candidates);
            assertThat(result).isNull();
        }

        @Test
        void returnsNullForDoneAgent() {
            var candidates = List.of(candidate("agent-a", "Does A"));
            var result = RoutingSupport.parseSelection(
                    "{\"agent\": \"done\", \"reason\": \"complete\"}", candidates);
            assertThat(result).isNull();
        }
    }

    @Nested
    class InvokeAndCollect {
        @Test
        void collectsTextDeltaEvents() {
            var provider = mock(AgentProvider.class);
            when(provider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().items(
                            new AgentEvent.TextDelta("{\"agent\""),
                            new AgentEvent.TextDelta(": \"reviewer\", \"reason\": \"ok\"}")));
            var result = RoutingSupport.invokeAndCollect(provider, "system", "user");
            assertThat(result).isEqualTo("{\"agent\": \"reviewer\", \"reason\": \"ok\"}");
        }

        @Test
        void returnsNullOnProviderFailure() {
            var provider = mock(AgentProvider.class);
            when(provider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM down")));
            var result = RoutingSupport.invokeAndCollect(provider, "system", "user");
            assertThat(result).isNull();
        }
    }

    @Nested
    class TrustFiltering {

        @Test
        void proceedsWithAllCandidatesWhenNoTrustServices() {
            var candidates = List.of(
                    candidateNoDescriptor("agent-a"),
                    candidateNoDescriptor("agent-b"));
            var context = new AgentRoutingContext(
                    UUID.randomUUID(), "analysis", NullNode.instance, "test", List.of());

            var outcome = RoutingSupport.applyTrustFilter(
                    null, null, null, context, candidates);

            assertThat(outcome).isInstanceOf(RoutingSupport.TrustFilterOutcome.Proceed.class);
            var proceed = (RoutingSupport.TrustFilterOutcome.Proceed) outcome;
            assertThat(proceed.eligible()).hasSize(2);
            assertThat(proceed.classified()).isNull();
        }

        @Test
        void decidesOnBootstrapEscalation() {
            var classifier = mock(TrustCandidateClassifier.class);
            var scoreSource = mock(TrustScoreSource.class);
            var policyProvider = mock(TrustRoutingPolicyProvider.class);
            var policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), true, null, Set.of());
            when(policyProvider.forCapability("analysis")).thenReturn(policy);

            var candidate = candidateNoDescriptor("bootstrap-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            candidate, TrustCandidateClassifier.Phase.BOOTSTRAP,
                            OptionalDouble.empty(), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);

            var context = new AgentRoutingContext(
                    UUID.randomUUID(), "analysis", NullNode.instance, "test", List.of());

            var outcome = RoutingSupport.applyTrustFilter(
                    classifier, scoreSource, policyProvider, context, List.of(candidate));

            assertThat(outcome).isInstanceOf(RoutingSupport.TrustFilterOutcome.Decided.class);
            var decided = (RoutingSupport.TrustFilterOutcome.Decided) outcome;
            assertThat(decided.assignment()).isInstanceOf(RoutingResult.Escalated.class);
        }
    }
}
