package io.casehub.blocks.agentic.personality;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InnerLifeTickTest {

    @Test
    void silentWithReason() {
        var tick = new InnerLifeTick.Silent("civility denied");
        assertThat(tick.reason()).isEqualTo("civility denied");
    }

    @Test
    void silentWithNullReason() {
        var tick = new InnerLifeTick.Silent(null);
        assertThat(tick.reason()).isNull();
    }

    @Test
    void initiatedCarriesAllFields() {
        var tick = new InnerLifeTick.Initiated("Hello!", "#general", 0.85);
        assertThat(tick.content()).isEqualTo("Hello!");
        assertThat(tick.channelHint()).isEqualTo("#general");
        assertThat(tick.motivationScore()).isEqualTo(0.85);
    }

    @Test
    void initiatedWithNullChannelHint() {
        var tick = new InnerLifeTick.Initiated("Hi", null, 0.7);
        assertThat(tick.channelHint()).isNull();
    }

    @Test
    void exhaustiveSwitchCoversAllVariants() {
        List<InnerLifeTick> ticks = List.of(
                new InnerLifeTick.Silent("quiet"),
                new InnerLifeTick.Initiated("msg", "#ch", 0.9));
        for (InnerLifeTick t : ticks) {
            String label = switch (t) {
                case InnerLifeTick.Silent s -> "silent";
                case InnerLifeTick.Initiated i -> "initiated";
            };
            assertThat(label).isNotBlank();
        }
    }
}
