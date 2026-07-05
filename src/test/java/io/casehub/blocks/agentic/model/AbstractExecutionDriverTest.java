package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.ActivationContext;
import io.casehub.blocks.agentic.activation.ActivationRule;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractExecutionDriverTest {

    @Nested
    class ActivationContextPopulation {

        @Test
        void populatesActivationCountAcrossIterations() {
            var capturedContexts = new ArrayList<ActivationContext<String>>();
            ActivationRule<String> capturingActivation = ctx -> {
                capturedContexts.add(ctx);
                return Uni.createFrom().item(true);
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    capturingActivation,
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(3),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(), "test");

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "ctx").await().indefinitely();

            assertThat(capturedContexts).hasSize(3);
            assertThat(capturedContexts.get(0).activationCount()).isEqualTo(0);
            assertThat(capturedContexts.get(1).activationCount()).isEqualTo(1);
            assertThat(capturedContexts.get(2).activationCount()).isEqualTo(2);
        }

        @Test
        void lastAggregationResultCarriesForwardFromPreviousIteration() {
            var capturedContexts = new ArrayList<ActivationContext<String>>();
            ActivationRule<String> capturingActivation = ctx -> {
                capturedContexts.add(ctx);
                return Uni.createFrom().item(true);
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    capturingActivation,
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(3),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(), "test");

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "ctx").await().indefinitely();

            assertThat(capturedContexts).hasSize(3);
            assertThat(capturedContexts.get(0).lastAggregationResult()).isEmpty();
            assertThat(capturedContexts.get(1).lastAggregationResult()).isPresent();
            assertThat(capturedContexts.get(1).lastAggregationResult().get())
                    .isInstanceOf(AggregationResult.class);
            assertThat(capturedContexts.get(2).lastAggregationResult()).isPresent();
        }

        @Test
        void consecutiveIdleActivationsIncrementsOnSkipAndResetsOnActivation() {
            var capturedContexts = new ArrayList<ActivationContext<String>>();
            var callCount = new int[]{0};
            ActivationRule<String> alternatingActivation = ctx -> {
                capturedContexts.add(ctx);
                boolean activate = callCount[0] != 1;
                callCount[0]++;
                return Uni.createFrom().item(activate);
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    alternatingActivation,
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(3),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(), "test");

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "ctx").await().indefinitely();

            assertThat(capturedContexts.get(0).consecutiveIdleActivations()).isEqualTo(0);
            assertThat(capturedContexts.get(1).consecutiveIdleActivations()).isEqualTo(0);
            assertThat(capturedContexts.get(2).consecutiveIdleActivations()).isEqualTo(1);
        }
    }

    @Nested
    class ExecutionLifecycle {

        @Test
        void callsOnExecutionStartBeforeLoopAndOnExecutionCompleteAfter() {
            var events = new ArrayList<String>();
            var listener = new ExecutionEventListener() {
                @Override
                public void onExecutionStart(ExecutionModel<?> model) {
                    events.add("start");
                }
                @Override
                public void onExecutionComplete(ExecutionResult result) {
                    events.add("complete:" + result.getClass().getSimpleName());
                }
                @Override
                public void onAgentDispatched(AgentRef agent) {
                    events.add("dispatch");
                }
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(listener), "test");

            new OrchestratedDriver<String>().execute(model, "ctx").await().indefinitely();

            assertThat(events.get(0)).isEqualTo("start");
            assertThat(events.get(events.size() - 1)).isEqualTo("complete:Completed");
        }

        @Test
        void callsOnExecutionCompleteEvenOnException() {
            var events = new ArrayList<String>();
            var listener = new ExecutionEventListener() {
                @Override
                public void onExecutionComplete(ExecutionResult result) {
                    events.add("complete");
                }
            };

            var model = new ExecutionModel<String>(
                    ctx -> { throw new RuntimeException("routing exploded"); },
                    new IdentityDecomposition<>(),
                    new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(),
                    FailurePolicy.defaults(),
                    List.of(listener), "test");

            try {
                new OrchestratedDriver<String>().execute(model, "ctx").await().indefinitely();
            } catch (Exception ignored) {}

            assertThat(events).contains("complete");
        }
    }

    @Nested
    class InvokerIntegration {

        @Test
        void usesProvidedInvokerInsteadOfDefaultDispatch() {
            var invocations = new ArrayList<String>();
            AgentInvoker<String> trackingInvoker = (agent, state) -> {
                invocations.add("invoked:" + state);
                return Uni.createFrom().item(AgentResult.success(agent, "from-invoker"));
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "should-not-see")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(), "test");

            var driver = new OrchestratedDriver<>(trackingInvoker);
            var result = driver.execute(model, "mystate").await().indefinitely();

            assertThat(invocations).containsExactly("invoked:mystate");
            assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        }
    }
}
