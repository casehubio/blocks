package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.LlmSelectedRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.platform.agent.AgentProvider;

public class SupervisorBuilder<T> extends AbstractPatternBuilder<T, SupervisorBuilder<T>> {

    public SupervisorBuilder() {
        this.routing = new FirstMatchRouting<>(c -> true);
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
        this.termination = new MaxIterationsTermination<>(10);
    }

    public SupervisorBuilder(AgentProvider agentProvider) {
        this();
        this.routing = new LlmSelectedRouting<>(agentProvider);
    }

    @Override
    public SupervisorBuilder<T> agents(AgentRef... agents) {
        return (SupervisorBuilder<T>) super.agents(agents);
    }

    @Override
    public SupervisorBuilder<T> agents(RoutingCandidate... candidates) {
        return (SupervisorBuilder<T>) super.agents(candidates);
    }
}
