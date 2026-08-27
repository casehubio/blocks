package io.casehub.blocks.speech.ws.protocol;

public record ConversationTurn(String role, String text) {
    public ConversationTurn {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role required");
        if (text == null) throw new IllegalArgumentException("text required");
    }
}
