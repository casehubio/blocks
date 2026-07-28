package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.function.Function;

public class HybridDecomposition<T> implements DecompositionStrategy<T> {

    private static final System.Logger LOG = System.getLogger(HybridDecomposition.class.getName());

    private final DecompositionStrategy<T> primaryStrategy;
    private final DecompositionStrategy<T> fallbackStrategy;

    public HybridDecomposition(AgentProvider agentProvider) {
        this(new StaticDecomposition<>(), new LlmDecomposition<>(agentProvider));
    }

    public HybridDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this(new StaticDecomposition<>(), new LlmDecomposition<>(agentProvider, stateRenderer));
    }

    public HybridDecomposition(DecompositionStrategy<T> primaryStrategy,
                                DecompositionStrategy<T> fallbackStrategy) {
        this.primaryStrategy = Objects.requireNonNull(primaryStrategy, "primaryStrategy");
        this.fallbackStrategy = Objects.requireNonNull(fallbackStrategy, "fallbackStrategy");
    }

    @Override
    public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> compound,
                                                        DecompositionContext<T> context) {
        return primaryStrategy.decompose(compound, context)
                              .invoke(plan -> LOG.log(System.Logger.Level.DEBUG,
                                                      "Primary strategy succeeded for ''{0}''", taskName(compound)))
                              .onFailure(NoMethodMatchedException.class)
                              .recoverWithUni(failure -> {
                                  var taskName = ((NoMethodMatchedException) failure).taskName();
                                  LOG.log(System.Logger.Level.INFO,
                                          "No static method matched for ''{0}'' — falling back to LLM", taskName);
                                  if (context instanceof AgenticDecompositionContext<T> ac && ac.agents().isEmpty()) {
                                      LOG.log(System.Logger.Level.WARNING,
                                              "Fallback for task ''{0}'' has no agents — call .agents() on the builder",
                                              taskName);
                                  }
                                  return fallbackStrategy.decompose(compound, context)
                                                         .invoke(plan -> LOG.log(System.Logger.Level.DEBUG,
                                                                                 "Fallback produced plan with {0} task(s) for ''{1}''",
                                                                                 plan.nodes().size(), taskName));
                              });
    }

    private static <T> String taskName(TaskNode<T> node) {
        return node instanceof TaskNode.CompoundTask<T> ct ? ct.name() : "(leaf)";
    }
}
