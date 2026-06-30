package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.activation.ActivationContext;
import io.casehub.blocks.agentic.aggregation.AggregationContext;
import io.casehub.blocks.agentic.routing.RoutingContext;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Imperative loop driver that composes the five agentic SPIs into a
 * synchronous execution cycle: route -> activate -> dispatch -> aggregate -> terminate.
 *
 * <p>Each iteration refreshes routing candidates, selects agents, dispatches them,
 * aggregates results, and evaluates termination. The loop continues until a
 * termination condition fires, a routing failure triggers the failure policy,
 * or cancellation is requested.
 *
 * <p>Agent invocation currently supports {@link AgentRef.ExternalAgent} via its
 * completion-stage function. Other AgentRef variants return a failure result —
 * runtime dispatch integration is deferred to a later task.
 */
public class OrchestratedDriver<T> implements ExecutionDriver<T> {

    private static final System.Logger LOG = System.getLogger(OrchestratedDriver.class.getName());
    private final AtomicReference<ExecutionState> currentState = new AtomicReference<>(new ExecutionState.Idle());
    private volatile boolean cancelled = false;

    @Override
    public Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext) {
        return Uni.createFrom().item(() -> {
            cancelled = false;
            var start = Instant.now();
            var allResults = new ArrayList<AgentResult>();
            int iteration = 0;

            while (!cancelled) {
                transition(model, new ExecutionState.Running(iteration));

                // Phase 1: refresh candidates and route
                var candidates = model.candidateSupplier().get();
                var routingCtx = new RoutingContext<>(
                        "task", candidates, initialContext);
                var decision = model.routing().route(routingCtx).await().indefinitely();

                notifyRoutingDecision(model, decision, candidates);

                // Phase 2: handle routing outcome
                if (decision instanceof RoutingDecision.Selected selected) {
                    var results = dispatchAgents(model, selected.agents(), initialContext);
                    allResults.addAll(results);

                    var aggCtx = new AggregationContext<>(initialContext);
                    var aggregated = model.aggregation()
                            .aggregate(results, aggCtx).await().indefinitely();

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
                iteration++;
                var elapsed = Duration.between(start, Instant.now());
                var termCtx = new TerminationContext<>(
                        initialContext, iteration, elapsed, List.copyOf(allResults));
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
            }

            transition(model, new ExecutionState.Cancelled());
            return new ExecutionResult.Cancelled();
        });
    }

    private List<AgentResult> dispatchAgents(ExecutionModel<T> model,
                                             List<AgentRef> agents,
                                             T context) {
        var results = new ArrayList<AgentResult>();
        for (var agent : agents) {
            notifyAgentDispatched(model, agent);

            var activationCtx = new ActivationContext<>(
                    null, context, agent, 0, Optional.empty(), 0);
            var activated = model.activation()
                    .shouldActivate(activationCtx).await().indefinitely();

            notifyActivation(model, agent, activated);

            if (!activated) continue;

            var result = invokeAgent(model, agent);
            results.add(result);

            notifyAgentResult(model, result);
        }
        return results;
    }

    private AgentResult invokeAgent(ExecutionModel<T> model, AgentRef agent) {
        var start = Instant.now();
        try {
            if (agent instanceof AgentRef.ExternalAgent ext) {
                transition(model, new ExecutionState.WaitingForAgent(agent));
                var future = ext.fn().apply(null);
                var result = future.toCompletableFuture().join();
                var duration = Duration.between(start, Instant.now());
                return new AgentResult(agent, result.output(), duration, result.status());
            }
            return AgentResult.failure(agent,
                    "Unsupported AgentRef variant: " + agent.getClass().getSimpleName());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Agent invocation failed", e);
            notifyFailure(model, agent, e);
            return AgentResult.failure(agent, e.getMessage());
        }
    }

    private void transition(ExecutionModel<T> model, ExecutionState newState) {
        var old = currentState.getAndSet(newState);
        for (var listener : model.listeners()) {
            listener.onStateTransition(old, newState);
        }
    }

    private void notifyRoutingDecision(ExecutionModel<T> model,
                                       RoutingDecision decision,
                                       List<io.casehub.blocks.agentic.RoutingCandidate> candidates) {
        for (var listener : model.listeners()) {
            listener.onRoutingDecision(decision, candidates);
        }
    }

    private void notifyAggregation(ExecutionModel<T> model,
                                   io.casehub.blocks.agentic.aggregation.AggregationResult result) {
        for (var listener : model.listeners()) {
            listener.onAggregation(result);
        }
    }

    private void notifyTermination(ExecutionModel<T> model,
                                   TerminationDecision decision) {
        for (var listener : model.listeners()) {
            listener.onTermination(decision);
        }
    }

    private void notifyAgentDispatched(ExecutionModel<T> model, AgentRef agent) {
        for (var listener : model.listeners()) {
            listener.onAgentDispatched(agent);
        }
    }

    private void notifyActivation(ExecutionModel<T> model,
                                  AgentRef agent, boolean activated) {
        for (var listener : model.listeners()) {
            listener.onActivation(agent, activated);
        }
    }

    private void notifyAgentResult(ExecutionModel<T> model, AgentResult result) {
        for (var listener : model.listeners()) {
            listener.onAgentResult(result);
        }
    }

    private void notifyFailure(ExecutionModel<T> model, AgentRef agent, Throwable cause) {
        for (var listener : model.listeners()) {
            listener.onFailure(agent, cause);
        }
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
