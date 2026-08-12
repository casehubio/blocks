package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.TaskNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityDependencyDecompositionTest {

  private final CapabilityDependencyDecomposition<String> goap = new CapabilityDependencyDecomposition<>();

  @Test
  void singleStepPlan() {
    var agent = candidate("analyst",
        capability("analyse", List.of("raw-data"), List.of("risk-score")));
    var ctx = goapCtx(List.of(agent), Set.of("risk-score"), Set.of("raw-data"));

    var plan = decompose(ctx);

    assertThat(plan.nodes()).hasSize(1);
    var task = soleTask(plan);
    assertThat(task.agent().name()).isEqualTo("analyst");
    assertThat(task.rationale()).contains("risk-score");
  }

  @Test
  void linearChain() {
    var enricher = candidate("enricher",
        capability("enrich", List.of("raw-data"), List.of("enriched-data")));
    var scorer = candidate("scorer",
        capability("score", List.of("enriched-data"), List.of("risk-score")));
    var ctx = goapCtx(List.of(enricher, scorer), Set.of("risk-score"), Set.of("raw-data"));

    var plan = decompose(ctx);

    assertThat(plan.nodes()).hasSize(2);
    var sorted = plan.topologicalSort();
    assertThat(sorted.get(0).task()).isInstanceOf(PlannedTask.class);
    assertThat(((PlannedTask<?>) sorted.get(0).task()).agent().name()).isEqualTo("enricher");
    assertThat(((PlannedTask<?>) sorted.get(1).task()).agent().name()).isEqualTo("scorer");
    assertThat(sorted.get(1).dependsOn()).containsExactly(sorted.get(0).id());
  }

  @Test
  void parallelPlan() {
    var agentA = candidate("agent-a",
        capability("produce-x", List.of("input"), List.of("type-x")));
    var agentB = candidate("agent-b",
        capability("produce-y", List.of("input"), List.of("type-y")));
    var ctx = goapCtx(List.of(agentA, agentB),
        Set.of("type-x", "type-y"), Set.of("input"));

    var plan = decompose(ctx);

    assertThat(plan.nodes()).hasSize(2);
    assertThat(plan.entryNodeIds()).hasSize(2);
    plan.nodes().values().forEach(node ->
        assertThat(node.dependsOn()).isEmpty());
  }

  @Test
  void diamondPlan() {
    var agentB = candidate("agent-b",
        capability("produce-y", List.of("input"), List.of("type-y")));
    var agentC = candidate("agent-c",
        capability("produce-z", List.of("input"), List.of("type-z")));
    var agentA = candidate("agent-a",
        capability("combine", List.of("type-y", "type-z"), List.of("result")));
    var ctx = goapCtx(List.of(agentA, agentB, agentC),
        Set.of("result"), Set.of("input"));

    var plan = decompose(ctx);

    assertThat(plan.nodes()).hasSize(3);
    assertThat(plan.entryNodeIds()).hasSize(2);
    assertThat(plan.exitNodeIds()).hasSize(1);

    var exitNode = plan.nodes().get(plan.exitNodeIds().iterator().next());
    assertThat(exitNode.dependsOn()).hasSize(2);
  }

  @Test
  void noPathThrows() {
    var agent = candidate("agent",
        capability("irrelevant", List.of("a"), List.of("b")));
    var ctx = goapCtx(List.of(agent), Set.of("needed-type"), Set.of("input"));
    var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "goal", List.of());

    assertThatThrownBy(() -> goap.decompose(compound, ctx))
        .isInstanceOf(NoMethodMatchedException.class)
        .hasMessageContaining("goal");
  }

  @Test
  void noAgentsWithCapabilitiesThrows() {
    var agent = candidateNoDescriptor("bare-agent");
    var ctx = goapCtx(List.of(agent), Set.of("needed"), Set.of("input"));
    var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "goal", List.of());

    assertThatThrownBy(() -> goap.decompose(compound, ctx))
        .isInstanceOf(NoMethodMatchedException.class);
  }

  @Test
  void leafTaskPassesThroughUnchanged() {
    var agent = AgentRef.external(s -> CompletableFuture.completedFuture(null));
    var leaf = new PrimitiveTask<String>("id", java.time.Instant.now(), null, agent, null, null);
    var ctx = goapCtx(List.of(), Set.of("x"), Set.of());

    var plan = goap.decompose(leaf, ctx);

    assertThat(plan.nodes()).hasSize(1);
    assertThat(plan.topologicalSort().get(0).task()).isSameAs(leaf);
  }

  @Test
  void requiresGoapContext() {
    var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "goal", List.of());
    var plainCtx = new AgenticDecompositionContext<>("state", List.of(), 0);

    assertThatThrownBy(() -> goap.decompose(compound, plainCtx))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GoapDecompositionContext");
  }

  @Test
  void goalAlreadySatisfiedThrows() {
    var agent = candidate("agent",
        capability("produce", List.of("a"), List.of("b")));
    var ctx = goapCtx(List.of(agent), Set.of("already-here"), Set.of("already-here"));
    var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "trivial", List.of());

    assertThatThrownBy(() -> goap.decompose(compound, ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already satisfied");
  }

  @Test
  void multipleOutputsFromSingleCapability() {
    var agent = candidate("multi-producer",
        capability("produce-both", List.of("input"), List.of("type-a", "type-b")));
    var ctx = goapCtx(List.of(agent), Set.of("type-a", "type-b"), Set.of("input"));

    var plan = decompose(ctx);

    assertThat(plan.nodes()).hasSize(1);
  }

  @Test
  void prefersHigherQualityCapability() {
    var lowQ = candidate("low-quality",
        capabilityWithQuality("produce", List.of("input"), List.of("result"), 0.3));
    var highQ = candidate("high-quality",
        capabilityWithQuality("produce", List.of("input"), List.of("result"), 0.9));
    var ctx = goapCtx(List.of(lowQ, highQ), Set.of("result"), Set.of("input"));

    var plan = decompose(ctx);

    assertThat(plan.nodes()).hasSize(1);
    var task = soleTask(plan);
    assertThat(task.agent().name()).isEqualTo("high-quality");
  }

  @Test
  void agentWithNullOutputTypesIsSkipped() {
    var noOutputs = candidate("no-outputs",
        AgentCapability.builder().name("cap").build());
    var producer = candidate("producer",
        capability("produce", List.of("input"), List.of("result")));
    var ctx = goapCtx(List.of(noOutputs, producer), Set.of("result"), Set.of("input"));

    var plan = decompose(ctx);

    assertThat(plan.nodes()).hasSize(1);
    assertThat(soleTask(plan).agent().name()).isEqualTo("producer");
  }

  @Test
  void capabilityWithNoPreconditions() {
    var agent = candidate("generator",
        capability("generate", List.of(), List.of("result")));
    var ctx = goapCtx(List.of(agent), Set.of("result"), Set.of());

    var plan = decompose(ctx);

    assertThat(plan.nodes()).hasSize(1);
    var node = plan.nodes().values().iterator().next();
    assertThat(node.dependsOn()).isEmpty();
  }

  @Test
  void idIsGoap() {
    assertThat(goap.id()).isEqualTo("capability-dependency");
  }

  // --- helpers ---

  private DagPlan<TaskNode.LeafTask<String>> decompose(CapabilityDependencyContext<String> ctx) {
    var compound = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "goal", List.of());
    return goap.decompose(compound, ctx);
  }

  private static PlannedTask<?> soleTask(DagPlan<TaskNode.LeafTask<String>> plan) {
    return (PlannedTask<?>) plan.topologicalSort().get(0).task();
  }

  private static CapabilityDependencyContext<String> goapCtx(List<RoutingCandidate> agents,
                                                             Set<String> goalTypes,
                                                             Set<String> availableTypes) {
    return new CapabilityDependencyContext<>("state", agents, 0, goalTypes, availableTypes);
  }

  private static RoutingCandidate candidate(String name, AgentCapability... caps) {
    var ref = AgentRef.external(name, s -> CompletableFuture.completedFuture(null));
    var descriptor = AgentDescriptor.builder()
        .agentId(name).name(name).slot("test").tenancyId("t1")
        .capabilities(List.of(caps))
        .build();
    return new RoutingCandidate(ref, descriptor);
  }

  private static RoutingCandidate candidateNoDescriptor(String name) {
    var ref = AgentRef.external(name, s -> CompletableFuture.completedFuture(null));
    return new RoutingCandidate(ref, null);
  }

  private static AgentCapability capability(String name, List<String> inputs, List<String> outputs) {
    return AgentCapability.builder().name(name)
        .inputTypes(inputs).outputTypes(outputs).build();
  }

  private static AgentCapability capabilityWithQuality(String name, List<String> inputs,
                                                        List<String> outputs, double quality) {
    return AgentCapability.builder().name(name)
        .inputTypes(inputs).outputTypes(outputs).qualityHint(quality).build();
  }
}
