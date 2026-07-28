package io.casehub.blocks.agentic.decomposition;

import io.casehub.api.model.ExecutorRef;
import io.casehub.api.model.TaskStatus;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.engine.plan.TaskNode;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record PlannedTask<T>(
    String id,
    Instant createdAt,
    String description,
    AgentRef agent,
    @Nullable String rationale)
    implements TaskNode.LeafTask<T> {
  public PlannedTask {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(agent, "agent");
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
