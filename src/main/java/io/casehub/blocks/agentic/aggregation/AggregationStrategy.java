package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;

import java.util.List;

public interface AggregationStrategy<T> {
    Uni<AggregationResult> aggregate(List<AgentResult> results,
                                     AggregationContext<T> context);
}
