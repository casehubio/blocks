package io.casehub.blocks.conversation;

import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonGroundConvergenceIntegrationTest {

    private final AtomicLong messageIdSeq = new AtomicLong(1);
    private final Instant base = Instant.parse("2026-07-21T10:00:00Z");
    private final ConversationProjectionTest.TestConversationProjection projection =
            new ConversationProjectionTest.TestConversationProjection();

    private MessageView message(String content, String correlationId, String topic,
                                 MessageType type, String sender, int minuteOffset) {
        var msg = mock(MessageView.class);
        when(msg.content()).thenReturn(content);
        when(msg.correlationId()).thenReturn(correlationId);
        when(msg.topic()).thenReturn(topic);
        when(msg.id()).thenReturn(messageIdSeq.getAndIncrement());
        when(msg.type()).thenReturn(type);
        when(msg.sender()).thenReturn(sender);
        when(msg.createdAt()).thenReturn(base.plusSeconds(minuteOffset * 60L));
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
    void consensusScenario_debateReachesAgreement() {
        var state = projection.identity();

        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "AGENT_A", "round", "1",
                "priority", "HIGH", "scope", "design"),
                "Use event sourcing"), "p1", "architecture", MessageType.COMMAND, "agent-a", 1));
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "AGENT_B", "round", "1",
                "priority", "MEDIUM", "scope", "design"),
                "Use CQRS with projections"), "p2", "architecture", MessageType.COMMAND, "agent-b", 2));

        state = projection.apply(state, message(encode(meta(
                "entryType", "ACCEPT", "role", "AGENT_B", "round", "2"),
                "Event sourcing works"), "p1", "architecture", MessageType.RESPONSE, "agent-b", 3));
        state = projection.apply(state, message(encode(meta(
                "entryType", "ACCEPT", "role", "AGENT_A", "round", "2"),
                "Projections are the way"), "p2", "architecture", MessageType.RESPONSE, "agent-a", 4));

        var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.explicitAcknowledgement(1));
        assertThat(cg.establishedFacts()).hasSize(2);
        assertThat(cg.pendingClaims()).isEmpty();

        var signal = ConvergenceAnalyser.analyse(state, cg,
                ConvergencePolicies.structural(0.8, 3), 10);
        assertThat(signal.state()).isEqualTo(ConvergenceState.CONSENSUS);

        var config = ConversationRendererConfig.builder()
                .groupByTopic(true)
                .showEpistemicStatus(true)
                .showConvergenceSignal(true)
                .build();
        var renderer = new ConversationRenderer(config);
        var ctx = new RenderContext(Map.of(), cg, signal, Map.of());
        var result = renderer.render(state, ctx);

        assertThat(result).contains("**Convergence:** CONSENSUS");
        assertThat(result).contains("[established by agent-b]");
        assertThat(result).contains("[established by agent-a]");
    }

    @Test
    void deadlockScenario_positionsHarden() {
        var state = projection.identity();

        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "AGENT_A", "round", "1",
                "priority", "HIGH", "scope", "design"),
                "Use microservices"), "p1", "architecture", MessageType.COMMAND, "agent-a", 1));
        state = projection.apply(state, message(encode(meta(
                "entryType", "REJECT", "role", "AGENT_B", "round", "2"),
                "Monolith is better"), "p1", "architecture", MessageType.DECLINE, "agent-b", 2));

        var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.explicitAcknowledgement(1));
        assertThat(cg.disputedPoints()).hasSize(1);

        var signal = ConvergenceAnalyser.analyse(state, cg,
                ConvergencePolicies.commonGroundRatio(0.8, 0.5), 10);
        assertThat(signal.state()).isEqualTo(ConvergenceState.DEADLOCK);
    }

    @Test
    void diminishingReturnsScenario_conversationGoesThin() {
        var state = projection.identity();

        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC", "role", "AGENT_A", "round", "1",
                "priority", "HIGH", "scope", "design"),
                "This is a detailed analysis of the system architecture with full context"),
                "p1", "design", MessageType.COMMAND, "agent-a", 1));

        state = projection.apply(state, message(encode(meta(
                "entryType", "ACCEPT", "role", "AGENT_B", "round", "8"),
                "ok"), "p1", "design", MessageType.STATUS, "agent-b", 10));

        var cg = CommonGroundAnalyser.analyse(state, EpistemicRules.explicitAcknowledgement(1));
        var signal = ConvergenceAnalyser.analyse(state, cg,
                ConvergencePolicies.structural(0.8, 3), 10);
        assertThat(signal.state()).isEqualTo(ConvergenceState.DIMINISHING_RETURNS);
    }
}
