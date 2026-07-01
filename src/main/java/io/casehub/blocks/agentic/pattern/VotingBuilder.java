package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.aggregation.MajorityVote;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

public class VotingBuilder<T> extends AbstractPatternBuilder<T, VotingBuilder<T>> {

    public VotingBuilder() {
        this.task = "vote";
        this.routing = ctx -> Uni.createFrom().item(
                new RoutingDecision.Selected(
                        ctx.candidates().stream().map(RoutingCandidate::ref).toList()));
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new MajorityVote<>();
        this.termination = ctx -> Uni.createFrom().item(
                ctx.iterationCount() >= 1
                        ? new TerminationDecision.Complete(ctx.results())
                        : TerminationDecision.Continue.INSTANCE);
    }

    public VotingBuilder<T> evaluators(AgentRef... agents) {
        return (VotingBuilder<T>) super.agents(agents);
    }

    public VotingBuilder<T> strategy(AggregationStrategy<T> strategy) {
        this.aggregation = strategy;
        return this;
    }
}
