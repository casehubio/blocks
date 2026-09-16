package io.casehub.blocks.agentic.social.prompt;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CharacterDrivePromptSectionTest {

    private InMemoryMindMapStore store;
    private CharacterDrivePromptSection section;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "hooded-claw";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        section = new CharacterDrivePromptSection(store);

        var subgraphId = store.createSubgraph(
            new SubgraphInput("beliefs-" + AGENT, "cognitive", null), TENANT);

        store.addNode(NodeInput.of("scheming", subgraphId)
            .withConfidence(Confidence.stated(0.8, Instant.now()))
            .withProvenance("drive-adaptation")
            .withProperties(Map.of(
                "cognitiveKind", "drive-intensity",
                "agent-id", AGENT,
                "drive-type", "scheming",
                "intensity", "0.92",
                "description", "Compelled to hatch elaborate plans")),
            TENANT);

        store.addNode(NodeInput.of("dominance", subgraphId)
            .withConfidence(Confidence.stated(0.8, Instant.now()))
            .withProvenance("drive-adaptation")
            .withProperties(Map.of(
                "cognitiveKind", "drive-intensity",
                "agent-id", AGENT,
                "drive-type", "dominance",
                "intensity", "0.65",
                "description", "Must be most powerful in every room")),
            TENANT);
    }

    @Test
    void rendersAdaptedDrivesSortedByIntensity() {
        var ctx = new PromptContext(AGENT, TENANT, null);
        var result = section.contribute(ctx);

        assertNotNull(result);
        assertTrue(result.contains("scheming"), "Should contain scheming drive");
        assertTrue(result.contains("dominance"), "Should contain dominance drive");
        int schemingIdx = result.indexOf("scheming");
        int dominanceIdx = result.indexOf("dominance");
        assertTrue(schemingIdx < dominanceIdx,
            "Scheming (92%) should appear before dominance (65%)");
    }

    @Test
    void returnsNullWhenNoDriveNodes() {
        var ctx = new PromptContext("unknown-agent", TENANT, null);
        var result = section.contribute(ctx);
        assertNull(result);
    }

    @Test
    void rendersPercentageAndDescription() {
        var ctx = new PromptContext(AGENT, TENANT, null);
        var result = section.contribute(ctx);

        assertTrue(result.contains("92%"), "Should show 92% for scheming");
        assertTrue(result.contains("65%"), "Should show 65% for dominance");
        assertTrue(result.contains("Compelled to hatch"), "Should include description");
        assertTrue(result.contains("Character Motivations"), "Should have heading");
    }
}
