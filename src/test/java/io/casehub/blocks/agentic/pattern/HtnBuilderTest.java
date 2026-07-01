package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.decomposition.DecompositionMethod;
import io.casehub.blocks.agentic.decomposition.TaskNode;
import io.casehub.blocks.agentic.model.ExecutionResult;
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
                        (compound, ctx) -> Uni.createFrom().item(List.of(
                                new TaskNode.PrimitiveTask<String>(build, s -> true, s -> {}),
                                new TaskNode.PrimitiveTask<String>(test, s -> true, s -> {}),
                                new TaskNode.PrimitiveTask<String>(deploy, s -> true, s -> {}))))));

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
                        (compound, ctx) -> Uni.createFrom().item(List.of(
                                new TaskNode.PrimitiveTask<>(hotfix, s -> true, s -> {})))),
                new DecompositionMethod<>(
                        (String s) -> true,
                        (compound, ctx) -> Uni.createFrom().item(List.of(
                                new TaskNode.PrimitiveTask<>(fullDeploy, s -> true, s -> {}))))));

        var result = Patterns.<String>htn()
                .rootTask(rootTask)
                .task("deploy")
                .execute("hotfix for bug #99")
                .await().indefinitely();

        assertThat(log).containsExactly("hotfix");
    }
}
