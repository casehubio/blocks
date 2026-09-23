package io.casehub.blocks.social.jpa.quarkus;

import io.casehub.blocks.agentic.social.narrative.IndividualEpisode;
import io.casehub.blocks.agentic.social.narrative.NarrativeScope;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.blocks.agentic.social.narrative.NarrativeStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JpaNarrativeStoreTest {

    @Inject
    NarrativeStore store;

    @Test
    void storeAndLoad() {
        var now = Instant.now();
        var fragment = new IndividualEpisode("ep1", now, null, List.of("tag1"), "desc", 0.5, List.of("r1"));
        var state = new NarrativeState("scope-n1", "tenant-n1", NarrativeScope.INDIVIDUAL,
                List.of(fragment), now, 3);
        store.store(state);
        var loaded = store.load("scope-n1", "tenant-n1");
        assertThat(loaded).isNotNull();
        assertThat(loaded.fragments()).hasSize(1);
        assertThat(loaded.fragments().get(0)).isInstanceOf(IndividualEpisode.class);
        assertThat(((IndividualEpisode) loaded.fragments().get(0)).description()).isEqualTo("desc");
        assertThat(loaded.reflectionCountAtSynthesis()).isEqualTo(3);
    }

    @Test
    void loadMissing() {
        assertThat(store.load("none", "none")).isNull();
    }

    @Test
    void mergeOnDuplicateKey() {
        var now = Instant.now();
        store.store(new NarrativeState("scope-n2", "tenant-n2", NarrativeScope.INDIVIDUAL,
                List.of(), now, 1));
        var updated = new IndividualEpisode("ep2", now, null, List.of(), "updated", 0.8, List.of());
        store.store(new NarrativeState("scope-n2", "tenant-n2", NarrativeScope.INDIVIDUAL,
                List.of(updated), now, 5));
        var loaded = store.load("scope-n2", "tenant-n2");
        assertThat(loaded).isNotNull();
        assertThat(loaded.fragments()).hasSize(1);
        assertThat(loaded.reflectionCountAtSynthesis()).isEqualTo(5);
    }
}
