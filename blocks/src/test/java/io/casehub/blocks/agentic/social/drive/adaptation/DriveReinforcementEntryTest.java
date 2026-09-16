package io.casehub.blocks.agentic.social.drive.adaptation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DriveReinforcementEntryTest {

    @Test
    void defaultsToPositivePleasure() {
        var entry = new DriveReinforcementEntry("scheming", null, null);
        assertEquals(ReinforcementDirection.POSITIVE, entry.direction());
        assertEquals(RewardAxis.PLEASURE, entry.rewardAxis());
    }

    @Test
    void rejectNullDriveType() {
        assertThrows(NullPointerException.class,
            () -> new DriveReinforcementEntry(null, null, null));
    }

    @Test
    void preserveExplicitValues() {
        var entry = new DriveReinforcementEntry("social-harmony",
            ReinforcementDirection.NEGATIVE, RewardAxis.DOMINANCE);
        assertEquals("social-harmony", entry.driveType());
        assertEquals(ReinforcementDirection.NEGATIVE, entry.direction());
        assertEquals(RewardAxis.DOMINANCE, entry.rewardAxis());
    }

    @Test
    void compositeRewardAxis() {
        var entry = new DriveReinforcementEntry("greed", null, RewardAxis.COMPOSITE);
        assertEquals(RewardAxis.COMPOSITE, entry.rewardAxis());
        assertEquals(ReinforcementDirection.POSITIVE, entry.direction());
    }
}
