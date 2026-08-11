package io.casehub.blocks.channel.summary;

import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpThreadSummaryStoreTest {

    private final NoOpThreadSummaryStore store = new NoOpThreadSummaryStore();

    @Test
    void saveReturnsSameInstance() {
        var summary = ThreadSummary.builder(UUID.randomUUID(), "corr-1")
                .content("summary text")
                .annotations(Map.of())
                .updatedAt(Instant.now())
                .updatedBy("system")
                .tenancyId("tenant-1")
                .build();

        assertThat(store.save(summary)).isSameAs(summary);
    }

    @Test
    void findByCorrelationIdReturnsEmpty() {
        assertThat(store.findByCorrelationId(UUID.randomUUID(), "corr-1")).isEmpty();
    }

    @Test
    void findByChannelReturnsEmptyList() {
        assertThat(store.findByChannel(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deleteIsNoOp() {
        store.delete(UUID.randomUUID(), "corr-1");
    }

    @Test
    void implementsThreadSummaryStore() {
        ThreadSummaryStore spi = store;
        assertThat(spi.findByChannel(UUID.randomUUID())).isEmpty();
    }
}
