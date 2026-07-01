package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

import java.util.function.Predicate;

public class LoopBuilder<T> extends AbstractPatternBuilder<T, LoopBuilder<T>> {

    private int maxIterations = 10;

    public LoopBuilder() {
        this.task = "loop";
        this.routing = new RoundRobinRouting<>();
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
        this.termination = new MaxIterationsTermination<>(maxIterations);
    }

    public LoopBuilder<T> maxIterations(int max) {
        this.maxIterations = max;
        this.termination = new MaxIterationsTermination<>(max);
        return this;
    }

    public LoopBuilder<T> exitCondition(Predicate<T> predicate) {
        var maxIter = this.maxIterations;
        this.termination = ctx -> {
            if (predicate.test(ctx.state())) {
                return Uni.createFrom().item(new TerminationDecision.Complete(ctx.state()));
            }
            if (ctx.iterationCount() >= maxIter) {
                return Uni.createFrom().item(new TerminationDecision.Complete("Max iterations"));
            }
            return Uni.createFrom().item(TerminationDecision.Continue.INSTANCE);
        };
        return this;
    }

    @Override
    public LoopBuilder<T> agents(AgentRef... agents) {
        return (LoopBuilder<T>) super.agents(agents);
    }
}
