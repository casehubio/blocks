package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.decomposition.PlannedTask;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.ReplanContext;
import io.casehub.engine.plan.TaskNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HtnExecutorTest {

    private AgentRef successAgent(String name, Object result) {
        return AgentRef.external(name,
                ctx -> CompletableFuture.completedFuture(AgentResult.success(null, result)));
    }

    private AgentRef failAgent(String name, String error) {
        return AgentRef.external(name,
                ctx -> CompletableFuture.completedFuture(AgentResult.failure(null, error)));
    }

    private TaskNode.LeafTask<Object> leaf(String id, String desc, AgentRef agent) {
        return new PlannedTask<>(id, Instant.now(), desc, agent, null);
    }

    @Test
    void executesSequentialPlanToCompletion() {
        var a1 = successAgent("a1", Map.of("step", "1"));
        var a2 = successAgent("a2", Map.of("step", "2"));

        var plan = DagPlan.sequence(List.of(leaf("s1", "step-1", a1), leaf("s2", "step-2", a2)));

        var decomposition = fixedPlanDecomposition(plan);
        var root = new TaskNode.CompoundTask<>("goal", List.of());
        var model = buildModel(decomposition, a1, a2);

        var executor = new HtnExecutor<>(AgentInvoker.defaultInvoker());
        var result = executor.execute(root, model, Map.of());

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void replansOnStepFailure() {
        var fail = failAgent("fail", "connection refused");
        var recovery = successAgent("recovery", Map.of("recovered", true));

        var originalPlan = DagPlan.singleton(leaf("s1", "will-fail", fail));
        var revisedPlan = DagPlan.singleton(leaf("r1", "recovery", recovery));

        var replanCalled = new AtomicBoolean(false);
        var decomposition = new DecompositionStrategy<Object>() {
            @Override
            public DagPlan<TaskNode.LeafTask<Object>> decompose(
                    TaskNode<Object> task, DecompositionContext<Object> ctx) {
                return originalPlan;
            }

            @Override
            public DagPlan<TaskNode.LeafTask<Object>> replan(
                    TaskNode<Object> task, DecompositionContext<Object> ctx,
                    ReplanContext<Object> replanCtx) {
                replanCalled.set(true);
                assertThat(replanCtx.failedStep().stepId()).isEqualTo("node-0");
                assertThat(replanCtx.completedSteps()).isEmpty();
                assertThat(replanCtx.replanCount()).isEqualTo(0);
                return revisedPlan;
            }
        };

        var root = new TaskNode.CompoundTask<>("goal", List.of());
        var model = buildModelWithReplan(decomposition, 2, fail, recovery);

        var executor = new HtnExecutor<>(AgentInvoker.defaultInvoker());
        var result = executor.execute(root, model, Map.of());

        assertThat(replanCalled.get()).isTrue();
        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void replanContextCarriesCompletedSteps() {
        var ok = successAgent("ok", Map.of("done", true));
        var fail = failAgent("fail", "timeout");
        var recovery = successAgent("recovery", Map.of("recovered", true));

        var originalPlan = DagPlan.sequence(List.of(
                leaf("s1", "succeeds", ok), leaf("s2", "fails", fail)));
        var revisedPlan = DagPlan.singleton(leaf("r1", "recovery", recovery));

        var capturedCtx = new java.util.concurrent.atomic.AtomicReference<ReplanContext<Object>>();
        var decomposition = new DecompositionStrategy<Object>() {
            @Override
            public DagPlan<TaskNode.LeafTask<Object>> decompose(
                    TaskNode<Object> task, DecompositionContext<Object> ctx) {
                return originalPlan;
            }

            @Override
            public DagPlan<TaskNode.LeafTask<Object>> replan(
                    TaskNode<Object> task, DecompositionContext<Object> ctx,
                    ReplanContext<Object> replanCtx) {
                capturedCtx.set(replanCtx);
                return revisedPlan;
            }
        };

        var root = new TaskNode.CompoundTask<>("goal", List.of());
        var model = buildModelWithReplan(decomposition, 2, ok, fail, recovery);

        var executor = new HtnExecutor<>(AgentInvoker.defaultInvoker());
        executor.execute(root, model, Map.of());

        var ctx = capturedCtx.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.completedSteps()).hasSize(1);
        assertThat(ctx.completedSteps().get(0).stepId()).isEqualTo("node-0");
        assertThat(ctx.failedStep().stepId()).isEqualTo("node-1");
        assertThat(ctx.failedStep().errorMessage()).isEqualTo("timeout");
    }

    @Test
    void stopsReplanningAfterMaxReplans() {
        var fail = failAgent("fail", "always fails");
        var failPlan = DagPlan.singleton(leaf("s1", "will-fail", fail));

        var replanCount = new AtomicInteger(0);
        var decomposition = new DecompositionStrategy<Object>() {
            @Override
            public DagPlan<TaskNode.LeafTask<Object>> decompose(
                    TaskNode<Object> task, DecompositionContext<Object> ctx) {
                return failPlan;
            }

            @Override
            public DagPlan<TaskNode.LeafTask<Object>> replan(
                    TaskNode<Object> task, DecompositionContext<Object> ctx,
                    ReplanContext<Object> replanCtx) {
                replanCount.incrementAndGet();
                return failPlan;
            }
        };

        var root = new TaskNode.CompoundTask<>("goal", List.of());
        var model = buildModelWithReplan(decomposition, 2, fail);

        var executor = new HtnExecutor<>(AgentInvoker.defaultInvoker());
        var result = executor.execute(root, model, Map.of());

        assertThat(replanCount.get()).isEqualTo(2);
        assertThat(result).isInstanceOf(ExecutionResult.Failed.class);
    }

    @Test
    void escalatesOnReplanExhaustion() {
        var fail = failAgent("fail", "fails");
        var failPlan = DagPlan.singleton(leaf("s1", "will-fail", fail));

        var decomposition = new DecompositionStrategy<Object>() {
            @Override
            public DagPlan<TaskNode.LeafTask<Object>> decompose(
                    TaskNode<Object> task, DecompositionContext<Object> ctx) {
                return failPlan;
            }

            @Override
            public DagPlan<TaskNode.LeafTask<Object>> replan(
                    TaskNode<Object> task, DecompositionContext<Object> ctx,
                    ReplanContext<Object> replanCtx) {
                return failPlan;
            }
        };

        var root = new TaskNode.CompoundTask<>("goal", List.of());
        var failurePolicy = new FailurePolicy(
                FailurePolicy.RoutingFailureAction.FAIL,
                FailurePolicy.AggregationFailureAction.FAIL,
                FailurePolicy.defaults().agentRetry(),
                new FailurePolicy.ReplanPolicy(1, FailurePolicy.RoutingFailureAction.ESCALATE));

        var model = new ExecutionModel<>(
                new io.casehub.blocks.agentic.routing.SequentialRouting<>(),
                decomposition,
                new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                new io.casehub.blocks.agentic.aggregation.CollectAll<>(),
                ctx -> new io.casehub.blocks.agentic.termination.TerminationDecision.Continue(),
                () -> List.of(new RoutingCandidate(fail, null)),
                failurePolicy,
                List.of(),
                "htn");

        var executor = new HtnExecutor<>(AgentInvoker.defaultInvoker());
        var result = executor.execute(root, model, Map.of());

        assertThat(result).isInstanceOf(ExecutionResult.Escalated.class);
    }

    @Test
    void noReplanWhenPolicyIsNull() {
        var fail = failAgent("fail", "error");
        var failPlan = DagPlan.singleton(leaf("s1", "will-fail", fail));

        var decomposition = fixedPlanDecomposition(failPlan);
        var root = new TaskNode.CompoundTask<>("goal", List.of());
        var model = buildModel(decomposition, fail);

        var executor = new HtnExecutor<>(AgentInvoker.defaultInvoker());
        var result = executor.execute(root, model, Map.of());

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    // --- helpers ---

    private DecompositionStrategy<Object> fixedPlanDecomposition(
            DagPlan<TaskNode.LeafTask<Object>> plan) {
        return (task, ctx) -> plan;
    }

    private ExecutionModel<Object> buildModel(
            DecompositionStrategy<Object> decomposition, AgentRef... agents) {
        var candidates = java.util.Arrays.stream(agents)
                .map(a -> new RoutingCandidate(a, null)).toList();
        return new ExecutionModel<>(
                new io.casehub.blocks.agentic.routing.SequentialRouting<>(),
                decomposition,
                new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                new io.casehub.blocks.agentic.aggregation.CollectAll<>(),
                ctx -> new io.casehub.blocks.agentic.termination.TerminationDecision.Continue(),
                () -> candidates,
                FailurePolicy.defaults(),
                List.of(),
                "htn");
    }

    private ExecutionModel<Object> buildModelWithReplan(
            DecompositionStrategy<Object> decomposition, int maxReplans, AgentRef... agents) {
        var candidates = java.util.Arrays.stream(agents)
                .map(a -> new RoutingCandidate(a, null)).toList();
        var failurePolicy = new FailurePolicy(
                FailurePolicy.RoutingFailureAction.FAIL,
                FailurePolicy.AggregationFailureAction.FAIL,
                FailurePolicy.defaults().agentRetry(),
                new FailurePolicy.ReplanPolicy(maxReplans, FailurePolicy.RoutingFailureAction.FAIL));
        return new ExecutionModel<>(
                new io.casehub.blocks.agentic.routing.SequentialRouting<>(),
                decomposition,
                new io.casehub.blocks.agentic.activation.OnExplicitDispatch<>(),
                new io.casehub.blocks.agentic.aggregation.CollectAll<>(),
                ctx -> new io.casehub.blocks.agentic.termination.TerminationDecision.Continue(),
                () -> candidates,
                failurePolicy,
                List.of(),
                "htn");
    }
}
