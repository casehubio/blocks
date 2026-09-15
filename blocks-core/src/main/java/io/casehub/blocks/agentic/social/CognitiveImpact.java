package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.jspecify.annotations.Nullable;

public record CognitiveImpact(
        @Nullable InteractionSignal userModelSignal,
        @Nullable MoodSignal moodSignal,
        boolean suppressBdiExtraction,
        @Nullable EngagementSignal strategySignal,
        @Nullable String conversationId) {

    public static CognitiveImpact fromText(String description) {
        return new CognitiveImpact(
                new InteractionSignal.CustomSignal(description, QualitySignal.NEUTRAL),
                null, false, null, null);
    }

    public static CognitiveImpact withConversationId(String conversationId) {
        return new CognitiveImpact(null, null, false, null, conversationId);
    }
}
