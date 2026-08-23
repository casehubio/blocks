package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface DriveGoalMapper {
    @Nullable DriveGoalProposal evaluate(String agentId, String tenantId, DriveIntensity intensity);
}
