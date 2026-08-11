package io.casehub.blocks.agentic.termination;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.function.Predicate;

/**
 * Termination condition that delegates convergence judgment to an external agent.
 *
 * <p>The judge receives all accumulated debate results as {@code List<AgentResult>}
 * and returns an {@link AgentResult}. The convergence predicate maps that result
 * to continue/converge — by default, {@link AgentResult.AgentResultStatus#SUCCESS SUCCESS}
 * means converged, anything else means continue.
 *
 * <p>A safety cap at {@code maxIterations} forces termination even if the judge
 * never reports convergence.
 *
 * @param <T> the execution state type (unused by the judge — the judge sees results, not state)
 */
public class JudgeConvergence<T> implements TerminationCondition<T> {

    private final AgentRef judge;
    private final AgentInvoker<List<AgentResult>> invoker;
    private final int maxIterations;
    private final Predicate<AgentResult> convergencePredicate;

    /**
     * Full constructor with explicit invoker and convergence predicate.
     */
    public JudgeConvergence(AgentRef judge,
                            AgentInvoker<List<AgentResult>> invoker,
                            int maxIterations,
                            Predicate<AgentResult> convergencePredicate) {
        this.judge = judge;
        this.invoker = invoker;
        this.maxIterations = maxIterations;
        this.convergencePredicate = convergencePredicate;
    }

    /**
     * Convenience constructor using the default invoker and default convergence predicate
     * (SUCCESS = converged, anything else = continue).
     *
     * @param judge must be an {@link AgentRef.ExternalAgent} — the default invoker only
     *              supports ExternalAgent
     * @param maxIterations safety cap
     * @throws IllegalArgumentException if judge is not an ExternalAgent
     */
    public JudgeConvergence(AgentRef judge, int maxIterations) {
        if (!(judge instanceof AgentRef.ExternalAgent)) {
            throw new IllegalArgumentException(
                    "Convenience constructor requires ExternalAgent, got: "
                            + judge.getClass().getSimpleName());
        }
        this.judge = judge;
        this.invoker = AgentInvoker.defaultInvoker();
        this.maxIterations = maxIterations;
        this.convergencePredicate = result ->
                result.status() == AgentResult.AgentResultStatus.SUCCESS;
    }

    @Override
    public Uni<TerminationDecision> evaluate(TerminationContext<T> context) {
        // Safety cap: force termination at max iterations
        if (context.iterationCount() >= maxIterations) {
            return Uni.createFrom().item(
                    new TerminationDecision.Complete("Max iterations reached (" + maxIterations + ")"));
        }

        // Invoke the judge with accumulated results
        return invoker.invoke(judge, context.results())
                .map(judgeResult -> {
                    if (convergencePredicate.test(judgeResult)) {
                        return (TerminationDecision) new TerminationDecision.Complete(
                                judgeResult.output());
                    }
                    return (TerminationDecision) TerminationDecision.Continue.INSTANCE;
                });
    }
}
