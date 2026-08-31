package io.casehub.blocks.agentic.judgment;

public final class IterationBased<T> implements JudgmentTrigger<T> {

    private final int every;

    public IterationBased(int every) {
        if (every < 1) throw new IllegalArgumentException("every must be >= 1");
        this.every = every;
    }

    @Override
    public boolean shouldYield(JudgmentContext<T> context) {
        return context.iteration() % every == 0;
    }
}
