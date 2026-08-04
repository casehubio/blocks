package io.casehub.blocks.channel.summary;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.channel.ThreadSummaryUpdatedEvent;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.api.spi.SummaryResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@ApplicationScoped
public class ThreadSummaryObserver {

    private static final System.Logger LOG =
            System.getLogger(ThreadSummaryObserver.class.getName());
    static final int MAX_THREAD_MESSAGES = 500;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private final ContentSummariser<Message> contentSummariser;
    private final CrossTenantMessageStore messageStore;
    private final ThreadSummaryStore threadSummaryStore;
    private final Event<ThreadSummaryUpdatedEvent> summaryEvents;

    @Inject ManagedExecutor executor;

    @Inject
    public ThreadSummaryObserver(ContentSummariser<Message> contentSummariser,
                                 CrossTenantMessageStore messageStore,
                                 ThreadSummaryStore threadSummaryStore,
                                 Event<ThreadSummaryUpdatedEvent> summaryEvents) {
        this.contentSummariser = contentSummariser;
        this.messageStore = messageStore;
        this.threadSummaryStore = threadSummaryStore;
        this.summaryEvents = summaryEvents;
    }

    void onTerminalMessage(
            @Observes(during = TransactionPhase.AFTER_SUCCESS)
            MessageReceivedEvent event) {
        MessageType type = event.messageType();
        if (type != MessageType.DONE && type != MessageType.FAILURE) return;
        if (event.correlationId() == null) return;

        String key = event.channelId() + ":" + event.correlationId();
        if (!inFlight.add(key)) return;

        if (executor == null) {
            try {
                summariseThread(event.channelId(), event.correlationId(),
                        event.channelName(), event.tenancyId());
            } finally {
                inFlight.remove(key);
            }
            return;
        }

        try {
            String channelName = event.channelName();
            String tenancyId = event.tenancyId();
            UUID channelId = event.channelId();
            String correlationId = event.correlationId();
            executor.submit(() -> {
                try {
                    summariseThread(channelId, correlationId,
                            channelName, tenancyId);
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(key);
            LOG.log(System.Logger.Level.WARNING,
                    "Executor rejected thread summary for "
                            + event.correlationId(), e);
        }
    }

    void summariseThread(UUID channelId, String correlationId,
                         String channelName, String tenancyId) {
        try {
            List<Message> messages = messageStore.scan(
                    MessageQuery.builder()
                            .channelId(channelId)
                            .correlationId(correlationId)
                            .limit(MAX_THREAD_MESSAGES)
                            .build());

            if (messages.isEmpty()) return;

            SummaryResult previous = threadSummaryStore
                    .findByCorrelationId(channelId, correlationId)
                    .map(ts -> new SummaryResult(ts.content(), ts.annotations()))
                    .orElse(null);

            SummaryResult result = contentSummariser
                    .summarise(messages, previous)
                    .toCompletableFuture().join();

            threadSummaryStore.save(ThreadSummary.builder(channelId, correlationId)
                    .content(result.text())
                    .annotations(result.annotations())
                    .updatedAt(Instant.now())
                    .updatedBy("system:thread-summariser")
                    .tenancyId(tenancyId)
                    .build());

            summaryEvents.fireAsync(new ThreadSummaryUpdatedEvent(
                    channelId, channelName, correlationId,
                    "system:thread-summariser"));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Thread summarisation failed for " + correlationId
                            + " on channel " + channelId, e);
        }
    }
}
