package io.casehub.blocks.channel.summary;

import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicChannelSummariserTest {

    private final HeuristicChannelSummariser summariser = new HeuristicChannelSummariser();

    @Test
    void emptyMessages_returnsCurrentSummary() {
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "test-channel", "tenant-1",
                SummaryResult.ofText("existing summary"), 10L, 0,
                List.of(), q -> List.of());

        assertThat(summariser.update(ctx).text()).isEqualTo("existing summary");
    }

    @Test
    void nullCurrentSummary_producesFreshSummary() {
        var msg = message("alice", "Hello everyone");
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "test-channel", "tenant-1",
                null, null, 1,
                List.of(msg), q -> List.of());

        var result = summariser.update(ctx).text();
        assertThat(result).contains("1 messages");
        assertThat(result).contains("alice");
        assertThat(result).doesNotContain("null");
    }

    @Test
    void multipleParticipants_listedInSummary() {
        var msgs = List.of(
                message("alice", "First point"),
                message("bob", "Counterpoint"),
                message("alice", "Response"));
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "debate", "tenant-1",
                SummaryResult.ofText("Prior summary."), null, 3,
                msgs, q -> List.of());

        var result = summariser.update(ctx).text();
        assertThat(result).contains("alice", "bob");
        assertThat(result).contains("Prior summary.");
        assertThat(result).contains("3 messages");
    }

    @Test
    void messagesWithTopics_topicsListed() {
        var msg1 = Message.builder().id(1L).channelId(UUID.randomUUID())
                .sender("alice").content("text").messageType(MessageType.RESPONSE)
                .topic("architecture").createdAt(Instant.now()).build();
        var msg2 = Message.builder().id(2L).channelId(UUID.randomUUID())
                .sender("bob").content("text").messageType(MessageType.RESPONSE)
                .topic("testing").createdAt(Instant.now()).build();
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "dev", "tenant-1",
                null, null, 2,
                List.of(msg1, msg2), q -> List.of());

        var result = summariser.update(ctx).text();
        assertThat(result).contains("architecture", "testing");
    }

    private static Message message(String sender, String content) {
        return Message.builder()
                .id(1L)
                .channelId(UUID.randomUUID())
                .sender(sender)
                .content(content)
                .messageType(MessageType.RESPONSE)
                .createdAt(Instant.now())
                .build();
    }
}
