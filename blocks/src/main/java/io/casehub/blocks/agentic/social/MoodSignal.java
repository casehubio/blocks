package io.casehub.blocks.agentic.social;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public sealed interface MoodSignal {

    double pleasureDelta();
    double arousalDelta();
    double dominanceDelta();

    record InteractionAppraisal(
            double pleasureDelta,
            double arousalDelta,
            double dominanceDelta,
            @Nullable String cause
    ) implements MoodSignal {
        public InteractionAppraisal {
            validateDelta("pleasureDelta", pleasureDelta);
            validateDelta("arousalDelta", arousalDelta);
            validateDelta("dominanceDelta", dominanceDelta);
        }
    }

    record DirectShift(
            double pleasureDelta,
            double arousalDelta,
            double dominanceDelta,
            String cause
    ) implements MoodSignal {
        public DirectShift {
            Objects.requireNonNull(cause, "cause required");
            if (cause.isBlank()) throw new IllegalArgumentException("cause must not be blank");
            validateDelta("pleasureDelta", pleasureDelta);
            validateDelta("arousalDelta", arousalDelta);
            validateDelta("dominanceDelta", dominanceDelta);
        }
    }

    private static void validateDelta(String name, double value) {
        if (value < -2.0 || value > 2.0)
            throw new IllegalArgumentException(name + " must be in [-2, 2], got " + value);
    }
}
