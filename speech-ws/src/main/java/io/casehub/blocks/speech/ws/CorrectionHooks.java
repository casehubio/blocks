package io.casehub.blocks.speech.ws;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public record CorrectionHooks(
        UnaryOperator<String> corrector,
        Consumer<String> onResponse,
        Supplier<String> vocabularyHintSupplier
) {}
