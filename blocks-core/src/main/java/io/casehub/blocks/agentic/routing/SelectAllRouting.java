package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.RoutingCandidate;

public class SelectAllRouting<T> implements RoutingStrategy<T> {

    @Override
    public RoutingDecision route(RoutingContext<T> context) {
        var refs = context.candidates().stream()
                .map(RoutingCandidate::ref)
                .toList();
        return new RoutingDecision.Selected(refs);
    }
}
