package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationState;

import java.time.Duration;

@FunctionalInterface
public interface ConversationListener {
    void onDispatch(ConversationState state, TerminationDecision decision,
                    int dispatchCount, Duration elapsed);
}
