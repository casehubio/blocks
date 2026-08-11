package io.casehub.blocks.agentic.aggregation;

public sealed interface AggregationResult
        permits AggregationResult.Resolved, AggregationResult.Partial,
                AggregationResult.Deadlocked {

    record Resolved(Object value) implements AggregationResult {}
    record Partial(Object collected, int remaining) implements AggregationResult {}
    record Deadlocked(String reason) implements AggregationResult {}
}
