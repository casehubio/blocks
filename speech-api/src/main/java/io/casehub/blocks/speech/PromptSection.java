package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface PromptSection {
    @Nullable String contribute(PromptContext context);
}
