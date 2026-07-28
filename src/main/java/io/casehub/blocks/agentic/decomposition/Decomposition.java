package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class Decomposition {
    private Decomposition() {}

    public static <T> IdentityDecomposition<T> none() {
        return new IdentityDecomposition<>();
    }

    public static <T> StaticDecomposition<T> staticTree() {
        return new StaticDecomposition<>();
    }

    public static <T> GoalOrientedDecomposition<T> goap() {
        return new GoalOrientedDecomposition<>();
    }


    public static <T> HybridDecomposition<T> hybrid(io.casehub.platform.agent.AgentProvider agentProvider) {
        return new HybridDecomposition<>(agentProvider);
    }

    public static <T> HybridDecomposition<T> hybrid(io.casehub.platform.agent.AgentProvider agentProvider,
                                                    java.util.function.Function<T, String> stateRenderer) {
        return new HybridDecomposition<>(agentProvider, stateRenderer);
    }


    public static <T> DecompositionMethod<T> method(Predicate<T> guard,
                                                    DecompositionStrategy<T> strategy) {
        return new DecompositionMethod<>(guard, strategy);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> DecompositionStrategy<T> sequence(TaskNode<T>... tasks) {
        var leafTasks = java.util.Arrays.stream(tasks)
                                        .filter(t -> t instanceof TaskNode.LeafTask<T>)
                                        .map(t -> (TaskNode.LeafTask<T>) t)
                                        .toList();
        return (compound, ctx) -> io.smallrye.mutiny.Uni.createFrom()
                                                        .item(DagPlan.sequence(leafTasks));
    }

    public static <T> PrimitiveTask<T> primitive(AgentRef agent) {
        return new PrimitiveTask<>(UUID.randomUUID().toString(), Instant.now(), null, agent, null, null);
    }

    public static <T> PrimitiveTask<T> primitive(String description, AgentRef agent) {
        return new PrimitiveTask<>(UUID.randomUUID().toString(), Instant.now(), description, agent, null, null);
    }

    public static <T> TaskNode.CompoundTask<T> compound(String name,
                                                        List<DecompositionMethod<T>> methods) {
        return new TaskNode.CompoundTask<>(name, methods);
    }

    public static <T> PlannedTask<T> planned(String description, AgentRef agent) {
        return new PlannedTask<>(UUID.randomUUID().toString(), Instant.now(), description, agent, null);
    }

    public static <T> PlannedTask<T> planned(String description, AgentRef agent,
                                                      String rationale) {
        return new PlannedTask<>(UUID.randomUUID().toString(), Instant.now(), description, agent, rationale);
    }
}
