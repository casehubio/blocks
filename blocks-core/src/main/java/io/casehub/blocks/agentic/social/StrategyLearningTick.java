package io.casehub.blocks.agentic.social;

import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface StrategyLearningTick {

    record NoChange(@Nullable String reason) implements StrategyLearningTick {}

    record Observed(int signalsProcessed, double engagementRate,
                    double meanSentiment) implements StrategyLearningTick {}

    record Learned(int signalsProcessed, double engagementRate,
                   double meanSentiment, List<String> conversationsStored,
                   int casesStored) implements StrategyLearningTick {
        public Learned { conversationsStored = List.copyOf(conversationsStored); }
    }
}
