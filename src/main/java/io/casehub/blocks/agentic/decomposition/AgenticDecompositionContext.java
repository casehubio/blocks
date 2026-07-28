package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.engine.plan.DecompositionContext;

import java.util.List;

public record AgenticDecompositionContext<T>(T state, List<RoutingCandidate> agents, int depth)
    implements DecompositionContext<T> {
  public AgenticDecompositionContext {
    agents = List.copyOf(agents);
  }
}
