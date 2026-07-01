package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

import java.util.function.Predicate;

public class ConditionalBuilder<T> extends AbstractPatternBuilder<T, ConditionalBuilder<T>> {

    public ConditionalBuilder() {
        this.task = "conditional";
        this.routing = new FirstMatchRouting<>(c -> true);
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
        this.termination = ctx -> Uni.createFrom().item(
                ctx.iterationCount() >= 1
                        ? new TerminationDecision.Complete(ctx.results())
                        : TerminationDecision.Continue.INSTANCE);
    }

    public ConditionalBuilder<T> when(Predicate<RoutingCandidate> predicate, AgentRef agent) {
        this.routing = new FirstMatchRouting<>(predicate);
        return (ConditionalBuilder<T>) super.agents(agent);
    }
}
