package io.casehub.blocks.prompt;

import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PromptVariantStore {
    void store(PromptVariant variant);

    @Nullable PromptVariant getActive(String signatureId, String variantSlot);

    List<PromptVariant> getHistory(String signatureId, int limit);

    void activate(String signatureId, @Nullable String variantId, String variantSlot);
}
