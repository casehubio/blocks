package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.jspecify.annotations.Nullable;

public record CognitiveImpact(
        @Nullable InteractionSignal userModelSignal,
        @Nullable MoodSignal moodSignal,
        boolean suppressBdiExtraction,
        @Nullable EngagementSignal strategySignal) {

    public static CognitiveImpact fromText(String description) {
        return new CognitiveImpact(
                new InteractionSignal.CustomSignal(description, QualitySignal.NEUTRAL),
                null, false, null);
    }
}
