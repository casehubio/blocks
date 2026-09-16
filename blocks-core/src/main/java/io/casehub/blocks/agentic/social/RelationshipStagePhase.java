package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.relationship.RelationshipQuery;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import jakarta.annotation.Priority;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Priority(18)
public class RelationshipStagePhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(RelationshipStagePhase.class.getName());
    private static final double POSITIVE_THRESHOLD = 0.1;
    private static final double NEGATIVE_THRESHOLD = -0.1;

    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;
    private final RelationshipStageConfigProvider configProvider;
    private final String peopleSubgraphName;
    private final long consolidationIntervalMs;

    public RelationshipStagePhase(
            MindMapStore mindMapStore,
            CaseMemoryStore memoryStore,
            RelationshipStageConfigProvider configProvider,
            String peopleSubgraphName,
            long consolidationIntervalMs) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
        this.configProvider = configProvider;
        this.peopleSubgraphName = peopleSubgraphName;
        this.consolidationIntervalMs = consolidationIntervalMs;
    }

    @Override
    public String name() {
        return "relationship-stage";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        var subgraphs = mindMapStore.listSubgraphs(tenantId);
        var peopleSg = subgraphs.stream()
            .filter(sg -> peopleSubgraphName.equals(sg.name()))
            .findFirst();

        if (peopleSg.isEmpty()) {
            LOG.warning("No people subgraph '" + peopleSubgraphName
                + "' found in tenant " + tenantId);
            return;
        }

        var nodes = mindMapStore.nodesIn(peopleSg.get().id(), tenantId);

        var sharedNodes = new HashMap<String, MindMapNode>();
        for (var node : nodes) {
            if (!node.traits().contains("overlay")) {
                node.property("agentId").ifPresent(aid -> sharedNodes.put(node.id(), node));
            }
        }

        for (var overlay : nodes) {
            if (!overlay.traits().contains("overlay")) continue;
            var observerId = overlay.property(OverlayRef.AGENT_ID).orElse(null);
            if (observerId == null) continue;

            var targetNodeId = OverlayRef.sharedNodeId(overlay).orElse(null);
            if (targetNodeId == null) continue;
            var sharedNode = sharedNodes.get(targetNodeId);
            if (sharedNode == null) continue;
            var targetAgentId = sharedNode.property("agentId").orElse(null);
            if (targetAgentId == null) continue;

            updateFamiliarity(overlay, observerId, targetAgentId, tenantId);
        }
    }

    private void updateFamiliarity(MindMapNode overlay, String observerId,
                                    String targetAgentId, String tenantId) {
        var memories = memoryStore.query(
            RelationshipQuery.forPair(observerId, targetAgentId, tenantId)
                .withLimit(500));

        int positive = 0, negative = 0, neutral = 0;
        Instant latestTimestamp = null;

        for (var memory : memories) {
            Double pleasure = memory.pleasure();
            if (pleasure != null && pleasure > POSITIVE_THRESHOLD) positive++;
            else if (pleasure != null && pleasure < NEGATIVE_THRESHOLD) negative++;
            else neutral++;

            if (latestTimestamp == null || (memory.createdAt() != null
                && memory.createdAt().isAfter(latestTimestamp))) {
                latestTimestamp = memory.createdAt();
            }
        }

        long ticksSinceLastInteraction = 0;
        if (latestTimestamp != null && consolidationIntervalMs > 0) {
            long elapsedMs = Duration.between(latestTimestamp, Instant.now()).toMillis();
            ticksSinceLastInteraction = Math.max(0, elapsedMs / consolidationIntervalMs);
        }

        var stageConfig = configProvider.forAgent(observerId);
        double score = UserModelOrchestrator.computeFamiliarity(
            positive, negative, neutral, stageConfig, ticksSinceLastInteraction);
        String stage = stageConfig.resolveStage(score);

        mindMapStore.updateNode(overlay.id(),
            NodeUpdate.empty().withPropertiesToSet(Map.of(
                OverlayFamiliarityPropertyModel.FAMILIARITY_SCORE, String.valueOf(score),
                OverlayFamiliarityPropertyModel.FAMILIARITY_STAGE, stage,
                OverlayFamiliarityPropertyModel.FAMILIARITY_INTERACTION_COUNT,
                    String.valueOf(positive + negative + neutral))),
            tenantId);
    }
}
