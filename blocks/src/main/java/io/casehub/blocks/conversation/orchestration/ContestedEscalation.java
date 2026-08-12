package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationState;

public class ContestedEscalation implements TerminationCondition<ConversationState> {

    private static final String DISPUTED_STATUS = "DISPUTED";

    private final int maxDisputeRounds;

    public ContestedEscalation(int maxDisputeRounds) {
        this.maxDisputeRounds = maxDisputeRounds;
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<ConversationState> context) {
        for (var point : context.state().points().values()) {
            if (!DISPUTED_STATUS.equals(point.status())) {continue;}

            long disputeCount = point.thread().stream()
                                     .filter(e -> "DISPUTE".equals(e.entryType()))
                                     .count();
            if (disputeCount > maxDisputeRounds) {
                return new TerminationDecision.Escalate(
                        "Point '" + point.topic() + "' disputed "
                        + disputeCount + " times (threshold: "
                        + maxDisputeRounds + ")");
            }
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
