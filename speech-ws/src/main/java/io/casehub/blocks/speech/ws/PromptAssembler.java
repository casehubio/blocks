package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.ws.protocol.ConversationTurn;

import java.util.List;

@FunctionalInterface
public interface PromptAssembler {
    AssembledPrompt assemble(String userMessage, List<ConversationTurn> history);
}
