package io.casehub.blocks.annotations.runtime;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.pattern.Patterns;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.quarkus.runtime.annotations.Recorder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Recorder
public class BlocksAnnotationsRecorder {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Supplier<ExecutionModel<Object>> createExecutionModel(PatternDescriptor desc) {
        return () -> buildModel(desc);
    }

    private ExecutionModel<Object> buildModel(PatternDescriptor desc) {
        AgentRef[] agents = createAgentRefs(desc.participants());
        RoutingCandidate[] candidates = Arrays.stream(agents)
                .map(a -> new RoutingCandidate(a, null))
                .toArray(RoutingCandidate[]::new);

        return switch (desc.patternType()) {
            case SUPERVISOR -> buildSupervisor(desc, candidates);
            case DEBATE -> buildDebate(desc, agents);
            case VOTING -> buildVoting(desc, candidates);
            case HTN -> buildHtn(desc, candidates);
            case SEQUENCE -> buildSequence(desc, agents);
            case PARALLEL -> buildParallel(desc, candidates);
            case LOOP -> buildLoop(desc, candidates);
            case CONDITIONAL -> buildConditional(desc, candidates);
        };
    }

    private ExecutionModel<Object> buildSupervisor(PatternDescriptor desc, RoutingCandidate[] candidates) {
        var builder = Patterns.<Object>supervisor();
        builder.agents(candidates);
        builder.task(desc.beanName());

        int maxIterations = (int) desc.attributes().getOrDefault("maxIterations", 10);
        builder.terminate(new io.casehub.blocks.agentic.termination.MaxIterationsTermination<>(maxIterations));

        return builder.build();
    }

    private ExecutionModel<Object> buildDebate(PatternDescriptor desc, AgentRef[] allAgents) {
        var builder = Patterns.<Object>debate();

        int maxRounds = (int) desc.attributes().getOrDefault("maxRounds", 5);
        builder.maxRounds(maxRounds);

        AgentRef judgeRef = null;
        AgentRef[] debaters = Arrays.stream(allAgents)
                .toArray(AgentRef[]::new);

        for (int i = 0; i < desc.participants().size(); i++) {
            if (desc.participants().get(i).isJudge()) {
                judgeRef = allAgents[i];
            }
        }

        builder.debaters(debaters);
        if (judgeRef != null) {
            builder.judge(judgeRef);
        }
        builder.task(desc.beanName());

        return builder.build();
    }

    private ExecutionModel<Object> buildVoting(PatternDescriptor desc, RoutingCandidate[] candidates) {
        var builder = Patterns.<Object>voting();
        var agents = Arrays.stream(candidates).map(RoutingCandidate::ref).toArray(AgentRef[]::new);
        builder.evaluators(agents);
        builder.task(desc.beanName());
        return builder.build();
    }

    private ExecutionModel<Object> buildHtn(PatternDescriptor desc, RoutingCandidate[] candidates) {
        var builder = Patterns.<Object>htn();
        var agents = Arrays.stream(candidates).map(RoutingCandidate::ref).toArray(AgentRef[]::new);
        builder.agents(agents);
        builder.task(desc.beanName());
        return builder.build();
    }

    private ExecutionModel<Object> buildSequence(PatternDescriptor desc, AgentRef[] agents) {
        var builder = Patterns.<Object>sequence();
        builder.agents(agents);
        builder.task(desc.beanName());
        return builder.build();
    }

    private ExecutionModel<Object> buildParallel(PatternDescriptor desc, RoutingCandidate[] candidates) {
        var agents = Arrays.stream(candidates).map(RoutingCandidate::ref).toArray(AgentRef[]::new);
        var builder = Patterns.<Object>parallel();
        builder.agents(agents);
        builder.task(desc.beanName());
        return builder.build();
    }

    private ExecutionModel<Object> buildLoop(PatternDescriptor desc, RoutingCandidate[] candidates) {
        var agents = Arrays.stream(candidates).map(RoutingCandidate::ref).toArray(AgentRef[]::new);
        var builder = Patterns.<Object>loop();
        builder.agents(agents);
        int maxIterations = (int) desc.attributes().getOrDefault("maxIterations", 10);
        builder.maxIterations(maxIterations);
        builder.task(desc.beanName());
        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ExecutionModel<Object> buildConditional(PatternDescriptor desc, RoutingCandidate[] candidates) {
        var candidateList = List.of(candidates);
        return new ExecutionModel<>(
                new FirstMatchRouting<>(c -> true),
                new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(),
                new PassThrough<>(),
                ctx -> ctx.iterationCount() >= 1
                        ? new TerminationDecision.Complete(ctx.results())
                        : TerminationDecision.Continue.INSTANCE,
                () -> candidateList,
                FailurePolicy.defaults(),
                List.of(),
                desc.beanName(),
                PatternType.CONDITIONAL,
                null
        );
    }

    private AgentRef[] createAgentRefs(List<PatternDescriptor.AgentParticipant> participants) {
        return participants.stream()
                .map(this::createAgentRef)
                .toArray(AgentRef[]::new);
    }

    private AgentRef createAgentRef(PatternDescriptor.AgentParticipant participant) {
        return AgentRef.external(participant.label(), ctx ->
                                                              CompletableFuture.failedFuture(
                                                                      new UnsupportedOperationException("Invoke via CDI-resolved AgentProvider at runtime")));
    }
}
