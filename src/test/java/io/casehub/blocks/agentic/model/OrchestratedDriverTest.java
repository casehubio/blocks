package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratedDriverTest {

    @Nested
    class SingleAgentLoop {

        @Test
        void executesAgentAndTerminatesAfterMaxIterations() {
            var callCount = new AtomicInteger(0);
            var agent = AgentRef.external((Object input) -> {
                callCount.incrementAndGet();
                return CompletableFuture.completedFuture(
                        AgentResult.success(null, "result-" + callCount.get()));
            });
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(3),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of());

            var driver = new OrchestratedDriver<String>();
            var result = driver.execute(model, "initial").await().indefinitely();

            assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
            assertThat(callCount.get()).isEqualTo(3);
        }

        @Test
        void returnsCompletedResultFromTermination() {
            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(
                            AgentResult.success(null, "output")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of());

            var driver = new OrchestratedDriver<String>();
            var result = driver.execute(model, "ctx").await().indefinitely();

            assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
            var completed = (ExecutionResult.Completed) result;
            assertThat(completed.result()).isEqualTo("Max iterations reached");
        }

        @Test
        void stateIsCompleteAfterSuccessfulExecution() {
            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(
                            AgentResult.success(null, "done")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of());

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "ctx").await().indefinitely();

            assertThat(driver.state()).isInstanceOf(ExecutionState.Complete.class);
        }
    }

    @Nested
    class EventListenerNotification {

        @Test
        void notifiesListenerOnRoutingAndTermination() {
            var events = new ArrayList<String>();
            var listener = new ExecutionEventListener() {
                @Override
                public void onRoutingDecision(RoutingDecision decision,
                                              List<RoutingCandidate> candidates) {
                    events.add("routed");
                }

                @Override
                public void onTermination(TerminationDecision decision) {
                    events.add("terminated");
                }
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(listener));

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "state").await().indefinitely();

            assertThat(events).contains("routed", "terminated");
        }

        @Test
        void notifiesListenerOnAgentDispatchAndResult() {
            var events = new ArrayList<String>();
            var listener = new ExecutionEventListener() {
                @Override
                public void onAgentDispatched(AgentRef agent) {
                    events.add("dispatched");
                }

                @Override
                public void onAgentResult(AgentResult result) {
                    events.add("result:" + result.output());
                }

                @Override
                public void onAggregation(AggregationResult result) {
                    events.add("aggregated");
                }
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "value")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(listener));

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "state").await().indefinitely();

            assertThat(events).containsExactly("dispatched", "result:value", "aggregated");
        }

        @Test
        void notifiesStateTransitions() {
            var transitions = new ArrayList<String>();
            var listener = new ExecutionEventListener() {
                @Override
                public void onStateTransition(ExecutionState from, ExecutionState to) {
                    transitions.add(from.getClass().getSimpleName()
                            + "->" + to.getClass().getSimpleName());
                }
            };

            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(listener));

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "ctx").await().indefinitely();

            assertThat(transitions).contains(
                    "Idle->Running",
                    "Running->WaitingForAgent",
                    "WaitingForAgent->Complete");
        }
    }

    @Nested
    class UnresolvableRouting {

        @Test
        void failsWhenNoCandidateMatchesAndPolicyIsFail() {
            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> false),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(5),
                    () -> List.of(new RoutingCandidate(
                            AgentRef.external((Object i) ->
                                    CompletableFuture.completedFuture(
                                            AgentResult.success(null, "x"))), null)),
                    FailurePolicy.defaults(),
                    List.of());

            var driver = new OrchestratedDriver<String>();
            var result = driver.execute(model, "state").await().indefinitely();

            assertThat(result).isInstanceOf(ExecutionResult.Failed.class);
            var failed = (ExecutionResult.Failed) result;
            assertThat(failed.reason()).contains("No candidate matched");
        }

        @Test
        void escalatesWhenPolicyIsEscalate() {
            var policy = new FailurePolicy(
                    FailurePolicy.RoutingFailureAction.ESCALATE,
                    FailurePolicy.AggregationFailureAction.FAIL,
                    new FailurePolicy.AgentRetryPolicy(3,
                            java.time.Duration.ofSeconds(1),
                            FailurePolicy.BackoffStrategy.FIXED,
                            FailurePolicy.AgentFailureAction.FAIL));

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> false),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(5),
                    () -> List.of(new RoutingCandidate(
                            AgentRef.external((Object i) ->
                                    CompletableFuture.completedFuture(
                                            AgentResult.success(null, "x"))), null)),
                    policy,
                    List.of());

            var driver = new OrchestratedDriver<String>();
            var result = driver.execute(model, "state").await().indefinitely();

            assertThat(result).isInstanceOf(ExecutionResult.Escalated.class);
        }

        @Test
        void stateIsFaultedAfterRoutingFailure() {
            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> false),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(5),
                    () -> List.of(new RoutingCandidate(
                            AgentRef.external((Object i) ->
                                    CompletableFuture.completedFuture(
                                            AgentResult.success(null, "x"))), null)),
                    FailurePolicy.defaults(),
                    List.of());

            var driver = new OrchestratedDriver<String>();
            driver.execute(model, "state").await().indefinitely();

            assertThat(driver.state()).isInstanceOf(ExecutionState.Faulted.class);
        }
    }

    @Nested
    class AgentFailure {

        @Test
        void handlesAgentExceptionGracefully() {
            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.<AgentResult>failedFuture(
                            new RuntimeException("agent crashed")));
            var candidate = new RoutingCandidate(agent, null);

            var failureEvents = new ArrayList<String>();
            var listener = new ExecutionEventListener() {
                @Override
                public void onFailure(AgentRef a, Throwable cause) {
                    failureEvents.add("failure:" + cause.getMessage());
                }
            };

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(1),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of(listener));

            var driver = new OrchestratedDriver<String>();
            var result = driver.execute(model, "ctx").await().indefinitely();

            // Agent failure is captured as a result, not an exception
            assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
            assertThat(failureEvents).hasSize(1);
            assertThat(failureEvents.get(0)).contains("agent crashed");
        }
    }

    @Nested
    class Cancellation {

        @Test
        void cancelStopsExecutionLoop() {
            var callCount = new AtomicInteger(0);
            var driver = new OrchestratedDriver<String>();

            var agent = AgentRef.external((Object input) -> {
                int count = callCount.incrementAndGet();
                if (count >= 2) {
                    driver.cancel().await().indefinitely();
                }
                return CompletableFuture.completedFuture(
                        AgentResult.success(null, "iteration-" + count));
            });
            var candidate = new RoutingCandidate(agent, null);

            var model = new ExecutionModel<String>(
                    new FirstMatchRouting<>(c -> true),
                    new IdentityDecomposition<>(),
                    new OnExplicitDispatch<>(),
                    new PassThrough<>(),
                    new MaxIterationsTermination<>(100),
                    () -> List.of(candidate),
                    FailurePolicy.defaults(),
                    List.of());

            var result = driver.execute(model, "ctx").await().indefinitely();

            assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
            assertThat(callCount.get()).isLessThanOrEqualTo(3);
        }
    }
}
