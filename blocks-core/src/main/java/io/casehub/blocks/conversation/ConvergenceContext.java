package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.MessageType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ConvergenceContext(
        int totalPoints,
        int establishedCount,
        int pendingCount,
        int disputedCount,
        double recentSimilarity,
        double messageLengthTrend,
        int roundsSinceNewPoint,
        int roundsSinceStatusChange,
        Map<MessageType, Integer> recentMessageTypeCounts) {
    public ConvergenceContext {
        recentMessageTypeCounts = Collections.unmodifiableMap(
                new LinkedHashMap<>(recentMessageTypeCounts));
    }
}
