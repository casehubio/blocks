package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

public class SequenceBuilder<T> extends AbstractPatternBuilder<T, SequenceBuilder<T>> {

    private int agentCount = 0;

    public SequenceBuilder() {
        this.task = "sequence";
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
    }

    @Override
    public SequenceBuilder<T> agents(AgentRef... agents) {
        this.agentCount = agents.length;
        int count = this.agentCount;
        this.routing = new SequentialRouting<>();
        this.termination = ctx -> {
            if (ctx.iterationCount() >= count) {
                return Uni.createFrom().item(
                        new TerminationDecision.Complete(ctx.results().isEmpty() ? null :
                                ctx.results().get(ctx.results().size() - 1).output()));
            }
            return Uni.createFrom().item(TerminationDecision.Continue.INSTANCE);
        };
        return (SequenceBuilder<T>) super.agents(agents);
    }

    @Override
    public SequenceBuilder<T> agents(RoutingCandidate... candidates) {
        this.agentCount = candidates.length;
        return (SequenceBuilder<T>) super.agents(candidates);
    }
}
