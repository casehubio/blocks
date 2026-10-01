package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.neocortex.cognition.mentalmodel.MentalModelConfig;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.personality.PersonalityEvolutionConfig;
import io.casehub.neocortex.cognition.relationship.RelationshipStageConfig;
import io.casehub.neocortex.cognition.strategy.StrategyLearningConfig;
import io.casehub.neocortex.cognition.usermodel.UserModelConfig;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.emergence.CollectiveGoalConfig;
import io.casehub.neocortex.cognition.emergence.NormDetectionConfig;
import io.casehub.neocortex.cognition.goal.CognitiveGoalConfig;
import io.casehub.neocortex.cognition.goal.GoalEscalationConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalConfig;
import io.casehub.neocortex.cognition.narrative.NarrativeConfig;
import io.casehub.neocortex.cognition.narrative.NarrativeSynthesisGate;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitiveGoalConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.CollectiveGoalConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.DriveConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.GoalEscalationConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.GoalProposalConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.MentalModelConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.MoodConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.NarrativeConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.NarrativeSynthesisGateSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.NormDetectionConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.PersonalityEvolutionConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.RelationshipStageConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.RetentionConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.StrategyLearningConfigSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.UserModelConfigSpec;
import io.casehub.blocks.memory.RetentionConfig;
import org.jspecify.annotations.Nullable;

public class CognitionCompiler {

    public CompiledCognition compile(CognitionDefinition definition) {
        return new CompiledCognition(
                compileDrive(definition.drive()),
                compileMood(definition.mood()),
                compilePersonality(definition.personality()),
                compileUserModel(definition.userModel()),
                compileStrategyLearning(definition.strategyLearning()),
                compileMentalModel(definition.mentalModel()),
                compileNarrative(definition.narrative()),
                compileGoalProposal(definition.goalProposal()),
                compileGoalEscalation(definition.goalEscalation()),
                compileNormDetection(definition.normDetection()),
                compileCollectiveGoal(definition.collectiveGoal()),
                compileRetention(definition.retention()),
                compileCognitiveGoal(definition.cognitiveGoal()));
    }

    private DriveConfig compileDrive(@Nullable DriveConfigSpec spec) {
        if (spec == null) return DriveConfig.defaults();
        var d = DriveConfig.defaults();
        return new DriveConfig(
                spec.axisWeights() != null ? spec.axisWeights() : d.axisWeights(),
                spec.changeThreshold() != null ? spec.changeThreshold() : d.changeThreshold(),
                spec.moodPleasureModulation() != null ? spec.moodPleasureModulation() : d.moodPleasureModulation(),
                spec.moodArousalModulation() != null ? spec.moodArousalModulation() : d.moodArousalModulation(),
                spec.personalityModulationStrength() != null ? spec.personalityModulationStrength() : d.personalityModulationStrength(),
                spec.maxIntensity() != null ? spec.maxIntensity() : d.maxIntensity(),
                spec.minIntensity() != null ? spec.minIntensity() : d.minIntensity(),
                spec.affiliationDecayThreshold() != null ? spec.affiliationDecayThreshold() : d.affiliationDecayThreshold(),
                spec.affiliationStaleDuration() != null ? spec.affiliationStaleDuration() : d.affiliationStaleDuration(),
                spec.autonomyConfidenceFloor() != null ? spec.autonomyConfidenceFloor() : d.autonomyConfidenceFloor(),
                spec.narrativeModulationStrength() != null ? spec.narrativeModulationStrength() : d.narrativeModulationStrength());
    }

    private MoodConfig compileMood(@Nullable MoodConfigSpec spec) {
        if (spec == null) return MoodConfig.defaults();
        var d = MoodConfig.defaults();
        return new MoodConfig(
                spec.baseline() != null ? spec.baseline() : d.baseline(),
                spec.decayTimeConstant() != null ? spec.decayTimeConstant() : d.decayTimeConstant(),
                spec.maxDisplacement() != null ? spec.maxDisplacement() : d.maxDisplacement(),
                spec.moodInfluence() != null ? spec.moodInfluence() : d.moodInfluence(),
                spec.staleStateTimeout() != null ? spec.staleStateTimeout() : d.staleStateTimeout());
    }

    private PersonalityEvolutionConfig compilePersonality(@Nullable PersonalityEvolutionConfigSpec spec) {
        if (spec == null) return PersonalityEvolutionConfig.defaults();
        var d = PersonalityEvolutionConfig.defaults();
        return new PersonalityEvolutionConfig(
                spec.decayFactor() != null ? spec.decayFactor() : d.decayFactor(),
                spec.l2Ceiling() != null ? spec.l2Ceiling() : d.l2Ceiling(),
                spec.dampeningFactor() != null ? spec.dampeningFactor() : d.dampeningFactor());
    }

    private UserModelConfig compileUserModel(@Nullable UserModelConfigSpec spec) {
        if (spec == null) return UserModelConfig.defaults();
        var d = UserModelConfig.defaults();
        return new UserModelConfig(
                spec.minSignalsForSynthesis() != null ? spec.minSignalsForSynthesis() : d.minSignalsForSynthesis(),
                spec.synthesisCooldown() != null ? spec.synthesisCooldown() : d.synthesisCooldown(),
                spec.decayRate() != null ? spec.decayRate() : d.decayRate(),
                spec.positiveWeight() != null ? spec.positiveWeight() : d.positiveWeight(),
                spec.negativeWeight() != null ? spec.negativeWeight() : d.negativeWeight(),
                compileRelationshipStage(spec.stageConfig()),
                spec.expectedTickInterval() != null ? spec.expectedTickInterval() : d.expectedTickInterval(),
                spec.evictionTimeout() != null ? spec.evictionTimeout() : d.evictionTimeout(),
                spec.memoryDomain() != null ? spec.memoryDomain() : d.memoryDomain(),
                spec.caseType() != null ? spec.caseType() : d.caseType(),
                spec.maxObservationsInPrompt() != null ? spec.maxObservationsInPrompt() : d.maxObservationsInPrompt());
    }

    private RelationshipStageConfig compileRelationshipStage(@Nullable RelationshipStageConfigSpec spec) {
        if (spec == null) return RelationshipStageConfig.defaults();
        var d = RelationshipStageConfig.defaults();
        return new RelationshipStageConfig(
                spec.tiers() != null ? spec.tiers() : d.tiers(),
                spec.decayRate() != null ? spec.decayRate() : d.decayRate(),
                spec.positiveWeight() != null ? spec.positiveWeight() : d.positiveWeight(),
                spec.negativeWeight() != null ? spec.negativeWeight() : d.negativeWeight());
    }

    private StrategyLearningConfig compileStrategyLearning(@Nullable StrategyLearningConfigSpec spec) {
        if (spec == null) return StrategyLearningConfig.defaults();
        var d = StrategyLearningConfig.defaults();
        return new StrategyLearningConfig(
                spec.minSignalsForConversationCase() != null ? spec.minSignalsForConversationCase() : d.minSignalsForConversationCase(),
                spec.minCasesForReflection() != null ? spec.minCasesForReflection() : d.minCasesForReflection(),
                spec.maxReflectionSources() != null ? spec.maxReflectionSources() : d.maxReflectionSources(),
                spec.maxGuidelines() != null ? spec.maxGuidelines() : d.maxGuidelines(),
                spec.defaultDimensionValue() != null ? spec.defaultDimensionValue() : d.defaultDimensionValue(),
                spec.maxBufferSize() != null ? spec.maxBufferSize() : d.maxBufferSize(),
                spec.staleStateTimeout() != null ? spec.staleStateTimeout() : d.staleStateTimeout(),
                spec.memoryDomain() != null ? spec.memoryDomain() : d.memoryDomain(),
                spec.engagementCaseType() != null ? spec.engagementCaseType() : d.engagementCaseType(),
                spec.profileCaseType() != null ? spec.profileCaseType() : d.profileCaseType());
    }

    private MentalModelConfig compileMentalModel(@Nullable MentalModelConfigSpec spec) {
        if (spec == null) return MentalModelConfig.defaults();
        var d = MentalModelConfig.defaults();
        return new MentalModelConfig(
                spec.beliefHalfLife() != null ? spec.beliefHalfLife() : d.beliefHalfLife(),
                spec.desireHalfLife() != null ? spec.desireHalfLife() : d.desireHalfLife(),
                spec.intentionHalfLife() != null ? spec.intentionHalfLife() : d.intentionHalfLife(),
                spec.confidenceFloor() != null ? spec.confidenceFloor() : d.confidenceFloor(),
                spec.projectionFloor() != null ? spec.projectionFloor() : d.projectionFloor(),
                spec.minSignalsForInference() != null ? spec.minSignalsForInference() : d.minSignalsForInference(),
                spec.inferenceCooldown() != null ? spec.inferenceCooldown() : d.inferenceCooldown(),
                spec.maxSignalsInPrompt() != null ? spec.maxSignalsInPrompt() : d.maxSignalsInPrompt(),
                spec.maxBufferSize() != null ? spec.maxBufferSize() : d.maxBufferSize(),
                spec.evictionTimeout() != null ? spec.evictionTimeout() : d.evictionTimeout(),
                spec.expectedTickInterval() != null ? spec.expectedTickInterval() : d.expectedTickInterval(),
                spec.memoryDomain() != null ? spec.memoryDomain() : d.memoryDomain(),
                spec.caseType() != null ? spec.caseType() : d.caseType());
    }

    private NarrativeConfig compileNarrative(@Nullable NarrativeConfigSpec spec) {
        if (spec == null) return NarrativeConfig.defaults();
        var d = NarrativeConfig.defaults();
        return new NarrativeConfig(
                compileNarrativeSynthesisGate(spec.synthesisGate()),
                spec.maxEpisodes() != null ? spec.maxEpisodes() : d.maxEpisodes(),
                spec.maxThemes() != null ? spec.maxThemes() : d.maxThemes(),
                spec.themeSalienceFloor() != null ? spec.themeSalienceFloor() : d.themeSalienceFloor(),
                spec.maxReflectionsPerSynthesis() != null ? spec.maxReflectionsPerSynthesis() : d.maxReflectionsPerSynthesis(),
                spec.memoryDomain() != null ? spec.memoryDomain() : d.memoryDomain(),
                spec.caseType() != null ? spec.caseType() : d.caseType());
    }

    private NarrativeSynthesisGate compileNarrativeSynthesisGate(@Nullable NarrativeSynthesisGateSpec spec) {
        if (spec == null) return NarrativeSynthesisGate.defaults();
        var d = NarrativeSynthesisGate.defaults();
        return new NarrativeSynthesisGate(
                spec.minNewReflections() != null ? spec.minNewReflections() : d.minNewReflections(),
                spec.noveltyThreshold() != null ? spec.noveltyThreshold() : d.noveltyThreshold(),
                spec.quietPeriodBypass() != null ? spec.quietPeriodBypass() : d.quietPeriodBypass());
    }

    private GoalProposalConfig compileGoalProposal(@Nullable GoalProposalConfigSpec spec) {
        if (spec == null) return GoalProposalConfig.defaults();
        var d = GoalProposalConfig.defaults();
        return new GoalProposalConfig(
                spec.proposalThreshold() != null ? spec.proposalThreshold() : d.proposalThreshold(),
                spec.relevanceThreshold() != null ? spec.relevanceThreshold() : d.relevanceThreshold(),
                spec.maxDriveGoals() != null ? spec.maxDriveGoals() : d.maxDriveGoals(),
                spec.staleAfter() != null ? spec.staleAfter() : d.staleAfter(),
                spec.cooldown() != null ? spec.cooldown() : d.cooldown(),
                spec.failureAbandonmentThreshold() != null ? spec.failureAbandonmentThreshold() : d.failureAbandonmentThreshold());
    }

    private GoalEscalationConfig compileGoalEscalation(@Nullable GoalEscalationConfigSpec spec) {
        if (spec == null) return GoalEscalationConfig.defaults();
        var d = GoalEscalationConfig.defaults();
        return new GoalEscalationConfig(
                spec.escalationSalienceThreshold() != null ? spec.escalationSalienceThreshold() : d.escalationSalienceThreshold(),
                spec.minAxisAlignmentWeight() != null ? spec.minAxisAlignmentWeight() : d.minAxisAlignmentWeight(),
                spec.crossAxisMinWeight() != null ? spec.crossAxisMinWeight() : d.crossAxisMinWeight(),
                spec.minCrossAxisCount() != null ? spec.minCrossAxisCount() : d.minCrossAxisCount(),
                spec.escalationCycles() != null ? spec.escalationCycles() : d.escalationCycles(),
                spec.demotionCycles() != null ? spec.demotionCycles() : d.demotionCycles(),
                spec.maxPrimaryDriveGoals() != null ? spec.maxPrimaryDriveGoals() : d.maxPrimaryDriveGoals());
    }

    private NormDetectionConfig compileNormDetection(@Nullable NormDetectionConfigSpec spec) {
        if (spec == null) return NormDetectionConfig.defaults();
        var d = NormDetectionConfig.defaults();
        return new NormDetectionConfig(
                spec.minObservationsForNorm() != null ? spec.minObservationsForNorm() : d.minObservationsForNorm(),
                spec.establishedThreshold() != null ? spec.establishedThreshold() : d.establishedThreshold(),
                spec.decliningThreshold() != null ? spec.decliningThreshold() : d.decliningThreshold(),
                spec.minAgentsForNorm() != null ? spec.minAgentsForNorm() : d.minAgentsForNorm(),
                spec.memoryDomain() != null ? spec.memoryDomain() : d.memoryDomain(),
                spec.caseType() != null ? spec.caseType() : d.caseType());
    }

    private CollectiveGoalConfig compileCollectiveGoal(@Nullable CollectiveGoalConfigSpec spec) {
        if (spec == null) return CollectiveGoalConfig.defaults();
        var d = CollectiveGoalConfig.defaults();
        return new CollectiveGoalConfig(
                spec.alignmentThreshold() != null ? spec.alignmentThreshold() : d.alignmentThreshold(),
                spec.minAlignedAgents() != null ? spec.minAlignedAgents() : d.minAlignedAgents(),
                spec.cooldown() != null ? spec.cooldown() : d.cooldown());
    }

    private RetentionConfig compileRetention(@Nullable RetentionConfigSpec spec) {
        if (spec == null) return RetentionConfig.DEFAULT;
        var d = RetentionConfig.DEFAULT;
        return new RetentionConfig(
                spec.retentionThreshold() != null ? spec.retentionThreshold() : d.retentionThreshold(),
                spec.confidenceWeight() != null ? spec.confidenceWeight() : d.confidenceWeight(),
                spec.recencyWeight() != null ? spec.recencyWeight() : d.recencyWeight(),
                spec.scopeWeight() != null ? spec.scopeWeight() : d.scopeWeight(),
                spec.trustWeight() != null ? spec.trustWeight() : d.trustWeight());
    }

    private CognitiveGoalConfig compileCognitiveGoal(@Nullable CognitiveGoalConfigSpec spec) {
        if (spec == null) {return CognitiveGoalConfig.defaults();}
        var d = CognitiveGoalConfig.defaults();
        return new CognitiveGoalConfig(
                spec.caseCreationThreshold() != null ? spec.caseCreationThreshold() : d.caseCreationThreshold(),
                spec.surfacingCooldown() != null ? spec.surfacingCooldown() : d.surfacingCooldown(),
                spec.minimumSurfacingPriority() != null ? spec.minimumSurfacingPriority() : d.minimumSurfacingPriority(),
                spec.driveWeight() != null ? spec.driveWeight() : d.driveWeight());
    }

}
