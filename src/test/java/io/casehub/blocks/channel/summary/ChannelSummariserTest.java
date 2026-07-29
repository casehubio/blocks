package io.casehub.blocks.channel.summary;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelSummariserTest {

    @Test
    void delegatesToContentSummariser() {
        ContentSummariser<Message> delegate = (items, prev) ->
                CompletableFuture.completedFuture(
                        SummaryResult.ofText("delegated:" + items.size()));
        var hook = new ChannelSummariser(delegate);

        var msg = message("alice", "hello");
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "test-ch", "tenant-1",
                null, null, 1, List.of(msg), q -> List.of());

        var result = hook.update(ctx);
        assertThat(result.text()).isEqualTo("delegated:1");
    }

    @Test
    void emptyMessages_returnsPreviousResult() {
        ContentSummariser<Message> delegate = (items, prev) -> {
            throw new AssertionError("should not be called");
        };
        var hook = new ChannelSummariser(delegate);
        var prev = new SummaryResult("existing", Map.of("k", "v"));
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "ch", "t", prev, null, 0, List.of(), q -> List.of());

        assertThat(hook.update(ctx)).isSameAs(prev);
    }

    @Test
    void nullMessages_returnsEmptyResult() {
        ContentSummariser<Message> delegate = (items, prev) -> {
            throw new AssertionError("should not be called");
        };
        var hook = new ChannelSummariser(delegate);
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "ch", "t", null, null, 0, null, q -> List.of());

        assertThat(hook.update(ctx).text()).isEmpty();
    }

    @Test
    void passesPreviousResultToDelegate() {
        var captured = new java.util.concurrent.atomic.AtomicReference<SummaryResult>();
        ContentSummariser<Message> delegate = (items, prev) -> {
            captured.set(prev);
            return CompletableFuture.completedFuture(SummaryResult.ofText("ok"));
        };
        var hook = new ChannelSummariser(delegate);
        var prev = new SummaryResult("prior", Map.of("tier", "grouped"));
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "ch", "t", prev, null, 1,
                List.of(message("alice", "hi")), q -> List.of());

        hook.update(ctx);
        assertThat(captured.get()).isSameAs(prev);
    }

    private static Message message(String sender, String content) {
        return Message.builder()
                .id(1L).channelId(UUID.randomUUID())
                .sender(sender).content(content)
                .messageType(MessageType.RESPONSE)
                .createdAt(Instant.now()).build();
    }
}
