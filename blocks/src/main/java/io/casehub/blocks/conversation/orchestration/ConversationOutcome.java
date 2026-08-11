package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationState;

import java.time.Duration;
import java.util.List;

public record ConversationOutcome(
        ConversationState finalState,
        TerminationDecision terminationDecision,
        List<AgentResult> agentResults,
        int dispatchCount,
        Duration elapsed
) {
    public ConversationOutcome { agentResults = List.copyOf(agentResults); }
}
