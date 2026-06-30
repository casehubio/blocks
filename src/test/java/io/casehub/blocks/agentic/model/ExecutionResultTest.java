package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionResultTest {

    @Nested
    class CompletedVariant {
        @Test
        void holdsResult() {
            var completed = new ExecutionResult.Completed("done");
            assertThat(completed.result()).isEqualTo("done");
        }

        @Test
        void permitsNullResult() {
            var completed = new ExecutionResult.Completed(null);
            assertThat(completed.result()).isNull();
        }

        @Test
        void isInstanceOfExecutionResult() {
            ExecutionResult result = new ExecutionResult.Completed("value");
            assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
        }
    }

    @Nested
    class FailedVariant {
        @Test
        void holdsReasonAndCause() {
            var cause = new RuntimeException("boom");
            var failed = new ExecutionResult.Failed("something broke", cause);
            assertThat(failed.reason()).isEqualTo("something broke");
            assertThat(failed.cause()).isSameAs(cause);
        }

        @Test
        void permitsNullCause() {
            var failed = new ExecutionResult.Failed("no cause", null);
            assertThat(failed.cause()).isNull();
        }
    }

    @Nested
    class EscalatedVariant {
        @Test
        void holdsReason() {
            var escalated = new ExecutionResult.Escalated("needs human");
            assertThat(escalated.reason()).isEqualTo("needs human");
        }
    }

    @Nested
    class CancelledVariant {
        @Test
        void isDistinctSealedVariant() {
            ExecutionResult result = new ExecutionResult.Cancelled();
            assertThat(result).isInstanceOf(ExecutionResult.Cancelled.class);
        }
    }

    @Test
    void patternMatchExhaustiveness() {
        // Verifies all four sealed variants are matchable
        ExecutionResult result = new ExecutionResult.Completed("x");
        var label = switch (result) {
            case ExecutionResult.Completed c -> "completed";
            case ExecutionResult.Failed f -> "failed";
            case ExecutionResult.Escalated e -> "escalated";
            case ExecutionResult.Cancelled c -> "cancelled";
        };
        assertThat(label).isEqualTo("completed");
    }
}
