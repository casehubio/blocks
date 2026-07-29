package io.casehub.blocks.channel.summary;

import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.SummaryResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicMessageSummariserTest {

    private final HeuristicMessageSummariser summariser = new HeuristicMessageSummariser();

    @Test
    void extractsParticipantsAndTopics() {
        var msgs = List.of(
                message("alice", "First point", "architecture"),
                message("bob", "Counterpoint", "testing"),
                message("alice", "Response", "architecture"));

        var result = summariser.summarise(msgs, null).toCompletableFuture().join();

        assertThat(result.text()).contains("alice", "bob");
        assertThat(result.text()).contains("architecture", "testing");
        assertThat(result.text()).contains("3 messages");
        assertThat(result.annotations()).containsEntry("tier", "grouped");
        assertThat(result.annotations()).containsEntry("participants", "alice,bob");
        assertThat(result.annotations()).containsKey("topics");
    }

    @Test
    void mergesParticipantsAndTopicsAcrossInvocations() {
        var first = List.of(message("alice", "Hello", "caching"));
        var firstResult = summariser.summarise(first, null).toCompletableFuture().join();

        var second = List.of(message("bob", "Hi", "indexing"));
        var secondResult = summariser.summarise(second, firstResult).toCompletableFuture().join();

        assertThat(secondResult.annotations().get("participants")).isEqualTo("alice,bob");
        assertThat(secondResult.annotations().get("topics")).isEqualTo("caching,indexing");
        assertThat(secondResult.text()).startsWith(firstResult.text());
    }

    @Test
    void propagatesUnknownAnnotationKeys() {
        var prev = new SummaryResult("prior", Map.of("domain", "medical", "urgency", "high"));
        var result = summariser.summarise(
                List.of(message("alice", "text", null)), prev)
                .toCompletableFuture().join();

        assertThat(result.annotations())
                .containsEntry("domain", "medical")
                .containsEntry("urgency", "high")
                .containsEntry("tier", "grouped");
    }

    @Test
    void nullTopicAndSender_handledGracefully() {
        var msg = Message.builder()
                .id(1L).channelId(UUID.randomUUID())
                .sender(null).content("text").topic(null)
                .messageType(MessageType.RESPONSE)
                .createdAt(Instant.now()).build();

        var result = summariser.summarise(List.of(msg), null).toCompletableFuture().join();

        assertThat(result.text()).contains("1 messages");
        assertThat(result.annotations()).containsEntry("tier", "grouped");
    }

    private static Message message(String sender, String content, String topic) {
        return Message.builder()
                .id(1L).channelId(UUID.randomUUID())
                .sender(sender).content(content).topic(topic)
                .messageType(MessageType.RESPONSE)
                .createdAt(Instant.now()).build();
    }
}
