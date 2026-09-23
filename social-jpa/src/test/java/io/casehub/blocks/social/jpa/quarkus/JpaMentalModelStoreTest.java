package io.casehub.blocks.social.jpa.quarkus;

import io.casehub.blocks.agentic.social.AttributedState;
import io.casehub.blocks.agentic.social.BdiDimension;
import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.agentic.social.MentalModelStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JpaMentalModelStoreTest {

    @Inject
    MentalModelStore store;

    @Test
    void storeAndLookup() {
        var now = Instant.now();
        var beliefs = List.of(new AttributedState("sky-blue", "sky is blue", 0.9, 5, now, BdiDimension.BELIEF));
        var desires = List.of(new AttributedState("explore", "wants to explore", 0.7, 2, now, BdiDimension.DESIRE));
        var intentions = List.of(new AttributedState("ask-q", "ask question", 0.6, 1, now, BdiDimension.INTENTION));
        var snapshot = new MentalModelSnapshot("agent-mm1", "subject-mm1", "tenant-mm1",
                beliefs, desires, intentions, now, null, now);
        store.store(snapshot);
        var found = store.lookup("agent-mm1", "subject-mm1", "tenant-mm1");
        assertThat(found).isPresent();
        assertThat(found.get().beliefs()).hasSize(1);
        assertThat(found.get().beliefs().get(0).key()).isEqualTo("sky-blue");
        assertThat(found.get().beliefs().get(0).dimension()).isEqualTo(BdiDimension.BELIEF);
        assertThat(found.get().desires()).hasSize(1);
        assertThat(found.get().intentions()).hasSize(1);
    }

    @Test
    void lookupMissing() {
        assertThat(store.lookup("none", "none", "none")).isEmpty();
    }

    @Test
    void findByAgent() {
        var now = Instant.now();
        store.store(new MentalModelSnapshot("agent-mm2", "s1", "tenant-mm2",
                List.of(), List.of(), List.of(), now, null, now));
        store.store(new MentalModelSnapshot("agent-mm2", "s2", "tenant-mm2",
                List.of(), List.of(), List.of(), now, null, now));
        var results = store.findByAgent("agent-mm2", "tenant-mm2");
        assertThat(results).hasSize(2);
    }

    @Test
    void mergeOnDuplicateKey() {
        var now = Instant.now();
        store.store(new MentalModelSnapshot("agent-mm3", "s1", "tenant-mm3",
                List.of(), List.of(), List.of(), now, null, now));
        var updatedBeliefs = List.of(
                new AttributedState("updated", "updated belief", 0.95, 10, now, BdiDimension.BELIEF));
        store.store(new MentalModelSnapshot("agent-mm3", "s1", "tenant-mm3",
                updatedBeliefs, List.of(), List.of(), now, now, now));
        var found = store.lookup("agent-mm3", "s1", "tenant-mm3");
        assertThat(found).isPresent();
        assertThat(found.get().beliefs()).hasSize(1);
        assertThat(found.get().beliefs().get(0).key()).isEqualTo("updated");
        assertThat(found.get().lastInference()).isNotNull();
    }

    @Test
    void eraseSubject() {
        var now = Instant.now();
        store.store(new MentalModelSnapshot("agent-mm4", "s1", "tenant-mm4",
                List.of(), List.of(), List.of(), now, null, now));
        store.eraseSubject("s1", "tenant-mm4");
        assertThat(store.lookup("agent-mm4", "s1", "tenant-mm4")).isEmpty();
    }
}
