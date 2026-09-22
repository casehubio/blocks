package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;

import java.time.Instant;

@FunctionalInterface
public interface ConfidenceScorer {
    double score(CbrMatch<? extends CbrRecord> memory, Instant now);
}
