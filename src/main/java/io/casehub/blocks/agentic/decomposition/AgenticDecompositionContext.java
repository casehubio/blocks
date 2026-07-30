package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.engine.plan.DecompositionContext;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record AgenticDecompositionContext<T>(T state, List<RoutingCandidate> agents, int depth,
                                             @Nullable String staticFailureHint,
                                             @Nullable String subtaskDescription,
                                             @Nullable String parentGoal,
                                             @Nullable List<String> siblingNames)
        implements DecompositionContext<T> {
    public AgenticDecompositionContext {
        agents = List.copyOf(agents);
        if (siblingNames != null) {
            siblingNames = List.copyOf(siblingNames);
        }
    }

    public AgenticDecompositionContext(T state, List<RoutingCandidate> agents, int depth,
                                       @Nullable String staticFailureHint) {
        this(state, agents, depth, staticFailureHint, null, null, null);
    }

    public AgenticDecompositionContext(T state, List<RoutingCandidate> agents, int depth) {
        this(state, agents, depth, null, null, null, null);
    }
}
