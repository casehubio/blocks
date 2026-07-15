package io.casehub.blocks.channel.examples.summarisation;

import io.casehub.blocks.channel.ChannelEventAdapter;
import io.casehub.blocks.channel.ChannelEventPublisher;
import io.casehub.blocks.summarisation.*;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelSummarisationPipelineTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final EventLevel L1 = new EventLevel("channel-events", 1);
    private static final EventLevel L2 = new EventLevel("episodes", 2);
    private static final EventLevel L3 = new EventLevel("phases", 3);

    record ChannelEvent(String correlationId, MessageType messageType,
                        String content, String sender) {}

    record Episode(String correlationId, int messageCount, String summary) {}

    record Phase(String name, int episodeCount) {}

    private MessageReceivedEvent channelMessage(MessageType type, String content,
                                                 String correlationId, String sender,
                                                 long epochMillis) {
        return new MessageReceivedEvent(null, "test-channel", CHANNEL_ID, "tenant-1",
            type, sender, correlationId, Instant.ofEpochMilli(epochMillis),
            content, "general");
    }

    @Test
    void fullPipeline_correlationChainCompletionTriggersEpisode() {
        var eventBus = new EventStreamBus<ChannelEvent>();
        var episodeBus = new EventStreamBus<Episode>();

        var adapter = new ChannelEventAdapter<>(
            event -> {
                if (event.correlationId() == null) return null;
                if (event.messageType() == MessageType.EVENT) return null;
                return new ChannelEvent(event.correlationId(), event.messageType(),
                    event.content(), event.senderId());
            }, L1, eventBus);

        Summariser<ChannelEvent, Episode> episodeSummariser = Summariser.ofSync(batch -> {
            var first = batch.get(0).payload();
            return List.of(new Episode(first.correlationId(), batch.size(),
                "chain of " + batch.size() + " messages"));
        });
        var keyedRunner = new KeyedSummarisationRunner<>(
            (ChannelEvent e) -> e.correlationId(),
            group -> group.stream().anyMatch(e -> e.payload().messageType().isTerminal()),
            0, episodeSummariser, episodeBus, L2);

        eventBus.subscribe(e -> true, e -> keyedRunner.collect(e));

        List<LevelEvent<Episode>> episodes = new ArrayList<>();
        episodeBus.subscribe(e -> true, episodes::add);

        adapter.onMessage(channelMessage(MessageType.COMMAND, "do task",
            "corr-1", "user-1", 1000));
        adapter.onMessage(channelMessage(MessageType.STATUS, "working",
            "corr-1", "agent-1", 2000));
        adapter.onMessage(channelMessage(MessageType.DONE, "finished",
            "corr-1", "agent-1", 3000));

        keyedRunner.tick(3000);

        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).payload().correlationId()).isEqualTo("corr-1");
        assertThat(episodes.get(0).payload().messageCount()).isEqualTo(3);
        assertThat(episodes.get(0).level()).isEqualTo(L2);
    }

    @Test
    void staleChain_forceEmittedOnTimeout() {
        var eventBus = new EventStreamBus<ChannelEvent>();
        var episodeBus = new EventStreamBus<Episode>();

        var adapter = new ChannelEventAdapter<>(
            event -> {
                if (event.correlationId() == null) return null;
                return new ChannelEvent(event.correlationId(), event.messageType(),
                    event.content(), event.senderId());
            }, L1, eventBus);

        Summariser<ChannelEvent, Episode> episodeSummariser = Summariser.ofSync(batch -> {
            var first = batch.get(0).payload();
            return List.of(new Episode(first.correlationId(), batch.size(), "stale"));
        });
        var keyedRunner = new KeyedSummarisationRunner<>(
            (ChannelEvent e) -> e.correlationId(),
            group -> group.stream().anyMatch(e -> e.payload().messageType().isTerminal()),
            5000, episodeSummariser, episodeBus, L2);

        eventBus.subscribe(e -> true, e -> keyedRunner.collect(e));

        List<LevelEvent<Episode>> episodes = new ArrayList<>();
        episodeBus.subscribe(e -> true, episodes::add);

        adapter.onMessage(channelMessage(MessageType.COMMAND, "do task",
            "corr-stale", "user-1", 1000));
        adapter.onMessage(channelMessage(MessageType.STATUS, "working",
            "corr-stale", "agent-1", 2000));

        keyedRunner.tick(5000);
        assertThat(episodes).as("not stale yet at t=5000").isEmpty();

        keyedRunner.tick(7001);
        assertThat(episodes).as("stale after 5s of inactivity").hasSize(1);
        assertThat(episodes.get(0).payload().messageCount()).isEqualTo(2);
    }

    @Test
    void phaseWindowing_composesWithKeyedRunner() {
        var eventBus = new EventStreamBus<ChannelEvent>();
        var episodeBus = new EventStreamBus<Episode>();
        var phaseBus = new EventStreamBus<Phase>();

        var adapter = new ChannelEventAdapter<>(
            event -> {
                if (event.correlationId() == null) return null;
                if (event.messageType() == MessageType.EVENT) return null;
                return new ChannelEvent(event.correlationId(), event.messageType(),
                    event.content(), event.senderId());
            }, L1, eventBus);

        Summariser<ChannelEvent, Episode> episodeSummariser = Summariser.ofSync(batch ->
            List.of(new Episode(batch.get(0).payload().correlationId(),
                batch.size(), "episode")));

        var keyedRunner = new KeyedSummarisationRunner<>(
            (ChannelEvent e) -> e.correlationId(),
            group -> group.stream().anyMatch(e -> e.payload().messageType().isTerminal()),
            0, episodeSummariser, episodeBus, L2);

        Summariser<Episode, Phase> phaseSummariser = Summariser.ofSync(batch ->
            List.of(new Phase("active", batch.size())));

        var phaseRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 2), phaseSummariser, phaseBus, L3);

        eventBus.subscribe(e -> true, e -> keyedRunner.collect(e));
        episodeBus.subscribe(e -> true, e -> phaseRunner.collect(e));

        List<LevelEvent<Phase>> phases = new ArrayList<>();
        phaseBus.subscribe(p -> true, phases::add);

        adapter.onMessage(channelMessage(MessageType.COMMAND, "task1", "c1", "u", 1000));
        adapter.onMessage(channelMessage(MessageType.DONE, "done1", "c1", "a", 2000));
        keyedRunner.tick(2000);

        adapter.onMessage(channelMessage(MessageType.COMMAND, "task2", "c2", "u", 3000));
        adapter.onMessage(channelMessage(MessageType.DONE, "done2", "c2", "a", 4000));
        keyedRunner.tick(4000);

        phaseRunner.tick(4000);

        assertThat(phases).hasSize(1);
        assertThat(phases.get(0).payload().episodeCount()).isEqualTo(2);
        assertThat(phases.get(0).level()).isEqualTo(L3);
    }

    @Test
    void publisher_capturesOutputDispatches() {
        var episodeBus = new EventStreamBus<Episode>();
        List<MessageDispatch> dispatched = new ArrayList<>();
        MessageDispatcher dispatcher = d -> {
            dispatched.add(d);
            return new DispatchResult(1L, d.channelId(), d.sender(), d.type(),
                d.correlationId(), d.inReplyTo(), d.artefactRefs(), d.target(),
                null, null, null, 0, List.of());
        };

        new ChannelEventPublisher<>(episodeBus, dispatcher,
            event -> MessageDispatch.builder()
                .channelId(CHANNEL_ID)
                .sender("summarisation.episodes")
                .actorType(ActorType.AGENT)
                .type(MessageType.STATUS)
                .content("Episode: " + event.payload().summary())
                .build());

        episodeBus.publish(new LevelEvent<>(
            new Episode("c1", 3, "chain of 3"), 1000, L2));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).content()).isEqualTo("Episode: chain of 3");
        assertThat(dispatched.get(0).sender()).isEqualTo("summarisation.episodes");
    }

    @Test
    void senderFiltering_preventsFeedbackLoop() {
        var eventBus = new EventStreamBus<ChannelEvent>();

        var adapter = new ChannelEventAdapter<>(
            event -> {
                if (event.senderId().startsWith("summarisation.")) return null;
                if (event.correlationId() == null) return null;
                return new ChannelEvent(event.correlationId(), event.messageType(),
                    event.content(), event.senderId());
            }, L1, eventBus);

        List<LevelEvent<ChannelEvent>> received = new ArrayList<>();
        eventBus.subscribe(e -> true, received::add);

        adapter.onMessage(channelMessage(MessageType.STATUS, "real message",
            "c1", "user-1", 1000));
        adapter.onMessage(channelMessage(MessageType.STATUS, "published summary",
            "c1", "summarisation.episodes", 2000));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload().sender()).isEqualTo("user-1");
    }
}
