package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.engagement.EngagementEvent;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public sealed interface EngagementSignal {

    record TurnOutcome(
            EngagementEvent event,
            Map<String, Double> dimensionalSnapshot,
            @Nullable String responseExcerpt
    ) implements EngagementSignal {
        public TurnOutcome {
            Objects.requireNonNull(event, "event required");
            Objects.requireNonNull(dimensionalSnapshot, "dimensionalSnapshot required");
            dimensionalSnapshot = Map.copyOf(dimensionalSnapshot);
        }
    }

    record ConversationOutcome(
            String conversationId,
            @Nullable String conversationSummary,
            int turnCount
    ) implements EngagementSignal {
        public ConversationOutcome {
            Objects.requireNonNull(conversationId, "conversationId required");
            if (conversationId.isBlank())
                throw new IllegalArgumentException("conversationId must not be blank");
            if (turnCount < 0)
                throw new IllegalArgumentException("turnCount must be >= 0");
        }
    }
}
