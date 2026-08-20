package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.DispositionValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvolutionTickTest {

    @Test
    void stableIsSealed() {
        EvolutionTick tick = new EvolutionTick.Stable();
        assertThat(tick).isInstanceOf(EvolutionTick.class);
    }

    @Test
    void driftingCarriesMagnitude() {
        var tick = new EvolutionTick.Drifting(0.08);
        assertThat(tick.magnitude()).isEqualTo(0.08);
    }

    @Test
    void haltedCarriesMagnitude() {
        var tick = new EvolutionTick.Halted(0.20);
        assertThat(tick.magnitude()).isEqualTo(0.20);
    }

    @Test
    void evolvedCarriesTypeLabelsAndProfile() {
        var profile = List.of(new DispositionValue("ti", 0.35), new DispositionValue("ne", 0.20));
        var tick = new EvolutionTick.Evolved("TI-NE", "NE-TI", profile);
        assertThat(tick.previousTypeLabel()).isEqualTo("TI-NE");
        assertThat(tick.newTypeLabel()).isEqualTo("NE-TI");
        assertThat(tick.newProfile()).hasSize(2);
    }

    @Test
    void evolvedProfileIsDefensivelyCopied() {
        var mutable = new java.util.ArrayList<>(List.of(new DispositionValue("ti", 0.35)));
        var tick = new EvolutionTick.Evolved("A", "B", mutable);
        assertThat(tick.newProfile()).isUnmodifiable();
    }

    @Test
    void dampenedCarriesDecayFactor() {
        var tick = new EvolutionTick.Dampened(0.2);
        assertThat(tick.decayFactor()).isEqualTo(0.2);
    }

    @Test
    void exhaustiveSwitchCoversAllVariants() {
        List<EvolutionTick> ticks = List.of(
                new EvolutionTick.Stable(),
                new EvolutionTick.Drifting(0.05),
                new EvolutionTick.Halted(0.20),
                new EvolutionTick.Evolved("A", "B", List.of()),
                new EvolutionTick.Dampened(0.2));
        for (EvolutionTick t : ticks) {
            String label = switch (t) {
                case EvolutionTick.Stable s -> "stable";
                case EvolutionTick.Drifting d -> "drifting";
                case EvolutionTick.Halted h -> "halted";
                case EvolutionTick.Evolved e -> "evolved";
                case EvolutionTick.Dampened d -> "dampened";
            };
            assertThat(label).isNotBlank();
        }
    }
}
