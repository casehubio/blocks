package io.casehub.blocks.conversation;

public record ConvergenceSignal(
        ConvergenceState state,
        double confidence,
        String reason) {}
