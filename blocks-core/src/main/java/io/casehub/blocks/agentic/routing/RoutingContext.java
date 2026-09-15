package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.RoutingCandidate;

import java.util.List;

public record RoutingContext<T>(
        String task,
        List<RoutingCandidate> candidates,
        T state
) {
    public RoutingContext { candidates = List.copyOf(candidates); }
}
