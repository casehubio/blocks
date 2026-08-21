package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.memory.MemoryHygieneOrchestrator;

public class CuriosityDrive implements DriveSource {

    private final MemoryHygieneOrchestrator hygieneOrchestrator;

    public CuriosityDrive(MemoryHygieneOrchestrator hygieneOrchestrator) {
        this.hygieneOrchestrator = hygieneOrchestrator;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var gaps = hygieneOrchestrator.knowledgeGaps(agentId, tenantId);

        if (gaps.totalScored() == 0) {
            return new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "no hygiene data");
        }

        double retentionRatio = (double) gaps.lowRetentionCount() / gaps.totalScored();
        double groupDiversity = Math.min(gaps.consolidationGroups() / 10.0, 1.0);
        double intensity = Math.clamp((retentionRatio + groupDiversity) / 2.0, 0.0, 1.0);

        String trigger = gaps.lowRetentionCount() + " low-retention memories across "
                + gaps.consolidationGroups() + " groups";
        return new DriveIntensity(DriveAxis.CURIOSITY, intensity, trigger);
    }
}
