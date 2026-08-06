package io.casehub.blocks.conversation.orchestration;

import org.jspecify.annotations.Nullable;

import java.util.Map;

public record TurnContext(
        String senderId,
        @Nullable String targetId,
        String entryType,
        Map<String, String> metadata
) {
    public TurnContext { metadata = Map.copyOf(metadata); }
}
