package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.emergence.NormDetectionConfig;
import io.casehub.blocks.agentic.social.goal.GoalEscalationConfig;
import io.casehub.blocks.agentic.social.goal.GoalProposalConfig;
import io.casehub.blocks.agentic.social.narrative.NarrativeConfig;
import io.casehub.blocks.agentic.social.emergence.NormDetectionConfig;
import io.casehub.blocks.agentic.social.emergence.NormFilter;
import io.casehub.blocks.agentic.social.emergence.NormStrength;
import io.casehub.blocks.agentic.social.emergence.SocialNorm;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocialCognitionDefaultBeansTest {

    private final SocialCognitionDefaultBeans beans = new SocialCognitionDefaultBeans();

    @Test
    void driveConfigProducesDefaults() {
        assertThat(beans.driveConfig()).isEqualTo(DriveConfig.defaults());
    }

    @Test
    void moodConfigProducesDefaults() {
        assertThat(beans.moodConfig()).isEqualTo(MoodConfig.defaults());
    }

    @Test
    void personalityEvolutionConfigProducesDefaults() {
        assertThat(beans.personalityEvolutionConfig()).isEqualTo(PersonalityEvolutionConfig.defaults());
    }

    @Test
    void innerLifeConfigProducesDefaults() {
        assertThat(beans.innerLifeConfig()).isEqualTo(InnerLifeConfig.defaults());
    }

    @Test
    void mentalModelConfigProducesDefaults() {
        assertThat(beans.mentalModelConfig()).isEqualTo(MentalModelConfig.defaults());
    }

    @Test
    void userModelConfigProducesDefaults() {
        assertThat(beans.userModelConfig()).isEqualTo(UserModelConfig.defaults());
    }

    @Test
    void strategyLearningConfigProducesDefaults() {
        assertThat(beans.strategyLearningConfig()).isEqualTo(StrategyLearningConfig.defaults());
    }

    @Test
    void narrativeConfigProducesDefaults() {
        assertThat(beans.narrativeConfig()).isEqualTo(NarrativeConfig.defaults());
    }

    @Test
    void goalProposalConfigProducesDefaults() {
        assertThat(beans.goalProposalConfig()).isEqualTo(GoalProposalConfig.defaults());
    }

    @Test
    void goalEscalationConfigProducesDefaults() {
        assertThat(beans.goalEscalationConfig()).isEqualTo(GoalEscalationConfig.defaults());
    }

    @Test
    void normDetectionConfigProducesDefaults() {
        assertThat(beans.normDetectionConfig()).isEqualTo(NormDetectionConfig.defaults());
    }

    @Test
    void decisionSignalBusProducesNonNull() {
        EventStreamBus<DecisionSignal> bus = beans.decisionSignalBus();
        assertThat(bus).isNotNull();
    }

    @Test
    void subjectResolverProducesEmptySet() {
        SubjectResolver resolver = beans.subjectResolver();
        assertThat(resolver.relevantSubjects("any", "any")).isEmpty();
    }

    @Test
    void interactionMapperProducesPassthrough() {
        InteractionMapper mapper = beans.interactionMapper();
        CognitiveImpact impact = mapper.mapInteraction("a", "b", "greet");
        assertThat(impact.userModelSignal()).isNotNull();
        assertThat(impact.userModelSignal().description()).isEqualTo("greet");
        assertThat(impact.moodSignal()).isNull();
        assertThat(impact.suppressBdiExtraction()).isFalse();
    }

    @Test
    void normFilterProducesPassthrough() {
        NormFilter filter = beans.normFilter();
        var norm = new SocialNorm("n1", "be polite", "politeness", 0.8, 5,
                Set.of("a", "b"), Instant.now(), Instant.now(), NormStrength.ESTABLISHED);
        var result = filter.filter(List.of(norm), "agent", "tenant");
        assertThat(result).containsExactly(norm);
    }
}
