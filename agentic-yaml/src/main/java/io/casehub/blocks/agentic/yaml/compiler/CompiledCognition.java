package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.agentic.social.MentalModelConfig;
import io.casehub.blocks.agentic.social.MoodConfig;
import io.casehub.blocks.agentic.social.PersonalityEvolutionConfig;
import io.casehub.blocks.agentic.social.StrategyLearningConfig;
import io.casehub.blocks.agentic.social.UserModelConfig;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.emergence.CollectiveGoalConfig;
import io.casehub.blocks.agentic.social.emergence.NormDetectionConfig;
import io.casehub.blocks.agentic.social.goal.CognitiveGoalConfig;
import io.casehub.blocks.agentic.social.goal.GoalEscalationConfig;
import io.casehub.blocks.agentic.social.goal.GoalProposalConfig;
import io.casehub.blocks.agentic.social.narrative.NarrativeConfig;
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
