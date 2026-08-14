package io.casehub.blocks.negotiation;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;

import java.util.List;

public class NegotiationCompositeTermination implements TerminationCondition<NegotiationState> {

    private final List<TerminationCondition<NegotiationState>> conditions;

    public NegotiationCompositeTermination(List<TerminationCondition<NegotiationState>> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        for (var condition : conditions) {
            var decision = condition.evaluate(context);
            if (!(decision instanceof TerminationDecision.Continue)) return decision;
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
