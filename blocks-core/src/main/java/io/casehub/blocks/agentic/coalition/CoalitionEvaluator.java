package io.casehub.blocks.agentic.coalition;

@FunctionalInterface
public interface CoalitionEvaluator {
    CoalitionScore evaluate(CoalitionProposal proposal, CoalitionContext context);
}
