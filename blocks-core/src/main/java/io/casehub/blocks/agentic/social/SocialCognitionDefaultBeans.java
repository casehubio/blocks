package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.emergence.NormDetectionConfig;
import io.casehub.blocks.agentic.social.goal.GoalEscalationConfig;
import io.casehub.blocks.agentic.social.goal.GoalProposalConfig;
import io.casehub.blocks.agentic.social.narrative.NarrativeConfig;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class SocialCognitionDefaultBeans {

    @Produces
    @DefaultBean
    @Singleton
    DriveConfig driveConfig() {
        return DriveConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    MoodConfig moodConfig() {
        return MoodConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    PersonalityEvolutionConfig personalityEvolutionConfig() {
        return PersonalityEvolutionConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    InnerLifeConfig innerLifeConfig() {
        return InnerLifeConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    MentalModelConfig mentalModelConfig() {
        return MentalModelConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    UserModelConfig userModelConfig() {
        return UserModelConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    StrategyLearningConfig strategyLearningConfig() {
        return StrategyLearningConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    NarrativeConfig narrativeConfig() {
        return NarrativeConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    GoalProposalConfig goalProposalConfig() {
        return GoalProposalConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    GoalEscalationConfig goalEscalationConfig() {
        return GoalEscalationConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    NormDetectionConfig normDetectionConfig() {
        return NormDetectionConfig.defaults();
    }

    @Produces
    @DefaultBean
    @Singleton
    EventStreamBus<DecisionSignal> decisionSignalBus() {
        return new EventStreamBus<>();
    }

    @Produces
    @DefaultBean
    @Singleton
    SubjectResolver subjectResolver() {
        return (agentId, tenantId) -> java.util.Set.of();
    }

    @Produces
    @DefaultBean
    @Singleton
    InteractionMapper interactionMapper() {
        return (agentId, targetId, interactionType) ->
                CognitiveImpact.fromText(interactionType);
    }

    @Produces
    @DefaultBean
    @Singleton
    io.casehub.blocks.agentic.social.emergence.NormFilter normFilter() {
        return (norms, agentId, tenantId) -> norms;
    }
}
