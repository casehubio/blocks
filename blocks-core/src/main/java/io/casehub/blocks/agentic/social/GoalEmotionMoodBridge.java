package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.goal.CognitiveGoalOrchestrator;
import io.casehub.neocortex.cognitive.CognitiveEmotion;

public class GoalEmotionMoodBridge implements CognitionTickParticipant {

    private final CognitiveGoalOrchestrator goalOrchestrator;
    private final MoodOrchestrator moodOrchestrator;

    public GoalEmotionMoodBridge(CognitiveGoalOrchestrator goalOrchestrator,
                                  MoodOrchestrator moodOrchestrator) {
        this.goalOrchestrator = goalOrchestrator;
        this.moodOrchestrator = moodOrchestrator;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var agentId = context.agentId();
        var tenantId = context.tenantId();

        var stateOpt = goalOrchestrator.currentState(agentId, tenantId);
        if (stateOpt.isEmpty()) return;

        var emotions = stateOpt.get().goals().stream()
                .flatMap(ge -> ge.emotions().stream())
                .toList();

        if (emotions.isEmpty()) return;

        double totalIntensity = emotions.stream()
                .mapToDouble(CognitiveEmotion::intensity)
                .sum();

        if (totalIntensity <= 0) return;

        double weightedPleasure = emotions.stream()
                .mapToDouble(e -> e.intensity() * e.pad().pleasure())
                .sum() / totalIntensity;
        double weightedArousal = emotions.stream()
                .mapToDouble(e -> e.intensity() * e.pad().arousal())
                .sum() / totalIntensity;
        double weightedDominance = emotions.stream()
                .mapToDouble(e -> e.intensity() * e.pad().dominance())
                .sum() / totalIntensity;

        var signal = new MoodSignal.DirectShift(
                clamp(weightedPleasure), clamp(weightedArousal),
                clamp(weightedDominance), "goal-emotions");
        moodOrchestrator.record(signal, agentId, tenantId);
    }

    private static double clamp(double value) {
        return Math.max(-2.0, Math.min(2.0, value));
    }
}
