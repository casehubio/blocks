package io.casehub.blocks.agentic.termination;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaxIterationsTerminationTest {

    @Test
    void continuesWhenBelowMax() {
        var term = new MaxIterationsTermination<String>(5);
        var ctx = new TerminationContext<>("state", 3, Duration.ZERO, List.of());
        assertThat(term.evaluate(ctx).await().indefinitely())
                .isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void completesAtMax() {
        var term = new MaxIterationsTermination<String>(5);
        var ctx = new TerminationContext<>("state", 5, Duration.ZERO, List.of());
        assertThat(term.evaluate(ctx).await().indefinitely())
                .isInstanceOf(TerminationDecision.Complete.class);
    }
}
