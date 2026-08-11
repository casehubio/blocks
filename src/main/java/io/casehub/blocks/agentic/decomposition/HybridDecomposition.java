package io.casehub.blocks.agentic.decomposition;

import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.casehub.platform.agent.AgentProvider;


import java.util.Objects;
import java.util.function.Function;

public class HybridDecomposition<T> implements DecompositionStrategy<T> {

    private static final System.Logger LOG = System.getLogger(HybridDecomposition.class.getName());

    private final DecompositionStrategy<T> primaryStrategy;
    private final DecompositionStrategy<T> fallbackStrategy;

    public HybridDecomposition(AgentProvider agentProvider) {
        this(new StaticDecomposition<>(), new LlmDecomposition<>(agentProvider));
    }

    public HybridDecomposition(AgentProvider agentProvider, int maxDepth) {
        this(new StaticDecomposition<>(), new LlmDecomposition<>(agentProvider, maxDepth));
    }


    public HybridDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this(new StaticDecomposition<>(), new LlmDecomposition<>(agentProvider, stateRenderer));
    }

    public HybridDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer,
                               int maxDepth) {
        this(new StaticDecomposition<>(), new LlmDecomposition<>(agentProvider, stateRenderer, maxDepth));
    }


    public HybridDecomposition(DecompositionStrategy<T> primaryStrategy,
                                DecompositionStrategy<T> fallbackStrategy) {
        this.primaryStrategy = Objects.requireNonNull(primaryStrategy, "primaryStrategy");
        this.fallbackStrategy = Objects.requireNonNull(fallbackStrategy, "fallbackStrategy");
    }

    @Override
    public DagPlan<TaskNode.LeafTask<T>> decompose(TaskNode<T> compound,
                                                   DecompositionContext<T> context) {
        try {
            var plan = primaryStrategy.decompose(compound, context);
            LOG.log(System.Logger.Level.DEBUG,
                    "Primary strategy succeeded for ''{0}''", taskName(compound));
            return plan;
        } catch (NoMethodMatchedException nmme) {
            LOG.log(System.Logger.Level.INFO,
                    "No static method matched for ''{0}'' ({1} evaluated) — falling back to LLM",
                    nmme.taskName(), nmme.methodCount());

            var fallbackCtx = enrichContext(context, nmme);
            var plan = fallbackStrategy.decompose(compound, fallbackCtx);
            LOG.log(System.Logger.Level.DEBUG,
                    "Fallback produced plan with {0} task(s) for ''{1}''",
                    plan.nodes().size(), nmme.taskName());
            return plan;
        }
    }


    private DecompositionContext<T> enrichContext(DecompositionContext<T> context,
                                                  NoMethodMatchedException failure) {
        var hint = failure.methodCount() + " static method(s) evaluated, none matched for '"
                   + failure.taskName() + "'";
        if (context instanceof AgenticDecompositionContext<T> ac) {
            return new AgenticDecompositionContext<>(ac.state(), ac.agents(), ac.depth(), hint,
                                                     ac.subtaskDescription(), ac.parentGoal(), ac.siblingNames(), ac.decomposer(),
                                                     ac.planningConstraints());
        }
        return context;
    }

    private static <T> String taskName(TaskNode<T> node) {
        return node instanceof TaskNode.CompoundTask<T> ct ? ct.name() : "(leaf)";
    }
}
