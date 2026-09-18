package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.need.NeedTier;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NeedsPyramidPromptSectionTest {

    private InMemoryMindMapStore store;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "hooded-claw";
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        subgraphId = store.createSubgraph(
            new SubgraphInput("beliefs-" + AGENT, "cognitive", null), TENANT);

        store.addNode(NodeInput.of("scheming", subgraphId)
            .withConfidence(Confidence.stated(0.8, Instant.now()))
            .withProvenance("drive-adaptation")
            .withProperties(Map.of(
                "cognitiveKind", "drive-intensity",
                "agent-id", AGENT,
                "drive-type", "scheming",
                "intensity", "0.9")),
            TENANT);
    }

    private void seedSatisfaction(NeedTier tier, double value) {
        store.addNode(NodeInput.of("need-" + tier.name().toLowerCase(), subgraphId)
            .withConfidence(Confidence.stated(0.8, Instant.now()))
            .withProvenance("need-satisfaction")
            .withProperties(Map.of(
                "cognitiveKind", "need-satisfaction",
                "agent-id", AGENT,
                "tier", tier.name(),
                "satisfaction", String.valueOf(value))),
            TENANT);
    }

    @Test
    void rendersCorrectBandLabels() {
        var tierMapping = Map.of("scheming", Set.of(NeedTier.SELF_EXPRESSION, NeedTier.SAFETY));

        seedSatisfaction(NeedTier.SELF_EXPRESSION, 0.9);
        seedSatisfaction(NeedTier.SAFETY, 0.1);

        var section = new NeedsPyramidPromptSection(store, tierMapping);
        var result = section.contribute(new PromptContext(AGENT, TENANT, null));

        assertNotNull(result);
        assertTrue(result.contains("Inner Needs"), "Should have heading");
        assertTrue(result.contains("self-expression"), "Should render SELF_EXPRESSION");
        assertTrue(result.contains("safety"), "Should render SAFETY");
        assertTrue(result.contains("fulfilled") || result.contains("true to yourself"),
            "0.9 should render as fulfilled");
        assertTrue(result.contains("neglected") || result.contains("uneasy"),
            "0.1 should render as critically neglected");
    }

    @Test
    void filtersUnreachableTiers() {
        var tierMapping = Map.of("scheming", Set.of(NeedTier.SELF_EXPRESSION));

        seedSatisfaction(NeedTier.SELF_EXPRESSION, 0.5);
        seedSatisfaction(NeedTier.TASKS, 0.3);

        var section = new NeedsPyramidPromptSection(store, tierMapping);
        var result = section.contribute(new PromptContext(AGENT, TENANT, null));

        assertNotNull(result);
        assertTrue(result.contains("self-expression"), "Reachable tier should appear");
        assertFalse(result.contains("task"), "Unreachable TASKS should be filtered out");
    }

    @Test
    void returnsNullForDrivelessAgent() {
        var tierMapping = Map.of("scheming", Set.of(NeedTier.SELF_EXPRESSION));

        var section = new NeedsPyramidPromptSection(store, tierMapping);
        var result = section.contribute(new PromptContext("unknown-agent", TENANT, null));

        assertNull(result, "Driveless agent should get null (no Inner Needs)");
    }

    @Test
    void rendersAllFiveBands() {
        var tierMapping = Map.of("scheming",
            Set.of(NeedTier.SAFETY, NeedTier.TASKS, NeedTier.SOCIAL,
                   NeedTier.SELF_EXPRESSION, NeedTier.UNDERSTANDING));

        seedSatisfaction(NeedTier.SAFETY, 0.1);       // critically neglected
        seedSatisfaction(NeedTier.TASKS, 0.3);         // neglected
        seedSatisfaction(NeedTier.SOCIAL, 0.5);        // adequate
        seedSatisfaction(NeedTier.UNDERSTANDING, 0.7); // well-met
        seedSatisfaction(NeedTier.SELF_EXPRESSION, 0.9); // fulfilled

        var section = new NeedsPyramidPromptSection(store, tierMapping);
        var result = section.contribute(new PromptContext(AGENT, TENANT, null));

        assertNotNull(result);
        assertTrue(result.contains("safety"), "SAFETY should appear");
        assertTrue(result.contains("task"), "TASKS should appear");
        assertTrue(result.contains("social"), "SOCIAL should appear");
        assertTrue(result.contains("curiosity"), "UNDERSTANDING should appear");
        assertTrue(result.contains("self-expression"), "SELF_EXPRESSION should appear");
    }
}
