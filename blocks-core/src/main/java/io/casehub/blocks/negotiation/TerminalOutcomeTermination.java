package io.casehub.blocks.negotiation;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;

public record TerminalOutcomeTermination() implements TerminationCondition<NegotiationState> {
    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        return switch (context.state().outcome()) {
            case AGREED -> new TerminationDecision.Complete("Proposal accepted");
            case DEADLOCKED -> new TerminationDecision.Failed("Negotiation deadlocked");
            case WITHDRAWN -> new TerminationDecision.Failed("Party withdrew");
            case PENDING -> TerminationDecision.Continue.INSTANCE;
        };
    }
}
