package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.model.DriverEvent;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelObserverTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private static final ChannelProjection<List<String>> COLLECTING =
        new ChannelProjection<>() {
            @Override
            public List<String> identity() { return List.of(); }

            @Override
            public List<String> apply(List<String> state, MessageView message) {
                var next = new ArrayList<>(state);
                next.add(message.content());
                return List.copyOf(next);
            }
        };

    private static MessageReceivedEvent event(String content) {
        return new MessageReceivedEvent(
            1L, "test-channel", CHANNEL_ID, "tenant",
            MessageType.STATUS, "agent-1", null, null,
            null, Instant.now(), content, null);
    }

    // --- Projection folding ---

    @Test
    void identityStateOnConstruction() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        assertThat(observer.currentState()).isEmpty();
    }

    @Test
    void onMessageFoldsProjection() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        observer.onMessage(event("hello"));
        assertThat(observer.currentState()).containsExactly("hello");
    }

    @Test
    void multipleMessagesFoldIncrementally() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        observer.onMessage(event("first"));
        observer.onMessage(event("second"));
        assertThat(observer.currentState()).containsExactly("first", "second");
    }

    // --- EventSource contract ---

    @Test
    void subscribeStoresSinkAndPostsDriverEvents() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        var events = new ArrayList<DriverEvent>();
        observer.subscribe(events::add);

        observer.onMessage(event("hello"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).source()).startsWith("channel:");
    }

    @Test
    void onMessageWithoutSinkFoldsWithoutSignaling() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        observer.onMessage(event("hello"));

        assertThat(observer.currentState()).containsExactly("hello");
    }

    @Test
    void cancellationClearsSink() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        var events = new ArrayList<DriverEvent>();
        var cancellation = observer.subscribe(events::add);

        cancellation.cancel();
        observer.onMessage(event("after-cancel"));

        assertThat(observer.currentState()).containsExactly("after-cancel");
        assertThat(events).isEmpty();
    }

    @Test
    void subscribeWithExistingSubscriberThrows() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        observer.subscribe(e -> {});

        assertThatThrownBy(() -> observer.subscribe(e -> {}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already has an active subscriber");
    }

    @Test
    void subscribeAfterCancelSucceeds() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        observer.subscribe(e -> {}).cancel();

        var events = new ArrayList<DriverEvent>();
        observer.subscribe(events::add);
        observer.onMessage(event("re-subscribed"));

        assertThat(events).hasSize(1);
    }

    // --- Reset ---

    @Test
    void resetClearsAccumulatedState() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        observer.onMessage(event("accumulated"));
        assertThat(observer.currentState()).hasSize(1);

        observer.reset();
        assertThat(observer.currentState()).isEmpty();
    }

    // --- Error handling ---

    @Test
    void projectionExceptionLeavesStateUnchangedAndSignalsError() {
        ChannelProjection<String> failingProjection = new ChannelProjection<>() {
            @Override public String identity() { return "initial"; }
            @Override public String apply(String s, MessageView msg) {
                throw new RuntimeException("projection failure");
            }
        };

        var observer = ChannelObserver.of(failingProjection, "test-channel");
        var events = new ArrayList<DriverEvent>();
        observer.subscribe(events::add);

        observer.onMessage(event("bad"));

        assertThat(observer.currentState()).isEqualTo("initial");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).source()).startsWith("projection-error:");
    }

    @Test
    void validMessageAfterProjectionErrorFoldsNormally() {
        var callCount = new AtomicInteger(0);
        ChannelProjection<String> sometimesFailingProjection = new ChannelProjection<>() {
            @Override public String identity() { return ""; }
            @Override public String apply(String s, MessageView msg) {
                if (callCount.incrementAndGet() == 1) {
                    throw new RuntimeException("first call fails");
                }
                return s + msg.content();
            }
        };

        var observer = ChannelObserver.of(sometimesFailingProjection, "test-channel");
        observer.onMessage(event("fail"));
        observer.onMessage(event("succeed"));

        assertThat(observer.currentState()).isEqualTo("succeed");
    }

    // --- Termination convenience ---

    @Test
    void terminateWhenReturnsCompleteWhenPredicateTrue() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        observer.onMessage(event("done"));

        TerminationCondition<String> condition =
            observer.terminateWhen(state -> state.contains("done"));
        var ctx = new TerminationContext<>("ignored", 1, Duration.ZERO, List.of());

        assertThat(condition.evaluate(ctx))
            .isInstanceOf(TerminationDecision.Complete.class);
    }

    @Test
    void terminateWhenReturnsContinueWhenPredicateFalse() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");

        TerminationCondition<String> condition =
            observer.terminateWhen(state -> state.contains("done"));
        var ctx = new TerminationContext<>("ignored", 1, Duration.ZERO, List.of());

        assertThat(condition.evaluate(ctx))
            .isInstanceOf(TerminationDecision.Continue.class);
    }

    @Test
    void asTerminationDelegatesToFunction() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        observer.onMessage(event("alert"));

        TerminationCondition<Integer> condition = observer.asTermination(
            state -> state.contains("alert")
                ? new TerminationDecision.Escalate("alert found")
                : TerminationDecision.Continue.INSTANCE);
        var ctx = new TerminationContext<>(42, 1, Duration.ZERO, List.of());

        assertThat(condition.evaluate(ctx))
            .isInstanceOf(TerminationDecision.Escalate.class);
    }

    // --- MessageObserver contract ---

    @Test
    void channelsReturnsConfiguredNames() {
        var observer = ChannelObserver.of(COLLECTING, "my-channel");
        assertThat(observer.channels()).containsExactly("my-channel");
    }

    @Test
    void scopeReturnsLocal() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        assertThat(observer.scope()).isEqualTo(MessageObserver.Scope.LOCAL);
    }

    // --- Builder ---

    @Test
    void builderMultipleChannels() {
        var observer = ChannelObserver.builder(COLLECTING)
            .channel("channel-a")
            .channel("channel-b")
            .build();
        assertThat(observer.channels()).containsExactlyInAnyOrder("channel-a", "channel-b");
    }

    @Test
    void builderEmptyThrows() {
        assertThatThrownBy(() -> ChannelObserver.builder(COLLECTING).build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("At least one channel");
    }

    @Test
    void builderDeduplicatesChannelNames() {
        var observer = ChannelObserver.builder(COLLECTING)
            .channel("same")
            .channel("same")
            .build();
        assertThat(observer.channels()).hasSize(1);
    }

    // --- Event delivery ordering ---

    @Test
    void projectionUpdatedBeforeDriverEventPosted() {
        var observer = ChannelObserver.of(COLLECTING, "test-channel");
        var stateAtSignal = new ArrayList<List<String>>();

        observer.subscribe(e -> stateAtSignal.add(observer.currentState()));
        observer.onMessage(event("check-ordering"));

        assertThat(stateAtSignal).hasSize(1);
        assertThat(stateAtSignal.get(0)).containsExactly("check-ordering");
    }
}
