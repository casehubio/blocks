package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.neocortex.cognition.mentalmodel.MentalModelConfig;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.personality.PersonalityEvolutionConfig;
import io.casehub.neocortex.cognition.strategy.StrategyLearningConfig;
import io.casehub.neocortex.cognition.usermodel.UserModelConfig;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.emergence.CollectiveGoalConfig;
import io.casehub.neocortex.cognition.emergence.NormDetectionConfig;
import io.casehub.neocortex.cognition.goal.CognitiveGoalConfig;
import io.casehub.neocortex.cognition.goal.GoalEscalationConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalConfig;
import io.casehub.neocortex.cognition.narrative.NarrativeConfig;
import io.casehub.blocks.memory.RetentionConfig;

public record CompiledCognition(
        DriveConfig drive,
        MoodConfig mood,
        PersonalityEvolutionConfig personality,
        UserModelConfig userModel,
        StrategyLearningConfig strategyLearning,
        MentalModelConfig mentalModel,
        NarrativeConfig narrative,
        GoalProposalConfig goalProposal,
        GoalEscalationConfig goalEscalation,
        NormDetectionConfig normDetection,
        CollectiveGoalConfig collectiveGoal,
        RetentionConfig retention,
        CognitiveGoalConfig cognitiveGoal) {}
