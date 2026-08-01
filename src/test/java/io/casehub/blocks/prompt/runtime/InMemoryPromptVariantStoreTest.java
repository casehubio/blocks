package io.casehub.blocks.prompt.runtime;

import io.casehub.blocks.prompt.PromptVariant;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InMemoryPromptVariantStoreTest {

    private PromptVariant variant(String signatureId, String variantId, double quality) {
        return new PromptVariant(signatureId, variantId, List.of(), null, quality,
                Instant.now(), null, 0);
    }

    @Test
    void storeAndRetrieveActive() {
        var store = new InMemoryPromptVariantStore();
        var v = variant("sig", "v1", 0.8);
        store.store(v);
        store.activate("sig", "v1", "control");
        assertThat(store.getActive("sig", "control")).isEqualTo(v);
    }

    @Test
    void getActiveReturnsNullWhenNotSet() {
        var store = new InMemoryPromptVariantStore();
        assertThat(store.getActive("sig", "control")).isNull();
    }

    @Test
    void activateNullClearsSlot() {
        var store = new InMemoryPromptVariantStore();
        var v = variant("sig", "v1", 0.8);
        store.store(v);
        store.activate("sig", "v1", "experiment");
        store.activate("sig", null, "experiment");
        assertThat(store.getActive("sig", "experiment")).isNull();
    }

    @Test
    void historyReturnsInReverseChronologicalOrder() {
        var store = new InMemoryPromptVariantStore();
        store.store(variant("sig", "v1", 0.5));
        store.store(variant("sig", "v2", 0.7));
        store.store(variant("sig", "v3", 0.9));
        var history = store.getHistory("sig", 2);
        assertThat(history).extracting(PromptVariant::variantId)
                .containsExactly("v3", "v2");
    }

    @Test
    void historyIsolatedBySignature() {
        var store = new InMemoryPromptVariantStore();
        store.store(variant("sig-a", "v1", 0.8));
        store.store(variant("sig-b", "v2", 0.7));
        assertThat(store.getHistory("sig-a", 10)).hasSize(1);
        assertThat(store.getHistory("sig-b", 10)).hasSize(1);
    }

    @Test
    void slotsAreIndependentPerSignature() {
        var store = new InMemoryPromptVariantStore();
        var v1 = variant("sig", "v1", 0.8);
        var v2 = variant("sig", "v2", 0.9);
        store.store(v1);
        store.store(v2);
        store.activate("sig", "v1", "control");
        store.activate("sig", "v2", "experiment");
        assertThat(store.getActive("sig", "control").variantId()).isEqualTo("v1");
        assertThat(store.getActive("sig", "experiment").variantId()).isEqualTo("v2");
    }

    @Test
    void activateUnknownVariantThrows() {
        var store = new InMemoryPromptVariantStore();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.activate("sig", "nonexistent", "control"));
    }

    @Test
    void emptyHistoryReturnsEmptyList() {
        var store = new InMemoryPromptVariantStore();
        assertThat(store.getHistory("sig", 10)).isEmpty();
    }

    @Test
    void historyLimitExceedingActualReturnsAll() {
        var store = new InMemoryPromptVariantStore();
        store.store(variant("sig", "v1", 0.8));
        store.store(variant("sig", "v2", 0.9));
        assertThat(store.getHistory("sig", 100)).hasSize(2);
    }

    @Test
    void activateWithDuplicateIdReturnsLatestVersion() {
        var store = new InMemoryPromptVariantStore();
        var original = new PromptVariant("sig", "v1", List.of(), null, 0.8,
                                         Instant.now(), null, 0);
        var updated = new PromptVariant("sig", "v1", List.of(), null, 0.9,
                                        Instant.now(), null, 2);
        store.store(original);
        store.store(updated);
        store.activate("sig", "v1", "experiment");
        var active = store.getActive("sig", "experiment");
        assertThat(active.consecutiveWins()).isEqualTo(2);
        assertThat(active.qualityScore()).isEqualTo(0.9);
    }

}
