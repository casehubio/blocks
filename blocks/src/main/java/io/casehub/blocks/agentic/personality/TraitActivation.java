package io.casehub.blocks.agentic.personality;

import io.casehub.eidos.api.SignalValence;

public record TraitActivation(String functionTerm, SignalValence valence) {
    public TraitActivation {
        if (functionTerm == null || functionTerm.isBlank()) {
            throw new IllegalArgumentException("functionTerm must not be blank");
        }
        if (valence == null) {
            throw new IllegalArgumentException("valence must not be null");
        }
    }
}
