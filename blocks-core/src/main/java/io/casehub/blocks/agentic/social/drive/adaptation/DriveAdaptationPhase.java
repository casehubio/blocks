package io.casehub.blocks.agentic.social.drive.adaptation;

import io.casehub.blocks.agentic.social.need.NeedSatisfactionConfig;
import io.casehub.blocks.agentic.social.need.NeedTier;
import io.casehub.blocks.agentic.social.need.NeedTierMappingProvider;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import jakarta.annotation.Priority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

@Priority(17)
public class DriveAdaptationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(DriveAdaptationPhase.class.getName());
    private static final String CURSOR_NODE_NAME = "drive-adaptation-cursor";
    private static final String DRIVE_KIND = "drive-intensity";
    private static final String EXPERIENCE_PROVENANCE = "experience-consolidation";

    private final MindMapStore mindMapStore;
    private final Map<String, Map<String, List<DriveReinforcementEntry>>> reinforcementMap;
    private final DriveAdaptationConfig config;
    private final Map<String, Set<NeedTier>> tierMapping;
    private final NeedSatisfactionConfig needConfig;

    public DriveAdaptationPhase(
            MindMapStore mindMapStore,
            Map<String, Map<String, List<DriveReinforcementEntry>>> reinforcementMap,
            DriveAdaptationConfig config) {
        this(mindMapStore, reinforcementMap, config, null, null);
    }

    public DriveAdaptationPhase(
            MindMapStore mindMapStore,
            Map<String, Map<String, List<DriveReinforcementEntry>>> reinforcementMap,
            DriveAdaptationConfig config,
            NeedTierMappingProvider tierMappingProvider,
            NeedSatisfactionConfig needConfig) {
        this.mindMapStore = mindMapStore;
        this.reinforcementMap = reinforcementMap;
        this.config = config;
        this.tierMapping = tierMappingProvider != null ? tierMappingProvider.tierMapping() : Map.of();
        this.needConfig = needConfig;
    }

    @Override
    public String name() {
        return "drive-adaptation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        var subgraphs = mindMapStore.listSubgraphs(tenantId);
        for (var subgraph : subgraphs) {
            if (!"cognitive".equals(subgraph.type())) continue;
            processSubgraph(subgraph, tenantId);
        }
    }

    private void processSubgraph(MindMapSubgraph subgraph, String tenantId) {
        var nodes = mindMapStore.nodesIn(subgraph.id(), tenantId);

        var driveNodes = new HashMap<String, MindMapNode>();
        var experienceNodes = new ArrayList<MindMapNode>();
        var needSatisfactionNodes = new HashMap<NeedTier, MindMapNode>();
        String cursorNodeId = null;
        String lastProcessedId = null;
        String agentId = null;

        for (var node : nodes) {
            var kind = node.properties().get("cognitiveKind");
            if (DRIVE_KIND.equals(kind)) {
                var driveType = node.properties().get("drive-type");
                if (driveType != null) driveNodes.put(driveType, node);
                if (agentId == null) agentId = node.properties().get("agent-id");
            } else if (EXPERIENCE_PROVENANCE.equals(node.provenance())) {
                experienceNodes.add(node);
            }
            if (CURSOR_NODE_NAME.equals(node.name())) {
                cursorNodeId = node.id();
                lastProcessedId = node.properties().get("last-processed-node-id");
            }
        }

        if (driveNodes.isEmpty()) {
            var agentIds = nodes.stream()
                .map(n -> n.properties().get("agent-id"))
                .filter(Objects::nonNull)
                .distinct().toList();
            if (!agentIds.isEmpty()) {
                LOG.warning("No drive nodes found for agent(s) " + agentIds
                    + " in tenant " + tenantId);
            }
            return;
        }

        if (agentId == null) {
            agentId = driveNodes.values().iterator().next().properties().get("agent-id");
        }

        for (var node : nodes) {
            var kind = node.properties().get("cognitiveKind");
            if ("need-satisfaction".equals(kind) && agentId.equals(node.properties().get("agent-id"))) {
                var tierName = node.properties().get("tier");
                if (tierName != null) {
                    try {
                        needSatisfactionNodes.put(NeedTier.valueOf(tierName), node);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        var agentReinforcement = reinforcementMap.get(agentId);
        if (agentReinforcement == null) {
            applyDecayAndSave(needSatisfactionNodes, tenantId);
            return;
        }

        var newExperiences = filterNewExperiences(experienceNodes, lastProcessedId);
        if (newExperiences.isEmpty()) {
            applyDecayAndSave(needSatisfactionNodes, tenantId);
            return;
        }

        var rewardAccumulator = accumulateRewards(newExperiences, agentReinforcement);

        var currentSatisfaction = loadCurrentSatisfaction(needSatisfactionNodes);
        accumulateTierSatisfaction(currentSatisfaction, newExperiences, agentReinforcement);
        applyUpdates(driveNodes, rewardAccumulator, currentSatisfaction, tenantId);
        applyDecay(currentSatisfaction);
        saveSatisfaction(needSatisfactionNodes, currentSatisfaction, tenantId);

        var lastId = newExperiences.get(newExperiences.size() - 1).id();
        saveCursor(subgraph.id(), cursorNodeId, lastId, tenantId);
    }

    private void applyDecayAndSave(Map<NeedTier, MindMapNode> needSatisfactionNodes, String tenantId) {
        if (needConfig == null || needSatisfactionNodes.isEmpty()) return;
        var currentSatisfaction = loadCurrentSatisfaction(needSatisfactionNodes);
        applyDecay(currentSatisfaction);
        saveSatisfaction(needSatisfactionNodes, currentSatisfaction, tenantId);
    }

    private List<MindMapNode> filterNewExperiences(
            List<MindMapNode> experienceNodes, String lastProcessedId) {
        if (lastProcessedId == null) return experienceNodes;
        return experienceNodes.stream()
            .filter(n -> n.id().compareTo(lastProcessedId) > 0)
            .toList();
    }

    record DriveReward(double totalReward, int count) {
        DriveReward add(double reward) {
            return new DriveReward(totalReward + reward, count + 1);
        }

        double effectiveReward() {
            if (count == 0) return 0.0;
            return totalReward / (1 + Math.log(count));
        }
    }

    private Map<String, DriveReward> accumulateRewards(
            List<MindMapNode> experiences,
            Map<String, List<DriveReinforcementEntry>> agentReinforcement) {
        var accumulator = new HashMap<String, DriveReward>();

        for (var exp : experiences) {
            var eventType = exp.properties().get("event-type");
            if (eventType == null) continue;

            var entries = agentReinforcement.get(eventType);
            if (entries == null) continue;

            for (var entry : entries) {
                double padValue = extractPadValue(exp, entry.rewardAxis());
                if (padValue == 0.0) continue;

                double arousal = exp.arousal() != null ? exp.arousal() : 0.0;
                double reward = padValue * (1 + Math.abs(arousal) * config.arousalWeight());

                if (entry.direction() == ReinforcementDirection.NEGATIVE) {
                    reward = -reward;
                }

                accumulator.merge(entry.driveType(),
                    new DriveReward(reward, 1),
                    (a, b) -> a.add(b.totalReward()));
            }
        }
        return accumulator;
    }

    private double extractPadValue(MindMapNode node, RewardAxis axis) {
        return switch (axis) {
            case PLEASURE -> node.pleasure() != null ? node.pleasure() : 0.0;
            case DOMINANCE -> node.dominance() != null ? node.dominance() : 0.0;
            case COMPOSITE -> {
                double p = node.pleasure() != null ? node.pleasure() : 0.0;
                double a = node.arousal() != null ? node.arousal() : 0.0;
                double d = node.dominance() != null ? node.dominance() : 0.0;
                yield 0.6 * p + 0.2 * a + 0.2 * d;
            }
        };
    }

    private void applyUpdates(
            Map<String, MindMapNode> driveNodes,
            Map<String, DriveReward> rewardAccumulator,
            Map<NeedTier, Double> currentSatisfaction,
            String tenantId) {
        for (var entry : rewardAccumulator.entrySet()) {
            var driveNode = driveNodes.get(entry.getKey());
            if (driveNode == null) continue;

            double currentIntensity = Double.parseDouble(
                driveNode.properties().getOrDefault("intensity", "0.5"));

            double effectiveLR = config.learningRate();
            var tiers = tierMapping.get(entry.getKey());
            if (tiers != null && !tiers.isEmpty() && !currentSatisfaction.isEmpty()) {
                double avgSatisfaction = tiers.stream()
                    .mapToDouble(t -> currentSatisfaction.getOrDefault(t, 0.5))
                    .average().orElse(0.5);
                effectiveLR = config.learningRate() * (1 - avgSatisfaction);
            }

            double delta = entry.getValue().effectiveReward() * effectiveLR;
            double newIntensity = currentIntensity * (1 + delta);
            newIntensity = Math.clamp(newIntensity, config.minIntensity(), config.maxIntensity());

            mindMapStore.updateNode(driveNode.id(),
                NodeUpdate.empty().withPropertiesToSet(
                    Map.of("intensity", String.valueOf(newIntensity))),
                tenantId);
        }
    }

    private void accumulateTierSatisfaction(
            Map<NeedTier, Double> currentSatisfaction,
            List<MindMapNode> experiences,
            Map<String, List<DriveReinforcementEntry>> agentReinforcement) {
        if (needConfig == null || tierMapping.isEmpty()) return;

        var tierRaw = new HashMap<NeedTier, Double>();
        var tierCount = new HashMap<NeedTier, Integer>();

        for (var exp : experiences) {
            var eventType = exp.properties().get("event-type");
            if (eventType == null) continue;
            var entries = agentReinforcement.get(eventType);
            if (entries == null) continue;

            for (var entry : entries) {
                var tiers = tierMapping.get(entry.driveType());
                if (tiers == null) continue;

                double padValue = extractPadValue(exp, entry.rewardAxis());
                if (padValue > 0) {
                    for (var tier : tiers) {
                        tierRaw.merge(tier, needConfig.satisfactionIncrement() * padValue, Double::sum);
                        tierCount.merge(tier, 1, Integer::sum);
                    }
                } else if (padValue < 0) {
                    for (var tier : tiers) {
                        tierRaw.merge(tier, -(needConfig.dissatisfactionIncrement() * Math.abs(padValue)), Double::sum);
                        tierCount.merge(tier, 1, Integer::sum);
                    }
                }
            }
        }

        for (var entry : tierRaw.entrySet()) {
            var tier = entry.getKey();
            int count = tierCount.getOrDefault(tier, 1);
            double effectiveDelta = entry.getValue() / (1 + Math.log(count));
            double current = currentSatisfaction.getOrDefault(tier, 0.5);
            currentSatisfaction.put(tier, Math.clamp(current + effectiveDelta, 0.0, 1.0));
        }
    }

    private void applyDecay(Map<NeedTier, Double> currentSatisfaction) {
        if (needConfig == null) return;
        for (NeedTier tier : NeedTier.values()) {
            double satisfaction = currentSatisfaction.getOrDefault(tier, 0.5);
            double restingLevel = needConfig.restingLevel(tier);
            double decayRate = needConfig.decayRate(tier);
            double decayed = restingLevel + (satisfaction - restingLevel) * (1 - decayRate);
            currentSatisfaction.put(tier, Math.clamp(decayed, 0.0, 1.0));
        }
    }

    private Map<NeedTier, Double> loadCurrentSatisfaction(Map<NeedTier, MindMapNode> needNodes) {
        var result = new HashMap<NeedTier, Double>();
        for (var entry : needNodes.entrySet()) {
            double val = Double.parseDouble(
                entry.getValue().properties().getOrDefault("satisfaction", "0.5"));
            result.put(entry.getKey(), val);
        }
        return result;
    }

    private void saveSatisfaction(
            Map<NeedTier, MindMapNode> needNodes,
            Map<NeedTier, Double> currentSatisfaction,
            String tenantId) {
        for (var entry : currentSatisfaction.entrySet()) {
            var node = needNodes.get(entry.getKey());
            if (node != null) {
                mindMapStore.updateNode(node.id(),
                    NodeUpdate.empty().withPropertiesToSet(
                        Map.of("satisfaction", String.valueOf(entry.getValue()))),
                    tenantId);
            }
        }
    }

    private void saveCursor(String subgraphId, String cursorNodeId,
                            String lastId, String tenantId) {
        if (cursorNodeId != null) {
            mindMapStore.updateNode(cursorNodeId,
                NodeUpdate.empty().withPropertiesToSet(
                    Map.of("last-processed-node-id", lastId)),
                tenantId);
        } else {
            mindMapStore.addNode(
                NodeInput.of(CURSOR_NODE_NAME, subgraphId)
                    .withProvenance("drive-adaptation")
                    .withProperties(Map.of(
                        "cognitiveKind", "cursor",
                        "last-processed-node-id", lastId)),
                tenantId);
        }
    }
}
