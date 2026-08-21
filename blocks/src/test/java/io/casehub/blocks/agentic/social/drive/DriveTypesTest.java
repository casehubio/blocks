package io.casehub.blocks.agentic.social.drive;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DriveTypesTest {

    @Test
    void driveIntensity_validRange() {
        var intensity = new DriveIntensity(DriveAxis.CURIOSITY, 0.75, "3 knowledge gaps");
        assertThat(intensity.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(intensity.intensity()).isEqualTo(0.75);
        assertThat(intensity.trigger()).isEqualTo("3 knowledge gaps");
    }

    @Test
    void driveIntensity_rejectsNegative() {
        assertThatThrownBy(() -> new DriveIntensity(DriveAxis.CURIOSITY, -0.1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void driveIntensity_rejectsAboveOne() {
        assertThatThrownBy(() -> new DriveIntensity(DriveAxis.CURIOSITY, 1.1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void driveIntensity_rejectsNullAxis() {
        assertThatThrownBy(() -> new DriveIntensity(null, 0.5, "x"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void driveIntensity_rejectsNullTrigger() {
        assertThatThrownBy(() -> new DriveIntensity(DriveAxis.CURIOSITY, 0.5, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void driveProfile_validConstruction() {
        var drives = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.8, "gaps"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.3, "ok"));
        var profile = new DriveProfile("agent-1", "tenant-1", drives, 0.55,
                DriveAxis.CURIOSITY, Instant.now());
        assertThat(profile.drives()).hasSize(2);
        assertThat(profile.dominantDrive()).isEqualTo(DriveAxis.CURIOSITY);
    }

    @Test
    void driveProfile_defensiveCopy() {
        var mutable = new java.util.HashMap<DriveAxis, DriveIntensity>();
        mutable.put(DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "x"));
        var profile = new DriveProfile("a", "t", mutable, 0.5, DriveAxis.CURIOSITY, Instant.now());
        mutable.put(DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.1, "y"));
        assertThat(profile.drives()).hasSize(1);
    }

    @Test
    void driveProfile_rejectsInvalidComposite() {
        assertThatThrownBy(() -> new DriveProfile("a", "t", Map.of(), 1.5,
                DriveAxis.CURIOSITY, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void driveTick_noChange() {
        var tick = new DriveTick.NoChange("no signals");
        assertThat(tick.reason()).isEqualTo("no signals");
    }

    @Test
    void driveTick_updated() {
        var now = Instant.now();
        var prev = new DriveProfile("a", "t", Map.of(), 0.0, DriveAxis.CURIOSITY, now);
        var curr = new DriveProfile("a", "t", Map.of(), 0.5, DriveAxis.CURIOSITY, now);
        var tick = new DriveTick.Updated(prev, curr, List.of(DriveAxis.CURIOSITY));
        assertThat(tick.changed()).containsExactly(DriveAxis.CURIOSITY);
    }

    @Test
    void driveConfig_defaults() {
        var config = DriveConfig.defaults();
        assertThat(config.axisWeights()).hasSize(4);
        assertThat(config.changeThreshold()).isEqualTo(0.05);
    }

    @Test
    void driveConfig_rejectsInvalidRange() {
        assertThatThrownBy(() -> new DriveConfig(Map.of(), 0.05, 0.3, 0.2, 0.25, 0.5, 0.8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIntensity must be >= minIntensity");
    }
}
