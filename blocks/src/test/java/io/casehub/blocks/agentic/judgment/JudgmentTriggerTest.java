package io.casehub.blocks.agentic.judgment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentTriggerTest {

    private static JudgmentContext<String> ctx(int iteration) {
        return new JudgmentContext<>("state", List.of(), null, iteration, null);
    }

    @Test
    void alwaysYield_returnsTrue() {
        assertThat(new AlwaysYield<String>().shouldYield(ctx(0))).isTrue();
    }

    @Test
    void neverYield_returnsFalse() {
        assertThat(new NeverYield<String>().shouldYield(ctx(0))).isFalse();
    }

    @Test
    void iterationBased_yieldsEveryNIterations() {
        var trigger = new IterationBased<String>(3);
        assertThat(trigger.shouldYield(ctx(0))).isTrue();
        assertThat(trigger.shouldYield(ctx(1))).isFalse();
        assertThat(trigger.shouldYield(ctx(2))).isFalse();
        assertThat(trigger.shouldYield(ctx(3))).isTrue();
        assertThat(trigger.shouldYield(ctx(6))).isTrue();
    }

    @Test
    void confidenceThreshold_yieldsWhenBelowThreshold() {
        var trigger = ConfidenceThreshold.<String>below(0.8, ctx -> ctx.iteration() * 0.3);
        assertThat(trigger.shouldYield(ctx(0))).isTrue();
        assertThat(trigger.shouldYield(ctx(2))).isTrue();
        assertThat(trigger.shouldYield(ctx(3))).isFalse();
    }

    @Test
    void and_compositionRequiresBoth() {
        var always = new AlwaysYield<String>();
        var never = new NeverYield<String>();
        assertThat(always.and(always).shouldYield(ctx(0))).isTrue();
        assertThat(always.and(never).shouldYield(ctx(0))).isFalse();
    }

    @Test
    void or_compositionRequiresEither() {
        var always = new AlwaysYield<String>();
        var never = new NeverYield<String>();
        assertThat(never.or(always).shouldYield(ctx(0))).isTrue();
        assertThat(never.or(never).shouldYield(ctx(0))).isFalse();
    }
}
