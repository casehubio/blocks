package io.casehub.blocks.agentic.routing;

public interface RoutingStrategy<T> {
    RoutingDecision route(RoutingContext<T> context);
}
