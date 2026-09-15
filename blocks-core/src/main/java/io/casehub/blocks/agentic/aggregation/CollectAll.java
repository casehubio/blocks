package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentResult;

import java.util.List;

public class CollectAll<T> implements AggregationStrategy<T> {

    @Override
    public AggregationResult aggregate(List<AgentResult> results,
                                       AggregationContext<T> context) {
        return new AggregationResult.Resolved(List.copyOf(results));
    }
}
