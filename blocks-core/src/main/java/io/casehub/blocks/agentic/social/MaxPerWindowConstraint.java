package io.casehub.blocks.agentic.social;

public class MaxPerWindowConstraint implements CivilityConstraint {

    private final int maxInitiations;

    public MaxPerWindowConstraint(int maxInitiations) {
        this.maxInitiations = maxInitiations;
    }

    @Override
    public CivilityCheck permitInitiation(InitiationContext context) {
        if (context.initiationsInWindow() >= maxInitiations) {
            return new CivilityCheck.Denied("rate limit exceeded: " + context.initiationsInWindow() + " >= " + maxInitiations);
        }
        return new CivilityCheck.Permitted();
    }
}
