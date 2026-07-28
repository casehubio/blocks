package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.time.Instant;

@FunctionalInterface
public interface AgentInvoker<T> {

    Uni<AgentResult> invoke(AgentRef agent, T state);

    default AgentInvoker<T> withFallback(AgentInvoker<T> fallback) {
        var primary = this;
        return (agent, state) -> primary.invoke(agent, state)
                                        .onItem().transformToUni(result -> {
                    if (result.status() == AgentResult.AgentResultStatus.FAILURE
                        && result.output() instanceof String msg
                        && msg.startsWith("Unsupported AgentRef")) {
                        return fallback.invoke(agent, state);
                    }
                    return Uni.createFrom().item(result);
                });
    }

    static <T> AgentInvoker<T> defaultInvoker() {
        return (agent, state) -> Uni.createFrom().item(() -> {
            var start = Instant.now();
            return switch (agent) {
                case AgentRef.ExternalAgent ext -> {
                    var result  = ext.fn().apply(state).toCompletableFuture().join();
                    var elapsed = Duration.between(start, Instant.now());
                    yield new AgentResult(agent, result.output(), elapsed, result.status());
                }
                case AgentRef.ComposedAgent composed -> invokeComposed(agent, composed, state, start);
                case AgentRef.ChannelAgent ignored -> AgentResult.failure(agent,
                                                                          "Unsupported AgentRef variant: ChannelAgent requires an AgentProvider");
                case AgentRef.WorkerAgent ignored -> AgentResult.failure(agent,
                                                                         "Unsupported AgentRef variant: WorkerAgent requires engine runtime");
                case AgentRef.HumanAgent ignored -> AgentResult.failure(agent,
                                                                        "Unsupported AgentRef variant: HumanAgent requires work-api");
            };
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> AgentResult invokeComposed(AgentRef agent,
                                                  AgentRef.ComposedAgent composed,
                                                  T state, Instant start) {
        var                  model           = (ExecutionModel<Object>) (ExecutionModel<?>) composed.model();
        AgentInvoker<Object> nested          = defaultInvoker();
        var                  driver          = new OrchestratedDriver<>(nested);
        var                  executionResult = driver.execute(model, state).await().indefinitely();
        var                  elapsed         = Duration.between(start, Instant.now());
        return switch (executionResult) {
            case ExecutionResult.Completed c -> new AgentResult(agent, c.result(), elapsed, AgentResult.AgentResultStatus.SUCCESS);
            case ExecutionResult.Failed f -> new AgentResult(agent, f.reason(), elapsed, AgentResult.AgentResultStatus.FAILURE);
            case ExecutionResult.Escalated e -> new AgentResult(agent, "Escalated: " + e.reason(), elapsed, AgentResult.AgentResultStatus.FAILURE);
            case ExecutionResult.Cancelled ignored -> new AgentResult(agent, "Cancelled", elapsed, AgentResult.AgentResultStatus.FAILURE);
        };
    }
}
