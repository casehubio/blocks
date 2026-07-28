package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.RoutingCandidate;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GoapDecompositionContext<T>(T state, List<RoutingCandidate> agents, int depth,
                                          Set<String> goalTypes, Set<String> availableTypes)
    implements io.casehub.engine.plan.DecompositionContext<T> {
  public GoapDecompositionContext {
    agents = List.copyOf(agents);
    Objects.requireNonNull(goalTypes, "goalTypes");
    goalTypes = Set.copyOf(goalTypes);
    Objects.requireNonNull(availableTypes, "availableTypes");
    availableTypes = Set.copyOf(availableTypes);
    if (goalTypes.isEmpty()) {
      throw new IllegalArgumentException("goalTypes must not be empty");
    }
  }
}
