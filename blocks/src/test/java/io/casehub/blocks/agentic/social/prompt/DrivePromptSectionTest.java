package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DrivePromptSectionTest {

    private static final PromptContext CTX = new PromptContext("agent1", "tenant1", null);

    @Test
    void rendersDriveState() {
        var drives = mock(DriveOrchestrator.class);
        var profile = new DriveProfile("agent1", "tenant1",
                Map.of(
                        DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.7, "knowledge gaps detected"),
                        DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.3, "social interaction")),
                0.5, DriveAxis.CURIOSITY, Instant.now());
        when(drives.currentDrives("agent1", "tenant1")).thenReturn(Optional.of(profile));
        var section = new DrivePromptSection(drives);
        var result = section.contribute(CTX);
        assertThat(result).isNotNull();
        assertThat(result).containsIgnoringCase("curiosity");
    }

    @Test
    void returnsNullWhenNoDrives() {
        var drives = mock(DriveOrchestrator.class);
        when(drives.currentDrives("agent1", "tenant1")).thenReturn(Optional.empty());
        var section = new DrivePromptSection(drives);
        assertThat(section.contribute(CTX)).isNull();
    }
}
