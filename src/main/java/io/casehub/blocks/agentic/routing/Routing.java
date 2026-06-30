package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.platform.agent.AgentProvider;

import java.util.function.Predicate;

public final class Routing {
    private Routing() {}

    public static <T> FirstMatchRouting<T> firstMatch(Predicate<RoutingCandidate> matcher) {
        return new FirstMatchRouting<>(matcher);
    }

    public static <T> RoundRobinRouting<T> roundRobin() {
        return new RoundRobinRouting<>();
    }

    public static <T> SequentialRouting<T> sequential() {
        return new SequentialRouting<>();
    }

    public static <T> LlmSelectedRouting<T> llmSelected(AgentProvider agentProvider) {
        return new LlmSelectedRouting<>(agentProvider);
    }
}
