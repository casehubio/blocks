package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationState;

import java.util.List;

public class CompositeTermination implements TerminationCondition<ConversationState> {

    private final List<TerminationCondition<ConversationState>> conditions;

    public CompositeTermination(
            List<TerminationCondition<ConversationState>> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public TerminationDecision evaluate(
            TerminationContext<ConversationState> context) {
        for (var condition : conditions) {
            var decision = condition.evaluate(context);
            if (!(decision instanceof TerminationDecision.Continue)) {
                return decision;
            }
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
