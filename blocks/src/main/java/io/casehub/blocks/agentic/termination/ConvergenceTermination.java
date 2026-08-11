package io.casehub.blocks.agentic.termination;

import io.casehub.blocks.conversation.*;
import io.smallrye.mutiny.Uni;

import java.util.Set;
import java.util.function.Function;

public class ConvergenceTermination<T> implements TerminationCondition<T> {

    private final Function<T, ConversationState> stateExtractor;
    private final EpistemicRule epistemicRule;
    private final ConvergencePolicy policy;
    private final int recentWindow;
    private final double confidenceThreshold;
    private final Set<ConvergenceState> terminateOn;

    public ConvergenceTermination(Function<T, ConversationState> stateExtractor,
                                  EpistemicRule epistemicRule,
                                  ConvergencePolicy policy,
                                  int recentWindow,
                                  double confidenceThreshold,
                                  Set<ConvergenceState> terminateOn) {
        this.stateExtractor = stateExtractor;
        this.epistemicRule = epistemicRule;
        this.policy = policy;
        this.recentWindow = recentWindow;
        this.confidenceThreshold = confidenceThreshold;
        this.terminateOn = Set.copyOf(terminateOn);
    }

    @Override
    public Uni<TerminationDecision> evaluate(TerminationContext<T> context) {
        ConversationState state = stateExtractor.apply(context.state());
        CommonGroundState cg = CommonGroundAnalyser.analyse(state, epistemicRule);
        ConvergenceSignal signal = ConvergenceAnalyser.analyse(state, cg, policy, recentWindow);

        if (terminateOn.contains(signal.state())
                && signal.confidence() >= confidenceThreshold) {
            return Uni.createFrom().item(toDecision(signal));
        }
        return Uni.createFrom().item(TerminationDecision.Continue.INSTANCE);
    }

    private TerminationDecision toDecision(ConvergenceSignal signal) {
        return switch (signal.state()) {
            case CONSENSUS -> new TerminationDecision.Complete(signal.reason());
            case DEADLOCK -> new TerminationDecision.Escalate(signal.reason());
            case DIMINISHING_RETURNS -> new TerminationDecision.Complete(signal.reason());
            default -> TerminationDecision.Continue.INSTANCE;
        };
    }
}
