package io.casehub.blocks.summarisation.examples.channel;

import io.casehub.blocks.channel.summary.ChannelSummariser;
import io.casehub.blocks.channel.summary.HeuristicMessageSummariser;
import io.casehub.blocks.summarisation.*;
import io.casehub.blocks.summarisation.llm.LlmContentSummariser;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end example: tiered channel summarisation.
 *
 * Shows how a domain repo would wire ContentSummariser implementations
 * with TieredContentSummariser to get adaptive summarisation — verbatim
 * for small batches, heuristic for medium, LLM for large — with
 * annotation accumulation across invocations.
 */
class TieredChannelSummaryExampleTest {

    // -- Tier 1: verbatim for ≤3 messages ----------------------------------
    // -- Tier 2: heuristic for 4–15 messages --------------------------------
    // -- Tier 3: LLM for 16+ messages ----------------------------------------

    private final AgentProvider agentProvider = mock(AgentProvider.class);

    private ContentSummariser<Message> buildTieredSummariser() {
        var verbatim = new VerbatimContentSummariser<Message>(
                msg -> "[" + msg.sender() + "] " + msg.content());
        var heuristic = new HeuristicMessageSummariser();
        var llm = new LlmContentSummariser<Message>(agentProvider,
                msg -> "[" + msg.sender() + "] " + msg.content(),
                SummaryMode.EDIT, "Channel: design-review");
        return new TieredContentSummariser<Message>(verbatim, heuristic, llm, 3, 15);
    }

    @Test
    void smallBatch_verbatimRendering() {
        var tiered = buildTieredSummariser();
        var msgs = messages("alice", "Let's use Redis", "bob", "Agreed");

        var result = tiered.summarise(msgs, null).toCompletableFuture().join();

        assertThat(result.text()).contains("[alice] Let's use Redis");
        assertThat(result.text()).contains("[bob] Agreed");
        assertThat(result.annotations()).containsEntry("tier", "verbatim");
    }

    @Test
    void mediumBatch_heuristicExtraction() {
        var tiered = buildTieredSummariser();
        var msgs = messages(
                "alice", "First point", "architecture",
                "bob", "Counterpoint", "testing",
                "carol", "Third view", "architecture",
                "alice", "Response", "testing",
                "dave", "New angle", "deployment");

        var result = tiered.summarise(msgs, null).toCompletableFuture().join();

        assertThat(result.annotations()).containsEntry("tier", "grouped");
        assertThat(result.annotations().get("participants")).contains("alice", "bob", "carol", "dave");
        assertThat(result.annotations().get("topics")).contains("architecture", "testing", "deployment");
        assertThat(result.text()).contains("5 messages");
    }

    @Test
    void largeBatch_llmSynthesis() {
        when(agentProvider.invoke(any()))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("The team discussed Redis vs Memcached, " +
                                "converging on Redis for its persistence guarantees.")));

        var tiered = buildTieredSummariser();
        var msgs = generateMessages(20);

        var result = tiered.summarise(msgs, null).toCompletableFuture().join();

        assertThat(result.annotations()).containsEntry("tier", "synthesised");
        assertThat(result.text()).contains("Redis");
    }

    @Test
    void annotationsAccumulateAcrossInvocations() {
        var tiered = buildTieredSummariser();

        // Invocation 1: medium batch from alice and bob about caching
        var batch1 = messages(
                "alice", "We need caching", "caching",
                "bob", "Redis or Memcached?", "caching",
                "alice", "Redis has persistence", "caching",
                "bob", "Good point", "caching");
        var result1 = tiered.summarise(batch1, null).toCompletableFuture().join();

        assertThat(result1.annotations().get("participants")).isEqualTo("alice,bob");
        assertThat(result1.annotations().get("topics")).isEqualTo("caching");

        // Invocation 2: small batch (verbatim) from carol about indexing
        var batch2 = messages("carol", "What about search indexing?", "indexing");
        var result2 = tiered.summarise(batch2, result1).toCompletableFuture().join();

        // Verbatim tier preserves previous annotations — carol's batch doesn't
        // destroy the participant/topic accumulation from batch 1
        assertThat(result2.annotations()).containsEntry("tier", "verbatim");
        assertThat(result2.annotations().get("participants")).isEqualTo("alice,bob");

        // Invocation 3: medium batch adds dave, new topic
        var batch3 = messages(
                "dave", "Deployment strategy", "deployment",
                "alice", "Blue-green", "deployment",
                "carol", "Canary preferred", "deployment",
                "dave", "Let's benchmark both", "deployment");
        var result3 = tiered.summarise(batch3, result2).toCompletableFuture().join();

        // Heuristic merges all accumulated participants and topics
        assertThat(result3.annotations().get("participants")).contains("alice", "bob", "carol", "dave");
        assertThat(result3.annotations().get("topics")).contains("caching", "deployment");
        assertThat(result3.text())
                .contains(result2.text())
                .contains("4 messages");
    }

    @Test
    void hookIntegration_channelSummariserDelegatesToTiered() {
        var tiered = buildTieredSummariser();
        var hook = new ChannelSummariser(tiered);
        var msgs = messages("alice", "Hello", "bob", "Hi there");

        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "design-review", "tenant-1",
                null, null, 2, msgs, q -> List.of());

        var result = hook.update(ctx);

        assertThat(result.text()).contains("[alice] Hello");
        assertThat(result.annotations()).containsEntry("tier", "verbatim");
    }

    @Test
    void pipelineIntegration_sameAlgorithmInSummarisationRunner() {
        var heuristic = new HeuristicMessageSummariser();
        Summariser<Message, String> pipelineSummariser =
                new ContentSummariserToSummariser<>(heuristic);

        var events = List.of(
                new LevelEvent<>(message("alice", "Point A", "design"), 1000L, new EventLevel("raw", 0)),
                new LevelEvent<>(message("bob", "Point B", "design"), 2000L, new EventLevel("raw", 0)),
                new LevelEvent<>(message("carol", "Point C", "testing"), 3000L, new EventLevel("raw", 0)),
                new LevelEvent<>(message("alice", "Point D", "design"), 4000L, new EventLevel("raw", 0)));

        var result = pipelineSummariser.summarise(events).toCompletableFuture().join();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst())
                .contains("alice", "bob", "carol")
                .contains("design", "testing")
                .contains("4 messages");
    }

    // -- Helpers -------------------------------------------------------------

    private static List<Message> messages(String... senderContentPairs) {
        var list = new ArrayList<Message>();
        for (int i = 0; i < senderContentPairs.length; i += 2) {
            list.add(message(senderContentPairs[i], senderContentPairs[i + 1], null));
        }
        return list;
    }

    private static List<Message> messages(String sender1, String content1, String topic1,
                                           String sender2, String content2, String topic2) {
        return List.of(
                message(sender1, content1, topic1),
                message(sender2, content2, topic2));
    }

    private static List<Message> messages(String sender1, String content1, String topic1,
                                           String sender2, String content2, String topic2,
                                           String sender3, String content3, String topic3,
                                           String sender4, String content4, String topic4) {
        return List.of(
                message(sender1, content1, topic1),
                message(sender2, content2, topic2),
                message(sender3, content3, topic3),
                message(sender4, content4, topic4));
    }

    private static List<Message> messages(String sender1, String content1, String topic1,
                                           String sender2, String content2, String topic2,
                                           String sender3, String content3, String topic3,
                                           String sender4, String content4, String topic4,
                                           String sender5, String content5, String topic5) {
        return List.of(
                message(sender1, content1, topic1),
                message(sender2, content2, topic2),
                message(sender3, content3, topic3),
                message(sender4, content4, topic4),
                message(sender5, content5, topic5));
    }

    private static List<Message> messages(String sender, String content, String topic) {
        return List.of(message(sender, content, topic));
    }

    private static List<Message> generateMessages(int count) {
        var senders = new String[]{"alice", "bob", "carol", "dave"};
        var topics = new String[]{"caching", "deployment", "monitoring"};
        var list = new ArrayList<Message>();
        for (int i = 0; i < count; i++) {
            list.add(message(senders[i % senders.length],
                    "Message " + i, topics[i % topics.length]));
        }
        return list;
    }

    private static Message message(String sender, String content, String topic) {
        return Message.builder()
                .id((long) content.hashCode())
                .channelId(UUID.randomUUID())
                .sender(sender)
                .content(content)
                .topic(topic)
                .messageType(MessageType.RESPONSE)
                .createdAt(Instant.now())
                .build();
    }
}
