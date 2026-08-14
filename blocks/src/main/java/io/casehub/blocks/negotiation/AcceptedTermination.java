package io.casehub.blocks.negotiation;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;

public record AcceptedTermination() implements TerminationCondition<NegotiationState> {
    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        if (context.state().outcome() == NegotiationOutcome.AGREED) {
            return new TerminationDecision.Complete("Proposal accepted");
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
