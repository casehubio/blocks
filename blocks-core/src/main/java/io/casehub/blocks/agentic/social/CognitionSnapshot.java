package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.agentic.social.goal.DriveGoalProposal;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.neocortex.memory.mood.MoodState;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CognitionSnapshot(
        String agentId,
        String tenantId,
        int turnNumber,
        Instant capturedAt,
        @Nullable MoodState mood,
        @Nullable DriveProfile drives,
        Map<String, MentalModelSnapshot> mentalModels,
        Map<String, UserProfile> userProfiles,
        @Nullable StrategyProfile strategy,
        @Nullable NarrativeState narrative,
        List<DriveGoalProposal> goalProposals
) {

    public static CognitionSnapshot capture(CognitionCore core,
                                             String agentId,
                                             String tenantId,
                                             int turnNumber,
                                             Set<String> subjectIds) {
        var moodState = core.mood().currentMood(agentId, tenantId)
                .orElse(null);
        var driveProfile = core.drives().currentDrives(agentId, tenantId)
                .orElse(null);

        var mentalModels = new LinkedHashMap<String, MentalModelSnapshot>();
        if (core.mentalModel() != null) {
            for (var snapshot : core.mentalModel().activeSnapshots(agentId, tenantId)) {
                mentalModels.put(snapshot.subjectId(), snapshot);
            }
        }

        var userProfiles = new LinkedHashMap<String, UserProfile>();
        if (core.userModel() != null) {
            for (String subjectId : subjectIds) {
                var profile = core.userModel()
                        .currentProfile(agentId, subjectId, tenantId);
                if (profile != null) userProfiles.put(subjectId, profile);
            }
        }

        StrategyProfile strategyProfile = null;
        if (core.strategy() != null) {
            strategyProfile = core.strategy()
                    .currentStrategy(agentId, tenantId).orElse(null);
        }

        NarrativeState narrativeState = null;
        if (core.narrative() != null) {
            narrativeState = core.narrative()
                    .currentNarrative(agentId, tenantId).orElse(null);
        }

        List<DriveGoalProposal> goals = List.of();
        if (core.goals() != null) {
            goals = core.goals().currentProposals(agentId, tenantId)
                    .orElse(List.of());
        }

        return new CognitionSnapshot(agentId, tenantId, turnNumber,
                Instant.now(), moodState, driveProfile, mentalModels,
                userProfiles, strategyProfile, narrativeState, goals);
    }

    public CognitionDelta diffFrom(@Nullable CognitionSnapshot previous) {
        return CognitionDelta.compute(previous, this);
    }
}
