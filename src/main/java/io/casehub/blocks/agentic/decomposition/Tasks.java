package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class Tasks {

    private Tasks() {}

    // ── leaf factories ──────────────────────────────────────────────────────

    public static <T> PrimitiveTask<T> primitive(AgentRef agent) {
        return new PrimitiveTask<>(generateId(), Instant.now(), null, agent, null, null);
    }

    public static <T> PrimitiveTask<T> primitive(AgentRef agent, Consumer<T> effect) {
        return new PrimitiveTask<>(generateId(), Instant.now(), null, agent, null, effect);
    }

    public static <T> PrimitiveTask<T> primitive(String description, AgentRef agent) {
        return new PrimitiveTask<>(generateId(), Instant.now(), description, agent, null, null);
    }

    public static <T> PrimitiveTask<T> primitive(String description, AgentRef agent, Consumer<T> effect) {
        return new PrimitiveTask<>(generateId(), Instant.now(), description, agent, null, effect);
    }

    public static <T> PlannedTask<T> planned(String description, AgentRef agent) {
        return new PlannedTask<>(generateId(), Instant.now(), description, agent, null);
    }

    public static <T> PlannedTask<T> planned(String description, AgentRef agent, @Nullable String rationale) {
        return new PlannedTask<>(generateId(), Instant.now(), description, agent, rationale);
    }

    // ── compound factories ──────────────────────────────────────────────────

    @SafeVarargs
    public static <T> TaskNode.CompoundTask<T> compound(String name, DecompositionMethod<T>... methods) {return new TaskNode.CompoundTask<>(generateId(), name, List.of(methods));}

    @SafeVarargs
    public static <T> TaskNode.CompoundTask<T> compound(String name, TaskNode<T>... children) {
        return new TaskNode.CompoundTask<>(generateId(), name, List.of(
                new DecompositionMethod<>(x -> true, new SequenceStrategy<>(List.of(children)), null)));
    }

    // ── decomposition method factories ──────────────────────────────────────

    @SafeVarargs
    public static <T> DecompositionMethod<T> decompose(TaskNode.LeafTask<T>... subtasks) {
        var tasks = List.of(subtasks);
        return new DecompositionMethod<>(x -> true,
                                         (ignored, ctx) -> Uni.createFrom().item(DagPlan.sequence(tasks)), null);
    }

    @SafeVarargs
    public static <T> DecompositionMethod<T> decompose(Predicate<T> guard, TaskNode.LeafTask<T>... subtasks) {
        var tasks = List.of(subtasks);
        return new DecompositionMethod<>(guard,
                                         (ignored, ctx) -> Uni.createFrom().item(DagPlan.sequence(tasks)), null);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private static String generateId() {
        return UUID.randomUUID().toString();
    }
}
