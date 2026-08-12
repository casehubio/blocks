package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.RoutingCandidate;

import java.util.List;
import java.util.function.Predicate;

public class FirstMatchRouting<T> implements RoutingStrategy<T> {

    private final Predicate<RoutingCandidate> matcher;

    public FirstMatchRouting(Predicate<RoutingCandidate> matcher) {
        this.matcher = matcher;
    }

    @Override
    public RoutingDecision route(RoutingContext<T> context) {
        return context.candidates().stream()
                      .filter(matcher)
                      .findFirst()
                      .<RoutingDecision>map(c -> new RoutingDecision.Selected(List.of(c.ref())))
                      .orElse(new RoutingDecision.Unresolvable("No candidate matched"));
    }
}
