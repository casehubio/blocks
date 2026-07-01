package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.ActivationContext;
import io.casehub.blocks.agentic.aggregation.AggregationContext;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.routing.RoutingContext;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base for execution drivers that composes the five agentic SPIs.
 * Extracts the common five-phase loop: route -> activate -> dispatch -> aggregate -> terminate.
 *
 * <p>Tracks per-agent activation state across iterations and populates {@link ActivationContext}
 * with tracked data instead of hardcoded zeros. Calls {@code onExecutionStart} and
 * {@code onExecutionComplete} lifecycle hooks.
 *
 * <p>Subclasses implement {@link #runLoop(ExecutionModel, Object)} to control iteration
 * vs. event-wait semantics.
 */
public abstract class AbstractExecutionDriver<T> implements ExecutionDriver<T> {

    private static final System.Logger LOG = System.getLogger(AbstractExecutionDriver.class.getName());

    protected final AgentInvoker<T> invoker;
    protected final AtomicReference<ExecutionState> currentState = new AtomicReference<>(new ExecutionState.Idle());
    protected volatile boolean cancelled = false;

    // Per-agent tracked state
    protected final Map<AgentRef, Integer> activationCounts = new HashMap<>();
    protected final Map<AgentRef, Integer> consecutiveIdleCounts = new HashMap<>();
    protected Object lastAggregationResult = null;

    protected AbstractExecutionDriver() {
        this(AgentInvoker.defaultInvoker());
    }

    protected AbstractExecutionDriver(AgentInvoker<T> invoker) {
        this.invoker = invoker;
    }

    @Override
    public final Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext) {
        return Uni.createFrom().item(() -> {
            cancelled = false;
            activationCounts.clear();
            consecutiveIdleCounts.clear();
            lastAggregationResult = null;

            notifyExecutionStart(model);

            ExecutionResult result = null;
            try {
                result = runLoop(model, initialContext).await().indefinitely();
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Execution failed", e);
                result = new ExecutionResult.Failed(e.getMessage(), e);
                throw e;
            } finally {
                if (result != null) {
                    notifyExecutionComplete(model, result);
                }
            }
            return result;
        });
    }

    /**
     * Subclass-specific loop structure. OrchestratedDriver uses a while loop;
     * ChoreographedDriver transitions to WaitingForEvent between iterations.
     */
    protected abstract Uni<ExecutionResult> runLoop(ExecutionModel<T> model, T context);

    /**
     * Executes one iteration of the five-phase loop. Returns null for Continue,
     * otherwise returns the terminal ExecutionResult.
     */
    protected ExecutionResult executeIteration(ExecutionModel<T> model, T context,
                                                int iteration, Instant start,
                                                List<AgentResult> allResults) {
        // Phase 1: refresh candidates and route
        var candidates = model.candidateSupplier().get();
        var routingCtx = new RoutingContext<>("task", candidates, context);
        var decision = model.routing().route(routingCtx).await().indefinitely();

        notifyRoutingDecision(model, decision, candidates);

        // Phase 2: handle routing outcome
        if (decision instanceof RoutingDecision.Selected selected) {
            var results = dispatchAgents(model, selected.agents(), context);
            allResults.addAll(results);

            var aggCtx = new AggregationContext<>(context);
            var aggregated = model.aggregation()
                    .aggregate(results, aggCtx).await().indefinitely();

            lastAggregationResult = aggregated;
            notifyAggregation(model, aggregated);
        } else if (decision instanceof RoutingDecision.Unresolvable unresolvable) {
            var action = model.failurePolicy().onRoutingFailure();
            if (action == FailurePolicy.RoutingFailureAction.FAIL) {
                transition(model, new ExecutionState.Faulted());
                return new ExecutionResult.Failed(unresolvable.reason(), null);
            } else if (action == FailurePolicy.RoutingFailureAction.ESCALATE) {
                return new ExecutionResult.Escalated(unresolvable.reason());
            }
            // RETRY_BROADER: fall through to continue the loop
        } else if (decision instanceof RoutingDecision.Escalate escalate) {
            return new ExecutionResult.Escalated(escalate.reason());
        }

        // Phase 3: evaluate termination
        var elapsed = Duration.between(start, Instant.now());
        var termCtx = new TerminationContext<>(
                context, iteration + 1, elapsed, List.copyOf(allResults));
        var termDecision = model.termination()
                .evaluate(termCtx).await().indefinitely();

        notifyTermination(model, termDecision);

        if (termDecision instanceof TerminationDecision.Complete complete) {
            transition(model, new ExecutionState.Complete());
            return new ExecutionResult.Completed(complete.result());
        } else if (termDecision instanceof TerminationDecision.Failed failed) {
            transition(model, new ExecutionState.Faulted());
            return new ExecutionResult.Failed(failed.reason(), null);
        } else if (termDecision instanceof TerminationDecision.Escalate escalate) {
            return new ExecutionResult.Escalated(escalate.reason());
        }
        // TerminationDecision.Continue: loop continues
        return null;
    }

    protected List<AgentResult> dispatchAgents(ExecutionModel<T> model,
                                                List<AgentRef> agents,
                                                T context) {
        var results = new ArrayList<AgentResult>();
        for (var agent : agents) {
            notifyAgentDispatched(model, agent);

            var activationCount = activationCounts.getOrDefault(agent, 0);
            var consecutiveIdleCount = consecutiveIdleCounts.getOrDefault(agent, 0);
            var activationCtx = new ActivationContext<>(
                    null, context, agent, activationCount,
                    Optional.ofNullable(lastAggregationResult), consecutiveIdleCount);

            var activated = model.activation()
                    .shouldActivate(activationCtx).await().indefinitely();

            notifyActivation(model, agent, activated);

            if (!activated) {
                consecutiveIdleCounts.put(agent, consecutiveIdleCount + 1);
                continue;
            }

            consecutiveIdleCounts.put(agent, 0);
            activationCounts.put(agent, activationCount + 1);

            var result = invokeAgent(model, agent, context);
            results.add(result);

            notifyAgentResult(model, result);
        }
        return results;
    }

    protected AgentResult invokeAgent(ExecutionModel<T> model, AgentRef agent, T context) {
        try {
            transition(model, new ExecutionState.WaitingForAgent(agent));
            return invoker.invoke(agent, context).await().indefinitely();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Agent invocation failed", e);
            notifyFailure(model, agent, e);
            return AgentResult.failure(agent, e.getMessage());
        }
    }

    protected void transition(ExecutionModel<T> model, ExecutionState newState) {
        var old = currentState.getAndSet(newState);
        for (var listener : model.listeners()) {
            listener.onStateTransition(old, newState);
        }
    }

    protected void notifyExecutionStart(ExecutionModel<T> model) {
        for (var listener : model.listeners()) {
            listener.onExecutionStart(model);
        }
    }

    protected void notifyExecutionComplete(ExecutionModel<T> model, ExecutionResult result) {
        for (var listener : model.listeners()) {
            listener.onExecutionComplete(result);
        }
    }

    protected void notifyRoutingDecision(ExecutionModel<T> model,
                                          RoutingDecision decision,
                                          List<RoutingCandidate> candidates) {
        for (var listener : model.listeners()) {
            listener.onRoutingDecision(decision, candidates);
        }
    }

    protected void notifyAggregation(ExecutionModel<T> model, AggregationResult result) {
        for (var listener : model.listeners()) {
            listener.onAggregation(result);
        }
    }

    protected void notifyTermination(ExecutionModel<T> model, TerminationDecision decision) {
        for (var listener : model.listeners()) {
            listener.onTermination(decision);
        }
    }

    protected void notifyAgentDispatched(ExecutionModel<T> model, AgentRef agent) {
        for (var listener : model.listeners()) {
            listener.onAgentDispatched(agent);
        }
    }

    protected void notifyActivation(ExecutionModel<T> model, AgentRef agent, boolean activated) {
        for (var listener : model.listeners()) {
            listener.onActivation(agent, activated);
        }
    }

    protected void notifyAgentResult(ExecutionModel<T> model, AgentResult result) {
        for (var listener : model.listeners()) {
            listener.onAgentResult(result);
        }
    }

    protected void notifyFailure(ExecutionModel<T> model, AgentRef agent, Throwable cause) {
        for (var listener : model.listeners()) {
            listener.onFailure(agent, cause);
        }
    }

    protected boolean isCancelled() {
        return cancelled;
    }

    @Override
    public Uni<Void> cancel() {
        cancelled = true;
        return Uni.createFrom().voidItem();
    }

    @Override
    public ExecutionState state() {
        return currentState.get();
    }
}
