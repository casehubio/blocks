package io.casehub.blocks.agentic.termination;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TerminationConditionTest {

    private static final TerminationContext<String> CTX =
        new TerminationContext<>("state", 1, Duration.ZERO, List.of());

    private static TerminationCondition<String> returning(TerminationDecision decision) {
        return ctx -> decision;
    }

    // --- or() ---

    @Test
    void orContinueAndContinueReturnsContinue() {
        var combined = returning(TerminationDecision.Continue.INSTANCE)
            .or(returning(TerminationDecision.Continue.INSTANCE));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void orCompleteAndContinueReturnsComplete() {
        var combined = returning(new TerminationDecision.Complete("a"))
            .or(returning(TerminationDecision.Continue.INSTANCE));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void orContinueAndCompleteReturnsComplete() {
        var combined = returning(TerminationDecision.Continue.INSTANCE)
            .or(returning(new TerminationDecision.Complete("b")));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void orCompleteAndEscalateReturnsEscalate() {
        var combined = returning(new TerminationDecision.Complete("a"))
            .or(returning(new TerminationDecision.Escalate("urgent")));
        var result = combined.evaluate(CTX);
        assertThat(result).isInstanceOf(TerminationDecision.Escalate.class);
        assertThat(((TerminationDecision.Escalate) result).reason()).isEqualTo("urgent");
    }

    @Test
    void orFailedAndCompleteReturnsFailed() {
        var combined = returning(new TerminationDecision.Failed("broken"))
            .or(returning(new TerminationDecision.Complete("ok")));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Failed.class);
    }

    // --- and() ---

    @Test
    void andContinueAndCompleteReturnsContinue() {
        var combined = returning(TerminationDecision.Continue.INSTANCE)
            .and(returning(new TerminationDecision.Complete("a")));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void andCompleteAndContinueReturnsContinue() {
        var combined = returning(new TerminationDecision.Complete("a"))
            .and(returning(TerminationDecision.Continue.INSTANCE));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void andCompleteAndCompleteReturnsComplete() {
        var combined = returning(new TerminationDecision.Complete("a"))
            .and(returning(new TerminationDecision.Complete("b")));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void andCompleteAndEscalateReturnsEscalate() {
        var combined = returning(new TerminationDecision.Complete("ok"))
            .and(returning(new TerminationDecision.Escalate("urgent")));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Escalate.class);
    }

    @Test
    void andCompleteAndFailedReturnsFailed() {
        var combined = returning(new TerminationDecision.Complete("ok"))
            .and(returning(new TerminationDecision.Failed("broken")));
        assertThat(combined.evaluate(CTX)).isInstanceOf(TerminationDecision.Failed.class);
    }
}
