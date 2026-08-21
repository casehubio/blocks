package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.cbr.TrendProfile;
import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface StrategyReflection {

    record NoChange(@Nullable String reason) implements StrategyReflection {}

    record Reflected(StrategyProfile profile, List<String> newGuidelines,
                     TrendProfile trends, int evidenceCases)
            implements StrategyReflection {
        public Reflected { newGuidelines = List.copyOf(newGuidelines); }
    }
}
