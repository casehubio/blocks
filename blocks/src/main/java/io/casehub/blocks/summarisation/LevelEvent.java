package io.casehub.blocks.summarisation;

public record LevelEvent<E>(E payload, long timestamp, EventLevel level) {}
