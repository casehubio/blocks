package io.casehub.blocks.agentic.decomposition;

import io.casehub.api.model.TaskDescriptor;
import io.casehub.api.model.TaskStatus;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskNodeTest {

    private static final Instant NOW = Instant.parse("2026-07-12T10:00:00Z");

    private static AgentRef dummyAgent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    @Nested
    class PrimitiveTaskVariant {
        @Test
        void carriesAllFields() {
            var agent = dummyAgent();
            var task  = new PrimitiveTask<String>("p1", NOW, "build it", agent, s -> true, s -> {});
            assertThat(task.id()).isEqualTo("p1");
            assertThat(task.createdAt()).isEqualTo(NOW);
            assertThat(task.description()).isEqualTo("build it");
            assertThat(task.agent()).isEqualTo(agent);
            assertThat(task.precondition().test("anything")).isTrue();
        }

        @Test
        void nullDescriptionAllowed() {
            var agent = dummyAgent();
            var task  = new PrimitiveTask<String>("p2", NOW, null, agent, null, null);
            assertThat(task.description()).isNull();
            assertThat(task.precondition()).isNull();
            assertThat(task.effect()).isNull();
        }

        @Test
        void rejectsNullId() {
            assertThatThrownBy(() -> new PrimitiveTask<String>(null, NOW, null, dummyAgent(), null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullCreatedAt() {
            assertThatThrownBy(() -> new PrimitiveTask<String>("p1", null, null, dummyAgent(), null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullAgent() {
            assertThatThrownBy(() -> new PrimitiveTask<String>("p1", NOW, null, null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class CompoundTaskVariant {
        @Test
        void carriesNameAndMethods() {
            var method = new DecompositionMethod<String>(s -> true, new IdentityDecomposition<>(), null);
            var task   = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "analyse", List.of(method));
            assertThat(task.name()).isEqualTo("analyse");
            assertThat(task.methods()).hasSize(1);
        }

        @Test
        void methodsListIsDefensivelyCopied() {
            var methods = new java.util.ArrayList<DecompositionMethod<String>>();
            methods.add(new DecompositionMethod<>(s -> true, new IdentityDecomposition<>(), null));
            var task = new TaskNode.CompoundTask<>(java.util.UUID.randomUUID().toString(), "t", methods);
            methods.clear();
            assertThat(task.methods()).hasSize(1);
        }
    }

    @Nested
    class PlannedTaskVariant {
        @Test
        void carriesAllFields() {
            var agent = dummyAgent();
            var task  = new PlannedTask<String>("t1", NOW, "analyse data", agent, "best fit for domain");
            assertThat(task.id()).isEqualTo("t1");
            assertThat(task.createdAt()).isEqualTo(NOW);
            assertThat(task.description()).isEqualTo("analyse data");
            assertThat(task.agent()).isSameAs(agent);
            assertThat(task.rationale()).isEqualTo("best fit for domain");
        }

        @Test
        void nullRationaleAllowed() {
            var agent = dummyAgent();
            var task  = new PlannedTask<String>("t2", NOW, "analyse data", agent, null);
            assertThat(task.rationale()).isNull();
        }

        @Test
        void rejectsNullDescription() {
            assertThatThrownBy(() -> new PlannedTask<String>("t1", NOW, null, dummyAgent(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullAgent() {
            assertThatThrownBy(() -> new PlannedTask<String>("t1", NOW, "desc", null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullId() {
            assertThatThrownBy(() -> new PlannedTask<String>(null, NOW, "desc", dummyAgent(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullCreatedAt() {
            assertThatThrownBy(() -> new PlannedTask<String>("t1", null, "desc", dummyAgent(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class TaskDescriptorContract {
        @Test
        void primitiveTaskImplementsTaskDescriptor() {
            var agent = dummyAgent();
            var task  = new PrimitiveTask<String>("p1", NOW, "do work", agent, null, null);
            assertThat(task).isInstanceOf(TaskDescriptor.class);
            assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
            assertThat(task.executor()).isSameAs(agent);
        }

        @Test
        void plannedTaskImplementsTaskDescriptor() {
            var agent = dummyAgent();
            var task  = new PlannedTask<String>("t1", NOW, "analyse", agent, null);
            assertThat(task).isInstanceOf(TaskDescriptor.class);
            assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
            assertThat(task.executor()).isSameAs(agent);
        }

        @Test
        void executorDelegatesToAgent() {
            var            agent = dummyAgent();
            TaskDescriptor desc  = new PlannedTask<String>("t1", NOW, "task", agent, null);
            assertThat(desc.executor()).isSameAs(agent);
            assertThat(desc.executor().name()).isEqualTo("external");
        }

        @Test
        void snapshotCapturesAllFields() {
            var worker = Worker.builder().name("reviewer").capabilityNames("review")
                               .description("Reviews data")
                               .function(x -> WorkerResult.of(Map.of())).build();
            var agent    = AgentRef.worker(worker);
            var task     = new PlannedTask<String>("snap-1", NOW, "review step", agent, null);
            var snapshot = task.snapshot();
            assertThat(snapshot.id()).isEqualTo("snap-1");
            assertThat(snapshot.description()).isEqualTo("review step");
            assertThat(snapshot.executorName()).isEqualTo("reviewer");
            assertThat(snapshot.executorDescription()).isEqualTo("Reviews data");
            assertThat(snapshot.status()).isEqualTo(TaskStatus.PENDING);
            assertThat(snapshot.createdAt()).isEqualTo(NOW);
        }
    }

    @Nested
    class LeafTaskContract {
        @Test
        void leafTaskAgentAccessorWorksForPrimitive() {
            var                       agent = dummyAgent();
            TaskNode.LeafTask<String> leaf  = new PrimitiveTask<>("p1", NOW, null, agent, null, null);
            assertThat(leaf.executor()).isSameAs(agent);
        }

        @Test
        void leafTaskAgentAccessorWorksForPlanned() {
            var                       agent = dummyAgent();
            TaskNode.LeafTask<String> leaf  = new PlannedTask<>("t1", NOW, "do analysis", agent, null);
            assertThat(leaf.executor()).isSameAs(agent);
        }

        @Test
        void leafTaskDescriptionAccessorWorksForBothVariants() {
            var                       agent       = dummyAgent();
            TaskNode.LeafTask<String> withDesc    = new PlannedTask<>("t1", NOW, "do analysis", agent, null);
            TaskNode.LeafTask<String> withoutDesc = new PrimitiveTask<>("p1", NOW, null, agent, null, null);
            assertThat(withDesc.description()).isEqualTo("do analysis");
            assertThat(withoutDesc.description()).isNull();
        }
    }

    @Test
    void sealedInterfacePermitsLeafAndCompound() {
        assertThat(TaskNode.class.getPermittedSubclasses()).hasSize(2);
        assertThat(TaskNode.LeafTask.class.isSealed()).isFalse();
        assertThat(PrimitiveTask.class).isAssignableTo(TaskNode.LeafTask.class);
        assertThat(PlannedTask.class).isAssignableTo(TaskNode.LeafTask.class);
    }
}
