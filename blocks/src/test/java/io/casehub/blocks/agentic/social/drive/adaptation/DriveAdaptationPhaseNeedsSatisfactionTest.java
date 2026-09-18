package io.casehub.blocks.agentic.social.drive.adaptation;

import io.casehub.blocks.agentic.social.need.NeedSatisfactionConfig;
import io.casehub.blocks.agentic.social.need.NeedTier;
import io.casehub.blocks.agentic.social.need.NeedTierMappingProvider;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DriveAdaptationPhaseNeedsSatisfactionTest {

    private InMemoryMindMapStore store;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "hooded-claw";
    private String subgraphId;

    record TestDriveConfig(
        double learningRate, double arousalWeight,
        double minIntensity, double maxIntensity,
        int maxPerPass
    ) implements DriveAdaptationConfig {}

    record TestDecayConfig(
        double safety, double tasks, double social,
        double selfExpression, double understanding
    ) implements NeedSatisfactionConfig.DecayConfig {}

    record TestRestingConfig(
        double safety, double tasks, double social,
        double selfExpression, double understanding
    ) implements NeedSatisfactionConfig.RestingConfig {}

    record TestNeedConfig(
        double satisfactionIncrement, double dissatisfactionIncrement,
        double initialSatisfaction,
        NeedSatisfactionConfig.DecayConfig decay,
        NeedSatisfactionConfig.RestingConfig resting
    ) implements NeedSatisfactionConfig {
        @Override public double decayRate(NeedTier tier) {
            return switch (tier) {
                case SAFETY -> decay.safety();
                case TASKS -> decay.tasks();
                case SOCIAL -> decay.social();
                case SELF_EXPRESSION -> decay.selfExpression();
                case UNDERSTANDING -> decay.understanding();
            };
        }
        @Override public double restingLevel(NeedTier tier) {
            return switch (tier) {
                case SAFETY -> resting.safety();
                case TASKS -> resting.tasks();
                case SOCIAL -> resting.social();
                case SELF_EXPRESSION -> resting.selfExpression();
                case UNDERSTANDING -> resting.understanding();
            };
        }
    }

    private NeedSatisfactionConfig defaultNeedConfig() {
        return new TestNeedConfig(
            0.1, 0.1, 0.5,
            new TestDecayConfig(0.15, 0.10, 0.08, 0.05, 0.03),
            new TestRestingConfig(0.6, 0.3, 0.4, 0.4, 0.4)
        );
    }

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
                "intensity", "0.9",
                "initial-intensity", "0.9",
                "description", "Compelled to hatch elaborate plans")),
            TENANT);

        for (NeedTier tier : NeedTier.values()) {
            store.addNode(NodeInput.of("need-" + tier.name().toLowerCase(), subgraphId)
                .withConfidence(Confidence.stated(0.8, Instant.now()))
                .withProvenance("need-satisfaction")
                .withProperties(Map.of(
                    "cognitiveKind", "need-satisfaction",
                    "agent-id", AGENT,
                    "tier", tier.name(),
                    "satisfaction", "0.5",
                    "resting-level", String.valueOf(tier == NeedTier.SAFETY ? 0.6 : 0.4))),
                TENANT);
        }
    }

    private DriveAdaptationPhase createPhase(
            Map<String, Map<String, List<DriveReinforcementEntry>>> reinforcement,
            Map<String, Set<NeedTier>> tierMapping) {
        var driveConfig = new TestDriveConfig(0.1, 0.3, 0.1, 1.0, 20);
        var needConfig = defaultNeedConfig();
        NeedTierMappingProvider mappingProvider = () -> tierMapping;
        return new DriveAdaptationPhase(
            store, reinforcement, driveConfig, mappingProvider, needConfig);
    }

    @Test
    void positiveEventIncreasesTierSatisfaction() {
        var reinforcement = Map.of(
            AGENT, Map.of(
                "conflict_resolution", List.of(
                    new DriveReinforcementEntry("scheming", null, null)
                )
            )
        );
        var tierMapping = Map.of("scheming", Set.of(NeedTier.SELF_EXPRESSION));
        var phase = createPhase(reinforcement, tierMapping);

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
        var selfExprNode = nodes.stream()
            .filter(n -> "need-satisfaction".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "SELF_EXPRESSION".equals(n.properties().get("tier")))
            .findFirst().orElseThrow();

        double satisfaction = Double.parseDouble(selfExprNode.properties().get("satisfaction"));
        assertTrue(satisfaction > 0.5,
            "SELF_EXPRESSION satisfaction should increase from 0.5, got " + satisfaction);
    }

    @Test
    void negativeEventDecreasesTierSatisfaction() {
        var reinforcement = Map.of(
            AGENT, Map.of(
                "trust_change", List.of(
                    new DriveReinforcementEntry("scheming", null, null)
                )
            )
        );
        var tierMapping = Map.of("scheming", Set.of(NeedTier.SELF_EXPRESSION));
        var phase = createPhase(reinforcement, tierMapping);

        store.addNode(NodeInput.of("trust event", subgraphId)
            .withProvenance("experience-consolidation")
            .withProperties(Map.of(
                "cognitiveKind", "experience",
                "agent-id", AGENT,
                "event-type", "trust_change"))
            .withPleasure(-0.7).withArousal(0.5),
            TENANT);

        phase.run(TENANT, List.of());

        var nodes = store.nodesIn(subgraphId, TENANT);
        var selfExprNode = nodes.stream()
            .filter(n -> "need-satisfaction".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "SELF_EXPRESSION".equals(n.properties().get("tier")))
            .findFirst().orElseThrow();

        double satisfaction = Double.parseDouble(selfExprNode.properties().get("satisfaction"));
        assertTrue(satisfaction < 0.5,
            "SELF_EXPRESSION satisfaction should decrease from 0.5, got " + satisfaction);
    }

    @Test
    void decayTowardRestingLevel() {
        var reinforcement = Map.<String, Map<String, List<DriveReinforcementEntry>>>of(
            AGENT, Map.of());
        var tierMapping = Map.of("scheming", Set.of(NeedTier.SELF_EXPRESSION));
        var phase = createPhase(reinforcement, tierMapping);

        phase.run(TENANT, List.of());

        var nodes = store.nodesIn(subgraphId, TENANT);
        var safetyNode = nodes.stream()
            .filter(n -> "need-satisfaction".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "SAFETY".equals(n.properties().get("tier")))
            .findFirst().orElseThrow();

        double satisfaction = Double.parseDouble(safetyNode.properties().get("satisfaction"));
        assertTrue(satisfaction > 0.5,
            "Safety (resting 0.6) should decay UP from 0.5, got " + satisfaction);
        assertTrue(satisfaction < 0.6,
            "Safety should not exceed resting level in one cycle, got " + satisfaction);
    }

    @Test
    void saturationConstraintReducesLearningRate() {
        var reinforcement = Map.of(
            AGENT, Map.of(
                "conflict_resolution", List.of(
                    new DriveReinforcementEntry("scheming", null, null)
                )
            )
        );
        var tierMapping = Map.of("scheming", Set.of(NeedTier.SELF_EXPRESSION));

        var nodes = store.nodesIn(subgraphId, TENANT);
        var selfExprNode = nodes.stream()
            .filter(n -> "need-satisfaction".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "SELF_EXPRESSION".equals(n.properties().get("tier")))
            .findFirst().orElseThrow();
        store.updateNode(selfExprNode.id(),
            io.casehub.neocortex.mindmap.NodeUpdate.empty().withPropertiesToSet(
                Map.of("satisfaction", "0.9")),
            TENANT);

        var phaseHigh = createPhase(reinforcement, tierMapping);

        store.addNode(NodeInput.of("conflict event high", subgraphId)
            .withProvenance("experience-consolidation")
            .withProperties(Map.of(
                "cognitiveKind", "experience",
                "agent-id", AGENT,
                "event-type", "conflict_resolution"))
            .withPleasure(0.6).withArousal(0.5),
            TENANT);

        phaseHigh.run(TENANT, List.of());

        var driveNodeHigh = store.nodesIn(subgraphId, TENANT).stream()
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "scheming".equals(n.properties().get("drive-type")))
            .findFirst().orElseThrow();
        double highSatIntensity = Double.parseDouble(driveNodeHigh.properties().get("intensity"));

        assertTrue(highSatIntensity < 0.92,
            "With high satisfaction, drive should barely change, got " + highSatIntensity);
        assertTrue(highSatIntensity > 0.9,
            "Drive should still increase slightly, got " + highSatIntensity);
    }

    @Test
    void unmappedDriveSkipsSatisfactionUpdate() {
        var reinforcement = Map.of(
            AGENT, Map.of(
                "conflict_resolution", List.of(
                    new DriveReinforcementEntry("scheming", null, null)
                )
            )
        );
        var tierMapping = Map.<String, Set<NeedTier>>of();
        var phase = createPhase(reinforcement, tierMapping);

        store.addNode(NodeInput.of("conflict event", subgraphId)
            .withProvenance("experience-consolidation")
            .withProperties(Map.of(
                "cognitiveKind", "experience",
                "agent-id", AGENT,
                "event-type", "conflict_resolution"))
            .withPleasure(0.6).withArousal(0.5),
            TENANT);

        phase.run(TENANT, List.of());

        var selfExprNode = store.nodesIn(subgraphId, TENANT).stream()
            .filter(n -> "need-satisfaction".equals(n.properties().get("cognitiveKind")))
            .filter(n -> "SELF_EXPRESSION".equals(n.properties().get("tier")))
            .findFirst().orElseThrow();

        double satisfaction = Double.parseDouble(selfExprNode.properties().get("satisfaction"));
        assertTrue(satisfaction < 0.5,
            "With no events, satisfaction should decay, got " + satisfaction);
    }
}
