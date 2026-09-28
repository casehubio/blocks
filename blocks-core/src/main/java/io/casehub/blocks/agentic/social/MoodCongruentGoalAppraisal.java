package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.mindmap.AppraisalContext;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;

import java.util.List;

public class MoodCongruentGoalAppraisal implements GoalAppraisal {

    private final GoalAppraisal delegate;
    private final MoodOrchestrator moodOrchestrator;
    private final MoodCongruenceConfig config;

    public MoodCongruentGoalAppraisal(GoalAppraisal delegate,
                                       MoodOrchestrator moodOrchestrator,
                                       MoodCongruenceConfig config) {
        this.delegate = delegate;
        this.moodOrchestrator = moodOrchestrator;
        this.config = config;
    }

    @Override
    public List<CognitiveEmotion> appraise(MindMapNode goal, AppraisalContext context) {
        var mood = moodOrchestrator.currentMood(context.agentId(), context.tenantId());
        if (mood.isEmpty()) {
            return delegate.appraise(goal, context);
        }

        var modulated = modulateWeights(context.weights(), mood.get(), context.moodBaseline());
        var modCtx = new AppraisalContext(
                context.tenantId(), context.agentId(), context.moodBaseline(),
                context.surfacingCount(), context.lastProgressAt(), context.lastSurfacedAt(),
                context.relationshipScores(), modulated);
        return delegate.appraise(goal, modCtx);
    }

    AppraisalWeights modulateWeights(AppraisalWeights weights, MoodState mood, PadProjection baseline) {
        double arousalDisp = mood.arousal() - baseline.arousal();
        double pleasureDisp = mood.pleasure() - baseline.pleasure();

        double fearMult = 1.0
                - config.arousalCongruenceFactor() * arousalDisp
                + config.pleasureCongruenceFactor() * pleasureDisp;
        fearMult = Math.clamp(fearMult, config.minThresholdMultiplier(), config.maxThresholdMultiplier());

        double urgencyMult = 1.0 + config.arousalCongruenceFactor() * arousalDisp * 0.5;
        urgencyMult = Math.clamp(urgencyMult, 0.5, 2.0);

        return new AppraisalWeights(
                weights.urgencyWeight() * urgencyMult,
                weights.relationshipWeight(),
                weights.fearOnsetThreshold() * fearMult,
                weights.selfStandardsStrictness(),
                weights.otherStandardsStrictness());
    }
}
