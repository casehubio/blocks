package io.casehub.blocks.agentic.personality;

public class ConsecutiveInitiationCooldownConstraint implements CivilityConstraint {

    private final int maxConsecutive;

    public ConsecutiveInitiationCooldownConstraint(int maxConsecutive) {
        this.maxConsecutive = maxConsecutive;
    }

    @Override
    public CivilityCheck permitInitiation(InitiationContext context) {
        if (context.consecutiveInitiationsWithoutResponse() >= maxConsecutive) {
            return new CivilityCheck.Denied("consecutive cooldown: " + context.consecutiveInitiationsWithoutResponse() + " unanswered initiations");
        }
        return new CivilityCheck.Permitted();
    }
}
