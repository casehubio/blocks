package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.mood.MoodState;
import org.jspecify.annotations.Nullable;

public sealed interface MoodTick {

    record NoChange(@Nullable String reason) implements MoodTick {}

    record Updated(MoodState moodState, int signalsApplied, boolean decayed) implements MoodTick {}
}
