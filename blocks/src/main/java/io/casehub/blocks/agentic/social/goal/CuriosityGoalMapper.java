package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import org.jspecify.annotations.Nullable;

public class CuriosityGoalMapper implements DriveGoalMapper {

    private final MemoryHygieneOrchestrator hygieneOrchestrator;

    public CuriosityGoalMapper(MemoryHygieneOrchestrator hygieneOrchestrator) {
        this.hygieneOrchestrator = hygieneOrchestrator;
    }

    @Override
    public @Nullable DriveGoalProposal evaluate(String agentId, String tenantId,
                                                 DriveIntensity intensity) {
        if (intensity.axis() != DriveAxis.CURIOSITY) return null;
        var gaps = hygieneOrchestrator.knowledgeGaps(agentId, tenantId);
        if (gaps == null || gaps.lowRetentionCount() == 0) {
            return null;
        }
        return new DriveGoalProposal(
                DriveAxis.CURIOSITY,
                "explore-knowledge-gaps",
                "Explore fragmented knowledge areas ("
                        + gaps.lowRetentionCount() + " low-retention memories across "
                        + gaps.consolidationGroups() + " groups)",
                "curiosity: " + gaps.lowRetentionCount() + " low-retention memories"
                        + " across " + gaps.consolidationGroups() + " groups",
                intensity.intensity());
    }
}
