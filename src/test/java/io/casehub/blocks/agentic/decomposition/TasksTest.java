package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.casehub.blocks.agentic.decomposition.Tasks.compound;
import static io.casehub.blocks.agentic.decomposition.Tasks.decompose;
import static io.casehub.blocks.agentic.decomposition.Tasks.planned;
import static io.casehub.blocks.agentic.decomposition.Tasks.primitive;
import static org.assertj.core.api.Assertions.assertThat;

class TasksTest {

    private static AgentRef agent() {
        return AgentRef.external(s ->
                                         CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static AgentRef agent(String name) {
        return AgentRef.external(name, s ->
                                               CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static <T> AgenticDecompositionContext<T> ctx(T state) {
        return new AgenticDecompositionContext<>(state, java.util.List.of(), 0);
    }

    @Nested
    class PrimitiveFactories {
        @Test
        void primitive_agentOnly_autoGeneratesIdAndTimestamp() {
            PrimitiveTask<String> task = primitive(agent());
            assertThat(task.id()).isNotBlank();
            assertThat(task.createdAt()).isNotNull();
            assertThat(task.description()).isNull();
            assertThat(task.precondition()).isNull();
            assertThat(task.effect()).isNull();
        }

        @Test
        void primitive_withEffect_setsEffect() {
            AtomicBoolean         called = new AtomicBoolean();
            PrimitiveTask<String> task   = primitive(agent(), s -> called.set(true));
            assertThat(task.effect()).isNotNull();
            task.effect().accept("x");
            assertThat(called).isTrue();
        }

        @Test
        void primitive_withDescription_setsDescription() {
            PrimitiveTask<String> task = primitive("validate input", agent());
            assertThat(task.description()).isEqualTo("validate input");
        }

        @Test
        void primitive_withDescriptionAndEffect_setsBoth() {
            PrimitiveTask<String> task = primitive("validate", agent(), s -> {});
            assertThat(task.description()).isEqualTo("validate");
            assertThat(task.effect()).isNotNull();
        }

        @Test
        void primitive_uniqueIdsPerCall() {
            var                   a  = agent();
            PrimitiveTask<String> t1 = primitive(a);
            PrimitiveTask<String> t2 = primitive(a);
            assertThat(t1.id()).isNotEqualTo(t2.id());
        }
    }

    @Nested
    class PlannedFactories {
        @Test
        void planned_basic_setsDescriptionAndAgent() {
            PlannedTask<String> task = planned("analyse data", agent());
            assertThat(task.id()).isNotBlank();
            assertThat(task.createdAt()).isNotNull();
            assertThat(task.description()).isEqualTo("analyse data");
            assertThat(task.rationale()).isNull();
        }

        @Test
        void planned_withRationale_setsRationale() {
            PlannedTask<String> task = planned("analyse data", agent(), "best fit");
            assertThat(task.rationale()).isEqualTo("best fit");
        }
    }

    @Nested
    class CompoundFromMethods {
        @Test
        void compound_fromMethods_createsCompoundTask() {
            var                           method = new DecompositionMethod<String>(s -> true, new IdentityDecomposition<>(), null);
            TaskNode.CompoundTask<String> task   = compound("process", method);
            assertThat(task.name()).isEqualTo("process");
            assertThat(task.methods()).hasSize(1);
        }

        @Test
        void compound_fromMultipleMethods_preservesOrder() {
            var                           m1   = new DecompositionMethod<String>(s -> s.startsWith("a"), new IdentityDecomposition<>(), null);
            var                           m2   = new DecompositionMethod<String>(s -> true, new IdentityDecomposition<>(), null);
            TaskNode.CompoundTask<String> task = compound("process", m1, m2);
            assertThat(task.methods()).hasSize(2);
            assertThat(task.methods().get(0).guard().test("alpha")).isTrue();
            assertThat(task.methods().get(0).guard().test("beta")).isFalse();
        }
    }

    @Nested
    class CompoundFromChildren {
        @Test
        void compound_fromLeafChildren_singleMethodAlwaysTrue() {
            TaskNode.CompoundTask<String> task = compound("process",
                                                          primitive(agent()), primitive(agent()));
            assertThat(task.methods()).hasSize(1);
            assertThat(task.methods().get(0).guard().test("anything")).isTrue();
        }

        @Test
        void compound_fromMixedChildren_acceptsCompoundChild() {
            TaskNode.CompoundTask<String> inner = compound("inner",
                                                           decompose(Tasks.<String>primitive(agent())));
            PrimitiveTask<String>         p1    = primitive(agent());
            PrimitiveTask<String>         p2    = primitive(agent());
            TaskNode.CompoundTask<String> outer = compound("outer", p1, inner, p2);
            assertThat(outer.methods()).hasSize(1);}

        @Test
        void compound_fromChildren_strategyDecomposes() {
            PrimitiveTask<String>         a1   = primitive(agent("validate"));
            PrimitiveTask<String>         a2   = primitive(agent("confirm"));
            TaskNode.CompoundTask<String> task = compound("process", a1, a2);

            var plan = task.methods().get(0).strategy()
                           .decompose(task, ctx("state")).await().indefinitely();
            assertThat(plan.nodes()).hasSize(2);
            var sorted = plan.topologicalSort();
            assertThat(sorted.get(0).task().executor().name()).isEqualTo("validate");
            assertThat(sorted.get(1).task().executor().name()).isEqualTo("confirm");}

        @Test
        void compound_fromChildren_recursivelyDecomposesCompound() {
            PrimitiveTask<String>         leaf1 = primitive(agent("step1"));
            PrimitiveTask<String>         leaf2 = primitive(agent("step2"));
            TaskNode.CompoundTask<String> inner = compound("inner", decompose(leaf2));
            TaskNode.CompoundTask<String> outer = compound("outer", leaf1, inner);

            var plan = outer.methods().get(0).strategy()
                            .decompose(outer, ctx("state")).await().indefinitely();
            assertThat(plan.nodes()).hasSize(2);
            var sorted = plan.topologicalSort();
            assertThat(sorted.get(0).task().executor().name()).isEqualTo("step1");
            assertThat(sorted.get(1).task().executor().name()).isEqualTo("step2");}
    }

    @Nested
    class DecomposeFactories {
        @Test
        void decompose_noGuard_alwaysTrue() {
            DecompositionMethod<String> method = decompose(primitive(agent()));
            assertThat(method.guard().test("anything")).isTrue();
        }

        @Test
        void decompose_withGuard_usesGuard() {
            DecompositionMethod<String> method = decompose(
                    s -> s.startsWith("digital"), primitive(agent()));
            assertThat(method.guard().test("digital-123")).isTrue();
            assertThat(method.guard().test("physical")).isFalse();
        }

        @Test
        void decompose_multipleLeafs_producesSequentialPlan() {
            PrimitiveTask<String>       a1     = primitive(agent("first"));
            PrimitiveTask<String>       a2     = primitive(agent("second"));
            DecompositionMethod<String> method = decompose(a1, a2);

            var plan = method.strategy()
                             .decompose(null, ctx("state")).await().indefinitely();
            assertThat(plan.nodes()).hasSize(2);
            var sorted = plan.topologicalSort();
            assertThat(sorted.get(0).task().executor().name()).isEqualTo("first");
            assertThat(sorted.get(1).task().executor().name()).isEqualTo("second");}
    }
}
