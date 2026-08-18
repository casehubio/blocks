package io.casehub.blocks.agentic.personality;

import io.casehub.eidos.api.DispositionValue;

import java.util.List;

public sealed interface EvolutionTick {
    record Stable() implements EvolutionTick {}

    record Drifting(double magnitude) implements EvolutionTick {}

    record Halted(double magnitude) implements EvolutionTick {}

    record Evolved(String previousTypeLabel, String newTypeLabel,
                   List<DispositionValue> newProfile) implements EvolutionTick {
        public Evolved {
            newProfile = List.copyOf(newProfile);
        }
    }

    record Dampened(double decayFactor) implements EvolutionTick {}
}
