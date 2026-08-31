package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.AttributedState;
import io.casehub.blocks.agentic.social.BdiDimension;
import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.agentic.social.drive.AutonomyDrive;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutonomyGoalMapperTest {

    private final AutonomyDrive      drive  = mock(AutonomyDrive.class);
    private final AutonomyGoalMapper mapper = new AutonomyGoalMapper(drive, 0.7);

    @Test
    void returnsProposal_whenHighConfidenceIntentions() {
        var intention = new AttributedState(
                "manipulate", "trying to manipulate", 0.85, 3,
                Instant.now(), BdiDimension.INTENTION);
        when(drive.lastSnapshots()).thenReturn(List.of(
                snapshot("subject-x", List.of(intention))));

        var proposal = mapper.evaluate("a1", "t1", intensity(0.6));
        assertThat(proposal).isNotNull();
        assertThat(proposal.axis()).isEqualTo(DriveAxis.AUTONOMY);
        assertThat(proposal.goalName()).isEqualTo("reassess-subject-x");
    }

    @Test
    void returnsNull_whenNoHighConfidenceIntentions() {
        var intention = new AttributedState(
                "curious", "might be curious", 0.3, 1,
                Instant.now(), BdiDimension.INTENTION);
        when(drive.lastSnapshots()).thenReturn(List.of(
                snapshot("subject-x", List.of(intention))));

        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    @Test
    void returnsNull_whenNoSnapshots() {
        when(drive.lastSnapshots()).thenReturn(List.of());
        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    @Test
    void returnsNull_whenSnapshotsNull() {
        when(drive.lastSnapshots()).thenReturn(null);
        assertThat(mapper.evaluate("a1", "t1", intensity(0.6))).isNull();
    }

    @Test
    void selectsMostMisaligned() {
        var i1 = new AttributedState(
                "intent-a", "desc", 0.8, 2, Instant.now(), BdiDimension.INTENTION);
        var i2 = new AttributedState(
                "intent-b", "desc", 0.9, 3, Instant.now(), BdiDimension.INTENTION);
        var snapshot1 = snapshot("subject-a", List.of(i1));
        var snapshot2 = snapshot("subject-b", List.of(i1, i2));
        when(drive.lastSnapshots()).thenReturn(List.of(snapshot1, snapshot2));

        var proposal = mapper.evaluate("a1", "t1", intensity(0.6));
        assertThat(proposal).isNotNull();
        assertThat(proposal.goalName()).isEqualTo("reassess-subject-b");
    }

    private MentalModelSnapshot snapshot(String subjectId, List<AttributedState> intentions) {
        return new MentalModelSnapshot(
                "a1", subjectId, "t1", List.of(), List.of(), intentions,
                Instant.now(), null, Instant.now());
    }

    private DriveIntensity intensity(double value) {
        return new DriveIntensity(DriveAxis.AUTONOMY, value, "test");
    }
}
