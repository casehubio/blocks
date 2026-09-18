package io.casehub.blocks.agentic.social.need;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.needs-pyramid")
public interface NeedSatisfactionConfig {

    @WithDefault("0.05")
    double satisfactionIncrement();

    @WithDefault("0.05")
    double dissatisfactionIncrement();

    @WithDefault("0.5")
    double initialSatisfaction();

    DecayConfig decay();

    interface DecayConfig {
        @WithDefault("0.15")
        double safety();

        @WithDefault("0.10")
        double tasks();

        @WithDefault("0.08")
        double social();

        @WithDefault("0.05")
        double selfExpression();

        @WithDefault("0.03")
        double understanding();
    }

    RestingConfig resting();

    interface RestingConfig {
        @WithDefault("0.6")
        double safety();

        @WithDefault("0.3")
        double tasks();

        @WithDefault("0.4")
        double social();

        @WithDefault("0.4")
        double selfExpression();

        @WithDefault("0.4")
        double understanding();
    }

    default double decayRate(NeedTier tier) {
        return switch (tier) {
            case SAFETY -> decay().safety();
            case TASKS -> decay().tasks();
            case SOCIAL -> decay().social();
            case SELF_EXPRESSION -> decay().selfExpression();
            case UNDERSTANDING -> decay().understanding();
        };
    }

    default double restingLevel(NeedTier tier) {
        return switch (tier) {
            case SAFETY -> resting().safety();
            case TASKS -> resting().tasks();
            case SOCIAL -> resting().social();
            case SELF_EXPRESSION -> resting().selfExpression();
            case UNDERSTANDING -> resting().understanding();
        };
    }
}
