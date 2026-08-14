package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentResult;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface BidExtractor {
    @Nullable Bid extract(AgentResult result, int round);
}
