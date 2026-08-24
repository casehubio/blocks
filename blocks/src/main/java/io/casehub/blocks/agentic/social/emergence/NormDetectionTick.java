package io.casehub.blocks.agentic.social.emergence;

import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface NormDetectionTick {
    record NoChange(@Nullable String reason) implements NormDetectionTick {}
    record Updated(DetectedNorms previous, DetectedNorms current,
                   List<String> newNormIds,
                   List<String> declinedNormIds) implements NormDetectionTick {}
}
