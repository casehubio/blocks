package io.casehub.blocks.channel.summary;

import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DefaultBean
@ApplicationScoped
public class NoOpThreadSummaryStore implements ThreadSummaryStore {

    @Override
    public ThreadSummary save(ThreadSummary summary) {
        return summary;
    }

    @Override
    public Optional<ThreadSummary> findByCorrelationId(UUID channelId, String correlationId) {
        return Optional.empty();
    }

    @Override
    public List<ThreadSummary> findByChannel(UUID channelId) {
        return List.of();
    }

    @Override
    public void delete(UUID channelId, String correlationId) {
    }
}
