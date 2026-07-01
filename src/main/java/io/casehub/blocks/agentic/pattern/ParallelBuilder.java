package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

public class ParallelBuilder<T> extends AbstractPatternBuilder<T, ParallelBuilder<T>> {

    public ParallelBuilder() {
        this.task = "parallel";
        this.routing = ctx -> Uni.createFrom().item(
                new RoutingDecision.Selected(
                        ctx.candidates().stream().map(RoutingCandidate::ref).toList()));
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new CollectAll<>();
        this.termination = ctx -> Uni.createFrom().item(
                ctx.iterationCount() >= 1
                        ? new TerminationDecision.Complete(ctx.results())
                        : TerminationDecision.Continue.INSTANCE);
    }

    @Override
    public ParallelBuilder<T> agents(AgentRef... agents) {
        return (ParallelBuilder<T>) super.agents(agents);
    }
}
