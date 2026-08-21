package io.casehub.blocks.agentic.social.drive;

@FunctionalInterface
public interface DriveSource {
    DriveIntensity evaluate(String agentId, String tenantId);
}
