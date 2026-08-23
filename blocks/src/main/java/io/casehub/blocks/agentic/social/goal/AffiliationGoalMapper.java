package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

public class AffiliationGoalMapper implements DriveGoalMapper {

    private final UserModelOrchestrator userModelOrchestrator;
    private final double decayThreshold;
    private final Duration staleDuration;

    public AffiliationGoalMapper(UserModelOrchestrator userModelOrchestrator,
                                  double decayThreshold, Duration staleDuration) {
        this.userModelOrchestrator = userModelOrchestrator;
        this.decayThreshold = decayThreshold;
        this.staleDuration = staleDuration;
    }

    @Override
    public @Nullable DriveGoalProposal evaluate(String agentId, String tenantId,
                                                 DriveIntensity intensity) {
        if (intensity.axis() != DriveAxis.AFFILIATION) return null;
        var profiles = userModelOrchestrator.activeProfiles(agentId, tenantId);
        if (profiles.isEmpty()) {
            return null;
        }

        var now = Instant.now();
        UserProfile mostNeglected = null;
        double worstScore = Double.MAX_VALUE;

        for (var profile : profiles) {
            boolean lowFamiliarity = profile.familiarityScore() < decayThreshold;
            boolean stale = Duration.between(profile.lastInteraction(), now)
                    .compareTo(staleDuration) > 0;
            if (lowFamiliarity || stale) {
                double score = profile.familiarityScore();
                if (score < worstScore) {
                    worstScore = score;
                    mostNeglected = profile;
                }
            }
        }

        if (mostNeglected == null) {
            return null;
        }

        return new DriveGoalProposal(
                DriveAxis.AFFILIATION,
                "reconnect-" + mostNeglected.subjectId(),
                "Reconnect with " + mostNeglected.subjectId()
                        + " (familiarity: " + String.format("%.2f", mostNeglected.familiarityScore()) + ")",
                "affiliation: relationship with " + mostNeglected.subjectId() + " neglected",
                intensity.intensity());
    }
}
