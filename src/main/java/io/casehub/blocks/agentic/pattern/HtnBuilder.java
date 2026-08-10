package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.decomposition.StaticDecomposition;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;

public class HtnBuilder<T> extends AbstractPatternBuilder<T, HtnBuilder<T>> {

    private TaskNode<T> rootTask;

    public HtnBuilder() {
        this.task          = "htn";
        this.patternType   = io.casehub.blocks.agentic.model.PatternType.HTN;
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

        if (candidateSupplier == null) {
            candidateSupplier = java.util.List::of;
        }

        var model    = build();
        var executor = new HtnExecutor<T>(AgentInvoker.defaultInvoker());

        return Uni.createFrom().item(() -> executor.execute(rootTask, model, initialContext));}

}
