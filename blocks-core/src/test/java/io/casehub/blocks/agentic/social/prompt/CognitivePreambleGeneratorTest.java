package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CognitionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitivePreambleGeneratorTest {

    @Test
    void allSubsystemsEnabled_producesCompletePreamble() {
        var config   = CognitionConfig.all();
        var preamble = CognitivePreambleGenerator.generate(config);
        assertNotNull(preamble);
        assertFalse(preamble.isBlank());
        assertTrue(preamble.contains("emotional state"));
        assertTrue(preamble.contains("motivational drives that influence"));
        assertTrue(preamble.contains("beliefs"));
        assertTrue(preamble.contains("goals"));
        assertTrue(preamble.contains("strategies"));
        assertTrue(preamble.contains("psychological needs"));
        assertTrue(preamble.contains("character motivations"));
        assertTrue(preamble.contains("inner needs"));
        assertFalse(preamble.contains("some are stronger"), "must not contain state-dependent language");
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

    @Test
    void characterDrivesOnly_noDisambiguation() {
        var config   = CognitionConfig.none().with("characterDrives", true);
        var preamble = CognitivePreambleGenerator.generate(config);
        assertTrue(preamble.contains("character motivations"));
        assertFalse(preamble.contains("psychological needs"), "disambiguation only when both drive systems active");
    }

    @Test
    void bothDriveSystems_includesDisambiguation() {
        var config   = CognitionConfig.none().with("drives", true).with("characterDrives", true);
        var preamble = CognitivePreambleGenerator.generate(config);
        assertTrue(preamble.contains("psychological needs"));
        assertTrue(preamble.contains("character motivations"));
        assertTrue(preamble.contains("Separately"));
    }

    @Test
    void needsPyramidEnabled_includesHierarchy() {
        var config   = CognitionConfig.none().with("needsPyramid", true);
        var preamble = CognitivePreambleGenerator.generate(config);
        assertTrue(preamble.contains("inner needs"));
        assertTrue(preamble.contains("hierarchy"));
    }

    @Test
    void noStateDependentLanguage() {
        var config   = CognitionConfig.all();
        var preamble = CognitivePreambleGenerator.generate(config);
        assertFalse(preamble.contains("right now"));
        assertFalse(preamble.contains("some are stronger"));
    }
}
