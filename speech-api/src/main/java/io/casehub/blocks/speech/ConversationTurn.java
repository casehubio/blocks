package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;

public record ConversationTurn(String role, String text, @Nullable String speaker) {
    public ConversationTurn {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role required");
        if (text == null) throw new IllegalArgumentException("text required");
    }

    public ConversationTurn(String role, String text) {
        this(role, text, null);
    }
}
