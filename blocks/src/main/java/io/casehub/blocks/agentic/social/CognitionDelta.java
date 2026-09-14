package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.goal.DriveGoalProposal;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CognitionDelta(
        @Nullable MoodDelta mood,
        @Nullable DriveDelta drives,
        Map<String, BdiDelta> mentalModelDeltas,
        Map<String, ProfileDelta> userProfileDeltas,
        int newEpisodeCount,
        int newThemeCount,
        List<DriveGoalProposal> newGoals
) {
    public record MoodDelta(double pleasureDelta, double arousalDelta,
                            double dominanceDelta) {}

    public record DriveDelta(Map<DriveAxis, Double> intensityDeltas,
                             double compositeDelta) {}

    public record BdiDelta(int newBeliefs, int newDesires,
                           int newIntentions) {}

    public record ProfileDelta(double familiarityDelta,
                               int interactionCountDelta) {}

    static CognitionDelta compute(@Nullable CognitionSnapshot before,
                                   CognitionSnapshot after) {
        MoodDelta moodDelta = null;
        if (after.mood() != null) {
            if (before == null || before.mood() == null) {
                moodDelta = new MoodDelta(
                        after.mood().pleasure(),
                        after.mood().arousal(),
                        after.mood().dominance());
            } else {
                moodDelta = new MoodDelta(
                        after.mood().pleasure() - before.mood().pleasure(),
                        after.mood().arousal() - before.mood().arousal(),
                        after.mood().dominance() - before.mood().dominance());
            }
        }

        DriveDelta driveDelta = null;
        if (after.drives() != null) {
            var deltas = new LinkedHashMap<DriveAxis, Double>();
            double prevComposite = 0;
            for (var entry : after.drives().drives().entrySet()) {
                double prev = 0;
                if (before != null && before.drives() != null) {
                    var prevIntensity = before.drives().drives()
                            .get(entry.getKey());
                    if (prevIntensity != null)
                        prev = prevIntensity.intensity();
                }
                deltas.put(entry.getKey(),
                        entry.getValue().intensity() - prev);
            }
            if (before != null && before.drives() != null) {
                prevComposite = before.drives().compositeMotivation();
            }
            driveDelta = new DriveDelta(deltas,
                    after.drives().compositeMotivation() - prevComposite);
        }

        var mentalDeltas = new LinkedHashMap<String, BdiDelta>();
        for (var entry : after.mentalModels().entrySet()) {
            var prev = before != null
                    ? before.mentalModels().get(entry.getKey()) : null;
            int prevBeliefs = prev != null ? prev.beliefs().size() : 0;
            int prevDesires = prev != null ? prev.desires().size() : 0;
            int prevIntentions = prev != null
                    ? prev.intentions().size() : 0;
            mentalDeltas.put(entry.getKey(), new BdiDelta(
                    entry.getValue().beliefs().size() - prevBeliefs,
                    entry.getValue().desires().size() - prevDesires,
                    entry.getValue().intentions().size() - prevIntentions));
        }

        var profileDeltas = new LinkedHashMap<String, ProfileDelta>();
        for (var entry : after.userProfiles().entrySet()) {
            var prev = before != null
                    ? before.userProfiles().get(entry.getKey()) : null;
            double prevFam = prev != null
                    ? prev.familiarityScore() : 0;
            int prevCount = prev != null
                    ? prev.totalInteractions() : 0;
            profileDeltas.put(entry.getKey(), new ProfileDelta(
                    entry.getValue().familiarityScore() - prevFam,
                    entry.getValue().totalInteractions() - prevCount));
        }

        int prevEpisodes = 0;
        int prevThemes = 0;
        if (before != null && before.narrative() != null) {
            prevEpisodes = before.narrative().episodes().size();
            prevThemes = before.narrative().themes().size();
        }
        int afterEpisodes = after.narrative() != null
                ? after.narrative().episodes().size() : 0;
        int afterThemes = after.narrative() != null
                ? after.narrative().themes().size() : 0;

        var prevGoalNames = before != null
                ? before.goalProposals().stream()
                        .map(DriveGoalProposal::goalName)
                        .toList()
                : List.<String>of();
        var newGoals = after.goalProposals().stream()
                .filter(g -> !prevGoalNames.contains(g.goalName()))
                .toList();

        return new CognitionDelta(moodDelta, driveDelta, mentalDeltas,
                profileDeltas, afterEpisodes - prevEpisodes,
                afterThemes - prevThemes, newGoals);
    }
}
