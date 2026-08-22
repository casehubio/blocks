package io.casehub.blocks.agentic.personality;

public record PersonalityEvolutionConfig(double decayFactor, double l2Ceiling, double dampeningFactor) {

    public static final double DEFAULT_DECAY_FACTOR = 0.8;
    public static final double DEFAULT_L2_CEILING = 0.15;
    public static final double DEFAULT_DAMPENING_FACTOR = 0.5;

    public PersonalityEvolutionConfig {
        if (decayFactor < 0.0 || decayFactor > 1.0) {
            throw new IllegalArgumentException("decayFactor must be in [0.0, 1.0]");
        }
        if (l2Ceiling <= 0.0) {
            throw new IllegalArgumentException("l2Ceiling must be positive");
        }
        if (dampeningFactor < 0.0 || dampeningFactor > 1.0) {
            throw new IllegalArgumentException("dampeningFactor must be in [0.0, 1.0]");
        }
    }

    public static PersonalityEvolutionConfig defaults() {
        return new PersonalityEvolutionConfig(DEFAULT_DECAY_FACTOR, DEFAULT_L2_CEILING, DEFAULT_DAMPENING_FACTOR);
    }
}
