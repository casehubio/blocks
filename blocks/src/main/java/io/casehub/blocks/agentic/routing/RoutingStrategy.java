package io.casehub.blocks.agentic.routing;

import io.smallrye.mutiny.Uni;

public interface RoutingStrategy<T> {
    Uni<RoutingDecision> route(RoutingContext<T> context);
}
