package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DecompositionMethod;

import java.util.Objects;

public record ScoredMethod<T>(DecompositionMethod<T> method, double score) {
    public ScoredMethod {
        Objects.requireNonNull(method, "method");
    }
}
