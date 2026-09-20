package io.casehub.blocks.agentic.social.need;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeedTierMappingProviderTest {

    @Test
    void emptyProviderReturnsEmptyMap() {
        var provider = NeedTierMappingProvider.empty();
        assertNotNull(provider);
        assertTrue(provider.tierMapping().isEmpty());
    }
}
