package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public sealed interface TaskNode<T>
        permits TaskNode.LeafTask, TaskNode.CompoundTask {

    sealed interface LeafTask<T> extends TaskNode<T>
            permits TaskNode.PrimitiveTask, TaskNode.PlannedTask {
        AgentRef agent();
        @Nullable String description();
    }

    record PrimitiveTask<T>(@Nullable String description, AgentRef agent,
                            @Nullable Predicate<T> precondition,
                            @Nullable Consumer<T> effect) implements LeafTask<T> {}

    record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
            implements TaskNode<T> {
        public CompoundTask { methods = List.copyOf(methods); }
    }

    record PlannedTask<T>(String description, AgentRef agent,
                          @Nullable String rationale) implements LeafTask<T> {
        public PlannedTask {
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(agent, "agent");
        }
    }
}
