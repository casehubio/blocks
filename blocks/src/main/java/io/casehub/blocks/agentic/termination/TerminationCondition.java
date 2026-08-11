package io.casehub.blocks.agentic.termination;

import io.smallrye.mutiny.Uni;

public interface TerminationCondition<T> {
    Uni<TerminationDecision> evaluate(TerminationContext<T> context);
}
