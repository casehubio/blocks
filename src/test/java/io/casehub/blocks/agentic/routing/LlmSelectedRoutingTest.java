package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.worker.api.Worker;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmSelectedRoutingTest {

    @Mock AgentProvider agentProvider;

    private RoutingCandidate candidate(String name, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(name)
                .name(name)
                .slot("agent")
                .tenancyId("test")
                .capabilities(List.of(
                        AgentCapability.builder().name("default").build()))
                .briefing(briefing)
                .build();
        var worker = Worker.builder().name(name).capabilityName("default").noFunction().build();
        return new RoutingCandidate(AgentRef.worker(worker), descriptor);
    }

    private void agentReturns(String text) {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(text)));
    }

    @Nested
    class AgentSelection {
        @Test
        void selectsAgentNamedByLlm() {
            var reviewer = candidate("reviewer", "Reviews code for bugs");
            var implementor = candidate("implementor", "Writes code");
            agentReturns("{\"agent\": \"reviewer\", \"reason\": \"Code review task\"}");

            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("Review this code", List.of(reviewer, implementor), "state");
            var decision = routing.route(ctx).await().indefinitely();

            assertThat(decision).isInstanceOf(RoutingDecision.Selected.class);
            var selected = (RoutingDecision.Selected) decision;
            assertThat(selected.agents()).hasSize(1);
            assertThat(selected.agents().get(0)).isEqualTo(reviewer.ref());
        }

        @Test
        void selectsSecondAgent() {
            var reviewer = candidate("reviewer", "Reviews code");
            var implementor = candidate("implementor", "Writes code");
            agentReturns("{\"agent\": \"implementor\", \"reason\": \"Implementation task\"}");

            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("Implement feature X", List.of(reviewer, implementor), "state");
            var decision = routing.route(ctx).await().indefinitely();

            assertThat(decision).isInstanceOf(RoutingDecision.Selected.class);
            assertThat(((RoutingDecision.Selected) decision).agents().get(0))
                    .isEqualTo(implementor.ref());
        }
    }

    @Nested
    class UnresolvableCases {
        @Test
        void returnsUnresolvableWhenLlmReturnsUnknownAgent() {
            var reviewer = candidate("reviewer", "Reviews code");
            agentReturns("{\"agent\": \"unknown-agent\", \"reason\": \"Not sure\"}");

            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("task", List.of(reviewer), "state");
            var decision = routing.route(ctx).await().indefinitely();

            assertThat(decision).isInstanceOf(RoutingDecision.Unresolvable.class);
            assertThat(((RoutingDecision.Unresolvable) decision).reason())
                    .contains("unknown-agent");
        }

        @Test
        void returnsUnresolvableWhenLlmReturnsDone() {
            var reviewer = candidate("reviewer", "Reviews code");
            agentReturns("{\"agent\": \"done\", \"reason\": \"Task complete\"}");

            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("task", List.of(reviewer), "state");
            var decision = routing.route(ctx).await().indefinitely();

            assertThat(decision).isInstanceOf(RoutingDecision.Unresolvable.class);
            assertThat(((RoutingDecision.Unresolvable) decision).reason())
                    .containsIgnoringCase("complete");
        }

        @Test
        void returnsUnresolvableWhenAgentProviderThrows() {
            when(agentProvider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM unavailable")));

            var reviewer = candidate("reviewer", "Reviews code");
            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("task", List.of(reviewer), "state");
            var decision = routing.route(ctx).await().indefinitely();

            assertThat(decision).isInstanceOf(RoutingDecision.Unresolvable.class);
            assertThat(((RoutingDecision.Unresolvable) decision).reason())
                    .contains("LLM unavailable");
        }

        @Test
        void returnsUnresolvableWhenResponseUnparseable() {
            agentReturns("this is not json at all");

            var reviewer = candidate("reviewer", "Reviews code");
            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("task", List.of(reviewer), "state");
            var decision = routing.route(ctx).await().indefinitely();

            assertThat(decision).isInstanceOf(RoutingDecision.Unresolvable.class);
        }
    }

    @Nested
    class NullDescriptors {
        @Test
        void worksWithCandidatesWithoutDescriptors() {
            var worker = Worker.builder().name("plain-worker").capabilityName("default").noFunction().build();
            var noDescriptor = new RoutingCandidate(AgentRef.worker(worker), null);
            var withDescriptor = candidate("reviewer", "Reviews code");
            agentReturns("{\"agent\": \"reviewer\", \"reason\": \"Has capabilities\"}");

            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("task", List.of(noDescriptor, withDescriptor), "state");
            var decision = routing.route(ctx).await().indefinitely();

            assertThat(decision).isInstanceOf(RoutingDecision.Selected.class);
            assertThat(((RoutingDecision.Selected) decision).agents().get(0))
                    .isEqualTo(withDescriptor.ref());
        }

        @Test
        void canSelectCandidateWithoutDescriptor() {
            var worker = Worker.builder().name("plain-worker").capabilityName("default").noFunction().build();
            var noDescriptor = new RoutingCandidate(AgentRef.worker(worker), null);
            agentReturns("{\"agent\": \"plain-worker\", \"reason\": \"Only option\"}");

            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("task", List.of(noDescriptor), "state");
            var decision = routing.route(ctx).await().indefinitely();

            assertThat(decision).isInstanceOf(RoutingDecision.Selected.class);
        }
    }

    @Nested
    class PromptConstruction {
        @Test
        void includesTaskDescriptionInPrompt() {
            var reviewer = candidate("reviewer", "Reviews code for bugs");
            agentReturns("{\"agent\": \"reviewer\", \"reason\": \"match\"}");

            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("Review the authentication module",
                    List.of(reviewer), "state");
            routing.route(ctx).await().indefinitely();

            var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
            verify(agentProvider).invoke(captor.capture());
            assertThat(captor.getValue().userPrompt())
                    .contains("Review the authentication module");
        }

        @Test
        void includesAgentDescriptionsInPrompt() {
            var reviewer = candidate("reviewer", "Reviews code for bugs and style issues");
            agentReturns("{\"agent\": \"reviewer\", \"reason\": \"match\"}");

            var routing = new LlmSelectedRouting<String>(agentProvider);
            var ctx = new RoutingContext<>("task", List.of(reviewer), "state");
            routing.route(ctx).await().indefinitely();

            var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
            verify(agentProvider).invoke(captor.capture());
            assertThat(captor.getValue().userPrompt())
                    .contains("reviewer")
                    .contains("Reviews code for bugs and style issues");
        }
    }
}
