package io.casehub.blocks.agentic.belief;

@FunctionalInterface
public interface ConsistencyChecker<T> {
    boolean isConsistent(BeliefSet<T> beliefs);
}
