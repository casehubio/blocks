package io.casehub.blocks.agentic.judgment;

public record RetryPolicy(int maxRetries, ExhaustionPolicy exhaustionPolicy) {

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, ExhaustionPolicy.FAIL);
    }
}
