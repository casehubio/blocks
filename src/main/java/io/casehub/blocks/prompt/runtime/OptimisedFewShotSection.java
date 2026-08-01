package io.casehub.blocks.prompt.runtime;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingPromptSection;
import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.PromptVariantStore;
import io.casehub.blocks.prompt.VariantSelector;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class OptimisedFewShotSection implements RoutingPromptSection {

    private final PromptVariantStore store;
    private final VariantSelector selector;
    private final String signatureId;

    public OptimisedFewShotSection(PromptVariantStore store, VariantSelector selector, String signatureId) {
        this.store = store;
        this.selector = selector;
        this.signatureId = signatureId;
    }

    @Override
    public @Nullable String render(AgentRoutingContext context, List<AgentCandidate> eligible) {
        var slot = selector.selectSlot(context.caseId(), context.capabilityName());
        var variant = store.getActive(signatureId, slot);
        if (variant == null || variant.examples().isEmpty()) return null;
        return formatExamples(variant.examples());
    }

    private String formatExamples(List<FewShotExample> examples) {
        var sb = new StringBuilder("Successful routing examples from similar past cases:\n");
        for (int i = 0; i < examples.size(); i++) {
            var ex = examples.get(i);
            sb.append("\n").append(i + 1).append(". ").append(ex.input())
                    .append("\n   Decision: ").append(ex.output())
                    .append("\n   Outcome: ").append(ex.outcome());
        }
        return sb.toString();
    }
}
