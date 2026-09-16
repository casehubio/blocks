package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CognitionConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CognitivePreambleGeneratorTest {

    @Test
    void allSubsystemsEnabled_producesCompletePreamble() {
        var config = CognitionConfig.all();
        var preamble = CognitivePreambleGenerator.generate(config);
        assertNotNull(preamble);
        assertFalse(preamble.isBlank());
        assertTrue(preamble.contains("emotional state"));
        assertTrue(preamble.contains("drives"));
        assertTrue(preamble.contains("beliefs"));
        assertTrue(preamble.contains("goals"));
        assertTrue(preamble.contains("strategies"));
    }

    @Test
    void noSubsystemsEnabled_producesMinimalPreamble() {
        var config = CognitionConfig.none();
        var preamble = CognitivePreambleGenerator.generate(config);
        assertNotNull(preamble);
        assertTrue(preamble.contains("inner life"));
        assertFalse(preamble.contains("drives"));
        assertFalse(preamble.contains("strategies"));
    }

    @Test
    void partialSubsystems_includesOnlyActive() {
        var config = CognitionConfig.none().with("mood", true).with("goals", true);
        var preamble = CognitivePreambleGenerator.generate(config);
        assertTrue(preamble.contains("emotional state"));
        assertTrue(preamble.contains("goals"));
        assertFalse(preamble.contains("strategies"));
        assertFalse(preamble.contains("drives"));
    }
}
