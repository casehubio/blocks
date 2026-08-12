package io.casehub.blocks.agentic.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DriverEventTest {

    @Test
    void signalFactoryCreatesEventWithSource() {
        var event = DriverEvent.signal("channel");
        assertThat(event.source()).isEqualTo("channel");
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.payload()).isNull();
    }

    @Test
    void timerFactoryCreatesTimerEvent() {
        var event = DriverEvent.timer();
        assertThat(event.source()).isEqualTo("timer");
        assertThat(event.payload()).isNull();
    }

    @Test
    void compactConstructorWithSourceOnly() {
        var event = new DriverEvent("test");
        assertThat(event.source()).isEqualTo("test");
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.payload()).isNull();
    }

    @Test
    void compactConstructorWithPayload() {
        var event = new DriverEvent("test", "data");
        assertThat(event.source()).isEqualTo("test");
        assertThat(event.payload()).isEqualTo("data");
    }
}
