package io.casehub.blocks.prompt.runtime;

import io.casehub.blocks.prompt.PromptVariant;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariantAwareSystemPromptCustomiserTest {

    private PromptVariant variant(String delta) {
        return new PromptVariant("sig", "v1", List.of(), delta, 0.8, Instant.now(), null, 0);
    }

    @Test
    void returnsBasePromptWhenNoActiveVariant() {
        var store = new InMemoryPromptVariantStore();
        var customiser = new VariantAwareSystemPromptCustomiser(store);
        assertThat(customiser.customise("Base prompt.", "sig", "control"))
                .isEqualTo("Base prompt.");
    }

    @Test
    void returnsBasePromptWhenDeltaIsNull() {
        var store = new InMemoryPromptVariantStore();
        var v = variant(null);
        store.store(v);
        store.activate("sig", "v1", "control");
        var customiser = new VariantAwareSystemPromptCustomiser(store);
        assertThat(customiser.customise("Base prompt.", "sig", "control"))
                .isEqualTo("Base prompt.");
    }

    @Test
    void appendsDeltaToBasePrompt() {
        var store = new InMemoryPromptVariantStore();
        var v = variant("Prefer agents with high throughput.");
        store.store(v);
        store.activate("sig", "v1", "control");
        var customiser = new VariantAwareSystemPromptCustomiser(store);
        assertThat(customiser.customise("Base prompt.", "sig", "control"))
                .isEqualTo("Base prompt.\n\nPrefer agents with high throughput.");
    }
}
