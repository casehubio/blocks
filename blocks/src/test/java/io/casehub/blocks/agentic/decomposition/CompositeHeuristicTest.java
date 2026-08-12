package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class CompositeHeuristicTest {

    private static AgentRef agent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static PrimitiveTask<String> leaf(String id) {
        return new PrimitiveTask<>(id, Instant.now(), id, agent(), null, null);
    }

    private static AgenticDecompositionContext<String> ctx() {
        return new AgenticDecompositionContext<>("state", List.of(), 0);
    }

    private static DecompositionMethod<String> method() {
        return new DecompositionMethod<>(s -> true,
                new SequenceStrategy<>(List.of(leaf("x"))), null);
    }

    @Test
    void combinesTwoDelegatesWithDifferentScales() {
        var m1 = method();
        var m2 = method();
        var methods = List.of(m1, m2);

        DecompositionHeuristic<String> costBased = (task, ms, ctx) ->
                List.of(
                        new ScoredMethod<>(ms.get(0), -3.0),
                        new ScoredMethod<>(ms.get(1), -1.0));

        DecompositionHeuristic<String> llmBased = (task, ms, ctx) ->
                List.of(
                        new ScoredMethod<>(ms.get(0), 0.2),
                        new ScoredMethod<>(ms.get(1), 0.8));

        var composite = new CompositeHeuristic<>(List.of(
                new CompositeHeuristic.WeightedHeuristic<>(costBased, 0.5),
                new CompositeHeuristic.WeightedHeuristic<>(llmBased, 0.5)));

        var scored = composite.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx());

        assertThat(scored).hasSize(2);
        var m1Score = scored.stream().filter(s -> s.method() == m1).findFirst().orElseThrow().score();
        var m2Score = scored.stream().filter(s -> s.method() == m2).findFirst().orElseThrow().score();
        assertThat(m2Score).isGreaterThan(m1Score);
    }

    @Test
    void allEqualScores_normalizeToHalf() {
        var m1 = method();
        var m2 = method();
        var methods = List.of(m1, m2);

        DecompositionHeuristic<String> flat = (task, ms, ctx) ->
                List.of(
                        new ScoredMethod<>(ms.get(0), 5.0),
                        new ScoredMethod<>(ms.get(1), 5.0));

        DecompositionHeuristic<String> varied = (task, ms, ctx) ->
                List.of(
                        new ScoredMethod<>(ms.get(0), 0.2),
                        new ScoredMethod<>(ms.get(1), 0.8));

        var composite = new CompositeHeuristic<>(List.of(
                new CompositeHeuristic.WeightedHeuristic<>(flat, 1.0),
                new CompositeHeuristic.WeightedHeuristic<>(varied, 1.0)));

        var scored = composite.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx());

        var m1Score = scored.stream().filter(s -> s.method() == m1).findFirst().orElseThrow().score();
        var m2Score = scored.stream().filter(s -> s.method() == m2).findFirst().orElseThrow().score();
        assertThat(m2Score).isGreaterThan(m1Score);
        assertThat(m1Score).isCloseTo(0.25, offset(0.001));
        assertThat(m2Score).isCloseTo(0.75, offset(0.001));
    }

    @Test
    void weightsRespected() {
        var m1 = method();
        var m2 = method();
        var methods = List.of(m1, m2);

        DecompositionHeuristic<String> prefersM1 = (task, ms, ctx) ->
                List.of(
                        new ScoredMethod<>(ms.get(0), 1.0),
                        new ScoredMethod<>(ms.get(1), 0.0));

        DecompositionHeuristic<String> prefersM2 = (task, ms, ctx) ->
                List.of(
                        new ScoredMethod<>(ms.get(0), 0.0),
                        new ScoredMethod<>(ms.get(1), 1.0));

        var composite = new CompositeHeuristic<>(List.of(
                new CompositeHeuristic.WeightedHeuristic<>(prefersM1, 9.0),
                new CompositeHeuristic.WeightedHeuristic<>(prefersM2, 1.0)));

        var scored = composite.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx());

        var m1Score = scored.stream().filter(s -> s.method() == m1).findFirst().orElseThrow().score();
        var m2Score = scored.stream().filter(s -> s.method() == m2).findFirst().orElseThrow().score();
        assertThat(m1Score).isGreaterThan(m2Score);
    }

    @Test
    void delegateViolatesCompletenessContract_failsFast() {
        var methods = List.of(method(), method());

        DecompositionHeuristic<String> broken = (task, ms, ctx) ->
                List.of(new ScoredMethod<>(ms.get(0), 1.0));

        var composite = new CompositeHeuristic<>(List.of(
                new CompositeHeuristic.WeightedHeuristic<>(broken, 1.0)));

        assertThatThrownBy(() -> composite.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completeness contract");
    }

    @Test
    void singleDelegate_passthrough() {
        var m1 = method();
        var methods = List.of(m1);

        DecompositionHeuristic<String> single = (task, ms, ctx) ->
                List.of(new ScoredMethod<>(ms.get(0), 0.7));

        var composite = new CompositeHeuristic<>(List.of(
                new CompositeHeuristic.WeightedHeuristic<>(single, 1.0)));

        var scored = composite.evaluate(
                new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "root", methods), methods, ctx());

        assertThat(scored).hasSize(1);
        assertThat(scored.get(0).score()).isEqualTo(0.5);
    }
}
