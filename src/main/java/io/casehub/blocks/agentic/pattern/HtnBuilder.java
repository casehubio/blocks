package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.engine.plan.DagPlan;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext;
import io.casehub.blocks.agentic.decomposition.StaticDecomposition;
import io.casehub.engine.plan.TaskNode;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.OrchestratedDriver;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

public class HtnBuilder<T> extends AbstractPatternBuilder<T, HtnBuilder<T>> {

    private TaskNode<T> rootTask;

    public HtnBuilder() {
        this.task          = "htn";
        this.routing       = new SequentialRouting<>();
        this.decomposition = new StaticDecomposition<>();
        this.activation    = new OnExplicitDispatch<>();
        this.aggregation   = new CollectAll<>();
        this.termination   = ctx -> Uni.createFrom().item(
                ctx.iterationCount() >= 1
                ? new TerminationDecision.Complete(ctx.results())
                : TerminationDecision.Continue.INSTANCE);
    }

    public HtnBuilder<T> rootTask(TaskNode<T> rootTask) {
        this.rootTask = rootTask;
        return this;
    }

    @Override
    public HtnBuilder<T> agents(io.casehub.blocks.agentic.AgentRef... agents) {
        return (HtnBuilder<T>) super.agents(agents);
    }

    @Override
    public HtnBuilder<T> agents(io.casehub.blocks.agentic.RoutingCandidate... candidates) {
        return (HtnBuilder<T>) super.agents(candidates);
    }


    @Override
    public Uni<ExecutionResult> execute(T initialContext) {
        if (rootTask == null) {
            throw new IllegalStateException("rootTask must be set before execute()");
        }

        return flatten(rootTask, initialContext)
                       .map(plan -> {
                           var sortedNodes = plan.topologicalSort();
                           var agents = sortedNodes.stream()
                                                   .map(n -> new RoutingCandidate((AgentRef) n.task().executor(), null))
                                                   .toList();

                           var localTermination = (TerminationCondition<T>) ctx -> Uni.createFrom().item(
                                   ctx.iterationCount() >= agents.size()
                                   ? new TerminationDecision.Complete(ctx.results())
                                   : TerminationDecision.Continue.INSTANCE);

                           var localModel = new ExecutionModel<>(
                                   routing,
                                   decomposition,
                                   activation,
                                   aggregation,
                                   localTermination,
                                   () -> agents,
                                   failurePolicy,
                                   listeners,
                                   task
                           );

                           return localModel;
                       })
                       .flatMap(localModel -> new OrchestratedDriver<T>().execute(localModel, initialContext));
    }

    private Uni<DagPlan<TaskNode.LeafTask<T>>> flatten(TaskNode<T> node, T state) {
        return switch (node) {
            case TaskNode.LeafTask<T> leaf -> Uni.createFrom().item(DagPlan.singleton(leaf));
            case TaskNode.CompoundTask<T> compound -> {
                var agents = candidateSupplier != null
                             ? candidateSupplier.get() : java.util.List.<io.casehub.blocks.agentic.RoutingCandidate>of();
                var ctx = new AgenticDecompositionContext<>(state, agents, 0);
                yield decomposition.decompose(compound, ctx);
            }
        };
    }
}
