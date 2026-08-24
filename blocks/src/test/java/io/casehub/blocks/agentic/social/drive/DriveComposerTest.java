package io.casehub.blocks.agentic.social.drive;

import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.neocortex.memory.mood.MoodState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DriveComposerTest {

    private final DriveComposer composer = new DriveComposer();
    private final Instant now = Instant.parse("2026-08-21T12:00:00Z");

    private Map<DriveAxis, DriveIntensity> uniformRaw(double value) {
        return Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, value, "x"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, value, "x"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, value, "x"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, value, "x"));
    }

    @Test
    void compose_noModulation_equalWeights() {
        var raw = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.8, "gaps"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.4, "ok"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.2, "stable"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.6, "pressure"));

        var profile = composer.compose(raw, null, null, null, DriveConfig.defaults(),
                "agent-1", "tenant-1", now);

        assertThat(profile.agentId()).isEqualTo("agent-1");
        assertThat(profile.dominantDrive()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(profile.compositeMotivation()).isCloseTo(0.5, within(0.01));
        assertThat(profile.drives()).hasSize(4);
        assertThat(profile.evaluatedAt()).isEqualTo(now);
    }

    @Test
    void compose_noModulation_unequalWeights() {
        var config = new DriveConfig(
                Map.of(DriveAxis.CURIOSITY, 2.0, DriveAxis.COMPETENCE, 1.0,
                       DriveAxis.AFFILIATION, 1.0, DriveAxis.AUTONOMY, 1.0),
                0.05, 0.3, 0.2, 0.25, 1.0, 0.0,
                0.5, java.time.Duration.ofHours(24), 0.6, 0.25);
        var raw = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 1.0, "x"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "x"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "x"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "x"));

        var profile = composer.compose(raw, null, null, null, config, "a", "t", now);

        assertThat(profile.compositeMotivation()).isCloseTo(0.4, within(0.01));
    }

    @Test
    void compose_moodModulation_highArousalAmplifies() {
        var mood = new MoodState("a", "t", 0.0, 0.8, 0.0, "excited", null, Map.of());

        var profile = composer.compose(uniformRaw(0.5), null, mood, null, DriveConfig.defaults(),
                "a", "t", now);

        for (var di : profile.drives().values()) {
            assertThat(di.intensity()).isGreaterThan(0.5);
        }
    }

    @Test
    void compose_moodModulation_lowDominanceAmplifiesAutonomy() {
        var mood = new MoodState("a", "t", 0.0, 0.0, -0.8, "controlled", null, Map.of());

        var profile = composer.compose(uniformRaw(0.5), null, mood, null, DriveConfig.defaults(),
                "a", "t", now);

        assertThat(profile.drives().get(DriveAxis.AUTONOMY).intensity())
                .isGreaterThan(profile.drives().get(DriveAxis.CURIOSITY).intensity());
    }

    @Test
    void compose_moodModulation_negativePleasureDampens() {
        var mood = new MoodState("a", "t", -0.8, 0.0, 0.0, "sad", null, Map.of());

        var profile = composer.compose(uniformRaw(0.5), null, mood, null, DriveConfig.defaults(),
                "a", "t", now);

        for (var di : profile.drives().values()) {
            assertThat(di.intensity()).isLessThan(0.5);
        }
    }

    @Test
    void compose_personalityModulation_socialOrientAmplifiesAffiliation() {
        var disposition = AgentDisposition.builder()
                .socialOrient(DispositionValue.of("collaborative"))
                .build();

        var profile = composer.compose(uniformRaw(0.5), disposition, null, null, DriveConfig.defaults(),
                "a", "t", now);

        assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity())
                .isGreaterThan(profile.drives().get(DriveAxis.COMPETENCE).intensity());
    }

    @Test
    void compose_personalityModulation_noDispositionValues_noEffect() {
        var disposition = AgentDisposition.builder().build();

        var profile = composer.compose(uniformRaw(0.5), disposition, null, null, DriveConfig.defaults(),
                "a", "t", now);

        for (var di : profile.drives().values()) {
            assertThat(di.intensity()).isEqualTo(0.5);
        }
    }

    @Test
    void compose_intensityClamped() {
        var mood = new MoodState("a", "t", 0.9, 0.9, 0.0, "euphoric", null, Map.of());
        var disposition = AgentDisposition.builder()
                .riskAppetite(DispositionValue.of("aggressive"))
                .build();

        var profile = composer.compose(Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.95, "x"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.5, "x"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.5, "x"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.5, "x")),
                disposition, mood, null, DriveConfig.defaults(), "a", "t", now);

        assertThat(profile.drives().get(DriveAxis.CURIOSITY).intensity()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void compose_emptyDrives_zeroComposite() {
        var profile = composer.compose(Map.of(), null, null, null, DriveConfig.defaults(),
                "a", "t", now);

        assertThat(profile.compositeMotivation()).isEqualTo(0.0);
    }

    @Test
    void compose_narrativeModulation_amplifiesAxis() {
        var narrativeMod = Map.of(DriveAxis.AFFILIATION, 0.5);

        var profile = composer.compose(uniformRaw(0.5), null, null, narrativeMod,
                                       DriveConfig.defaults(), "a", "t", now);

        assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity())
                .isGreaterThan(0.5);
        assertThat(profile.drives().get(DriveAxis.CURIOSITY).intensity())
                .isEqualTo(0.5);
    }

    @Test
    void compose_narrativeModulation_dampensAxis() {
        var narrativeMod = Map.of(DriveAxis.AUTONOMY, -0.6);

        var profile = composer.compose(uniformRaw(0.5), null, null, narrativeMod,
                                       DriveConfig.defaults(), "a", "t", now);

        assertThat(profile.drives().get(DriveAxis.AUTONOMY).intensity())
                .isLessThan(0.5);
    }

    @Test
    void compose_narrativeModulation_multipleAxes() {
        var narrativeMod = Map.of(
                DriveAxis.AFFILIATION, 0.4,
                DriveAxis.COMPETENCE, 0.3);

        var profile = composer.compose(uniformRaw(0.5), null, null, narrativeMod,
                                       DriveConfig.defaults(), "a", "t", now);

        assertThat(profile.drives().get(DriveAxis.AFFILIATION).intensity())
                .isGreaterThan(profile.drives().get(DriveAxis.CURIOSITY).intensity());
        assertThat(profile.drives().get(DriveAxis.COMPETENCE).intensity())
                .isGreaterThan(profile.drives().get(DriveAxis.CURIOSITY).intensity());
    }

    @Test
    void compose_nullNarrativeModulation_noEffect() {
        var withNull = composer.compose(uniformRaw(0.5), null, null, null,
                                        DriveConfig.defaults(), "a", "t", now);
        var withEmpty = composer.compose(uniformRaw(0.5), null, null, Map.of(),
                                         DriveConfig.defaults(), "a", "t", now);

        for (var axis : DriveAxis.values()) {
            assertThat(withNull.drives().get(axis).intensity())
                    .isEqualTo(withEmpty.drives().get(axis).intensity());
        }
    }
}
