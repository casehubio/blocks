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

import java.util.function.Function;

public class SupervisorBuilder<T> extends AbstractPatternBuilder<T, SupervisorBuilder<T>> {

    private AgentProvider agentProvider;
    private Function<T, String> stateRenderer;

    public SupervisorBuilder() {
        this.task = "supervisor";
        this.routing = new FirstMatchRouting<>(c -> true);
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
        this.termination = new MaxIterationsTermination<>(10);
    }

    public SupervisorBuilder(AgentProvider agentProvider) {
        this();
        this.agentProvider = agentProvider;
        this.routing = new LlmSelectedRouting<>(agentProvider);
    }

    /**
     * Sets a state renderer that converts typed state {@code T} to a String
     * representation for the LLM routing prompt. Eagerly applies — if an
     * {@link AgentProvider} is available, immediately replaces the routing
     * strategy with a new {@link LlmSelectedRouting} using this renderer.
     *
     * <p>Last-writer-wins with {@link #route}: calling {@code stateRenderer()}
     * after {@code route()} replaces the custom routing with LLM routing;
     * calling {@code route()} after {@code stateRenderer()} replaces LLM
     * routing with the custom strategy.
     */
    public SupervisorBuilder<T> stateRenderer(Function<T, String> stateRenderer) {
        this.stateRenderer = stateRenderer;
        if (agentProvider != null) {
            this.routing = new LlmSelectedRouting<>(agentProvider, stateRenderer);
        }
        return this;
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
