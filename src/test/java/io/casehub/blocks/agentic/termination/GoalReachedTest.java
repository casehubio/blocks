package io.casehub.blocks.agentic.termination;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class GoalReachedTest {

    @Test
    void completesWhenPredicateIsTrue() {
        Predicate<String> goalMet = s -> s.equals("done");
        var term = new GoalReached<>(goalMet);
        var ctx = new TerminationContext<>("done", 1, Duration.ZERO, List.of());
        assertThat(term.evaluate(ctx).await().indefinitely())
                .isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void continuesWhenPredicateIsFalse() {
        Predicate<String> goalMet = s -> s.equals("done");
        var term = new GoalReached<>(goalMet);
        var ctx = new TerminationContext<>("not yet", 1, Duration.ZERO, List.of());
        assertThat(term.evaluate(ctx).await().indefinitely())
                .isInstanceOf(TerminationDecision.Continue.class);
    }
}
