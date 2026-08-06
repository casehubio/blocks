package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationState;
import io.smallrye.mutiny.Uni;

import java.util.List;

public class CompositeTermination implements TerminationCondition<ConversationState> {

    private final List<TerminationCondition<ConversationState>> conditions;

    public CompositeTermination(
            List<TerminationCondition<ConversationState>> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public Uni<TerminationDecision> evaluate(
            TerminationContext<ConversationState> context) {
        Uni<TerminationDecision> chain = Uni.createFrom()
                .item(TerminationDecision.Continue.INSTANCE);
        for (var condition : conditions) {
            chain = chain.flatMap(prev -> {
                if (prev instanceof TerminationDecision.Continue) {
                    return condition.evaluate(context);
                }
                return Uni.createFrom().item(prev);
            });
        }
        return chain;
    }
}
