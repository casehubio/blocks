package io.casehub.blocks.agentic.termination;

import io.casehub.blocks.agentic.AgentResult;

import java.time.Duration;
import java.util.List;

public record TerminationContext<T>(
        T state,
        int iterationCount,
        Duration elapsed,
        List<AgentResult> results
) {
    public TerminationContext { results = List.copyOf(results); }
}
