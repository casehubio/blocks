package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.engine.plan.TaskNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import static io.casehub.blocks.agentic.decomposition.Tasks.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForwardReasoningDecompositionTest {

    static class MutableState {
        final Map<String, String> values = new HashMap<>();

        MutableState copy() {
            var c = new MutableState();
            c.values.putAll(values);
            return c;
        }
    }

    private static final UnaryOperator<MutableState> COPIER = MutableState::copy;

    private static AgentRef agent(String name) {
        return AgentRef.external(name, s ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static AgenticDecompositionContext<MutableState> ctx(MutableState state) {
        return new AgenticDecompositionContext<>(state, java.util.List.of(), 0);
    }

    @Test
    void effectsInfluenceDownstreamGuards() {
        PrimitiveTask<MutableState> setType = primitive(agent("setType"),
                s -> s.values.put("type", "DIGITAL"));
        PrimitiveTask<MutableState> sendLink = primitive(agent("sendLink"));
        PrimitiveTask<MutableState> manualReview = primitive(agent("manualReview"));

        TaskNode.CompoundTask<MutableState> tree = compound("process",
                setType,
                compound("fulfill",
                        decompose(s -> "DIGITAL".equals(s.values.get("type")), sendLink),
                        decompose(manualReview)));

        var state = new MutableState();
        var decomp = new ForwardReasoningDecomposition<>(COPIER);
        var plan = decomp.decompose(tree, ctx(state)).await().indefinitely();

        var sorted = plan.topologicalSort();
        assertThat(sorted).hasSize(2);
        assertThat(sorted.get(0).task().executor().name()).isEqualTo("setType");
        assertThat(sorted.get(1).task().executor().name()).isEqualTo("sendLink");
    }

    @Test
    void withoutForwardReasoning_fallsThrough() {
        PrimitiveTask<MutableState> setType = primitive(agent("setType"),
                s -> s.values.put("type", "DIGITAL"));
        PrimitiveTask<MutableState> sendLink = primitive(agent("sendLink"));
        PrimitiveTask<MutableState> manualReview = primitive(agent("manualReview"));

        TaskNode.CompoundTask<MutableState> tree = compound("process",
                setType,
                compound("fulfill",
                        decompose(s -> "DIGITAL".equals(s.values.get("type")), sendLink),
                        decompose(manualReview)));

        var state = new MutableState();
        var decomp = new StaticDecomposition<MutableState>();
        var plan = decomp.decompose(tree, ctx(state)).await().indefinitely();

        var sorted = plan.topologicalSort();
        assertThat(sorted).hasSize(2);
        assertThat(sorted.get(0).task().executor().name()).isEqualTo("setType");
        assertThat(sorted.get(1).task().executor().name()).isEqualTo("manualReview");
    }

    @Test
    void originalStateUnmodified() {
        PrimitiveTask<MutableState> setType = primitive(agent("setType"),
                s -> s.values.put("type", "DIGITAL"));

        TaskNode.CompoundTask<MutableState> tree = compound("process", setType);
        var state = new MutableState();
        new ForwardReasoningDecomposition<>(COPIER).decompose(tree, ctx(state)).await().indefinitely();

        assertThat(state.values).isEmpty();
    }

    @Test
    void leafWithoutEffect_doesNotThrow() {
        PrimitiveTask<MutableState> noEffect = primitive(agent("plain"));
        TaskNode.CompoundTask<MutableState> tree = compound("process", noEffect);

        var plan = new ForwardReasoningDecomposition<>(COPIER)
                .decompose(tree, ctx(new MutableState())).await().indefinitely();
        assertThat(plan.nodes()).hasSize(1);
    }

    @Test
    void noMatchingMethod_throws() {
        TaskNode.CompoundTask<MutableState> tree = compound("never",
                decompose(s -> false, primitive(agent("unreachable"))));

        assertThatThrownBy(() -> new ForwardReasoningDecomposition<>(COPIER)
                .decompose(tree, ctx(new MutableState())).await().indefinitely())
                .isInstanceOf(NoMethodMatchedException.class);
    }

    @Test
    void chainedEffects_accumulateCorrectly() {
        PrimitiveTask<MutableState> step1 = primitive(agent("step1"),
                s -> s.values.put("a", "1"));
        PrimitiveTask<MutableState> step2 = primitive(agent("step2"),
                s -> s.values.put("b", "2"));
        PrimitiveTask<MutableState> guarded = primitive(agent("guarded"));

        TaskNode.CompoundTask<MutableState> tree = compound("chain",
                step1, step2,
                compound("check",
                        decompose(s -> "1".equals(s.values.get("a")) && "2".equals(s.values.get("b")),
                                guarded)));

        var plan = new ForwardReasoningDecomposition<>(COPIER)
                .decompose(tree, ctx(new MutableState())).await().indefinitely();
        assertThat(plan.topologicalSort()).hasSize(3);
        assertThat(plan.topologicalSort().get(2).task().executor().name()).isEqualTo("guarded");
    }
}
