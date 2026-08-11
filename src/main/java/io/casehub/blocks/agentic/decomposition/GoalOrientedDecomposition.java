package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.engine.plan.DagNode;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.JoinType;
import io.casehub.engine.plan.TaskNode;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GoalOrientedDecomposition<T> implements DecompositionStrategy<T> {

  @Override
  public String id() {
    return "goap";
  }

  @Override
  public DagPlan<TaskNode.LeafTask<T>> decompose(TaskNode<T> task,
                                                  DecompositionContext<T> context) {
    if (task instanceof TaskNode.LeafTask<T> leaf) {
      return DagPlan.singleton(leaf);
    }

    if (!(context instanceof GoapDecompositionContext<T> goapCtx)) {
      throw new IllegalArgumentException(
          "GoalOrientedDecomposition requires GoapDecompositionContext");
    }

    var compound = (TaskNode.CompoundTask<T>) task;
    return plan(compound.name(), goapCtx);
  }

  private DagPlan<TaskNode.LeafTask<T>> plan(String taskName, GoapDecompositionContext<T> ctx) {
    List<GoapAction> actions = buildActions(ctx.agents());
    if (actions.isEmpty()) {
      throw new NoMethodMatchedException(taskName);
    }

    Set<String> unsatisfied = new LinkedHashSet<>(ctx.goalTypes());
    unsatisfied.removeAll(ctx.availableTypes());

    if (unsatisfied.isEmpty()) {
      throw new IllegalStateException(
          "All goal types already satisfied — no decomposition needed for '" + taskName + "'");
    }

    Map<String, GoapAction> typeProducers = new java.util.HashMap<>();
    List<GoapAction> selectedActions = new ArrayList<>();
    Set<GoapAction> alreadyInPlan = new HashSet<>();

    while (!unsatisfied.isEmpty()) {
      String needed = unsatisfied.iterator().next();
      unsatisfied.remove(needed);

      if (typeProducers.containsKey(needed)) continue;

      GoapAction producer = findProducer(needed, actions);
      if (producer == null) {
        throw new NoMethodMatchedException(taskName);
      }

      for (String effect : producer.effects) {
        typeProducers.putIfAbsent(effect, producer);
      }

      if (alreadyInPlan.add(producer)) {
        selectedActions.add(producer);
        for (String pre : producer.preconditions) {
          if (!ctx.availableTypes().contains(pre) && !typeProducers.containsKey(pre)) {
            unsatisfied.add(pre);
          }
        }
      }
    }

    return buildDag(selectedActions, typeProducers, ctx.availableTypes());
  }

  private List<GoapAction> buildActions(List<RoutingCandidate> agents) {
    var actions = new ArrayList<GoapAction>();
    for (var candidate : agents) {
      if (candidate.descriptor() == null) continue;
      if (candidate.descriptor().capabilities() == null) continue;
      for (var cap : candidate.descriptor().capabilities()) {
        if (cap.outputTypes() == null || cap.outputTypes().isEmpty()) continue;
        var preconditions = cap.inputTypes() != null ? Set.copyOf(cap.inputTypes()) : Set.<String>of();
        var effects = Set.copyOf(cap.outputTypes());
        actions.add(new GoapAction(candidate, cap, preconditions, effects));
      }
    }
    return actions;
  }

  private static @Nullable GoapAction findProducer(String type, List<GoapAction> actions) {
    GoapAction best = null;
    for (var action : actions) {
      if (action.effects.contains(type)) {
        if (best == null) {
          best = action;
        } else if (action.capability.qualityHint() != null
            && (best.capability.qualityHint() == null
                || action.capability.qualityHint() > best.capability.qualityHint())) {
          best = action;
        }
      }
    }
    return best;
  }

  private DagPlan<TaskNode.LeafTask<T>> buildDag(List<GoapAction> selectedActions,
                                                  Map<String, GoapAction> typeProducers,
                                                  Set<String> availableTypes) {
    IdentityHashMap<GoapAction, String> actionIds = new IdentityHashMap<>();
    for (int i = 0; i < selectedActions.size(); i++) {
      actionIds.put(selectedActions.get(i), "goap-" + i);
    }

    List<DagNode<TaskNode.LeafTask<T>>> dagNodes = new ArrayList<>();
    for (var action : selectedActions) {
      String id = actionIds.get(action);
      Set<String> deps = new HashSet<>();
      for (String pre : action.preconditions) {
        if (!availableTypes.contains(pre)) {
          GoapAction depAction = typeProducers.get(pre);
          if (depAction != null && depAction != action) {
            String depId = actionIds.get(depAction);
            if (depId != null) deps.add(depId);
          }
        }
      }

      String description = action.capability.description() != null
          ? action.capability.description() : action.capability.name();
      var leafTask = new PlannedTask<T>(
          UUID.randomUUID().toString(), Instant.now(), description, action.candidate.ref(),
          "GOAP: produces " + String.join(", ", action.effects));
      dagNodes.add(new DagNode<>(id, leafTask, deps, JoinType.ALL_OF));
    }

    return DagPlan.fromNodes(dagNodes);
  }

  private record GoapAction(RoutingCandidate candidate, AgentCapability capability,
                             Set<String> preconditions, Set<String> effects) {}
}
