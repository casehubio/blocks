package io.casehub.blocks.agentic.personality;

import java.time.Duration;
import java.time.Instant;

public class MinimumGapConstraint implements CivilityConstraint {

    private final Duration minimumGap;

    public MinimumGapConstraint(Duration minimumGap) {
        this.minimumGap = minimumGap;
    }

    @Override
    public CivilityCheck permitInitiation(InitiationContext context) {
        if (context.lastInitiationTimestamp().equals(Instant.EPOCH)) {
            return new CivilityCheck.Permitted();
        }
        Duration elapsed = Duration.between(context.lastInitiationTimestamp(), Instant.now());
        if (elapsed.compareTo(minimumGap) < 0) {
            return new CivilityCheck.Denied("minimum gap not met: " + elapsed.toSeconds() + "s < " + minimumGap.toSeconds() + "s");
        }
        return new CivilityCheck.Permitted();
    }
}
