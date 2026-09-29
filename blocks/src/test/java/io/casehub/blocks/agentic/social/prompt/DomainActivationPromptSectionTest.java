package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.DomainActivationSnapshot;
import io.casehub.neocortex.cognitive.index.AffectTrajectory;
import io.casehub.neocortex.cognitive.index.ConfidenceInterval;
import io.casehub.neocortex.cognitive.index.CorrelationStrength;
import io.casehub.neocortex.cognitive.index.DomainCorrelation;
import io.casehub.neocortex.cognitive.index.DomainPair;
import io.casehub.neocortex.cognitive.index.DomainSignal;
import io.casehub.neocortex.cognitive.index.EventImpact;
import io.casehub.neocortex.cognitive.index.PadDimension;
import io.casehub.neocortex.cognitive.index.TrendDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DomainActivationPromptSectionTest {

    private static final Map<String, String> NAMES = Map.of("sg1", "Work Projects", "sg2", "Family");

    @Test
    void returnsNullWhenNoRenderablePairs() {
        var weak = new DomainCorrelation(0.1, List.of(), 5, CorrelationStrength.NONE, 0.5, 0, 0);
        var snapshot = new DomainActivationSnapshot(
                Map.of(new DomainPair("sg1", "sg2"), weak),
                Map.of(), Map.of(), Map.of(), NAMES, Instant.now());
        var section = new DomainActivationPromptSection(snapshot);
        assertThat(section.contribute(null)).isNull();
    }

    @Test
    void rendersStrongCorrelation() {
        var strong = new DomainCorrelation(0.82, List.of(), 10, CorrelationStrength.STRONG, 0.005, 0, 0);
        var signal1 = new DomainSignal("sg1",
                new AffectTrajectory(0.1, 0.2, 0.1, 0.05, TrendDirection.IMPROVING, 0.1, 5),
                3, 10, 7);
        var signal2 = new DomainSignal("sg2",
                new AffectTrajectory(-0.1, 0.3, -0.1, 0.0, TrendDirection.STABLE, 0.1, 5),
                2, 8, 7);
        var snapshot = new DomainActivationSnapshot(
                Map.of(new DomainPair("sg1", "sg2"), strong),
                Map.of(), Map.of(),
                Map.of("sg1", signal1, "sg2", signal2),
                NAMES, Instant.now());
        var section = new DomainActivationPromptSection(snapshot);
        var text = section.contribute(null);
        assertThat(text).isNotNull();
        assertThat(text).contains("strongly correlated");
        assertThat(text).contains("Work Projects");
        assertThat(text).contains("Family");
    }

    @Test
    void includesMoodCorrelationWhenSignificant() {
        var strong = new DomainCorrelation(0.75, List.of(), 10, CorrelationStrength.STRONG, 0.01, 0, 0);
        var pair = new DomainPair("sg1", "sg2");
        var moodCorr = new DomainCorrelation(0.6, List.of(), 8, CorrelationStrength.MODERATE, 0.03, 5, 10);
        var snapshot = new DomainActivationSnapshot(
                Map.of(pair, strong),
                Map.of(pair, Map.of("sg1", moodCorr)),
                Map.of(), Map.of(), NAMES, Instant.now());
        var section = new DomainActivationPromptSection(snapshot);
        var text = section.contribute(null);
        assertThat(text).isNotNull();
        assertThat(text).contains("mood");
    }

    @Test
    void excludesMoodCorrelationWhenNotSignificant() {
        var strong = new DomainCorrelation(0.75, List.of(), 10, CorrelationStrength.STRONG, 0.01, 0, 0);
        var pair = new DomainPair("sg1", "sg2");
        var moodCorr = new DomainCorrelation(0.3, List.of(), 4, CorrelationStrength.WEAK, 0.15, 2, 10);
        var snapshot = new DomainActivationSnapshot(
                Map.of(pair, strong),
                Map.of(pair, Map.of("sg1", moodCorr)),
                Map.of(), Map.of(), NAMES, Instant.now());
        var section = new DomainActivationPromptSection(snapshot);
        var text = section.contribute(null);
        assertThat(text).isNotNull();
        assertThat(text).doesNotContain("mood");
    }

    @Test
    void includesEventImpactWhenCIDoesNotSpanZero() {
        var strong = new DomainCorrelation(0.75, List.of(), 10, CorrelationStrength.STRONG, 0.01, 0, 0);
        var pair = new DomainPair("sg1", "sg2");
        var impact = new EventImpact(
                Map.of(PadDimension.PLEASURE, 0.15),
                Map.of(PadDimension.PLEASURE, new ConfidenceInterval(0.05, 0.25)),
                5, 5, Map.of());
        var snapshot = new DomainActivationSnapshot(
                Map.of(pair, strong), Map.of(),
                Map.of(pair, Map.of("sg1", impact)),
                Map.of(), NAMES, Instant.now());
        var section = new DomainActivationPromptSection(snapshot);
        var text = section.contribute(null);
        assertThat(text).isNotNull();
        assertThat(text).contains("pleasure");
    }

    @Test
    void capsAtFivePairs() {
        var pairs = new LinkedHashMap<DomainPair, DomainCorrelation>();
        var names = new LinkedHashMap<String, String>();
        for (int i = 0; i < 7; i++) {
            for (int j = i + 1; j < 7; j++) {
                var sgA = "sg" + i;
                var sgB = "sg" + j;
                pairs.put(new DomainPair(sgA, sgB),
                        new DomainCorrelation(0.8 - (i * 0.02), List.of(), 10,
                                CorrelationStrength.STRONG, 0.01, 0, 0));
                names.put(sgA, "Domain " + i);
                names.put(sgB, "Domain " + j);
            }
        }
        var snapshot = new DomainActivationSnapshot(pairs, Map.of(), Map.of(), Map.of(), names, Instant.now());
        var section = new DomainActivationPromptSection(snapshot);
        var text = section.contribute(null);
        assertThat(text).isNotNull();
        var bulletCount = text.split("\n- ").length - 1;
        assertThat(bulletCount).isLessThanOrEqualTo(5);
    }

    @Test
    void rendersTrajectoryWhenNotBothStable() {
        var strong = new DomainCorrelation(0.82, List.of(), 10, CorrelationStrength.STRONG, 0.005, 0, 0);
        var signal1 = new DomainSignal("sg1",
                new AffectTrajectory(0.1, 0.2, 0.1, 0.05, TrendDirection.IMPROVING, 0.1, 5),
                3, 10, 7);
        var signal2 = new DomainSignal("sg2",
                new AffectTrajectory(-0.1, 0.3, -0.1, 0.0, TrendDirection.WORSENING, 0.1, 5),
                2, 8, 7);
        var snapshot = new DomainActivationSnapshot(
                Map.of(new DomainPair("sg1", "sg2"), strong),
                Map.of(), Map.of(),
                Map.of("sg1", signal1, "sg2", signal2),
                NAMES, Instant.now());
        var section = new DomainActivationPromptSection(snapshot);
        var text = section.contribute(null);
        assertThat(text).contains("Trajectory");
        assertThat(text).contains("improving");
        assertThat(text).contains("worsening");
    }
}
