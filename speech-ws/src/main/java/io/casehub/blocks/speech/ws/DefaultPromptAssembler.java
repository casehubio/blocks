package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.ws.protocol.ConversationTurn;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultPromptAssembler implements PromptAssembler {

    private static final String FALLBACK_SYSTEM_PROMPT =
            "You are a conversational assistant. Respond naturally and concisely.";

    private final String systemPrompt;

    public DefaultPromptAssembler(@Nullable String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : FALLBACK_SYSTEM_PROMPT;
    }

    @Override
    public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history) {
        var sb = new StringBuilder();
        for (ConversationTurn turn : history) {
            sb.append(turn.role().equals("user") ? "User" : "Assistant");
            sb.append(": ").append(turn.text()).append("\n");
        }
        sb.append("User: ").append(userMessage);
        return new AssembledPrompt(systemPrompt, sb.toString());
    }
}
