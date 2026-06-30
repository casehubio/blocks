package io.casehub.blocks.agentic.activation;

public final class Activation {
    private Activation() {}

    public static <T> OnExplicitDispatch<T> onExplicitDispatch() {
        return new OnExplicitDispatch<>();
    }

    public static <T> MaxIterationsGuard<T> maxIterations(int max) {
        return new MaxIterationsGuard<>(max);
    }
}
