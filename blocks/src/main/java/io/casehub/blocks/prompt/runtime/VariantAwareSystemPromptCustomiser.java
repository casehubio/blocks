package io.casehub.blocks.prompt.runtime;

import io.casehub.blocks.prompt.PromptVariantStore;
import io.casehub.blocks.prompt.SystemPromptCustomiser;

public class VariantAwareSystemPromptCustomiser implements SystemPromptCustomiser {

    private final PromptVariantStore store;

    public VariantAwareSystemPromptCustomiser(PromptVariantStore store) {
        this.store = store;
    }

    @Override
    public String customise(String baseSystemPrompt, String signatureId, String variantSlot) {
        var variant = store.getActive(signatureId, variantSlot);
        if (variant == null || variant.instructionDelta() == null) {
            return baseSystemPrompt;
        }
        return baseSystemPrompt + "\n\n" + variant.instructionDelta();
    }
}
