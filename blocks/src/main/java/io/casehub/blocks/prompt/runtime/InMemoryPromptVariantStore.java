package io.casehub.blocks.prompt.runtime;

import io.casehub.blocks.prompt.PromptVariant;
import io.casehub.blocks.prompt.PromptVariantStore;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPromptVariantStore implements PromptVariantStore {

    private final Map<String, List<PromptVariant>> history = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PromptVariant>> activeSlots = new ConcurrentHashMap<>();

    @Override
    public void store(PromptVariant variant) {
        history.computeIfAbsent(variant.signatureId(), k -> new ArrayList<>()).add(variant);
    }

    @Override
    public @Nullable PromptVariant getActive(String signatureId, String variantSlot) {
        var slots = activeSlots.get(signatureId);
        return slots != null ? slots.get(variantSlot) : null;
    }

    @Override
    public List<PromptVariant> getHistory(String signatureId, int limit) {
        var entries = history.getOrDefault(signatureId, List.of());
        int from = Math.max(0, entries.size() - limit);
        var result = new ArrayList<>(entries.subList(from, entries.size()));
        Collections.reverse(result);
        return List.copyOf(result);
    }

    @Override
    public void activate(String signatureId, @Nullable String variantId, String variantSlot) {
        if (variantId == null) {
            var slots = activeSlots.get(signatureId);
            if (slots != null) slots.remove(variantSlot);
            return;
        }
        var entries = history.getOrDefault(signatureId, List.of());
        var variant = entries.stream()
                .filter(v -> v.variantId().equals(variantId))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown variant: " + variantId + " for signature: " + signatureId));
        activeSlots.computeIfAbsent(signatureId, k -> new LinkedHashMap<>())
                .put(variantSlot, variant);
    }
}
