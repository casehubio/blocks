package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.agentic.social.drive.AffiliationDrive;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AffiliationGoalMapperTest {

    private final AffiliationDrive      drive  = mock(AffiliationDrive.class);
    private final AffiliationGoalMapper mapper =
            new AffiliationGoalMapper(drive, 0.3, Duration.ofDays(7));

    @Test
    void returnsProposal_whenNeglectedRelationship() {
        when(drive.lastProfiles()).thenReturn(List.of(
                profile("user-abc", 0.1, Instant.now().minus(Duration.ofDays(14)))));

        var proposal = mapper.evaluate("a1", "t1", intensity(0.5));
        assertThat(proposal).isNotNull();
        assertThat(proposal.axis()).isEqualTo(DriveAxis.AFFILIATION);
        assertThat(proposal.goalName()).isEqualTo("reconnect-user-abc");
    }

    @Test
    void returnsNull_whenAllRelationshipsHealthy() {
        when(drive.lastProfiles()).thenReturn(List.of(
                profile("user-abc", 0.8, Instant.now())));

        assertThat(mapper.evaluate("a1", "t1", intensity(0.5))).isNull();
    }

    @Test
    void returnsNull_whenNoProfiles() {
        when(drive.lastProfiles()).thenReturn(List.of());
        assertThat(mapper.evaluate("a1", "t1", intensity(0.5))).isNull();
    }

    @Test
    void returnsNull_whenProfilesNull() {
        when(drive.lastProfiles()).thenReturn(null);
        assertThat(mapper.evaluate("a1", "t1", intensity(0.5))).isNull();
    }

    @Test
    void selectsMostNeglected() {
        var healthy       = profile("user-a", 0.8, Instant.now());
        var neglected     = profile("user-b", 0.05, Instant.now().minus(Duration.ofDays(30)));
        var alsoNeglected = profile("user-c", 0.15, Instant.now().minus(Duration.ofDays(10)));
        when(drive.lastProfiles()).thenReturn(List.of(healthy, neglected, alsoNeglected));

        var proposal = mapper.evaluate("a1", "t1", intensity(0.5));
        assertThat(proposal).isNotNull();
        assertThat(proposal.goalName()).isEqualTo("reconnect-user-b");
    }

    private UserProfile profile(String subjectId, double familiarity, Instant lastInteraction) {
        return new UserProfile(
                "a1", subjectId, "t1", "acquaintance", familiarity,
                10, 5, 1, 4, lastInteraction, Instant.now(), null,
                null, null, null, null, Map.of());
    }

    private DriveIntensity intensity(double value) {
        return new DriveIntensity(DriveAxis.AFFILIATION, value, "test");
    }
}
