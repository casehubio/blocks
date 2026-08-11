package io.casehub.blocks.conversation;

import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.message.ReactionGroup;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicAwareConversationIntegrationTest {

    private final AtomicLong messageIdSeq = new AtomicLong(1);

    private final ConversationProjectionTest.TestConversationProjection projection =
            new ConversationProjectionTest.TestConversationProjection();

    private MessageView message(String content, String correlationId,
                                 String topic, MessageType type) {
        var msg = mock(MessageView.class);
        when(msg.content()).thenReturn(content);
        when(msg.correlationId()).thenReturn(correlationId);
        when(msg.topic()).thenReturn(topic);
        when(msg.id()).thenReturn(messageIdSeq.getAndIncrement());
        when(msg.type()).thenReturn(type);
        return msg;
    }

    private String encode(Map<String, String> meta, String body) {
        return ChannelMessageMeta.encode("TEST:", meta, body);
    }

    private Map<String, String> meta(String... pairs) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    void fullChoreography_multiTopicWithObligationChainsAndReactions() {
        var state = projection.identity();

        // Step 1: COMMAND in "review" topic — reviewer raises a point
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "REVIEWER",
                "round", "1", "priority", "HIGH", "scope", "correctness"),
                "NPE risk in handler"), "point-review-1", "review", MessageType.COMMAND));

        // Step 2: STATUS in "review" — implementor responds
        state = projection.apply(state, message(encode(meta(
                "entryType", "ACCEPT", "role", "IMPLEMENTOR",
                "round", "1"), "Working on fix"),
                "point-review-1", "review", MessageType.STATUS));

        // Step 3: DONE in "review" — implementor completes
        state = projection.apply(state, message(encode(meta(
                "entryType", "ACCEPT", "role", "IMPLEMENTOR",
                "round", "1"), "Fixed with null guard"),
                "point-review-1", "review", MessageType.DONE));

        // Step 4: COMMAND in "analysis" topic — separate concern
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "ANALYST",
                "round", "1", "priority", "MEDIUM", "scope", "performance"),
                "Query takes 3s"), "point-analysis-1", "analysis", MessageType.COMMAND));

        // Step 5: STATUS in "analysis"
        state = projection.apply(state, message(encode(meta(
                "entryType", "ACCEPT", "role", "IMPLEMENTOR",
                "round", "1"), "Added index"),
                "point-analysis-1", "analysis", MessageType.STATUS));

        // Verify topic attribution
        assertThat(state.points().get("point-review-1").topic()).isEqualTo("review");
        assertThat(state.points().get("point-analysis-1").topic()).isEqualTo("analysis");

        // Verify message IDs tracked
        assertThat(state.points().get("point-review-1").thread().get(0).messageId()).isNotNull();
        assertThat(state.points().get("point-review-1").thread().get(0).messageType())
                .isEqualTo(MessageType.COMMAND);
        assertThat(state.points().get("point-review-1").thread()).hasSize(3);
        assertThat(state.points().get("point-analysis-1").thread()).hasSize(2);

        // Build reactions
        Long reviewMsgId = state.points().get("point-review-1").thread().get(0).messageId();
        Long analysisMsgId = state.points().get("point-analysis-1").thread().get(0).messageId();

        var reactions = Map.of(
                reviewMsgId, List.of(new ReactionGroup("👍", 2, List.of("agent-a", "agent-b"))),
                analysisMsgId, List.of(new ReactionGroup("🔍", 1, List.of("agent-c"))));

        // Render with topic grouping and obligation chains
        var config = ConversationRendererConfig.builder()
                .groupByTopic(true)
                .showObligationChain(true)
                .statusEmoji(Map.of("OPEN", "🔴", "ACCEPTED", "✅"))
                .resolvedStatuses(Set.of("ACCEPTED"))
                .messageTypeLabel(Map.of(
                        MessageType.COMMAND, "commanded",
                        MessageType.STATUS, "progress",
                        MessageType.DONE, "completed"))
                .build();

        var renderer = new ConversationRenderer(config);
        var result = renderer.render(state, RenderContext.withReactions(reactions));

        // Topic sections present
        assertThat(result).contains("## review");
        assertThat(result).contains("## analysis");

        // Review topic: obligation chain complete (COMMAND → STATUS → DONE)
        assertThat(result).contains("commanded → progress → completed ✓");

        // Analysis topic: obligation chain incomplete (COMMAND → STATUS)
        assertThat(result).contains("commanded → progress ⏳");

        // Reactions present
        assertThat(result).contains("👍×2");
        assertThat(result).contains("🔍×1");

        // Topics ordered: review before analysis (insertion order)
        assertThat(result.indexOf("## review")).isLessThan(result.indexOf("## analysis"));
    }

    @Test
    void topicScoped_singleTopicView() {
        var state = projection.identity();

        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "REV", "round", "1"),
                "review point"), "p1", "review", MessageType.COMMAND));
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "REV", "round", "1"),
                "analysis point"), "p2", "analysis", MessageType.COMMAND));

        assertThat(state.points()).hasSize(2);
        assertThat(state.points().get("p1").topic()).isEqualTo("review");
        assertThat(state.points().get("p2").topic()).isEqualTo("analysis");

        // Flat rendering — both points visible regardless of topic
        var config = ConversationRendererConfig.builder()
                .groupByTopic(false)
                .build();
        var renderer = new ConversationRenderer(config);
        var result = renderer.render(state);

        assertThat(result).contains("review point");
        assertThat(result).contains("analysis point");
    }

    @Test
    void multiplePointsPerTopic_statusGroupingWithinTopic() {
        var state = projection.identity();

        // Two points in "review": one open, one resolved
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "REV", "round", "1",
                "priority", "HIGH", "scope", "bug"),
                "Critical bug"), "p1", "review", MessageType.COMMAND));
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "REV", "round", "1",
                "priority", "LOW", "scope", "style"),
                "Style nit"), "p2", "review", MessageType.COMMAND));
        // Resolve p2
        state = projection.apply(state, message(encode(meta(
                "entryType", "ACCEPT", "role", "IMP", "round", "1"),
                "Fixed"), "p2", "review", MessageType.DONE));

        var config = ConversationRendererConfig.builder()
                .groupByTopic(true)
                .resolvedStatuses(Set.of("ACCEPTED"))
                .build();
        var renderer = new ConversationRenderer(config);
        var result = renderer.render(state);

        // Both in "review" topic
        assertThat(result).contains("## review");
        // Unresolved before resolved within the topic
        int unresolvedPos = result.indexOf("Critical bug");
        int resolvedPos = result.indexOf("Style nit");
        assertThat(unresolvedPos).isLessThan(resolvedPos);
        // Resolved has strikethrough
        assertThat(result).contains("~~[p2]");
    }
}
