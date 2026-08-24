package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class NarrativeModulationTest {

    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void compute_singleTheme_singleAxis() {
        var theme = new DerivedTheme("t1", now, null, List.of(),
                "helper", 0.8,
                Map.of(DriveAxis.AFFILIATION, 0.5), List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(theme), now, 5);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod.get(DriveAxis.AFFILIATION)).isCloseTo(0.4, within(0.001));
        assertThat(mod).doesNotContainKey(DriveAxis.CURIOSITY);
    }

    @Test
    void compute_multipleThemes_additiveComposition() {
        var theme1 = new DerivedTheme("t1", now, null, List.of(),
                "helper", 0.8,
                Map.of(DriveAxis.AFFILIATION, 0.5), List.of());
        var theme2 = new DerivedTheme("t2", now, null, List.of(),
                "expert", 0.6,
                Map.of(DriveAxis.AFFILIATION, 0.3, DriveAxis.COMPETENCE, 0.4),
                List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(theme1, theme2), now, 5);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod.get(DriveAxis.AFFILIATION)).isCloseTo(0.58, within(0.001));
        assertThat(mod.get(DriveAxis.COMPETENCE)).isCloseTo(0.24, within(0.001));
    }

    @Test
    void compute_negativeWeight_dampens() {
        var theme = new DerivedTheme("t1", now, null, List.of(),
                "independent", 0.7,
                Map.of(DriveAxis.AUTONOMY, -0.4), List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(theme), now, 3);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod.get(DriveAxis.AUTONOMY)).isCloseTo(-0.28, within(0.001));
    }

    @Test
    void compute_noThemes_emptyMap() {
        var episode = new IndividualEpisode("e1", now, null, List.of(),
                "desc", 0.5, List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(episode), now, 1);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod).isEmpty();
    }

    @Test
    void compute_emptyState_emptyMap() {
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(), now, 0);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod).isEmpty();
    }

    @Test
    void compute_groupEpisodesIgnored() {
        var group = new GroupEpisode("g1", now, null, List.of(),
                "group ep", 0.5, Set.of("a", "b"), Map.of(), 0.9);
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(group), now, 1);

        var mod = NarrativeModulation.compute(state);

        assertThat(mod).isEmpty();
    }

    @Test
    void compute_resultIsImmutable() {
        var theme = new DerivedTheme("t1", now, null, List.of(),
                "helper", 0.8,
                Map.of(DriveAxis.AFFILIATION, 0.5), List.of());
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(theme), now, 5);

        var mod = NarrativeModulation.compute(state);

        assertThatThrownBy(() -> ((Map<DriveAxis, Double>) mod).put(DriveAxis.CURIOSITY, 1.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
