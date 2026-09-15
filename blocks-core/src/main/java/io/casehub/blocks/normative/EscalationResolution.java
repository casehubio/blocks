package io.casehub.blocks.normative;

import java.util.List;
import java.util.Objects;

public record EscalationResolution<T>(T escalationDecision) implements ConflictResolutionStrategy<T> {
    public EscalationResolution {
        Objects.requireNonNull(escalationDecision);
    }

    @Override
    public NormResolution<T> resolve(List<NormDecision<T>> conflicting) {
        var escalation = new NormDecision<>("escalation", escalationDecision, 0,
                NormSpecificity.UNIVERSAL, java.time.Instant.now());
        return new NormResolution<>(escalation, List.copyOf(conflicting),
                "Conflict detected — escalating to human resolution (" + conflicting.size() + " conflicting norms)",
                ResolutionMethod.ESCALATION);
    }
}
