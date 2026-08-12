package io.casehub.blocks.agentic.termination;

public interface TerminationCondition<T> {
    TerminationDecision evaluate(TerminationContext<T> context);
}
