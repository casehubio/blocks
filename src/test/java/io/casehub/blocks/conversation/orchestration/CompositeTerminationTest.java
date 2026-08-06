package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeTerminationTest {

    private final ConversationState empty =
            new ConversationState(Map.of(), List.of(), List.of(), Map.of());

    @Test
    void allContinue_returnsContinue() {
        var composite = new CompositeTermination(List.of(
                new AllAgreedTermination(Set.of("AGREED")),
                new ContestedEscalation(3)
        ));
        assertThat(evaluate(composite, empty, 1))
                .isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void firstNonContinueWins() {
        @SuppressWarnings("unchecked")
        var maxIter = (TerminationCondition<ConversationState>)
                (TerminationCondition<?>) new MaxIterationsTermination<>(2);
        var composite = new CompositeTermination(List.of(
                maxIter,
                new AllAgreedTermination(Set.of("AGREED"))
        ));
        var decision = evaluate(composite, empty, 5);
        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void orderMatters_laterConditionsSkippedAfterTerminal() {
        @SuppressWarnings("unchecked")
        var maxIter = (TerminationCondition<ConversationState>)
                (TerminationCondition<?>) new MaxIterationsTermination<>(1);
        var composite = new CompositeTermination(List.of(
                maxIter,
                new ContestedEscalation(0)
        ));
        var decision = evaluate(composite, empty, 5);
        assertThat(decision).isInstanceOf(TerminationDecision.Complete.class);
    }

    private TerminationDecision evaluate(CompositeTermination composite,
                                          ConversationState state, int iterations) {
        var ctx = new TerminationContext<>(state, iterations, Duration.ZERO, List.of());
        return composite.evaluate(ctx).await().indefinitely();
    }
}
