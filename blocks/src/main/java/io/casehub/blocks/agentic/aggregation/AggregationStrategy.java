package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentResult;
import java.util.List;

public interface AggregationStrategy<T> {
    AggregationResult aggregate(List<AgentResult> results,
                                AggregationContext<T> context);
}
