package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

class EventConcurrencyPolicyTest {

    @Test
    void serializeTakesOneEvent() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(DriverEvent.signal("a"));
        queue.add(DriverEvent.signal("b"));
        queue.add(DriverEvent.signal("c"));

        var events = EventConcurrencyPolicy.serialize().awaitEvents(queue);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).source()).isEqualTo("a");
        assertThat(queue).hasSize(2);
    }

    @Test
    void coalesceDrainsAllPending() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(DriverEvent.signal("a"));
        queue.add(DriverEvent.signal("b"));
        queue.add(DriverEvent.signal("c"));

        var events = EventConcurrencyPolicy.coalesce().awaitEvents(queue);
        assertThat(events).hasSize(3);
        assertThat(queue).isEmpty();
    }

    @Test
    void coalesceBlocksUntilFirstEvent() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        var thread = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            queue.add(DriverEvent.signal("delayed"));
        });
        thread.start();

        var events = EventConcurrencyPolicy.coalesce().awaitEvents(queue);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).source()).isEqualTo("delayed");
    }

    @Test
    void coalesceBySourceKeepsLastPerSource() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(new DriverEvent("timer", "tick-1"));
        queue.add(new DriverEvent("channel", "msg-1"));
        queue.add(new DriverEvent("timer", "tick-2"));
        queue.add(new DriverEvent("channel", "msg-2"));

        var events = EventConcurrencyPolicy.coalesceBySource().awaitEvents(queue);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(DriverEvent::source)
            .containsExactly("timer", "channel");
        assertThat(events).extracting(DriverEvent::payload)
            .containsExactly("tick-2", "msg-2");
    }

    @Test
    void coalesceWithWindowCollectsWithinDuration() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(DriverEvent.signal("first"));

        var thread = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            queue.add(DriverEvent.signal("second"));
        });
        thread.start();

        var events = EventConcurrencyPolicy.coalesce(Duration.ofMillis(200))
            .awaitEvents(queue);
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void thenChainsPolicies() throws InterruptedException {
        var queue = new LinkedBlockingQueue<DriverEvent>();
        queue.add(new DriverEvent("timer", "tick-1"));
        queue.add(new DriverEvent("channel", "msg"));
        queue.add(new DriverEvent("timer", "tick-2"));

        var policy = EventConcurrencyPolicy.coalesce()
            .then(EventConcurrencyPolicy.coalesceBySource());

        var events = policy.awaitEvents(queue);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(DriverEvent::payload)
            .containsExactly("tick-2", "msg");
    }
}
