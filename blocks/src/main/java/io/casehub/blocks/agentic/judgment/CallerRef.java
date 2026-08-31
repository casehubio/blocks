package io.casehub.blocks.agentic.judgment;

import io.casehub.blocks.agentic.AgentRef;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record CallerRef(
        String id,
        @Nullable String description,
        @Nullable Map<String, Object> routingHints,
        @Nullable AgentRef agentRef) {

    public static CallerRef agent(String id, @Nullable AgentRef ref) {
        return new CallerRef(id, null, null, ref);
    }

    public static CallerRef human(String id, Map<String, Object> routingHints) {
        return new CallerRef(id, null, routingHints, null);
    }
}
