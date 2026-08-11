package io.casehub.blocks.trust;

@FunctionalInterface
public interface IntakeClassifier<S> {
    IntakeResult classify(S subject, IntakeContext context);
}
