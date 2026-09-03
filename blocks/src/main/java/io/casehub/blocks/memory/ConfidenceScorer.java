package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.time.Instant;

@FunctionalInterface
public interface ConfidenceScorer {
    double score(ScoredCbrCase<? extends CbrCase> memory, Instant now);
}
