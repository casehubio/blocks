package io.casehub.blocks.agentic.judgment;

import org.jspecify.annotations.Nullable;

public record JudgmentDispatchRequest(
        JudgmentContext<?> context,
        CallerRef caller,
        @Nullable String feedback) {}
