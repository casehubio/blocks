package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;

import java.util.List;

public class CollectAll<T> implements AggregationStrategy<T> {

    @Override
    public Uni<AggregationResult> aggregate(List<AgentResult> results,
                                            AggregationContext<T> context) {
        return Uni.createFrom().item(new AggregationResult.Resolved(List.copyOf(results)));
    }
}
