package io.casehub.blocks.agentic.decomposition;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Declares the expected shape of a task's output, enabling validation gates between tasks.
 * Failed validation triggers retry at task granularity rather than pipeline restart.
 *
 * <p>Composable via {@link #and(OutputContract)}.
 */
@FunctionalInterface
public interface OutputContract {

    boolean validate(Object output);

    default OutputContract and(OutputContract other) {
        Objects.requireNonNull(other);
        return output -> this.validate(output) && other.validate(output);
    }

    static OutputContract nonNull() {
        return output -> output != null;
    }

    static OutputContract type(Class<?> expected) {
        Objects.requireNonNull(expected);
        return expected::isInstance;
    }

    static OutputContract of(Predicate<Object> test) {
        Objects.requireNonNull(test);
        return test::test;
    }
}
