package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class AffiliationDrive implements DriveSource {

    private final UserModelOrchestrator userModelOrchestrator;
    private final double decayThreshold;
    private final Duration staleDuration;
    private volatile @Nullable List<UserProfile> lastProfiles;


    public AffiliationDrive(UserModelOrchestrator userModelOrchestrator,
                            double decayThreshold, Duration staleDuration) {
        this.userModelOrchestrator = userModelOrchestrator;
        this.decayThreshold = decayThreshold;
        this.staleDuration = staleDuration;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var profiles = userModelOrchestrator.activeProfiles(agentId, tenantId);
        this.lastProfiles = profiles;

        if (profiles.isEmpty()) {
            return new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "no tracked subjects");
        }

        var now       = Instant.now();
        int neglected = 0;
        for (var profile : profiles) {
            boolean lowFamiliarity = profile.familiarityScore() < decayThreshold;
            boolean stale          = Duration.between(profile.lastInteraction(), now).compareTo(staleDuration) > 0;
            if (lowFamiliarity || stale) {neglected++;}
        }

        double intensity = Math.clamp((double) neglected / profiles.size(), 0.0, 1.0);
        String trigger   = neglected + " of " + profiles.size() + " relationships neglected";
        return new DriveIntensity(DriveAxis.AFFILIATION, intensity, trigger);}

    public @Nullable List<UserProfile> lastProfiles() {
        return lastProfiles;
    }

}
