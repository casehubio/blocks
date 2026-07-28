package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext;
import io.casehub.blocks.agentic.decomposition.PrimitiveTask;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.engine.plan.DagPlan;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.casehub.blocks.agentic.pattern.Patterns.htn;
import static org.assertj.core.api.Assertions.assertThat;

class HtnBuilderTest {

    @Test
    void buildsValidModel() {
        var agent = AgentRef.external((Object i) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "done")));

        var model = htn()
                .agents(agent)
                .build();

        assertThat(model.routing()).isNotNull();
        assertThat(model.decomposition()).isNotNull();
    }

    @Test
    void decomposesAndExecutesTaskTreeInOrder() {
        var log = new ArrayList<String>();
        var build = AgentRef.external((Object in) -> {
            log.add("build");
            return CompletableFuture.completedFuture(AgentResult.success(null, "built"));
        });
        var test = AgentRef.external((Object in) -> {
            log.add("test");
            return CompletableFuture.completedFuture(AgentResult.success(null, "tested"));
        });
        var deploy = AgentRef.external((Object in) -> {
            log.add("deploy");
            return CompletableFuture.completedFuture(AgentResult.success(null, "deployed"));
        });

        var rootTask = new TaskNode.CompoundTask<String>("deploy-app", List.of(
                new DecompositionMethod<String>(
                        state -> true,
                        (compound, ctx) -> Uni.createFrom().item(DagPlan.sequence(List.of(
                                new PrimitiveTask<String>("h1", java.time.Instant.now(), null, build, s -> true, s -> {}),
                                new PrimitiveTask<String>("h2", java.time.Instant.now(), null, test, s -> true, s -> {}),
                                new PrimitiveTask<String>("h3", java.time.Instant.now(), null, deploy, s -> true, s -> {})))))));

        var result = Patterns.<String>htn()
                .rootTask(rootTask)
                .task("deploy application")
                .execute("deploy v2.0")
                .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(log).containsExactly("build", "test", "deploy");
    }

    @Test
    void guardSelectsDecompositionMethodBasedOnState() {
        var log = new ArrayList<String>();
        var hotfix = AgentRef.external((Object in) -> {
            log.add("hotfix");
            return CompletableFuture.completedFuture(AgentResult.success(null, "applied"));
        });
        var fullDeploy = AgentRef.external((Object in) -> {
            log.add("full-deploy");
            return CompletableFuture.completedFuture(AgentResult.success(null, "deployed"));
        });

        var rootTask = new TaskNode.CompoundTask<String>("deploy", List.of(
                new DecompositionMethod<>(
                        (String s) -> s.contains("hotfix"),
                        (compound, ctx) -> Uni.createFrom().item(DagPlan.singleton(
                                new PrimitiveTask<>("h4", java.time.Instant.now(), null, hotfix, s -> true, s -> {})))),
                new DecompositionMethod<>(
                        (String s) -> true,
                        (compound, ctx) -> Uni.createFrom().item(DagPlan.singleton(
                                new PrimitiveTask<>("h5", java.time.Instant.now(), null, fullDeploy, s -> true, s -> {}))))));

        var result = Patterns.<String>htn()
                .rootTask(rootTask)
                .task("deploy")
                .execute("hotfix for bug #99")
                .await().indefinitely();

        assertThat(log).containsExactly("hotfix");
    }

    @Test
    void flattenDelegatesToDecompositionStrategy() {
        var log = new ArrayList<String>();
        var agent = AgentRef.external((Object in) -> {
            log.add("executed");
            return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
        });

        var customPlan = new PrimitiveTask<String>("c1", java.time.Instant.now(), null, agent, null, null);

        DecompositionStrategy<String> customStrategy = (compound, ctx) ->
                                                               Uni.createFrom().item(DagPlan.singleton(customPlan));

        var rootTask = new TaskNode.CompoundTask<String>("root", List.of(
                new DecompositionMethod<>(s -> true,
                                          (c, x) -> Uni.createFrom().failure(new AssertionError("should not be called — strategy should override")))));

        var result = Patterns.<String>htn()
                             .decompose(customStrategy)
                             .agents(agent)
                             .rootTask(rootTask)
                             .task("delegate-test")
                             .execute("state")
                             .await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        assertThat(log).containsExactly("executed");
    }

    @Test
    void agentsPassedToDecompositionContext() {
        var agent = AgentRef.external((Object in) ->
                                              CompletableFuture.completedFuture(AgentResult.success(null, "done")));
        var captured = new java.util.concurrent.atomic.AtomicReference<io.casehub.blocks.agentic.decomposition.AgenticDecompositionContext<String>>();

        DecompositionStrategy<String> capturing = (compound, ctx) -> {
            captured.set((AgenticDecompositionContext<String>) ctx);
            var leaf = new PrimitiveTask<String>("c1", java.time.Instant.now(), null, agent, null, null);
            return Uni.createFrom().item(DagPlan.singleton(leaf));
        };

        var rootTask = new TaskNode.CompoundTask<String>("root", List.of());

        Patterns.<String>htn()
                .decompose(capturing)
                .agents(agent)
                .rootTask(rootTask)
                .task("ctx-test")
                .execute("my-state")
                .await().indefinitely();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().agents()).hasSize(1);
        assertThat(captured.get().state()).isEqualTo("my-state");
    }


}
