package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.ActivationRule;
import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.judgment.JudgmentPhase;
import io.casehub.blocks.agentic.model.ExecutionBackend;
import io.casehub.blocks.agentic.model.ExecutionEventListener;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.engine.plan.DecompositionStrategy;
import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public abstract class AbstractPatternBuilder<T, B extends AbstractPatternBuilder<T, B>> {

    protected final List<ExecutionEventListener>     listeners     = new ArrayList<>();
    protected       RoutingStrategy<T>               routing;
    protected       DecompositionStrategy<T>         decomposition;
    protected       ActivationRule<T>                activation;
    protected       AggregationStrategy<T>           aggregation;
    protected       TerminationCondition<T>          termination;
    protected       Supplier<List<RoutingCandidate>> candidateSupplier;
    protected       FailurePolicy                    failurePolicy = FailurePolicy.defaults();
    protected       String                           task          = "execution";
    protected       ExecutionBackend<T>              backend;
    protected       PatternType                      patternType;
    protected       JudgmentPhase<T>                 judgment;

    public B route(RoutingStrategy<T> routing) {
        this.routing = routing;
        return (B) this;
    }

    public B decompose(DecompositionStrategy<T> decomposition) {
        this.decomposition = decomposition;
        return (B) this;
    }

    public B activate(ActivationRule<T> activation) {
        this.activation = activation;
        return (B) this;
    }

    public B aggregate(AggregationStrategy<T> aggregation) {
        this.aggregation = aggregation;
        return (B) this;
    }

    public B terminate(TerminationCondition<T> termination) {
        this.termination = termination;
        return (B) this;
    }

    public B onRoutingFailure(FailurePolicy.RoutingFailureAction action) {
        this.failurePolicy = new FailurePolicy(action,
                                               failurePolicy.onDeadlock(), failurePolicy.agentRetry(),
                                               failurePolicy.replanPolicy());
        return (B) this;
    }

    public B onDeadlock(FailurePolicy.AggregationFailureAction action) {
        this.failurePolicy = new FailurePolicy(failurePolicy.onRoutingFailure(),
                                               action, failurePolicy.agentRetry(),
                                               failurePolicy.replanPolicy());
        return (B) this;
    }

    public B maxAgentRetries(int max) {
        this.failurePolicy = new FailurePolicy(failurePolicy.onRoutingFailure(),
                                               failurePolicy.onDeadlock(),
                                               new FailurePolicy.AgentRetryPolicy(max,
                                                                                  failurePolicy.agentRetry().backoff(),
                                                                                  failurePolicy.agentRetry().backoffStrategy(),
                                                                                  failurePolicy.agentRetry().onExhausted()),
                                               failurePolicy.replanPolicy());
        return (B) this;
    }

    public B maxReplans(int max) {
        this.failurePolicy = new FailurePolicy(failurePolicy.onRoutingFailure(),
                                               failurePolicy.onDeadlock(),
                                               failurePolicy.agentRetry(),
                                               new FailurePolicy.ReplanPolicy(max,
                                                                              FailurePolicy.RoutingFailureAction.FAIL));
        return (B) this;
    }

    public B replanFallback(FailurePolicy.RoutingFailureAction action) {
        var existing = failurePolicy.replanPolicy();
        if (existing == null) {
            throw new IllegalStateException("Call maxReplans() before replanFallback()");
        }
        this.failurePolicy = new FailurePolicy(failurePolicy.onRoutingFailure(),
                                               failurePolicy.onDeadlock(),
                                               failurePolicy.agentRetry(),
                                               new FailurePolicy.ReplanPolicy(
                                                       existing.maxReplans(), action));
        return (B) this;
    }


    public B listener(ExecutionEventListener listener) {
        this.listeners.add(listener);
        return (B) this;
    }

    public B task(String task) {
        this.task = task;
        return (B) this;
    }

    public B backend(ExecutionBackend<T> backend) {
        this.backend = backend;
        return (B) this;
    }

    public B judgment(JudgmentPhase<T> judgment) {
        this.judgment = judgment;
        return (B) this;
    }

    protected B agents(AgentRef... agents) {
        var candidates = Arrays.stream(agents)
                               .map(a -> new RoutingCandidate(a, null))
                               .toList();
        this.candidateSupplier = () -> candidates;
        return (B) this;
    }

    protected B agents(RoutingCandidate... candidates) {
        var list = List.of(candidates);
        this.candidateSupplier = () -> list;
        return (B) this;
    }

    public ExecutionModel<T> build() {
        return new ExecutionModel<>(routing, decomposition, activation,
                                    aggregation, termination, candidateSupplier,
                                    failurePolicy, listeners, task, patternType,
                                    backend, judgment);
    }

    public Uni<ExecutionResult> execute(T initialContext) {
        var model = build();
        if (backend != null) {
            return backend.execute(model, initialContext);
        }
        return resolveDefaultBackend().execute(model, initialContext);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ExecutionBackend<T> resolveDefaultBackend() {
        java.util.ServiceLoader<ExecutionBackend> loader =
            java.util.ServiceLoader.load(ExecutionBackend.class);
        for (ExecutionBackend<?> candidate : loader) {
            return (ExecutionBackend<T>) candidate;
        }
        return ExecutionBackend.reactive();
    }
}
