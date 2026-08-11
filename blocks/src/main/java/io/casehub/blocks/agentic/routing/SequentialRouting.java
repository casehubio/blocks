package io.casehub.blocks.agentic.routing;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SequentialRouting<T> implements RoutingStrategy<T> {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public Uni<RoutingDecision> route(RoutingContext<T> context) {
        return Uni.createFrom().item(() -> {
            var candidates = context.candidates();
            if (candidates.isEmpty()) {
                return new RoutingDecision.Unresolvable("No candidates available");
            }
            int i = index.getAndIncrement();
            if (i < candidates.size()) {
                return new RoutingDecision.Selected(List.of(candidates.get(i).ref()));
            }
            return new RoutingDecision.Unresolvable("Sequence complete");
        });
    }
}
