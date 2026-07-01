package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.time.Instant;

@FunctionalInterface
public interface AgentInvoker<T> {

    Uni<AgentResult> invoke(AgentRef agent, T state);

    static <T> AgentInvoker<T> defaultInvoker() {
        return (agent, state) -> Uni.createFrom().item(() -> {
            var start = Instant.now();
            if (agent instanceof AgentRef.ExternalAgent ext) {
                var result = ext.fn().apply(state).toCompletableFuture().join();
                var duration = Duration.between(start, Instant.now());
                return new AgentResult(agent, result.output(), duration, result.status());
            }
            return AgentResult.failure(agent,
                    "Unsupported AgentRef variant: " + agent.getClass().getSimpleName());
        });
    }
}
