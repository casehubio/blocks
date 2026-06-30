package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;

import java.util.List;

public class PassThrough<T> implements AggregationStrategy<T> {

    @Override
    public Uni<AggregationResult> aggregate(List<AgentResult> results,
                                            AggregationContext<T> context) {
        var output = results.isEmpty() ? null : results.get(results.size() - 1).output();
        return Uni.createFrom().item(new AggregationResult.Resolved(output));
    }
}
