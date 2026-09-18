package io.casehub.blocks.agentic.social.need;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeedTierTest {

    @Test
    void allFiveTiersExist() {
        assertEquals(5, NeedTier.values().length);
        assertNotNull(NeedTier.SAFETY);
        assertNotNull(NeedTier.TASKS);
        assertNotNull(NeedTier.SOCIAL);
        assertNotNull(NeedTier.SELF_EXPRESSION);
        assertNotNull(NeedTier.UNDERSTANDING);
    }

    @Test
    void valueOfRoundTrips() {
        for (NeedTier tier : NeedTier.values()) {
            assertEquals(tier, NeedTier.valueOf(tier.name()));
        }
    }
}
