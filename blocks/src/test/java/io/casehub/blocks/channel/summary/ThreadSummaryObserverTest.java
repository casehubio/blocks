package io.casehub.blocks.channel.summary;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.channel.ThreadSummaryUpdatedEvent;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class ThreadSummaryObserverTest {

    private ThreadSummaryObserver observer;
    private ContentSummariser<Message> summariser;
    private CrossTenantMessageStore messageStore;
    private ThreadSummaryStore threadSummaryStore;
    private Event<ThreadSummaryUpdatedEvent> summaryEvents;

    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final String CHANNEL_NAME = "test-channel";
    private static final String CORRELATION_ID = "corr-001";
    private static final String TENANCY_ID = "tenant-1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        summariser = mock(ContentSummariser.class);
        messageStore = mock(CrossTenantMessageStore.class);
        threadSummaryStore = mock(ThreadSummaryStore.class);
        summaryEvents = mock(Event.class);
        observer = new ThreadSummaryObserver(
                summariser, messageStore, threadSummaryStore, summaryEvents);
    }

    @Test
    void doneMessageTriggersSummarisation() {
        List<Message> messages = List.of(mockMessage());
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.empty());
        when(summariser.summarise(eq(messages), isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        SummaryResult.ofText("Thread done")));
        when(threadSummaryStore.save(any())).thenAnswer(i -> i.getArgument(0));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        ArgumentCaptor<ThreadSummary> cap = ArgumentCaptor.forClass(ThreadSummary.class);
        verify(threadSummaryStore).save(cap.capture());
        assertThat(cap.getValue().content()).isEqualTo("Thread done");
        assertThat(cap.getValue().correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(cap.getValue().tenancyId()).isEqualTo(TENANCY_ID);
    }

    @Test
    void failureMessageTriggersSummarisation() {
        List<Message> messages = List.of(mockMessage());
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.empty());
        when(summariser.summarise(eq(messages), isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        SummaryResult.ofText("Thread failed")));
        when(threadSummaryStore.save(any())).thenAnswer(i -> i.getArgument(0));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        verify(threadSummaryStore).save(any());
    }

    @Test
    void nonTerminalMessageIsIgnored() {
        MessageReceivedEvent event = mockEvent(MessageType.STATUS, CORRELATION_ID);
        observer.onTerminalMessage(event);
        verifyNoInteractions(messageStore, summariser, threadSummaryStore);
    }

    @Test
    void handoffMessageIsIgnored() {
        MessageReceivedEvent event = mockEvent(MessageType.HANDOFF, CORRELATION_ID);
        observer.onTerminalMessage(event);
        verifyNoInteractions(messageStore, summariser, threadSummaryStore);
    }

    @Test
    void responseMessageIsIgnored() {
        MessageReceivedEvent event = mockEvent(MessageType.RESPONSE, CORRELATION_ID);
        observer.onTerminalMessage(event);
        verifyNoInteractions(messageStore, summariser, threadSummaryStore);
    }

    @Test
    void nullCorrelationIdIsIgnored() {
        MessageReceivedEvent event = mockEvent(MessageType.DONE, null);
        observer.onTerminalMessage(event);
        verifyNoInteractions(messageStore, summariser, threadSummaryStore);
    }

    @Test
    void emptyMessagesSkipsSummarisation() {
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of());

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        verifyNoInteractions(summariser);
        verify(threadSummaryStore, never()).save(any());
    }

    @Test
    void previousSummaryPassedToSummariser() {
        List<Message> messages = List.of(mockMessage());
        ThreadSummary previous = ThreadSummary.builder(CHANNEL_ID, CORRELATION_ID)
                .content("Previous summary")
                .annotations(Map.of("key", "val"))
                .build();
        SummaryResult previousResult = new SummaryResult("Previous summary", Map.of("key", "val"));

        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.of(previous));
        when(summariser.summarise(eq(messages), eq(previousResult)))
                .thenReturn(CompletableFuture.completedFuture(
                        SummaryResult.ofText("Updated summary")));
        when(threadSummaryStore.save(any())).thenAnswer(i -> i.getArgument(0));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        verify(summariser).summarise(messages, previousResult);
    }

    @Test
    void summariserFailureIsLoggedNotPropagated() {
        List<Message> messages = List.of(mockMessage());
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.empty());
        when(summariser.summarise(any(), isNull()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("LLM timeout")));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        verify(threadSummaryStore, never()).save(any());
    }

    @Test
    void eventFiredAfterSave() {
        List<Message> messages = List.of(mockMessage());
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(messages);
        when(threadSummaryStore.findByCorrelationId(CHANNEL_ID, CORRELATION_ID))
                .thenReturn(Optional.empty());
        when(summariser.summarise(any(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        SummaryResult.ofText("Done")));
        when(threadSummaryStore.save(any())).thenAnswer(i -> i.getArgument(0));

        observer.summariseThread(CHANNEL_ID, CORRELATION_ID, CHANNEL_NAME, TENANCY_ID);

        ArgumentCaptor<ThreadSummaryUpdatedEvent> cap =
                ArgumentCaptor.forClass(ThreadSummaryUpdatedEvent.class);
        verify(summaryEvents).fireAsync(cap.capture());
        assertThat(cap.getValue().channelName()).isEqualTo(CHANNEL_NAME);
        assertThat(cap.getValue().correlationId()).isEqualTo(CORRELATION_ID);
    }

    private Message mockMessage() {
        Message m = mock(Message.class);
        when(m.id()).thenReturn(1L);
        when(m.content()).thenReturn("Test message");
        when(m.createdAt()).thenReturn(Instant.now());
        return m;
    }

    private MessageReceivedEvent mockEvent(MessageType type, String correlationId) {
        return new MessageReceivedEvent(
                1L, CHANNEL_NAME, CHANNEL_ID, TENANCY_ID,
                type, "sender-1", null, ActorType.AGENT,
                correlationId, Instant.now(), "content", null);
    }
}
