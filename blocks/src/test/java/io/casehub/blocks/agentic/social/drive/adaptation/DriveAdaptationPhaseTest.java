package io.casehub.blocks.agentic.social.drive.adaptation;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DriveAdaptationPhaseTest {

    private InMemoryMindMapStore store;
    private DriveAdaptationPhase phase;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "hooded-claw";
    private String subgraphId;

    record TestConfig(
        double learningRate, double arousalWeight,
        double minIntensity, double maxIntensity,
        int maxPerPass
    ) implements DriveAdaptationConfig {}

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();

        var reinforcement = Map.of(
            AGENT, Map.of(
                "conflict_resolution", List.of(
                    new DriveReinforcementEntry("scheming", null, null)
                )
            )
        );

        var config = new TestConfig(0.1, 0.3, 0.1, 1.0, 20);
        phase = new DriveAdaptationPhase(store, reinforcement, config);

        subgraphId = store.createSubgraph(
            new SubgraphInput("beliefs-" + AGENT, "cognitive", null), TENANT);

        store.addNode(NodeInput.of("scheming", subgraphId)
            .withConfidence(Confidence.stated(0.8, Instant.now()))
            .withProvenance("drive-adaptation")
            .withProperties(Map.of(
                "cognitiveKind", "drive-intensity",
                "agent-id", AGENT,
                "drive-type", "scheming",
                "intensity", "0.9",
                "initial-intensity", "0.9",
                "description", "Compelled to hatch elaborate plans")),
            TENANT);
    }

    @Test
    void positiveReinforcementIncreasesIntensity() {
        store.addNode(NodeInput.of("conflict event", subgraphId)
            .withProvenance("experience-consolidation")
            .withProperties(Map.of(
                "cognitiveKind", "experience",
                "agent-id", AGENT,
                "event-type", "conflict_resolution"))
            .withPleasure(0.6).withArousal(0.5),
            TENANT);

        phase.run(TENANT, List.of());

        var nodes = store.nodesIn(subgraphId, TENANT);
        var driveNode = nodes.stream()
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "scheming".equals(n.properties().get("drive-type")))
            .findFirst().orElseThrow();

        double updatedIntensity = Double.parseDouble(
            driveNode.properties().get("intensity"));
        assertTrue(updatedIntensity > 0.9,
            "Expected intensity > 0.9, got " + updatedIntensity);
        assertTrue(updatedIntensity <= 1.0,
            "Expected intensity <= 1.0, got " + updatedIntensity);
    }

    @Test
    void negativeReinforcementDecreasesIntensity() {
        var reinforcement = Map.of(
            AGENT, Map.of(
                "social_interaction", List.of(
                    new DriveReinforcementEntry("scheming",
                        ReinforcementDirection.NEGATIVE, null)
                )
            )
        );
        var config = new TestConfig(0.1, 0.3, 0.1, 1.0, 20);
        phase = new DriveAdaptationPhase(store, reinforcement, config);

        store.addNode(NodeInput.of("social event", subgraphId)
            .withProvenance("experience-consolidation")
            .withProperties(Map.of(
                "cognitiveKind", "experience",
                "agent-id", AGENT,
                "event-type", "social_interaction"))
            .withPleasure(0.7).withArousal(0.3),
            TENANT);

        phase.run(TENANT, List.of());

        var nodes = store.nodesIn(subgraphId, TENANT);
        var driveNode = nodes.stream()
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "scheming".equals(n.properties().get("drive-type")))
            .findFirst().orElseThrow();

        double updated = Double.parseDouble(driveNode.properties().get("intensity"));
        assertTrue(updated < 0.9,
            "Expected intensity < 0.9 after negative reinforcement, got " + updated);
        assertTrue(updated >= 0.1,
            "Expected intensity >= 0.1 (floor), got " + updated);
    }

    @Test
    void intensityNeverDropsBelowFloor() {
        store.addNode(NodeInput.of("low-drive", subgraphId)
            .withConfidence(Confidence.stated(0.8, Instant.now()))
            .withProvenance("drive-adaptation")
            .withProperties(Map.of(
                "cognitiveKind", "drive-intensity",
                "agent-id", AGENT,
                "drive-type", "weak-drive",
                "intensity", "0.12",
                "initial-intensity", "0.12",
                "description", "Almost extinguished")),
            TENANT);

        var reinforcement = Map.of(
            AGENT, Map.of(
                "conflict_resolution", List.of(
                    new DriveReinforcementEntry("weak-drive",
                        ReinforcementDirection.NEGATIVE, null)
                )
            )
        );
        var config = new TestConfig(0.5, 0.3, 0.1, 1.0, 20);
        phase = new DriveAdaptationPhase(store, reinforcement, config);

        store.addNode(NodeInput.of("strong conflict", subgraphId)
            .withProvenance("experience-consolidation")
            .withProperties(Map.of(
                "cognitiveKind", "experience",
                "agent-id", AGENT,
                "event-type", "conflict_resolution"))
            .withPleasure(0.9).withArousal(0.9),
            TENANT);

        phase.run(TENANT, List.of());

        var nodes = store.nodesIn(subgraphId, TENANT);
        var driveNode = nodes.stream()
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "weak-drive".equals(n.properties().get("drive-type")))
            .findFirst().orElseThrow();

        double updated = Double.parseDouble(driveNode.properties().get("intensity"));
        assertEquals(0.1, updated, 0.001,
            "Intensity should clamp to floor 0.1");
    }

    @Test
    void dominanceRewardAxis() {
        var reinforcement = Map.of(
            AGENT, Map.of(
                "conflict_resolution", List.of(
                    new DriveReinforcementEntry("scheming", null, RewardAxis.DOMINANCE)
                )
            )
        );
        var config = new TestConfig(0.1, 0.3, 0.1, 1.0, 20);
        phase = new DriveAdaptationPhase(store, reinforcement, config);

        store.addNode(NodeInput.of("dominance event", subgraphId)
            .withProvenance("experience-consolidation")
            .withProperties(Map.of(
                "cognitiveKind", "experience",
                "agent-id", AGENT,
                "event-type", "conflict_resolution"))
            .withPleasure(-0.2).withArousal(0.4).withDominance(0.8),
            TENANT);

        phase.run(TENANT, List.of());

        var nodes = store.nodesIn(subgraphId, TENANT);
        var driveNode = nodes.stream()
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "scheming".equals(n.properties().get("drive-type")))
            .findFirst().orElseThrow();

        double updated = Double.parseDouble(driveNode.properties().get("intensity"));
        assertTrue(updated > 0.9,
            "Expected intensity > 0.9 with dominance axis, got " + updated);
    }

    @Test
    void noExperienceNodesProducesNoChange() {
        phase.run(TENANT, List.of());

        var nodes = store.nodesIn(subgraphId, TENANT);
        var driveNode = nodes.stream()
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .findFirst().orElseThrow();

        assertEquals("0.9", driveNode.properties().get("intensity"));
    }
}
