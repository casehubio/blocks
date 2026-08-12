package io.casehub.blocks.agentic.routing;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SequentialRouting<T> implements RoutingStrategy<T> {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public RoutingDecision route(RoutingContext<T> context) {
        var candidates = context.candidates();
        if (candidates.isEmpty()) {
            return new RoutingDecision.Unresolvable("No candidates available");
        }
        int i = index.getAndIncrement();
        if (i < candidates.size()) {
            return new RoutingDecision.Selected(List.of(candidates.get(i).ref()));
        }
        return new RoutingDecision.Unresolvable("Sequence complete");
    }
}
