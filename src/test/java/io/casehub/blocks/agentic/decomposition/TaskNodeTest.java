package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskNodeTest {

    private static AgentRef dummyAgent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    @Nested
    class PrimitiveTaskVariant {
        @Test
        void carriesDescriptionAndAgent() {
            var agent = dummyAgent();
            var task = new TaskNode.PrimitiveTask<String>("build it", agent, s -> true, s -> {});
            assertThat(task.description()).isEqualTo("build it");
            assertThat(task.agent()).isEqualTo(agent);
            assertThat(task.precondition().test("anything")).isTrue();
        }

        @Test
        void nullDescriptionAllowed() {
            var agent = dummyAgent();
            var task = new TaskNode.PrimitiveTask<String>(null, agent, null, null);
            assertThat(task.description()).isNull();
            assertThat(task.precondition()).isNull();
            assertThat(task.effect()).isNull();
        }
    }

    @Nested
    class CompoundTaskVariant {
        @Test
        void carriesNameAndMethods() {
            var method = new DecompositionMethod<String>(s -> true, new IdentityDecomposition<>());
            var task = new TaskNode.CompoundTask<String>("analyse", List.of(method));
            assertThat(task.name()).isEqualTo("analyse");
            assertThat(task.methods()).hasSize(1);
        }

        @Test
        void methodsListIsDefensivelyCopied() {
            var methods = new java.util.ArrayList<DecompositionMethod<String>>();
            methods.add(new DecompositionMethod<>(s -> true, new IdentityDecomposition<>()));
            var task = new TaskNode.CompoundTask<>("t", methods);
            methods.clear();
            assertThat(task.methods()).hasSize(1);
        }
    }

    @Nested
    class PlannedTaskVariant {
        @Test
        void carriesDescriptionAgentAndRationale() {
            var agent = dummyAgent();
            var task = new TaskNode.PlannedTask<String>("analyse data", agent, "best fit for domain");
            assertThat(task.description()).isEqualTo("analyse data");
            assertThat(task.agent()).isSameAs(agent);
            assertThat(task.rationale()).isEqualTo("best fit for domain");
        }

        @Test
        void nullRationaleAllowed() {
            var agent = dummyAgent();
            var task = new TaskNode.PlannedTask<String>("analyse data", agent, null);
            assertThat(task.rationale()).isNull();
        }

        @Test
        void rejectsNullDescription() {
            var agent = dummyAgent();
            assertThatThrownBy(() -> new TaskNode.PlannedTask<String>(null, agent, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullAgent() {
            assertThatThrownBy(() -> new TaskNode.PlannedTask<String>("desc", null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class LeafTaskContract {
        @Test
        void leafTaskAgentAccessorWorksForPrimitive() {
            var agent = dummyAgent();
            TaskNode.LeafTask<String> leaf = new TaskNode.PrimitiveTask<>(null, agent, null, null);
            assertThat(leaf.agent()).isSameAs(agent);
        }

        @Test
        void leafTaskAgentAccessorWorksForPlanned() {
            var agent = dummyAgent();
            TaskNode.LeafTask<String> leaf = new TaskNode.PlannedTask<>("do analysis", agent, null);
            assertThat(leaf.agent()).isSameAs(agent);
        }

        @Test
        void leafTaskDescriptionAccessorWorksForBothVariants() {
            var agent = dummyAgent();
            TaskNode.LeafTask<String> withDesc = new TaskNode.PlannedTask<>("do analysis", agent, null);
            TaskNode.LeafTask<String> withoutDesc = new TaskNode.PrimitiveTask<>(null, agent, null, null);
            assertThat(withDesc.description()).isEqualTo("do analysis");
            assertThat(withoutDesc.description()).isNull();
        }
    }

    @Test
    void sealedInterfacePermitsLeafAndCompound() {
        assertThat(TaskNode.class.getPermittedSubclasses()).hasSize(2);
        assertThat(TaskNode.LeafTask.class.getPermittedSubclasses()).hasSize(2);
    }
}
