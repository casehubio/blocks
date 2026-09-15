package io.casehub.blocks.agentic.decomposition;

import io.casehub.api.model.ExecutorRef;
import io.casehub.api.model.TaskStatus;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.engine.plan.TaskNode;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public record PrimitiveTask<T>(
        String id,
        Instant createdAt,
        @Nullable String description,
        AgentRef agent,
        @Nullable Predicate<T> precondition,
        @Nullable Consumer<T> effect,
        @Nullable OutputContract outputContract)
        implements TaskNode.LeafTask<T> {
    public PrimitiveTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(agent, "agent");
    }

    public PrimitiveTask(String id, Instant createdAt, @Nullable String description,
                         AgentRef agent, @Nullable Predicate<T> precondition,
                         @Nullable Consumer<T> effect) {
        this(id, createdAt, description, agent, precondition, effect, null);
    }

    @Override
    public ExecutorRef executor() {
        return agent;
    }

    @Override
    public TaskStatus status() {
        return TaskStatus.PENDING;
    }
}
