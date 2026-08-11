package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.model.ExecutionEventListener;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.OrchestratedDriver;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.engine.plan.DagNode;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.ReplanContext;
import io.casehub.engine.plan.TaskNode;


import java.util.ArrayList;
import java.util.List;

public class HtnExecutor<T> {

    private static final System.Logger LOG = System.getLogger(HtnExecutor.class.getName());

    private final AgentInvoker<T> invoker;

    public HtnExecutor(AgentInvoker<T> invoker) {
        this.invoker = invoker;
    }

    public ExecutionResult execute(TaskNode<T> rootTask, ExecutionModel<T> baseModel, T context) {
        var plan = decompose(rootTask, baseModel, context);
        return executeWithReplan(rootTask, baseModel, plan, context, 0);
    }

    private DagPlan<TaskNode.LeafTask<T>> decompose(
            TaskNode<T> task, ExecutionModel<T> model, T state) {
        if (task instanceof TaskNode.LeafTask<T> leaf) {
            return DagPlan.singleton(leaf);
        }
        var agents = model.candidateSupplier().get();
        var ctx = new AgenticDecompositionContext<>(state, agents, 0);
        return model.decomposition().decompose(task, ctx);
    }

    private ExecutionResult executeWithReplan(
            TaskNode<T> rootTask, ExecutionModel<T> baseModel,
            DagPlan<TaskNode.LeafTask<T>> plan, T context, int replanCount) {

        var sortedNodes = plan.topologicalSort();
        var candidates = sortedNodes.stream()
                .map(n -> new RoutingCandidate((AgentRef) n.task().executor(), null))
                .toList();

        var replanPolicy = baseModel.failurePolicy().replanPolicy();
        var collector = new ResultCollector();
        var localModel = buildLocalModel(baseModel, candidates, replanPolicy != null, collector);

        var driver = new OrchestratedDriver<T>(invoker);
        var result = driver.execute(localModel, context).await().indefinitely();

        if (!(result instanceof ExecutionResult.Failed failed)) {
            return result;
        }

        if (replanPolicy == null || replanCount >= replanPolicy.maxReplans()) {
            if (replanPolicy != null
                    && replanPolicy.fallbackAction() == FailurePolicy.RoutingFailureAction.ESCALATE) {
                return new ExecutionResult.Escalated(
                        "Re-plan attempts exhausted (" + replanPolicy.maxReplans() + "): " + failed.reason());
            }
            return result;
        }

        var replanCtx = buildReplanContext(sortedNodes, collector.results, plan, replanCount);

        try {
            var agents = baseModel.candidateSupplier().get();
            var decompCtx = new AgenticDecompositionContext<>(context, agents, 0);
            var newPlan = baseModel.decomposition()
                    .replan(rootTask, decompCtx, replanCtx);

            LOG.log(System.Logger.Level.INFO,
                    "Re-plan {0}/{1} produced {2} steps",
                    replanCount + 1, replanPolicy.maxReplans(), newPlan.nodes().size());

            return executeWithReplan(rootTask, baseModel, newPlan, context, replanCount + 1);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Re-plan failed", e);
            if (replanPolicy.fallbackAction() == FailurePolicy.RoutingFailureAction.ESCALATE) {
                return new ExecutionResult.Escalated("Re-plan failed: " + e.getMessage());
            }
            return result;
        }
    }

    private ExecutionModel<T> buildLocalModel(
            ExecutionModel<T> base, List<RoutingCandidate> candidates,
            boolean failOnError, ResultCollector collector) {
        var termination = buildTermination(candidates.size(), failOnError);

        var listeners = new ArrayList<>(base.listeners());
        listeners.add(collector);

        return new ExecutionModel<>(
                new io.casehub.blocks.agentic.routing.SequentialRouting<>(),
                base.decomposition(), base.activation(),
                base.aggregation(), termination, () -> candidates,
                base.failurePolicy(), listeners, base.task(), base.patternType());}

    private io.casehub.blocks.agentic.termination.TerminationCondition<T> buildTermination(
            int agentCount, boolean failOnError) {
        return ctx -> {
            if (failOnError) {
                var failed = ctx.results().stream()
                        .filter(r -> r.status() != AgentResult.AgentResultStatus.SUCCESS)
                        .findFirst();
                if (failed.isPresent()) {
                    return io.smallrye.mutiny.Uni.createFrom().item(
                            (TerminationDecision) new TerminationDecision.Failed(
                                    "Step failed: " + failed.get().output()));
                }
            }
            if (ctx.iterationCount() >= agentCount) {
                return io.smallrye.mutiny.Uni.createFrom().item(
                        (TerminationDecision) new TerminationDecision.Complete(ctx.results()));
            }
            return io.smallrye.mutiny.Uni.createFrom().item(
                    (TerminationDecision) TerminationDecision.Continue.INSTANCE);
        };
    }

    private ReplanContext<T> buildReplanContext(
            List<DagNode<TaskNode.LeafTask<T>>> sortedNodes,
            List<AgentResult> results,
            DagPlan<TaskNode.LeafTask<T>> plan,
            int replanCount) {

        var completed = new ArrayList<ReplanContext.CompletedStep>();
        ReplanContext.FailedStep failedStep = null;

        for (int i = 0; i < results.size(); i++) {
            var agentResult = results.get(i);
            var nodeId = i < sortedNodes.size() ? sortedNodes.get(i).id() : "unknown-" + i;

            if (agentResult.status() == AgentResult.AgentResultStatus.SUCCESS) {
                completed.add(new ReplanContext.CompletedStep(
                        nodeId, agentResult.output(), agentResult.duration()));
            } else if (failedStep == null) {
                failedStep = new ReplanContext.FailedStep(
                        nodeId,
                        agentResult.output() != null ? agentResult.output().toString() : "unknown error",
                        null, 0);
            }
        }

        if (failedStep == null) {
            failedStep = new ReplanContext.FailedStep("unknown", "No explicit failure found", null, 0);
        }

        return new ReplanContext<>(completed, failedStep, plan, replanCount);
    }

    static class ResultCollector implements ExecutionEventListener {
        final List<AgentResult> results = new ArrayList<>();

        @Override
        public void onAgentResult(AgentResult result) {
            results.add(result);
        }
    }
}
