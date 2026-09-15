package io.casehub.blocks.prompt.runtime;

import io.casehub.blocks.prompt.PromptQualityMetric;
import io.casehub.blocks.prompt.VariantOutcome;

import java.util.List;
import java.util.Map;

public class WeightedOutcomeMetric implements PromptQualityMetric {

    private static final Map<String, Double> DEFAULT_WEIGHTS = Map.of(
            "SUCCESS", 1.0,
            "GATE_EXPIRED", 0.5,
            "GATE_REJECTED", 0.25,
            "DECLINED", 0.0,
            "FAILURE", 0.0);

    @Override
    public double score(List<VariantOutcome> outcomes) {
        if (outcomes.isEmpty()) return 0.0;
        double total = 0.0;
        for (var outcome : outcomes) {
            total += DEFAULT_WEIGHTS.getOrDefault(outcome.outcome(), 0.0);
        }
        return total / outcomes.size();
    }
}
