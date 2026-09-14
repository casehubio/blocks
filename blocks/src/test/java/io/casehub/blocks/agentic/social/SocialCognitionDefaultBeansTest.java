package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.emergence.NormDetectionConfig;
import io.casehub.blocks.agentic.social.goal.GoalEscalationConfig;
import io.casehub.blocks.agentic.social.goal.GoalProposalConfig;
import io.casehub.blocks.agentic.social.narrative.NarrativeConfig;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
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
}
