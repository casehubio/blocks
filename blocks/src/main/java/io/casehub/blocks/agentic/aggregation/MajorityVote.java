package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MajorityVote<T> implements AggregationStrategy<T> {

    @Override
    public AggregationResult aggregate(List<AgentResult> results,
                                       AggregationContext<T> context) {
        var counts = new LinkedHashMap<Object, Integer>();
        for (var r : results) {
            counts.merge(r.output(), 1, Integer::sum);
        }
        int maxCount = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var winners = counts.entrySet().stream()
                            .filter(e -> e.getValue() == maxCount)
                            .map(Map.Entry::getKey)
                            .toList();
        if (winners.size() == 1) {
            return new AggregationResult.Resolved(winners.get(0));
        }
        return new AggregationResult.Deadlocked("Tied vote: " + winners.size() + " outputs with " + maxCount + " votes each");
    }
}
