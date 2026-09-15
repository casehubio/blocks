package io.casehub.blocks.agentic.social;

import org.jspecify.annotations.Nullable;

public sealed interface MentalModelTick {
    record Unchanged(@Nullable String reason) implements MentalModelTick {}
    record Updated(MentalModelSnapshot snapshot) implements MentalModelTick {}
    record Inferred(MentalModelSnapshot snapshot,
                    @Nullable MentalModelSnapshot previous) implements MentalModelTick {}
}
