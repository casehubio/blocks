package io.casehub.blocks.agentic.model;

import org.jspecify.annotations.Nullable;
import java.time.Instant;

public record DriverEvent(
    String source,
    Instant timestamp,
    @Nullable Object payload
) {
    public DriverEvent {
        java.util.Objects.requireNonNull(source, "source");
        java.util.Objects.requireNonNull(timestamp, "timestamp");
    }

    public DriverEvent(String source) {
        this(source, Instant.now(), null);
    }

    public DriverEvent(String source, Object payload) {
        this(source, Instant.now(), payload);
    }

    public static DriverEvent signal(String source) {
        return new DriverEvent(source);
    }

    public static DriverEvent timer() {
        return new DriverEvent("timer");
    }
}
