package io.casehub.blocks.agentic.belief;

import java.util.Objects;

public record Belief<T>(String key, T value, int entrenchment) {
    public Belief {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (entrenchment < 0) throw new IllegalArgumentException("Entrenchment must be >= 0");
    }

    public static <T> Belief<T> of(String key, T value) {
        return new Belief<>(key, value, 0);
    }

    public static <T> Belief<T> of(String key, T value, int entrenchment) {
        return new Belief<>(key, value, entrenchment);
    }
}
